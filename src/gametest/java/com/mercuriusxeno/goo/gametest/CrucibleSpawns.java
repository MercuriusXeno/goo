package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Spawns still item entities at spots on a crucible standing at {@link #CRUCIBLE_POS},
 * shared by the crucible gametests so each reads the basin from {@link CrucibleBasin}.
 */
final class CrucibleSpawns {

    /** The crucible block's low corner in test-relative coords. */
    private static final double BLOCK_ORIGIN = 1.0;
    /** The basin's center in test-relative X and Z. */
    static final double BASIN_CENTER_XZ = BLOCK_ORIGIN + 0.5;
    /** The basin floor in test-relative Y. */
    static final double BASIN_FLOOR_Y = BLOCK_ORIGIN + CrucibleBasin.FLOOR_Y;
    /** The crucible's position in test-relative coords. */
    static final BlockPos CRUCIBLE_POS = new BlockPos(1, 1, 1);
    /** A drop height just above the goo surface or floor, inside the cavity. */
    private static final double JUST_ABOVE_SURFACE = 0.05;

    private CrucibleSpawns() {}

    /**
     * Spawns a still item entity at the basin center, just above the goo surface
     * the crucible's reservoir stands at, or the floor when it is empty.
     *
     * @param helper the gametest helper
     * @param stack  the stack the entity carries
     * @return the spawned entity
     */
    static ItemEntity spawnInBasin(GameTestHelper helper, ItemStack stack) {
        CrucibleBlockEntity crucible = helper.getBlockEntity(CRUCIBLE_POS, CrucibleBlockEntity.class);
        double restY = BLOCK_ORIGIN + CrucibleBasin.itemRestY(crucible.basinVolumes());
        return spawnAt(helper, stack, new Vec3(BASIN_CENTER_XZ, restY + JUST_ABOVE_SURFACE, BASIN_CENTER_XZ));
    }

    /**
     * Spawns a still item entity at a test-relative point.
     *
     * @param helper the gametest helper
     * @param stack  the stack the entity carries
     * @param at     the test-relative spawn point
     * @return the spawned entity
     */
    static ItemEntity spawnAt(GameTestHelper helper, ItemStack stack, Vec3 at) {
        Vec3 absolute = helper.absoluteVec(at);
        ItemEntity entity = new ItemEntity(helper.getLevel(), absolute.x, absolute.y, absolute.z, stack);
        entity.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    /**
     * @param helper the gametest helper
     * @param entity the item entity
     * @return the entity's position relative to the crucible block's low corner
     */
    static Vec3 relativeToCrucible(GameTestHelper helper, ItemEntity entity) {
        return helper.relativeVec(entity.position()).subtract(BLOCK_ORIGIN, BLOCK_ORIGIN, BLOCK_ORIGIN);
    }
}
