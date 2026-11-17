package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for the lift combo Updraft grows on a prism: Updraft's stream
 * held on a bare prism through the real stream path for its hold grows the
 * lift, and what stands on the block above the prism rises up the shaft
 * (decisions lift-prism-levitates-the-block-above and channeled-hosts-convert-prisms-over-the-hold).
 */
@RegistersGameTests
public final class LiftTests {
    private static final String LIFT_RAISES = "lift_raises_what_stands_above";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LIFT_RAISES, LiftTests::updraftOnAPrismRaisesWhatStandsAbove);
    }

    private static final Identifier TYPHOON_UPDRAFT = Identifier.parse("goo:typhoon_updraft");
    private static final BlockPos PRISM_POS = new BlockPos(2, 1, 2);
    /** The block above the prism, the shaft's floor. */
    private static final BlockPos RIDER_POS = PRISM_POS.above();
    /** Three blocks south of the prism, where the player holds Updraft on it. */
    private static final BlockPos STAND_POS = new BlockPos(2, 1, 5);
    /** The least the rider climbs once the lift stands. */
    private static final double LEAST_RISE = 1.5;
    private static final String SHOULD_HOLD_COMBO = "The prism should hold Updraft's lift as its combo, held '%s'";
    private static final String SHOULD_RISE = "The zombie on the lift should rise %.1f blocks, rose %.2f";

    private LiftTests() {
    }

    /**
     * Updraft held on a bare prism with a zombie standing on it: once the
     * hold converts it, the prism holds the lift combo sourced from Updraft,
     * and the rider climbs a block and a half.
     *
     * @param helper the gametest helper
     */
    public static void updraftOnAPrismRaisesWhatStandsAbove(GameTestHelper helper) {
        standPrism(helper);
        Mob rider = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, RIDER_POS);
        double riderStart = rider.getY();
        int hold = PrismChanneler.hold(helper, TYPHOON_UPDRAFT);
        ServerPlayer holder = PrismChanneler.holdOn(helper, GooTypes.TYPHOON, TYPHOON_UPDRAFT, PRISM_POS, STAND_POS,
                hold);
        helper.runAfterDelay(hold + 1, () -> helper.getLevel().getServer().getPlayerList().remove(holder));
        helper.succeedWhen(() -> {
            String combo = helper.getBlockEntity(PRISM_POS, PrismBlockEntity.class).getCombo();
            helper.assertTrue(TYPHOON_UPDRAFT.toString().equals(combo), String.format(SHOULD_HOLD_COMBO, combo));
            double rose = rider.getY() - riderStart;
            helper.assertTrue(rose >= LEAST_RISE, String.format(SHOULD_RISE, LEAST_RISE, rose));
        });
    }

    private static void standPrism(GameTestHelper helper) {
        helper.setBlock(PRISM_POS.below(), Blocks.STONE);
        helper.setBlock(PRISM_POS, GooBlocks.PRISM.get());
    }
}
