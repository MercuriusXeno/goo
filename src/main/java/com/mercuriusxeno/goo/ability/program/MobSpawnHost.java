package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * A host a mob can be conjured at: the cell the mob stands in, and the
 * point the goo morphs into it from. A blob's landing conjures in the cell
 * it landed in; a tap's drip conjures in the cell below the tap.
 * spawn-goo-morphs-into-the-mob-it-births
 */
public interface MobSpawnHost extends StepHost {

    /**
     * Returns the server level the mob joins.
     *
     * @return the level
     */
    ServerLevel level();

    /**
     * Returns the cell the conjured mob stands in.
     *
     * @return the cell
     */
    BlockPos spawnCell();

    /**
     * Returns the point the goo morphs into the mob from.
     *
     * @return the morph's start
     */
    Vec3 morphFrom();
}
