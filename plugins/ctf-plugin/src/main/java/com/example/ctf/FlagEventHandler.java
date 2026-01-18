package com.example.ctf;

import com.example.ctf.match.MatchManager;
import com.example.ctf.match.MatchState;
import com.example.ctf.team.TeamManager;
import com.example.ctf.team.TeamVisualManager;
import com.example.ctf.ui.CTFScoreHud;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.TransformComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Handles CTF flag-related events:
 * - Drop item requests (G key) to drop the flag
 *
 * Note: Death detection is handled via FlagCarrierManager's tick system
 * which checks for dead carriers using the DeathComponent.
 */
public class FlagEventHandler {

    private final CTFPlugin plugin;

    public FlagEventHandler(@Nonnull CTFPlugin plugin) {
        this.plugin = plugin;
        registerEvents();
    }

    private void registerEvents() {
        // Listen for drop item requests (G key)
        plugin.getEventRegistry().register(
            DropItemEvent.PlayerRequest.class,
            this::onDropItemRequest
        );

        // Listen for player connect/disconnect for HUD and team management
        plugin.getEventRegistry().registerGlobal(
            PlayerConnectEvent.class,
            this::onPlayerConnect
        );

        plugin.getEventRegistry().registerGlobal(
            PlayerDisconnectEvent.class,
            this::onPlayerDisconnect
        );

        plugin.getLogger().atInfo().log("FlagEventHandler: Event listeners registered");
    }

    /**
     * Called when a player presses G to drop an item.
     * If they're dropping the flag item, we handle it specially.
     */
    private void onDropItemRequest(@Nonnull DropItemEvent.PlayerRequest event) {
        // Get the player from the event context
        Ref<EntityStore> entityRef = event.getEntityRef();
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        Player player = entityRef.getStore().getComponent(entityRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        PlayerRef playerRef = player.getPlayerRef();
        if (playerRef == null) {
            return;
        }

        UUID playerUuid = playerRef.getUuid();
        FlagCarrierManager flagManager = plugin.getFlagCarrierManager();

        // Check if this player is carrying a flag
        if (!flagManager.isCarryingFlag(playerUuid)) {
            return;
        }

        // Check if they're trying to drop from slot 0 (the flag slot)
        if (event.getSlotId() != 0) {
            // Not dropping from the flag slot, allow normal behavior
            return;
        }

        // Check if the item being dropped is a flag
        ItemStack droppingItem = player.getInventory().getHotbar().getItemStack(event.getSlotId());
        if (droppingItem == null || ItemStack.isEmpty(droppingItem)) {
            return;
        }

        String itemId = droppingItem.getItem().getId();
        if (itemId == null || !itemId.startsWith("CTF_Flag")) {
            return;
        }

        // Cancel the normal drop - we handle it specially
        event.setCancelled(true);

        // Get player position for drop location
        TransformComponent transform = entityRef.getStore()
            .getComponent(entityRef, TransformComponent.getComponentType());

        Vector3d dropPosition;
        if (transform != null) {
            dropPosition = transform.getTranslation();
        } else {
            dropPosition = new Vector3d(0, 0, 0);
        }

        // Drop the flag through our manager
        flagManager.dropFlag(playerUuid, dropPosition);

        plugin.getLogger().atInfo().log("Player {} dropped flag via G key at {}",
            playerUuid, dropPosition);
    }

    /**
     * Called when a player connects to the server.
     * Shows score HUD if a match is active.
     */
    private void onPlayerConnect(@Nonnull PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) {
            return;
        }

        // If a match is active, show the score HUD to the new player
        MatchManager matchManager = plugin.getMatchManager();
        if (matchManager != null && matchManager.getState() == MatchState.ACTIVE) {
            int redScore = matchManager.getScore(FlagTeam.RED);
            int blueScore = matchManager.getScore(FlagTeam.BLUE);

            CTFScoreHud.showToPlayer(playerRef, plugin);
            CTFScoreHud hud = CTFScoreHud.getHud(playerRef.getUuid());
            if (hud != null) {
                hud.updateScore(redScore, blueScore);
            }
        }

        plugin.getLogger().atDebug().log("Player {} connected", playerRef.getUuid());
    }

    /**
     * Called when a player disconnects from the server.
     * Cleans up their HUD, team assignment, and visual effects.
     */
    private void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) {
            return;
        }

        UUID playerUuid = playerRef.getUuid();

        // Clean up score HUD tracking
        CTFScoreHud.onPlayerDisconnect(playerUuid);

        // Clean up team visual tracking
        TeamVisualManager visualManager = plugin.getTeamVisualManager();
        if (visualManager != null) {
            visualManager.onPlayerDisconnect(playerUuid);
        }

        // Handle team manager disconnect (removes from team)
        TeamManager teamManager = plugin.getTeamManager();
        if (teamManager != null) {
            teamManager.handlePlayerDisconnect(playerUuid);
        }

        // Drop flag if carrying one
        FlagCarrierManager flagManager = plugin.getFlagCarrierManager();
        if (flagManager.isCarryingFlag(playerUuid)) {
            // Get position for drop
            Ref<EntityStore> entityRef = playerRef.getReference();
            Vector3d dropPosition = new Vector3d(0, 0, 0);
            if (entityRef != null && entityRef.isValid()) {
                TransformComponent transform = entityRef.getStore()
                    .getComponent(entityRef, TransformComponent.getComponentType());
                if (transform != null) {
                    dropPosition = transform.getTranslation();
                }
            }
            flagManager.dropFlag(playerUuid, dropPosition);
        }

        plugin.getLogger().atDebug().log("Player {} disconnected, cleaned up CTF state", playerUuid);
    }

}
