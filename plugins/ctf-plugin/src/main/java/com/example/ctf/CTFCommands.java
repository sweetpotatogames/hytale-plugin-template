package com.example.ctf;

import com.example.ctf.arena.ArenaManager;
import com.example.ctf.arena.CaptureZone;
import com.example.ctf.arena.ProtectedRegion;
import com.example.ctf.match.MatchManager;
import com.example.ctf.match.MatchState;
import com.example.ctf.team.TeamManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.SubCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.TransformComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;

/**
 * CTF commands for managing the Capture The Flag game mode.
 *
 * Commands:
 * - /ctf status - Shows current flag status and match info
 * - /ctf pickup <red|blue> - Pick up a flag (OP only)
 * - /ctf drop - Drop your flag (OP only)
 * - /ctf setstand <red|blue> - Set flag stand at your location (OP only)
 * - /ctf returnflag <red|blue> - Return a flag to its stand (OP only)
 *
 * Arena Commands (OP only):
 * - /ctf setspawn <red|blue> - Add spawn point at your location
 * - /ctf clearspawns <red|blue> - Clear all spawns for team
 * - /ctf setcapture <red|blue> [radius] - Set capture zone
 * - /ctf protect add <name> - Mark position 1 for protected region
 * - /ctf protect set <name> - Mark position 2, create region
 * - /ctf protect remove <name> - Delete protected region
 * - /ctf protect list - Show all protected regions
 * - /ctf save - Save arena config
 * - /ctf preset save|load|delete|list <name> - Manage presets
 *
 * Team Commands:
 * - /ctf team join <red|blue> - Join a team
 * - /ctf team leave - Leave current team
 * - /ctf team list - Show team rosters
 *
 * Match Commands (OP only):
 * - /ctf start - Start the match
 * - /ctf end - End match early
 * - /ctf reset - Reset scores and match
 * - /ctf score - Show current scores
 * - /ctf setlimit <n> - Set capture limit to win
 */
public class CTFCommands extends CommandBase {

    private final CTFPlugin plugin;

    public CTFCommands(@Nonnull CTFPlugin plugin) {
        super("ctf", "Capture The Flag commands");
        this.plugin = plugin;

        // Flag commands
        addSubCommand(new StatusSubCommand());
        addSubCommand(new PickupSubCommand());
        addSubCommand(new DropSubCommand());
        addSubCommand(new SetStandSubCommand());
        addSubCommand(new ReturnFlagSubCommand());

        // Arena commands
        addSubCommand(new SetSpawnSubCommand());
        addSubCommand(new ClearSpawnsSubCommand());
        addSubCommand(new SetCaptureSubCommand());
        addSubCommand(new ProtectSubCommand());
        addSubCommand(new SaveSubCommand());
        addSubCommand(new PresetSubCommand());

        // Team commands
        addSubCommand(new TeamSubCommand());

        // Match commands
        addSubCommand(new StartSubCommand());
        addSubCommand(new EndSubCommand());
        addSubCommand(new ResetSubCommand());
        addSubCommand(new ScoreSubCommand());
        addSubCommand(new SetLimitSubCommand());
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== CTF Plugin Commands ==="));
        ctx.sendMessage(Message.raw("Flag: /ctf status|pickup|drop|setstand|returnflag"));
        ctx.sendMessage(Message.raw("Arena: /ctf setspawn|clearspawns|setcapture|protect|save|preset"));
        ctx.sendMessage(Message.raw("Team: /ctf team join|leave|list"));
        ctx.sendMessage(Message.raw("Match: /ctf start|end|reset|score|setlimit"));
    }

    // ==================== Flag Commands ====================

