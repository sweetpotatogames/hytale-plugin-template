package com.example.dnd.combat;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.UUID;

/**
 * Handles blocking player actions when it's not their turn.
 */
public class CombatEventHandler {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final TurnManager turnManager;

    public CombatEventHandler(TurnManager turnManager) {
        this.turnManager = turnManager;
    }

    /**
     * Handle mouse button events - cancel if not the player's turn.
     */
    public void onPlayerMouseButton(PlayerMouseButtonEvent event) {
        PlayerRef playerRef = event.getPlayerRefComponent();
        World world = event.getPlayerRef().getStore().getExternalData().getWorld();

        CombatState combatState = turnManager.getCombatState(world);

        // If no combat active, allow all actions
        if (!combatState.isCombatActive()) {
            return;
        }

        UUID playerId = playerRef.getUuid();

        // If it's not this player's turn, cancel the action
        if (!combatState.isPlayerTurn(playerId)) {
            event.setCancelled(true);

            // Notify the player
            String currentPlayer = combatState.getCurrentPlayerName();
            playerRef.sendMessage(Message.raw(
                "[D&D] Not your turn! Waiting for: " + currentPlayer
            ));

            LOGGER.atFine().log("Blocked action from %s - not their turn (current: %s)",
                playerRef.getUsername(), currentPlayer);
        }
    }
}
