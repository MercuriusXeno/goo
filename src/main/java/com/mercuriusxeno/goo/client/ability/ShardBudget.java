package com.mercuriusxeno.goo.client.ability;

/**
 * How a Blast's broken cells share the shards a client throws: every nth
 * cell throws, each throwing the same count, the whole held under a budget
 * so the shards evict none of the explosion's cloud, poof and smoke.
 * blast-broken-blocks-throw-their-own-break-shards
 *
 * @param everyNthCell  one in this many broken cells throws shards
 * @param shardsPerCell the shards each throwing cell throws
 */
record ShardBudget(int everyNthCell, int shardsPerCell) {

    /**
     * The particles one client particle group holds before it evicts its
     * oldest: ParticleGroup.MAX_PARTICLES in the 26.1 client, read from
     * net.minecraft.client.particle.ParticleGroup. Terrain shards share the
     * single-quad group with the explosion's poof and smoke.
     */
    static final int CLIENT_PARTICLE_CAP = 16_384;

    /** The shards one Blast throws at most, a quarter of the cap, leaving the rest to the cloud and smoke. */
    static final int TOTAL_SHARDS = CLIENT_PARTICLE_CAP / 4;

    /** The shards a mined full cube throws: ClientLevel.addDestroyBlockEffect's 4 by 4 by 4 grid. */
    static final int FULL_BREAK_SHARDS = 64;

    /**
     * Shares the budget over a Blast's broken cells: a full break each where
     * the budget holds it, fewer shards a cell as the cells grow, and past
     * one shard a cell, every nth cell alone throws one.
     *
     * @param cellCount the cells the Blast broke
     * @return how the cells throw their shards
     */
    static ShardBudget forCells(int cellCount) {
        if (cellCount <= TOTAL_SHARDS) {
            return new ShardBudget(1, Math.min(FULL_BREAK_SHARDS, TOTAL_SHARDS / Math.max(1, cellCount)));
        }
        return new ShardBudget(Math.ceilDiv(cellCount, TOTAL_SHARDS), 1);
    }

    /**
     * The shards thrown in all over these cells.
     *
     * @param cellCount the cells the Blast broke
     * @return the throwing cells times each one's shards
     */
    int total(int cellCount) {
        return Math.ceilDiv(cellCount, everyNthCell) * shardsPerCell;
    }

    /**
     * Whether the cell at this index among the broken cells throws shards.
     *
     * @param index the cell's index in the payload
     * @return true for every nth cell
     */
    boolean throwsAt(int index) {
        return index % everyNthCell == 0;
    }
}
