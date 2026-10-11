package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
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
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for typhoon Lift on a prism: what stands on the block above the
 * prism rises up the shaft while what stands beside it stays, and a rider
 * sneaking in the shaft sinks gently instead
 * (decision lift-prism-levitates-the-block-above).
 */
public final class LiftTests {

    private static final Identifier TYPHOON_LIFT = Identifier.parse("goo:typhoon_lift");
    private static final BlockPos PRISM_POS = new BlockPos(2, 1, 2);
    /** The block above the prism, the shaft's floor. */
    private static final BlockPos RIDER_POS = PRISM_POS.above();
    /** On the floor two blocks east, outside the shaft. */
    private static final BlockPos BYSTANDER_POS = new BlockPos(4, 1, 2);
    private static final int RISE_TICKS = 10;
    /** The least the rider climbs in those ticks, short of the bay's ceiling. */
    private static final double LEAST_RISE = 1.5;
    private static final double STILL = 0.01;
    /** typhoon_lift.json's sink. */
    private static final double SINK = 0.2;
    private static final int SINK_TICKS = 2;
    private static final double SPEED_TOLERANCE = 1e-6;
    private static final String SHOULD_HOLD_COMBO = "The prism should take Lift as its combo";
    private static final String SHOULD_RISE = "The zombie on the lift should rise %.1f blocks in %d ticks, rose %.2f";
    private static final String SHOULD_STAY = "The zombie beside the shaft should stay put, moved %.2f up";
    private static final String SHOULD_SINK = "A sneaking rider should sink at %.2f blocks a tick, moves %.4f";

    private LiftTests() {
    }

    /**
     * A zombie stands on a lift prism and another beside it: ten ticks on,
     * the rider has climbed a block and a half and the bystander has not moved up.
     *
     * @param helper the gametest helper
     */
    public static void liftRaisesWhatStandsAbove(GameTestHelper helper) {
        standLift(helper);
        helper.setBlock(BYSTANDER_POS.below(), Blocks.STONE);
        Mob rider = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, RIDER_POS);
        Mob bystander = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BYSTANDER_POS);
        double riderStart = rider.getY();
        double bystanderStart = bystander.getY();
        helper.runAfterDelay(RISE_TICKS, () -> {
            double rose = rider.getY() - riderStart;
            double bystanderRose = bystander.getY() - bystanderStart;
            helper.assertTrue(rose >= LEAST_RISE, String.format(SHOULD_RISE, LEAST_RISE, RISE_TICKS, rose));
            helper.assertTrue(bystanderRose < STILL, String.format(SHOULD_STAY, bystanderRose));
            helper.succeed();
        });
    }

    /**
     * A player sneaking on the block above a lift prism is set sinking at the
     * lift's sink rather than rising.
     *
     * @param helper the gametest helper
     */
    public static void liftSinksASneakingRider(GameTestHelper helper) {
        standLift(helper);
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

    private static void standLift(GameTestHelper helper) {
        helper.setBlock(PRISM_POS.below(), Blocks.STONE);
        helper.setBlock(PRISM_POS, GooBlocks.PRISM.get());
        AbilityDefinition lift = AbilityRegistry.of(helper.getLevel()).getAbility(TYPHOON_LIFT);
        PrismBlockEntity prism = (PrismBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(PRISM_POS));
        helper.assertTrue(lift != null && prism != null
                && prism.runCombo(GooTypes.TYPHOON, TYPHOON_LIFT.toString(), lift.behaviors()), SHOULD_HOLD_COMBO);
    }
}
