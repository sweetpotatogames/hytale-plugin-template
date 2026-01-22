package com.example.dnd.combat;

import com.hypixel.hytale.server.core.universe.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages turn-based combat across worlds.
 */
public class TurnManager {
    private static TurnManager instance;

    // Combat state per world
    private final Map<UUID, CombatState> worldCombatStates = new ConcurrentHashMap<>();

    private TurnManager() {}

    /**
     * Get the singleton instance.
     */
    public static TurnManager get() {
        if (instance == null) {
            instance = new TurnManager();
        }
        return instance;
    }

    /**
     * Get or create the combat state for a world.
     */
    public CombatState getCombatState(World world) {
        return worldCombatStates.computeIfAbsent(
            world.getWorldConfig().getUuid(),
            k -> new CombatState()
        );
    }

    /**
     * Get combat state by world UUID directly.
     */
    public CombatState getCombatState(UUID worldId) {
        return worldCombatStates.computeIfAbsent(worldId, k -> new CombatState());
    }

    /**
     * Check if it's a player's turn in the given world.
     */
    public boolean isPlayerTurn(World world, UUID playerId) {
        CombatState state = worldCombatStates.get(world.getWorldConfig().getUuid());
        return state != null && state.isPlayerTurn(playerId);
    }

    /**
     * Check if combat is active in a world.
     */
    public boolean isCombatActive(World world) {
        CombatState state = worldCombatStates.get(world.getWorldConfig().getUuid());
        return state != null && state.isCombatActive();
    }

    /**
     * Clear combat state for a world.
     */
    public void clearCombatState(World world) {
        worldCombatStates.remove(world.getWorldConfig().getUuid());
    }
}