    private class StatusSubCommand extends SubCommand {
        public StatusSubCommand() {
            super("status", "Shows current flag and match status");
            setPermissionGroup(GameMode.Adventure);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            FlagCarrierManager flagManager = plugin.getFlagCarrierManager();
            MatchManager matchManager = plugin.getMatchManager();
            TeamManager teamManager = plugin.getTeamManager();

            ctx.sendMessage(Message.raw("=== CTF Status ==="));

            // Match status
            if (matchManager != null) {
                MatchState state = matchManager.getState();
                ctx.sendMessage(Message.raw("Match: " + state + " | " + matchManager.getScoreString()));
                if (state == MatchState.ACTIVE) {
                    ctx.sendMessage(Message.raw("First to " + matchManager.getScoreLimit() + " captures wins!"));
                }
            }

            // Flag status
            for (FlagTeam team : FlagTeam.values()) {
                FlagData flagData = flagManager.getFlagData(team);
                FlagState state = flagData.getState();
                String status = switch (state) {
                    case AT_STAND -> "At stand";
                    case CARRIED -> "Carried by " + flagData.getCarrierUuid();
                    case DROPPED -> flagData.hasImmunity() ? "Dropped (immune)" : "Dropped";
                };
                ctx.sendMessage(Message.raw(team.getDisplayName() + " flag: " + status));
            }

            // Player's team and flag status
            PlayerRef playerRef = ctx.getPlayerRef();
            if (playerRef != null && teamManager != null) {
                UUID playerUuid = playerRef.getUuid();
                FlagTeam playerTeam = teamManager.getPlayerTeam(playerUuid);
                if (playerTeam != null) {
                    ctx.sendMessage(Message.raw("Your team: " + playerTeam.getDisplayName()));
                }

                FlagTeam carriedTeam = flagManager.getCarriedFlagTeam(playerUuid);
                if (carriedTeam != null) {
                    ctx.sendMessage(Message.raw("You are carrying the " + carriedTeam.getDisplayName() + " flag!"));
                }
            }
        }
    }

    private class PickupSubCommand extends SubCommand {
        public PickupSubCommand() {
            super("pickup", "Pick up a flag");
            setPermissionGroup(GameMode.Creative);
            addArgument("team", "The team flag to pick up (red or blue)");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            PlayerRef playerRef = ctx.getPlayerRef();
            if (playerRef == null) {
                ctx.sendErrorMessage(Message.raw("This command must be run as a player"));
                return;
            }

            String teamArg = ctx.getString("team", "red");
            FlagTeam team = FlagTeam.fromString(teamArg);
            if (team == null) {
                ctx.sendErrorMessage(Message.raw("Invalid team. Use 'red' or 'blue'"));
                return;
            }

            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                ctx.sendErrorMessage(Message.raw("Could not find your player entity"));
                return;
            }

            Player player = entityRef.getStore().getComponent(entityRef, Player.getComponentType());
            if (player == null) {
                ctx.sendErrorMessage(Message.raw("Could not find your player"));
                return;
            }

            FlagCarrierManager manager = plugin.getFlagCarrierManager();
            if (manager.isCarryingFlag(playerRef.getUuid())) {
                ctx.sendErrorMessage(Message.raw("You are already carrying a flag!"));
                return;
            }

