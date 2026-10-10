package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.tap.TapDripScheduler;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametests for nether decay's tap: nether drips landing on stone leave it
 * stone short of the JSON's drip count and cobblestone at it, by the decay
 * map the channeled Decay reads (decision decay-drip-degrades-the-block-below).
 */
public final class DecayTapTests {

    /** nether_decay_tap.json's drip count. */
    private static final int DRIPS = 8;
    /** A block resting on the bay floor, where the stone lies. */
    private static final BlockPos ON_THE_FLOOR = new BlockPos(2, 0, 2);

    private DecayTapTests() {
    }

    /**
     * Seven nether drips on stone leave it stone; the eighth leaves cobblestone.
     *
     * @param helper the gametest helper
     */
    public static void decayTapDegradesBelow(GameTestHelper helper) {
        helper.setBlock(ON_THE_FLOOR, Blocks.STONE);
        helper.runAfterDelay(1, () -> {
            drip(helper, DRIPS - 1);
            helper.assertBlockPresent(Blocks.STONE, ON_THE_FLOOR);
            drip(helper, 1);
            helper.assertBlockPresent(Blocks.COBBLESTONE, ON_THE_FLOOR);
            helper.succeed();
        });
    }

    /**
     * Lands nether drips on the stone's top face, each running nether's tap ability.
     *
     * @param helper the gametest helper
     * @param drips  how many drips land
     */
    private static void drip(GameTestHelper helper, int drips) {
        BlockPos landingAbs = helper.absolutePos(ON_THE_FLOOR);
        AbilityRegistry abilities = AbilityRegistry.of(helper.getLevel());
        for (int dripped = 0; dripped < drips; dripped++) {
            TapDripScheduler.runTapAbility(new TapDripScheduler.PendingDrip(helper.getLevel(), landingAbs.above(),
                    landingAbs, Direction.UP, GooTypes.NETHER, 1, 0), abilities);
        }
    }
}
