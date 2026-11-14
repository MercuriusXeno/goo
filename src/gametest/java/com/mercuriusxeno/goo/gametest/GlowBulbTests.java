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
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for glow's Bulb through the real landing path: it places its
 * crystal on the face it lands on, and a Bulb landing on a prism lights it
 * as a beacon.
 * decision bulb-one-model-max-light-beacon-combo
 */
@RegistersGameTests
public final class GlowBulbTests {
    private static final String BULB_IS_ONE_SIZE_LIGHT_15 = "bulb_is_one_size_light_15";
    private static final String BULB_ON_PRISM_BEACONS = "bulb_on_prism_beacons";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, BULB_IS_ONE_SIZE_LIGHT_15, GlowBulbTests::bulbIsOneSizeLight15);
        GooTestFunctions.registerTest(registrar, BULB_ON_PRISM_BEACONS, GlowBulbTests::bulbOnPrismBeacons);
    }


    private static final BlockPos FLOOR_POS = new BlockPos(1, 1, 1);
    private static final BlockPos LANDED_POS = FLOOR_POS.above();
    private static final String BULB = "goo:glow_bulb";
    private static final int NO_ENTITY = -1;

    private static final String WRONG_COMBO = "The prism should hold Bulb's beacon combo, held '%s'";
    private static final String NOT_LIT = "Bulb's beacon should light the prism";

    private GlowBulbTests() {
    }

    /**
     * A Bulb landing on a stone floor places the glow crystal facing up.
     * GlowCrystalBlockTest holds its one state property and its light 15.
     *
     * @param helper the gametest helper
     */
    public static void bulbIsOneSizeLight15(GameTestHelper helper) {
        helper.setBlock(FLOOR_POS, Blocks.STONE);
        land(helper, FLOOR_POS);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(GooBlocks.GLOW_CRYSTAL.get(), LANDED_POS);
            helper.assertBlockProperty(LANDED_POS, GlowCrystalBlock.FACING, Direction.UP);
        });
    }

    /**
     * A Bulb landing on a prism runs its on_prism reaction: the prism holds
     * Bulb's beacon combo and stands lit; PrismBlockTest holds a lit prism's light.
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
        });
    }

    private static void land(GameTestHelper helper, BlockPos struck) {
        GooEffectScheduler arrivals = GooServerState.of(helper.getLevel().getServer()).gooEffects();
        int arrivalTick = helper.getLevel().getServer().getTickCount();
        arrivals.enqueue(new PendingEffect(arrivalTick, helper.getLevel(), null, GooTypes.GLOW, NO_ENTITY,
                helper.absolutePos(struck), Direction.UP, BULB));
        arrivals.drainArrivedEffects(arrivalTick);
    }

}
