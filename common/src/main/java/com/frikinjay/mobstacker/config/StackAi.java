package com.frikinjay.mobstacker.config;

/**
 * What a stack of two or more mobs does with its AI (the {@code stackAi} setting). A single mob always
 * keeps its own, so a stack killed down to its last mob wakes up again.
 */
public enum StackAi {
    /** It thinks like any mob: walks, looks around, attacks, follows food. */
    NORMAL,
    /**
     * It stops thinking - no goals, no brain, no pathfinding, so it attacks nothing and follows nobody -
     * but the world still acts on it: it falls, water carries it, a hit knocks it back, and it keeps
     * afloat the way it would have swum. Farms built on water and drops keep working.
     */
    PHYSICS_ONLY,
    /** Vanilla's NoAI: it does not move at all, not even to fall. The cheapest for the server. */
    FROZEN
}
