package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.tap.TapDripScheduler;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametests for glow's Radiant drip: one glow drip landing on the floor of
 * a sealed dark room leaves a wisp in the air above it; in a room lit past
 * the threshold it leaves none (decision radiant-drip-places-a-wisp).
 */
public final class RadiantTapTests {

    /** The room's walls, floor and roof span 0 to 4 across and 0 to 4 up; its air is inside. */
    private static final int ROOM_EDGE = 4;
    private static final BlockPos LANDING = new BlockPos(2, 0, 2);
    /** Ticks for the light engine to settle the sealed room before the drip lands. */
    private static final int SETTLE_TICKS = 5;
    private static final String NO_WISP = "A glow drip in the dark should leave a wisp above where it lands";
    private static final String WISP_IN_THE_LIGHT = "A glow drip in a lit room should leave no wisp";

    private RadiantTapTests() {
    }

    /**
     * One glow drip on the floor of a sealed dark room leaves a wisp above it.
     *
     * @param helper the gametest helper
     */
    public static void radiantTapWispsInTheDark(GameTestHelper helper) {
        sealRoom(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            drip(helper);
            helper.assertTrue(helper.getBlockState(LANDING.above()).is(GooBlocks.WISP.get()), NO_WISP);
            helper.succeed();
        });
    }

    /**
     * One glow drip on the floor of a sealed room lit past the threshold leaves no wisp.
     *
     * @param helper the gametest helper
     */
    public static void radiantTapSkipsALitRoom(GameTestHelper helper) {
        sealRoom(helper);
        for (int x = 1; x < ROOM_EDGE; x++) {
            for (int z = 1; z < ROOM_EDGE; z++) {
                helper.setBlock(new BlockPos(x, ROOM_EDGE - 1, z), Blocks.LIGHT.defaultBlockState());
            }
        }
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            drip(helper);
            helper.assertTrue(helper.getBlockState(LANDING.above()).isAir(), WISP_IN_THE_LIGHT);
            helper.succeed();
        });
    }

    private static void sealRoom(GameTestHelper helper) {
        for (int x = 0; x <= ROOM_EDGE; x++) {
            for (int y = 0; y <= ROOM_EDGE; y++) {
                for (int z = 0; z <= ROOM_EDGE; z++) {
                    boolean shell = x == 0 || x == ROOM_EDGE || y == 0 || y == ROOM_EDGE || z == 0 || z == ROOM_EDGE;
                    helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
    }

    /**
     * Lands one glow drip on the floor's top face, running glow's tap ability.
     *
     * @param helper the gametest helper
     */
    private static void drip(GameTestHelper helper) {
        BlockPos landingAbs = helper.absolutePos(LANDING);
        TapDripScheduler.runTapAbility(new TapDripScheduler.PendingDrip(helper.getLevel(), landingAbs.above(2),
                landingAbs, Direction.UP, GooTypes.GLOW, 1, 0), AbilityRegistry.of(helper.getLevel()));
    }
}
