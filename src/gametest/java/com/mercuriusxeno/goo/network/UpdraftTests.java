package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametest for typhoon Updraft landed through the real effect path: its
 * column carries a zombie standing in it upward, and its blob is gone once
 * the duration ends (decision updraft-blob-stands-a-column-of-wind). The bay's
 * ceiling stands below the column's top, so the test watches the climb.
 */
public final class UpdraftTests {

    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final String UPDRAFT = "goo:typhoon_updraft";
    private static final BlockPos FLOOR = new BlockPos(2, 0, 2);
    /** Where the blob stands, on the floor's top face. */
    private static final BlockPos BLOB = FLOOR.above();
    /** Beside the blob, inside the column's width. */
    private static final BlockPos ZOMBIE_POS = BLOB.east();
    private static final int CLIMB_TICKS = 15;
    /** The least the zombie climbs in those ticks, short of the bay's ceiling. */
    private static final double LEAST_CLIMB = 2.5;
    /** typhoon_updraft.json's duration, and a few ticks past it for the blob to go. */
    private static final int PAST_THE_DURATION = 200 + 5;
    private static final String BLOB_STANDS = "The updraft's blob should stand where it landed";
    private static final String SHOULD_CLIMB = "The column should carry the zombie up %.1f blocks in %d ticks, climbed %.2f";
    private static final String BLOB_GONE = "The updraft's blob should be gone after its duration";

    private UpdraftTests() {
    }

    /**
     * An updraft lands on the floor beside a zombie: within fifteen ticks the
     * zombie has climbed two and a half blocks, and after the duration the
     * blob is gone.
     *
     * @param helper the gametest helper
     */
    public static void updraftLiftsAZombie(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(ZOMBIE_POS.below(), Blocks.STONE);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        double startY = zombie.getY();
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.TYPHOON,
                NO_ENTITY, helper.absolutePos(FLOOR), Direction.UP, UPDRAFT));
        helper.assertTrue(helper.getBlockState(BLOB).is(GooBlocks.ABILITY_BLOCK.get()), BLOB_STANDS);
        helper.runAfterDelay(CLIMB_TICKS, () -> {
            double climbed = zombie.getY() - startY;
            helper.assertTrue(climbed >= LEAST_CLIMB, String.format(SHOULD_CLIMB, LEAST_CLIMB, CLIMB_TICKS, climbed));
        });
        helper.runAfterDelay(PAST_THE_DURATION, () -> {
            helper.assertFalse(helper.getBlockState(BLOB).is(GooBlocks.ABILITY_BLOCK.get()), BLOB_GONE);
            helper.succeed();
        });
    }
}
