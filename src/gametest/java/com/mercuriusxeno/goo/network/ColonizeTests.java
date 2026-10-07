package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametests for Colonize: a blob landing on a shroom network spreads that
 * network over the ground beside it, and one landing off any network starts
 * a mycelium network (decision colonize-blob-grows-the-network).
 */
public final class ColonizeTests {

    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final String COLONIZE = "goo:shroom_colonize";
    private static final BlockPos LANDED_ON = new BlockPos(2, 0, 2);
    private static final BlockPos BESIDE = LANDED_ON.east();
    private static final BlockPos TWO_OFF = LANDED_ON.south(2);
    private static final String MYCELIUM_STARTED = "The landed-on grass should turn mycelium";
    private static final String MYCELIUM_SPREAD = "Dirt beside the landing should turn mycelium";

    private ColonizeTests() {
    }

    /**
     * Colonize lands on crimson nylium with netherrack beside it and two
     * blocks off: both turn crimson nylium.
     *
     * @param helper the gametest helper
     */
    public static void colonizeSpreadsNylium(GameTestHelper helper) {
        helper.setBlock(LANDED_ON, Blocks.CRIMSON_NYLIUM);
        helper.setBlock(BESIDE, Blocks.NETHERRACK);
        helper.setBlock(TWO_OFF, Blocks.NETHERRACK);
        landOn(helper, LANDED_ON);
        helper.assertBlockPresent(Blocks.CRIMSON_NYLIUM, BESIDE);
        helper.assertBlockPresent(Blocks.CRIMSON_NYLIUM, TWO_OFF);
        helper.succeed();
    }

    /**
     * Colonize lands on grass with dirt beside it: the grass and the dirt
     * turn mycelium.
     *
     * @param helper the gametest helper
     */
    public static void colonizeStartsMycelium(GameTestHelper helper) {
        helper.setBlock(LANDED_ON, Blocks.GRASS_BLOCK);
        helper.setBlock(BESIDE, Blocks.DIRT);
        landOn(helper, LANDED_ON);
        helper.assertTrue(helper.getBlockState(LANDED_ON).is(Blocks.MYCELIUM), MYCELIUM_STARTED);
        helper.assertTrue(helper.getBlockState(BESIDE).is(Blocks.MYCELIUM), MYCELIUM_SPREAD);
        helper.succeed();
    }

    private static void landOn(GameTestHelper helper, BlockPos block) {
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.SHROOM,
                NO_ENTITY, helper.absolutePos(block), Direction.UP, COLONIZE));
    }
}
