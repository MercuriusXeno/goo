package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.PrismCombos;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for the lift combo Updraft grows on a prism: Updraft thrown at a
 * bare prism through the real throw path grows the lift at Updraft's own
 * cost and stands no wind column, what stands on the block above the prism
 * rises up the shaft while what stands beside it stays, and a rider sneaking
 * in the shaft sinks gently instead
 * (decisions lift-prism-levitates-the-block-above and thrown-hosts-absorb-their-prism-combos).
 */
@RegistersGameTests
public final class LiftTests {
    private static final String LIFT_RAISES = "lift_raises_what_stands_above";
    private static final String LIFT_SINKS = "lift_sinks_a_sneaking_rider";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LIFT_RAISES, LiftTests::updraftOnAPrismRaisesWhatStandsAbove);
        GooTestFunctions.registerTest(registrar, LIFT_SINKS, LiftTests::liftSinksASneakingRider);
    }


    private static final Identifier TYPHOON_UPDRAFT = Identifier.parse("goo:typhoon_updraft");
    private static final int NO_ENTITY = -1;
    private static final BlockPos PRISM_POS = new BlockPos(2, 1, 2);
    /** The block above the prism, the shaft's floor. */
    private static final BlockPos RIDER_POS = PRISM_POS.above();
    /** On the floor two blocks east, outside the shaft. */
    private static final BlockPos BYSTANDER_POS = new BlockPos(4, 1, 2);
    /** The least the rider climbs, short of the bay's ceiling. */
    private static final double LEAST_RISE = 1.5;
    private static final double STILL = 0.01;
    /** The sink Updraft's on_prism lift step names. */
    private static final double SINK = 0.2;
    private static final int SINK_TICKS = 2;
    private static final double SPEED_TOLERANCE = 1e-6;
    private static final String ABILITY_REQUIRED = "The ability registry must hold typhoon_updraft";
    private static final String SHOULD_HOLD_COMBO = "The prism should hold Updraft's lift as its combo, held '%s'";
    private static final String SHOULD_CHARGE = "The throw should cost Updraft's %d mB, spent %d";
    private static final String NO_COLUMN = "Updraft on a prism should stand no wind column above it";
    private static final String SHOULD_RISE = "The zombie on the lift should rise %.1f blocks, rose %.2f";
    private static final String SHOULD_STAY = "The zombie beside the shaft should stay put, moved %.2f up";
    private static final String SHOULD_SINK = "A sneaking rider should sink at %.2f blocks a tick, moves %.4f";

    private LiftTests() {
    }

    /**
     * Updraft thrown at a bare prism with a zombie standing on it and another
     * beside it: the throw costs Updraft's own cost, the prism takes the lift
     * combo sourced from Updraft, no wind column stands above it, and the
     * rider climbs a block and a half while the bystander has not moved up.
     *
     * @param helper the gametest helper
     */
    public static void updraftOnAPrismRaisesWhatStandsAbove(GameTestHelper helper) {
        AbilityDefinition updraft = AbilityRegistry.of(helper.getLevel()).getAbility(TYPHOON_UPDRAFT);
        helper.assertTrue(updraft != null, ABILITY_REQUIRED);
        standPrism(helper);
        helper.setBlock(BYSTANDER_POS.below(), Blocks.STONE);
        Mob rider = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, RIDER_POS);
        Mob bystander = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BYSTANDER_POS);
        double riderStart = rider.getY();
        double bystanderStart = bystander.getY();
        ServerPlayer thrower = SelfDeliveryTests.invoker(helper, GooTypes.TYPHOON);
        KnownRecipes.teachRequires(thrower, updraft);
        int heldBefore = typhoonHeld(thrower);

        GooThrowHandler.execute(thrower, new GooThrowPayload(GooTypes.id(GooTypes.TYPHOON), NO_ENTITY,
                helper.absolutePos(PRISM_POS), Direction.UP.ordinal(), false, TYPHOON_UPDRAFT.toString(),
                thrower.getEyePosition()));
        int spent = heldBefore - typhoonHeld(thrower);
        helper.getLevel().getServer().getPlayerList().remove(thrower);
        helper.assertTrue(spent == updraft.cost(), String.format(SHOULD_CHARGE, updraft.cost(), spent));

        helper.succeedWhen(() -> {
            String combo = helper.getBlockEntity(PRISM_POS, PrismBlockEntity.class).getCombo();
            helper.assertTrue(TYPHOON_UPDRAFT.toString().equals(combo), String.format(SHOULD_HOLD_COMBO, combo));
            helper.assertTrue(helper.getBlockState(RIDER_POS).isAir(), NO_COLUMN);
            double rose = rider.getY() - riderStart;
            double bystanderRose = bystander.getY() - bystanderStart;
            helper.assertTrue(rose >= LEAST_RISE, String.format(SHOULD_RISE, LEAST_RISE, rose));
            helper.assertTrue(bystanderRose < STILL, String.format(SHOULD_STAY, bystanderRose));
        });
    }

    /**
     * A player sneaking on the block above a lift prism is set sinking at the
     * lift's sink rather than rising.
     *
     * @param helper the gametest helper
     */
    public static void liftSinksASneakingRider(GameTestHelper helper) {
        standPrism(helper);
        AbilityDefinition updraft = AbilityRegistry.of(helper.getLevel()).getAbility(TYPHOON_UPDRAFT);
        PrismBlockEntity prism = helper.getBlockEntity(PRISM_POS, PrismBlockEntity.class);
        helper.assertTrue(updraft != null && prism.runCombo(GooTypes.TYPHOON, TYPHOON_UPDRAFT.toString(),
                PrismCombos.comboSteps(updraft)), String.format(SHOULD_HOLD_COMBO, prism.getCombo()));
        ServerPlayer rider = SelfDeliveryTests.invoker(helper, GooTypes.TYPHOON);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(RIDER_POS));
        rider.setPos(stand.x, stand.y, stand.z);
        rider.setShiftKeyDown(true);
        helper.runAfterDelay(SINK_TICKS, () -> {
            double vertical = rider.getDeltaMovement().y;
            helper.getLevel().getServer().getPlayerList().remove(rider);
            helper.assertTrue(Math.abs(vertical + SINK) < SPEED_TOLERANCE, String.format(SHOULD_SINK, SINK, vertical));
            helper.succeed();
        });
    }

    private static void standPrism(GameTestHelper helper) {
        helper.setBlock(PRISM_POS.below(), Blocks.STONE);
        helper.setBlock(PRISM_POS, GooBlocks.PRISM.get());
    }

    private static int typhoonHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.TYPHOON, 0);
    }
}