            if (manager.pickupFlag(player, team)) {
                ctx.sendMessage(Message.raw("You picked up the " + team.getDisplayName() + " flag!"));
                ctx.sendMessage(Message.raw("Movement restrictions are now active. Press G to drop."));
            } else {
                ctx.sendErrorMessage(Message.raw("Could not pick up the flag. It may be carried or have immunity."));
            }
        }
    }

    private class DropSubCommand extends SubCommand {
        public DropSubCommand() {
            super("drop", "Drop your flag");
            setPermissionGroup(GameMode.Creative);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            PlayerRef playerRef = ctx.getPlayerRef();
            if (playerRef == null) {
                ctx.sendErrorMessage(Message.raw("This command must be run as a player"));
                return;
            }

            UUID playerUuid = playerRef.getUuid();
            FlagCarrierManager manager = plugin.getFlagCarrierManager();

            if (!manager.isCarryingFlag(playerUuid)) {
                ctx.sendErrorMessage(Message.raw("You are not carrying a flag!"));
                return;
            }

            Ref<EntityStore> entityRef = playerRef.getReference();
            Vector3d dropPosition = new Vector3d(0, 0, 0);
            if (entityRef != null && entityRef.isValid()) {
                TransformComponent transform = entityRef.getStore()
                    .getComponent(entityRef, TransformComponent.getComponentType());
                if (transform != null) {
                    dropPosition = transform.getTranslation();
                }
            }

            manager.dropFlag(playerUuid, dropPosition);
            ctx.sendMessage(Message.raw("You dropped the flag!"));
        }
    }

    private class SetStandSubCommand extends SubCommand {
        public SetStandSubCommand() {
            super("setstand", "Set flag stand at your location");
            setPermissionGroup(GameMode.Creative);
            addArgument("team", "The team flag stand to set (red or blue)");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            PlayerRef playerRef = ctx.getPlayerRef();
            if (playerRef == null) {
                ctx.sendErrorMessage(Message.raw("This command must be run as a player"));
                return;
            }

            String teamArg = ctx.getString("team", "red");
            FlagTeam team = FlagTeam.fromString(teamArg);
            if (team == null) {
                ctx.sendErrorMessage(Message.raw("Invalid team. Use 'red' or 'blue'"));
                return;
            }

            Vector3d position = getPlayerPosition(ctx);
            if (position == null) return;

            plugin.getFlagCarrierManager().setFlagStandPosition(team, position);
            ctx.sendMessage(Message.raw("Set " + team.getDisplayName() + " flag stand at: " + formatPosition(position)));
        }
    }

    private class ReturnFlagSubCommand extends SubCommand {
        public ReturnFlagSubCommand() {
            super("returnflag", "Return a flag to its stand");
            setPermissionGroup(GameMode.Creative);
            addArgument("team", "The team flag to return (red or blue)");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            String teamArg = ctx.getString("team", "red");
            FlagTeam team = FlagTeam.fromString(teamArg);
            if (team == null) {
                ctx.sendErrorMessage(Message.raw("Invalid team. Use 'red' or 'blue'"));
                return;
            }

            plugin.getFlagCarrierManager().returnFlagToStand(team);
            ctx.sendMessage(Message.raw(team.getDisplayName() + " flag has been returned to its stand."));
        }
    }

    // ==================== Arena Commands ====================

    private class SetSpawnSubCommand extends SubCommand {
        public SetSpawnSubCommand() {
            super("setspawn", "Add spawn point at your location");
            setPermissionGroup(GameMode.Creative);
            addArgument("team", "The team to set spawn for (red or blue)");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            ArenaManager arenaManager = plugin.getArenaManager();
            if (arenaManager == null) {
                ctx.sendErrorMessage(Message.raw("Arena system not initialized"));
                return;
            }

            String teamArg = ctx.getString("team", "red");
            FlagTeam team = FlagTeam.fromString(teamArg);
            if (team == null) {
                ctx.sendErrorMessage(Message.raw("Invalid team. Use 'red' or 'blue'"));
                return;
            }

            Transform transform = getPlayerTransform(ctx);
            if (transform == null) return;

            arenaManager.addSpawnPoint(team, transform);
            int count = arenaManager.getSpawnCount(team);
            ctx.sendMessage(Message.raw("Added " + team.getDisplayName() + " spawn point #" + count + " at: " + formatPosition(transform.getPosition())));
        }
    }

    private class ClearSpawnsSubCommand extends SubCommand {
        public ClearSpawnsSubCommand() {
            super("clearspawns", "Clear all spawn points for a team");
            setPermissionGroup(GameMode.Creative);
            addArgument("team", "The team to clear spawns for (red or blue)");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            ArenaManager arenaManager = plugin.getArenaManager();
            if (arenaManager == null) {
                ctx.sendErrorMessage(Message.raw("Arena system not initialized"));
                return;
            }

            String teamArg = ctx.getString("team", "red");
            FlagTeam team = FlagTeam.fromString(teamArg);
            if (team == null) {
                ctx.sendErrorMessage(Message.raw("Invalid team. Use 'red' or 'blue'"));
                return;
            }

            arenaManager.clearSpawnPoints(team);
            ctx.sendMessage(Message.raw("Cleared all " + team.getDisplayName() + " spawn points."));
        }
    }

    private class SetCaptureSubCommand extends SubCommand {
        public SetCaptureSubCommand() {
            super("setcapture", "Set capture zone at your location");
            setPermissionGroup(GameMode.Creative);
            addArgument("team", "The team capture zone (red or blue)");
            addArgument("radius", "Capture zone radius (default: 3)");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            ArenaManager arenaManager = plugin.getArenaManager();
            if (arenaManager == null) {
                ctx.sendErrorMessage(Message.raw("Arena system not initialized"));
                return;
            }

            String teamArg = ctx.getString("team", "red");
            FlagTeam team = FlagTeam.fromString(teamArg);
            if (team == null) {
                ctx.sendErrorMessage(Message.raw("Invalid team. Use 'red' or 'blue'"));
                return;
            }

            double radius = ctx.getDouble("radius", CaptureZone.DEFAULT_RADIUS);
            if (radius <= 0) {
                ctx.sendErrorMessage(Message.raw("Radius must be positive"));
                return;
            }

            Vector3d position = getPlayerPosition(ctx);
            if (position == null) return;

            arenaManager.setCaptureZone(team, position, radius);
            ctx.sendMessage(Message.raw("Set " + team.getDisplayName() + " capture zone at: " + formatPosition(position) + " (radius: " + radius + ")"));
        }
    }

    private class ProtectSubCommand extends SubCommand {
        public ProtectSubCommand() {
            super("protect", "Manage protected regions");
            setPermissionGroup(GameMode.Creative);
            addArgument("action", "add|set|remove|list");
            addArgument("name", "Region name (for add/set/remove)");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            ArenaManager arenaManager = plugin.getArenaManager();
            if (arenaManager == null) {
                ctx.sendErrorMessage(Message.raw("Arena system not initialized"));
                return;
            }

            String action = ctx.getString("action", "list");
            String name = ctx.getString("name", "");

            switch (action.toLowerCase()) {
                case "add" -> {
                    if (name.isEmpty()) {
                        ctx.sendErrorMessage(Message.raw("Usage: /ctf protect add <name>"));
                        return;
                    }
                    Vector3d position = getPlayerPosition(ctx);
                    if (position == null) return;

                    PlayerRef playerRef = ctx.getPlayerRef();
                    if (playerRef == null) return;

                    arenaManager.startProtectedRegion(playerRef.getUuid(), name, position);
                    ctx.sendMessage(Message.raw("Position 1 marked for region '" + name + "'. Now use '/ctf protect set " + name + "' at the opposite corner."));
                }
                case "set" -> {
                    if (name.isEmpty()) {
                        ctx.sendErrorMessage(Message.raw("Usage: /ctf protect set <name>"));
                        return;
                    }

                    PlayerRef playerRef = ctx.getPlayerRef();
                    if (playerRef == null) return;

                    String pendingName = arenaManager.getPendingRegionName(playerRef.getUuid());
                    if (pendingName == null || !pendingName.equalsIgnoreCase(name)) {
                        ctx.sendErrorMessage(Message.raw("No pending region named '" + name + "'. Use '/ctf protect add " + name + "' first."));
                        return;
                    }

                    Vector3d position = getPlayerPosition(ctx);
                    if (position == null) return;

                    ProtectedRegion region = arenaManager.finishProtectedRegion(playerRef.getUuid(), position);
                    if (region != null) {
                        ctx.sendMessage(Message.raw("Protected region '" + name + "' created: " + formatPosition(region.getMin()) + " to " + formatPosition(region.getMax())));
                    } else {
                        ctx.sendErrorMessage(Message.raw("Failed to create region."));
                    }
                }
                case "remove" -> {
                    if (name.isEmpty()) {
                        ctx.sendErrorMessage(Message.raw("Usage: /ctf protect remove <name>"));
                        return;
                    }
                    if (arenaManager.removeProtectedRegion(name)) {
                        ctx.sendMessage(Message.raw("Removed protected region '" + name + "'."));
                    } else {
                        ctx.sendErrorMessage(Message.raw("Region '" + name + "' not found."));
                    }
                }
                case "list" -> {
                    var regions = arenaManager.getProtectedRegionNames();
                    if (regions.isEmpty()) {
                        ctx.sendMessage(Message.raw("No protected regions defined."));
                    } else {
                        ctx.sendMessage(Message.raw("Protected regions: " + String.join(", ", regions)));
                    }
                }
                default -> ctx.sendErrorMessage(Message.raw("Usage: /ctf protect <add|set|remove|list> [name]"));
            }
        }
    }

    private class SaveSubCommand extends SubCommand {
        public SaveSubCommand() {
            super("save", "Save arena configuration");
            setPermissionGroup(GameMode.Creative);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            ArenaManager arenaManager = plugin.getArenaManager();
            if (arenaManager == null) {
                ctx.sendErrorMessage(Message.raw("Arena system not initialized"));
                return;
            }

            arenaManager.save();
            ctx.sendMessage(Message.raw("Arena configuration saved."));
        }
    }

    private class PresetSubCommand extends SubCommand {
        public PresetSubCommand() {
            super("preset", "Manage arena presets");
            setPermissionGroup(GameMode.Creative);
            addArgument("action", "save|load|delete|list");
            addArgument("name", "Preset name");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            ArenaManager arenaManager = plugin.getArenaManager();
            if (arenaManager == null) {
                ctx.sendErrorMessage(Message.raw("Arena system not initialized"));
                return;
            }

            String action = ctx.getString("action", "list");
            String name = ctx.getString("name", "");

            switch (action.toLowerCase()) {
                case "save" -> {
                    if (name.isEmpty()) {
                        ctx.sendErrorMessage(Message.raw("Usage: /ctf preset save <name>"));
                        return;
                    }
                    if (!isValidPresetName(name)) {
                        ctx.sendErrorMessage(Message.raw("Invalid preset name. Use only letters, numbers, underscores, and hyphens."));
                        return;
                    }
                    if (arenaManager.savePreset(name)) {
                        ctx.sendMessage(Message.raw("Saved arena preset '" + name + "'."));
                    } else {
                        ctx.sendErrorMessage(Message.raw("Failed to save preset."));
                    }
                }
                case "load" -> {
                    if (name.isEmpty()) {
                        ctx.sendErrorMessage(Message.raw("Usage: /ctf preset load <name>"));
                        return;
                    }
                    if (arenaManager.loadPreset(name)) {
                        ctx.sendMessage(Message.raw("Loaded arena preset '" + name + "'. Use '/ctf save' to persist."));
                    } else {
                        ctx.sendErrorMessage(Message.raw("Failed to load preset. Does it exist?"));
                    }
                }
                case "delete" -> {
                    if (name.isEmpty()) {
                        ctx.sendErrorMessage(Message.raw("Usage: /ctf preset delete <name>"));
                        return;
                    }
                    if (arenaManager.deletePreset(name)) {
                        ctx.sendMessage(Message.raw("Deleted preset '" + name + "'."));
                    } else {
                        ctx.sendErrorMessage(Message.raw("Preset '" + name + "' not found."));
                    }
                }
                case "list" -> {
                    var presets = arenaManager.listPresets();
                    if (presets.isEmpty()) {
                        ctx.sendMessage(Message.raw("No presets saved. Use '/ctf preset save <name>' to create one."));
                    } else {
                        ctx.sendMessage(Message.raw("Available presets: " + String.join(", ", presets)));
                    }
                }
                default -> ctx.sendErrorMessage(Message.raw("Usage: /ctf preset <save|load|delete|list> [name]"));
            }
        }

        private boolean isValidPresetName(String name) {
            return name.matches("^[a-zA-Z0-9_-]+$");
        }
    }

    // ==================== Team Commands ====================

    private class TeamSubCommand extends SubCommand {
        public TeamSubCommand() {
            super("team", "Team management");
            setPermissionGroup(GameMode.Adventure);
            addArgument("action", "join|leave|list");
            addArgument("team", "Team to join (red or blue)");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            TeamManager teamManager = plugin.getTeamManager();
            if (teamManager == null) {
                ctx.sendErrorMessage(Message.raw("Team system not initialized"));
                return;
            }

            String action = ctx.getString("action", "list");

            switch (action.toLowerCase()) {
                case "join" -> {
                    PlayerRef playerRef = ctx.getPlayerRef();
                    if (playerRef == null) {
                        ctx.sendErrorMessage(Message.raw("This command must be run as a player"));
                        return;
                    }

                    String teamArg = ctx.getString("team", "");
                    if (teamArg.isEmpty()) {
                        ctx.sendErrorMessage(Message.raw("Usage: /ctf team join <red|blue>"));
                        return;
                    }

                    FlagTeam team = FlagTeam.fromString(teamArg);
                    if (team == null) {
                        ctx.sendErrorMessage(Message.raw("Invalid team. Use 'red' or 'blue'"));
                        return;
                    }

                    // Pass player name and ref for announcement and visual effects
                    teamManager.assignTeam(playerRef.getUuid(), team, playerRef.getUsername(), playerRef);
                    ctx.sendMessage(Message.raw("You joined the " + team.getDisplayName() + " team!"));
                }
                case "leave" -> {
                    PlayerRef playerRef = ctx.getPlayerRef();
                    if (playerRef == null) {
                        ctx.sendErrorMessage(Message.raw("This command must be run as a player"));
                        return;
                    }

                    // Pass player name and ref for announcement and visual effects
                    FlagTeam leftTeam = teamManager.leaveTeam(playerRef.getUuid(), playerRef.getUsername(), playerRef);
                    if (leftTeam != null) {
                        ctx.sendMessage(Message.raw("You left the " + leftTeam.getDisplayName() + " team."));
                    } else {
                        ctx.sendErrorMessage(Message.raw("You are not on a team."));
                    }
                }
                case "list" -> {
                    ctx.sendMessage(Message.raw("=== Team Rosters ==="));
                    for (FlagTeam team : FlagTeam.values()) {
                        Set<UUID> players = teamManager.getTeamPlayers(team);
                        ctx.sendMessage(Message.raw(team.getDisplayName() + " (" + players.size() + "): " +
                            (players.isEmpty() ? "(empty)" : players.toString())));
                    }
                }
                default -> ctx.sendErrorMessage(Message.raw("Usage: /ctf team <join|leave|list> [team]"));
            }
        }
    }

    // ==================== Match Commands ====================

    private class StartSubCommand extends SubCommand {
        public StartSubCommand() {
            super("start", "Start the CTF match");
            setPermissionGroup(GameMode.Creative);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            MatchManager matchManager = plugin.getMatchManager();
            if (matchManager == null) {
                ctx.sendErrorMessage(Message.raw("Match system not initialized"));
                return;
            }

            if (matchManager.startMatch()) {
                ctx.sendMessage(Message.raw("Match started!"));
            } else {
                ctx.sendErrorMessage(Message.raw("Could not start match. Current state: " + matchManager.getState()));
            }
        }
    }

    private class EndSubCommand extends SubCommand {
        public EndSubCommand() {
            super("end", "End the CTF match");
            setPermissionGroup(GameMode.Creative);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            MatchManager matchManager = plugin.getMatchManager();
            if (matchManager == null) {
                ctx.sendErrorMessage(Message.raw("Match system not initialized"));
                return;
            }

            if (matchManager.endMatch()) {
                ctx.sendMessage(Message.raw("Match ended."));
            } else {
                ctx.sendErrorMessage(Message.raw("Match is already ended."));
            }
        }
    }

    private class ResetSubCommand extends SubCommand {
        public ResetSubCommand() {
            super("reset", "Reset the CTF match");
            setPermissionGroup(GameMode.Creative);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            MatchManager matchManager = plugin.getMatchManager();
            if (matchManager == null) {
                ctx.sendErrorMessage(Message.raw("Match system not initialized"));
                return;
            }

            matchManager.resetMatch();
            ctx.sendMessage(Message.raw("Match reset. Scores cleared."));
        }
    }

    private class ScoreSubCommand extends SubCommand {
        public ScoreSubCommand() {
            super("score", "Show current scores");
            setPermissionGroup(GameMode.Adventure);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            MatchManager matchManager = plugin.getMatchManager();
            if (matchManager == null) {
                ctx.sendErrorMessage(Message.raw("Match system not initialized"));
                return;
            }

            ctx.sendMessage(Message.raw("Score: " + matchManager.getScoreString()));
            ctx.sendMessage(Message.raw("Match state: " + matchManager.getState()));
            ctx.sendMessage(Message.raw("First to " + matchManager.getScoreLimit() + " wins."));
        }
    }

    private class SetLimitSubCommand extends SubCommand {
        public SetLimitSubCommand() {
            super("setlimit", "Set capture limit to win");
            setPermissionGroup(GameMode.Creative);
            addArgument("limit", "Number of captures to win");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            MatchManager matchManager = plugin.getMatchManager();
            if (matchManager == null) {
                ctx.sendErrorMessage(Message.raw("Match system not initialized"));
                return;
            }

            int limit = ctx.getInt("limit", 3);
            if (limit < 1) {
                ctx.sendErrorMessage(Message.raw("Limit must be at least 1"));
                return;
            }

            if (matchManager.setScoreLimit(limit)) {
                ctx.sendMessage(Message.raw("Score limit set to " + limit + " captures."));
            } else {
                ctx.sendErrorMessage(Message.raw("Cannot change limit while match is active."));
            }
        }
    }

    // ==================== Helper Methods ====================

    private Vector3d getPlayerPosition(@Nonnull CommandContext ctx) {
        PlayerRef playerRef = ctx.getPlayerRef();
        if (playerRef == null) {
            ctx.sendErrorMessage(Message.raw("This command must be run as a player"));
            return null;
        }

        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) {
            ctx.sendErrorMessage(Message.raw("Could not find your player entity"));
            return null;
        }

        TransformComponent transform = entityRef.getStore()
            .getComponent(entityRef, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendErrorMessage(Message.raw("Could not get your position"));
            return null;
        }

        return transform.getTranslation();
    }

    private Transform getPlayerTransform(@Nonnull CommandContext ctx) {
        PlayerRef playerRef = ctx.getPlayerRef();
        if (playerRef == null) {
            ctx.sendErrorMessage(Message.raw("This command must be run as a player"));
            return null;
        }

        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) {
            ctx.sendErrorMessage(Message.raw("Could not find your player entity"));
            return null;
        }

        TransformComponent transform = entityRef.getStore()
            .getComponent(entityRef, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendErrorMessage(Message.raw("Could not get your position"));
            return null;
        }

        return new Transform(
            transform.getTranslation(),
            new Vector3f(transform.getPitch(), transform.getYaw(), 0)
        );
    }

    private String formatPosition(Vector3d position) {
        return String.format("%.1f, %.1f, %.1f", position.x(), position.y(), position.z());
    }
}
