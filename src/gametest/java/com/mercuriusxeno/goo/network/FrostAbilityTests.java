package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
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
 * Gametests for the frost abilities: each lands a frost ability the way the
 * glove would and reads the frozen gauges and the world it leaves.
 * Decisions nova-ring-grows-with-the-hold.
 */
public final class FrostAbilityTests {

    private static final BlockPos CASTER_POS = new BlockPos(1, 1, 1);
    /** About one and a half blocks from the caster: inside Nova's uncharged reach of two. */
    private static final BlockPos NEAR_POS = new BlockPos(2, 1, 2);
    /** About four blocks from the caster: past Nova's uncharged reach, inside its full reach of eight. */
    private static final BlockPos FAR_POS = new BlockPos(4, 1, 4);
    private static final Identifier FROST_NOVA = Identifier.parse("goo:frost_nova");
    /** frost_nova.json's charge max_ticks. */
    private static final int FULL_HOLD_TICKS = 60;
    private static final int NO_HOLD_TICKS = 0;
    /**
     * A full hold freezes two zombies by 16 health each, thinned by the crowd
     * to about 15.2 of their 20 health; an uncharged one by 4 of 20.
     */
    private static final float FULL_HOLD_FLOOR = 0.7f;
    private static final float NO_HOLD_CEILING = 0.25f;
    private static final int SETTLE_TICKS = 1;
    private static final String ABILITY_REQUIRED = "Ability registry must hold frost_nova";
    private static final String SHOULD_FREEZE_HARD = "A full Nova should freeze the %s zombie past %s, stands %s";
    private static final String SHOULD_FREEZE_LIGHTLY = "An uncharged Nova should freeze the near zombie lightly, stands %s";
    private static final String SHOULD_NOT_REACH = "An uncharged Nova should not reach the far zombie, stands %s";

    private FrostAbilityTests() {
    }

    /**
     * A full hold of Nova released beside two zombies reaches both and
     * freezes each hard.
     *
     * @param helper the gametest helper
     */
    public static void novaHeldReachesBothZombies(GameTestHelper helper) {
        Mob near = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, NEAR_POS);
        Mob far = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, FAR_POS);
        ServerPlayer caster = caster(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            release(caster, FULL_HOLD_TICKS);
            float nearGauge = near.getData(GooAttachments.FROZEN).gauge();
            float farGauge = far.getData(GooAttachments.FROZEN).gauge();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(nearGauge > FULL_HOLD_FLOOR,
                    String.format(SHOULD_FREEZE_HARD, "near", FULL_HOLD_FLOOR, nearGauge));
            helper.assertTrue(farGauge > FULL_HOLD_FLOOR,
                    String.format(SHOULD_FREEZE_HARD, "far", FULL_HOLD_FLOOR, farGauge));
            helper.succeed();
        });
    }

    /**
     * Nova let go at once reaches only the near zombie, and freezes it lightly.
     *
     * @param helper the gametest helper
     */
    public static void novaTappedReachesOnlyTheNear(GameTestHelper helper) {
        Mob near = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, NEAR_POS);
        Mob far = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, FAR_POS);
        ServerPlayer caster = caster(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            release(caster, NO_HOLD_TICKS);
            float nearGauge = near.getData(GooAttachments.FROZEN).gauge();
            float farGauge = far.getData(GooAttachments.FROZEN).gauge();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(nearGauge > 0f && nearGauge < NO_HOLD_CEILING,
                    String.format(SHOULD_FREEZE_LIGHTLY, nearGauge));
            helper.assertTrue(farGauge == 0f, String.format(SHOULD_NOT_REACH, farGauge));
            helper.succeed();
        });
    }

    private static void release(ServerPlayer caster, int heldTicks) {
        GooThrowHandler.releaseCharge(caster,
                new GooChargePayload(GooTypes.id(GooTypes.FROST), FROST_NOVA.toString(), heldTicks));
    }

    private static ServerPlayer caster(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.FROST);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(CASTER_POS));
        player.setPos(stand.x, stand.y, stand.z);
        AbilityDefinition nova = AbilityRegistry.of(helper.getLevel()).getAbility(FROST_NOVA);
        helper.assertTrue(nova != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(player, nova);
        return player;
    }
}
