package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * A host a mob can be born at: the cell the mob stands in, and the point the
 * goo morphs into it from. Weird's Slime births its slime in the cell its
 * blob landed in.
 * slime-creates-slimes-at-will
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
