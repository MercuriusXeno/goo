package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for the self delivery: a glove use with a self ability drains
 * its cost at stack zero and runs its programs on the invoking player the
 * same tick, through the real throw path (decision self-delivery-runs-on-player).
 */
public final class SelfDeliveryTests {

    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    private static final int NO_ENTITY = -1;
    /** Two thousand mB, two blinks' worth. */
    private static final int HELD_GOO = 2;
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    private static final Identifier ENDER_BLINK = Identifier.parse("goo:ender_blink");
    /** The range ender_blink.json's teleport step names. */
    private static final double BLINK_RANGE = 8;
    private static final double MOVE_TOLERANCE = 1e-6;
    private static final Identifier TYPHOON_PROPEL = Identifier.parse("goo:typhoon_propel");
    /** The strength typhoon_propel.json's push step names. */
    private static final double PROPEL_STRENGTH = 1.5;
    /** Pitch forty-five degrees above level. */
    private static final float LOOKING_UP = -45f;
    private static final float BUILT_UP_FALL = 10f;
    private static final String SHOULD_PROPEL = "The player's motion should read %s, read %s";
    private static final String SHOULD_CLEAR_FALL = "Propulsion should clear the fall, read %.1f";
    private static final String ABILITY_REQUIRED = "Ability registry must hold ender_blink";
    private static final String SHOULD_BLINK_EAST = "The player should move %.1f east the tick it blinks, moved %.3f";
    private static final String SHOULD_DRAIN_COST = "Blink should drain its stack-zero cost of %d mB, drained %d";

    private SelfDeliveryTests() {
    }

    /**
     * A mock player facing east invokes ender blink and moves the blink's
     * range east in that tick, with the goo drained by its cost.
     *
     * @param helper the gametest helper
     */
    public static void enderBlink(GameTestHelper helper) {
        AbilityDefinition blink = AbilityRegistry.of(helper.getLevel()).getAbility(ENDER_BLINK);
        helper.assertTrue(blink != null, ABILITY_REQUIRED);
        ServerPlayer player = invoker(helper, GooTypes.ENDER);
        player.setYRot(FACING_EAST);
        player.setXRot(0);
        double xBefore = player.getX();
        int heldBefore = enderHeld(player);

        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.ENDER), NO_ENTITY,
                player.blockPosition(), NO_ENTITY, false, ENDER_BLINK.toString(), player.getEyePosition()));

        double moved = player.getX() - xBefore;
        int drained = heldBefore - enderHeld(player);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(Math.abs(moved - BLINK_RANGE) < MOVE_TOLERANCE,
                String.format(SHOULD_BLINK_EAST, BLINK_RANGE, moved));
        helper.assertTrue(drained == blink.throwCost(0), String.format(SHOULD_DRAIN_COST, blink.throwCost(0), drained));
        helper.succeed();
    }

    /**
     * A mock player looking up and east invokes typhoon propulsion, and its
     * motion reads the push strength along its look with its fall cleared.
     *
     * @param helper the gametest helper
     */
    public static void typhoonPropel(GameTestHelper helper) {
        ServerPlayer player = invoker(helper, GooTypes.TYPHOON);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_UP);
        player.fallDistance = BUILT_UP_FALL;
        Vec3 expected = player.getLookAngle().scale(PROPEL_STRENGTH);

        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.TYPHOON), NO_ENTITY,
                player.blockPosition(), NO_ENTITY, false, TYPHOON_PROPEL.toString(), player.getEyePosition()));

        Vec3 motion = player.getDeltaMovement();
        double fall = player.fallDistance;
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(motion.distanceTo(expected) < MOVE_TOLERANCE, String.format(SHOULD_PROPEL, expected, motion));
        helper.assertTrue(fall == 0, String.format(SHOULD_CLEAR_FALL, fall));
        helper.succeed();
    }

    private static int enderHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.ENDER, 0);
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer invoker(GameTestHelper helper, ResourceKey<GooTypeDefinition> gooType) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(gooType, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
