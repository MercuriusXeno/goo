package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.network.GooEffectScheduler;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametests for glow's Bulb through the real landing path: the crystal it
 * places is one blockstate per face emitting the max light, and a Bulb
 * landing on a prism lights it as a beacon.
 * decision bulb-one-model-max-light-beacon-combo
 */
public final class GlowBulbTests {

    private static final BlockPos FLOOR_POS = new BlockPos(1, 1, 1);
    private static final BlockPos LANDED_POS = FLOOR_POS.above();
    private static final BlockPos NEIGHBOR_POS = LANDED_POS.above();
    private static final String BULB = "goo:glow_crystal";
    private static final int NO_ENTITY = -1;
    private static final int MAX_LIGHT = 15;
    private static final int NEIGHBOR_LIGHT = MAX_LIGHT - 1;
    private static final int STATE_PER_FACE = Direction.values().length;

    private static final String NOT_ONE_STATE = "The glow crystal should hold one state per face, held %d";
    private static final String WRONG_LIGHT = "Block light at %s should read %d, read %d";
    private static final String WRONG_COMBO = "The prism should hold Bulb's beacon combo, held '%s'";
    private static final String NOT_LIT = "Bulb's beacon should light the prism";

    private GlowBulbTests() {
    }

    /**
     * A Bulb landing on a stone floor places the glow crystal, whose block
     * holds one state per face and emits light 15: the crystal's cell reads
     * 15 and its neighbor 14.
     *
     * @param helper the gametest helper
     */
    public static void bulbIsOneSizeLight15(GameTestHelper helper) {
        helper.setBlock(FLOOR_POS, Blocks.STONE);
        land(helper, FLOOR_POS);
        int states = GooBlocks.GLOW_CRYSTAL.get().getStateDefinition().getPossibleStates().size();
        helper.assertTrue(states == STATE_PER_FACE, String.format(NOT_ONE_STATE, states));
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(GooBlocks.GLOW_CRYSTAL.get(), LANDED_POS);
            helper.assertBlockProperty(LANDED_POS, GlowCrystalBlock.FACING, Direction.UP);
            assertBlockLight(helper, LANDED_POS, MAX_LIGHT);
            assertBlockLight(helper, NEIGHBOR_POS, NEIGHBOR_LIGHT);
        });
    }

    /**
     * A Bulb landing on a prism runs its on_prism reaction: the prism holds
     * Bulb's beacon combo, stands lit and emits light 15.
     *
     * @param helper the gametest helper
     */
    public static void bulbOnPrismBeacons(GameTestHelper helper) {
        helper.setBlock(FLOOR_POS, Blocks.STONE);
        helper.setBlock(LANDED_POS, GooBlocks.PRISM.get());
        land(helper, LANDED_POS);
        helper.succeedWhen(() -> {
            PrismBlockEntity prism = helper.getBlockEntity(LANDED_POS, PrismBlockEntity.class);
            helper.assertTrue(BULB.equals(prism.getCombo()), String.format(WRONG_COMBO, prism.getCombo()));
            helper.assertTrue(helper.getBlockState(LANDED_POS).getValue(PrismBlock.LIT), NOT_LIT);
            assertBlockLight(helper, LANDED_POS, MAX_LIGHT);
        });
    }

    private static void land(GameTestHelper helper, BlockPos struck) {
        GooEffectScheduler arrivals = GooServerState.of(helper.getLevel().getServer()).gooEffects();
        int arrivalTick = helper.getLevel().getServer().getTickCount();
        arrivals.enqueue(new PendingEffect(arrivalTick, helper.getLevel(), null, GooTypes.GLOW, NO_ENTITY,
                helper.absolutePos(struck), Direction.UP, BULB));
        arrivals.drainArrivedEffects(arrivalTick);
    }

    private static void assertBlockLight(GameTestHelper helper, BlockPos pos, int expected) {
        int light = helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(pos));
        helper.assertTrue(light == expected, String.format(WRONG_LIGHT, pos, expected, light));
    }
}
