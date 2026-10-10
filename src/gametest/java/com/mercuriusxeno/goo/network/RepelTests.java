package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.typhoon.Airborn;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for typhoon Repel streamed through the real stream delivery: its
 * rushing wind flings a mob in its cone out past its reach while held
 * (decision repel-cone-flings-with-frost-wind-lines), and pushes a player
 * standing under Airborn at Repel's own strength, Airborn's boost being for
 * a player's own Jet alone (decision airborn-steerable-levitation-and-soft-falls).
 */
public final class RepelTests {

    private static final Identifier TYPHOON_REPEL = Identifier.parse("goo:typhoon_repel");
    private static final BlockPos STREAMER_POS = new BlockPos(0, 1, 2);
    /** In the sixteen-block light bay, with room east of the zombie to fling it past the reach. */
    private static final BlockPos LONG_RUN_STREAMER_POS = new BlockPos(1, 1, 8);
    private static final BlockPos LONG_RUN_ZOMBIE_POS = new BlockPos(4, 1, 8);
    /** Three blocks east of the streamer, inside the cone. */
    private static final BlockPos TARGET_POS = new BlockPos(3, 1, 2);
    private static final float FACING_EAST = -90f;
    /** Level, so a body three blocks off stands inside the 30-degree cone. */
    private static final float LEVEL = 0f;
    private static final int HOLD_TICKS = 10;
    /** typhoon_repel.json's push strength. */
    private static final double REPEL_STRENGTH = 1.2;
    private static final double SPEED_TOLERANCE = 1e-6;
    /** An Airborn whose jet boost would show if it reached another's push. */
    private static final Airborn BOOSTING = new Airborn(0.35f, 0.15f, 0.4f, 0.5f, 1.5f, Airborn.NEVER_EXPIRES);
    private static final String ABILITY_REQUIRED = "Ability registry must hold typhoon_repel";
    private static final String SHOULD_FLING_OUT =
            "Ten held ticks of Repel should fling the zombie past its %.1f-block reach, stands %.2f from the streamer";
    private static final String SHOULD_PUSH_AT_ITS_OWN_STRENGTH =
            "Repel should push an Airborn player at %.2f blocks a tick, pushed it at %.4f";

    private RepelTests() {
    }

    /**
     * A player streams Repel east at a zombie three blocks off: within ten
     * held ticks the zombie stands past the stream's reach.
     *
     * @param helper the gametest helper
     */
    public static void repelFlingsAZombie(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, LONG_RUN_ZOMBIE_POS);
        ServerPlayer streamer = streamer(helper, LONG_RUN_STREAMER_POS);
        AbilityDefinition repel = AbilityRegistry.of(helper.getLevel()).getAbility(TYPHOON_REPEL);
        Vec3 eye = streamer.getEyePosition();
        hold(helper, streamer);
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            double away = zombie.position().distanceTo(eye);
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            helper.assertTrue(away > repel.delivery().range(),
                    String.format(SHOULD_FLING_OUT, repel.delivery().range(), away));
            helper.succeed();
        });
    }

    /**
     * A player streams Repel at another player standing under Airborn: the
     * push sets that player moving at Repel's own strength, unboosted.
     *
     * @param helper the gametest helper
     */
    public static void repelPushesAnAirbornPlayerAtItsOwnStrength(GameTestHelper helper) {
        ServerPlayer streamer = streamer(helper, STREAMER_POS);
        ServerPlayer target = SelfDeliveryTests.invoker(helper, GooTypes.TYPHOON);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(TARGET_POS));
        target.setPos(stand.x, stand.y, stand.z);
        target.setData(GooAttachments.AIRBORN, BOOSTING);
        GooStreamHandler.streamTick(streamer, tick(streamer));
        double speed = target.getDeltaMovement().length();
        helper.getLevel().getServer().getPlayerList().remove(streamer);
        helper.getLevel().getServer().getPlayerList().remove(target);
        helper.assertTrue(Math.abs(speed - REPEL_STRENGTH) < SPEED_TOLERANCE,
                String.format(SHOULD_PUSH_AT_ITS_OWN_STRENGTH, REPEL_STRENGTH, speed));
        helper.succeed();
    }

    private static ServerPlayer streamer(GameTestHelper helper, BlockPos standing) {
        AbilityDefinition repel = AbilityRegistry.of(helper.getLevel()).getAbility(TYPHOON_REPEL);
        helper.assertTrue(repel != null, ABILITY_REQUIRED);
        ServerPlayer streamer = SelfDeliveryTests.invoker(helper, GooTypes.TYPHOON, TYPHOON_REPEL);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(standing));
        streamer.setPos(stand.x, stand.y, stand.z);
        streamer.setYRot(FACING_EAST);
        streamer.setXRot(LEVEL);
        KnownRecipes.teachRequires(streamer, repel);
        return streamer;
    }

    private static GooStreamPayload tick(ServerPlayer streamer) {
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.TYPHOON), TYPHOON_REPEL.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
    }

    private static void hold(GameTestHelper helper, ServerPlayer streamer) {
        GooStreamPayload tick = tick(streamer);
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
    }
}
