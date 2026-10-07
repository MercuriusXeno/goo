package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Gametest for the stream delivery: holding blaze spitfire at a zombie runs
 * the ability on it each tick of the hold, drains one cost per
 * ticks_per_charge of hold, and drains nothing once released
 * (decision stream-delivery-held-cone).
 */
public final class StreamDeliveryTests {

    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** Four blocks east of the player. */
    private static final BlockPos ZOMBIE_POS = STAND_POS.east(4);
    private static final int HELD_GOO = 2;
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward the zombie's middle four blocks off. */
    private static final float LOOKING_AT_ZOMBIE = 9f;
    private static final Identifier BLAZE_SPITFIRE = Identifier.parse("goo:blaze_spitfire");
    /** blaze_spitfire.json's ticks_per_charge: twenty ticks of hold drain one cost. */
    private static final int HOLD_TICKS = 20;
    private static final int RELEASED_TICKS = 10;
    private static final String ABILITY_REQUIRED = "Ability registry must hold blaze_spitfire";
    private static final String SHOULD_START_UNBURNT = "The helmeted zombie should not burn before the stream";
    private static final String SHOULD_BURN = "The streamed zombie should be on fire";
    private static final String SHOULD_DRAIN_ONE_COST = "Twenty ticks of hold should drain %d mB, drained %d";
    private static final String SHOULD_STOP_DRAINING = "A released stream should drain nothing, drained %d";

    private StreamDeliveryTests() {
    }

    /**
     * A mock player holds blaze spitfire at a zombie four blocks ahead for
     * twenty ticks: the zombie burns and the blaze goo drops by one cost;
     * released, the goo stays put for ten more ticks.
     *
     * @param helper the gametest helper
     */
    public static void blazeSpitfire(GameTestHelper helper) {
        AbilityDefinition spitfire = AbilityRegistry.of(helper.getLevel()).getAbility(BLAZE_SPITFIRE);
        helper.assertTrue(spitfire != null, ABILITY_REQUIRED);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        ServerPlayer player = streamer(helper);
        KnownRecipes.teachRequires(player, spitfire);
        int heldBefore = blazeHeld(player);
        helper.assertFalse(zombie.isOnFire(), SHOULD_START_UNBURNT);
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.BLAZE), BLAZE_SPITFIRE.toString(),
                player.getEyePosition(), player.getEyePosition(), player.getY());
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            int drained = heldBefore - blazeHeld(player);
            helper.assertTrue(zombie.isOnFire(), SHOULD_BURN);
            helper.assertTrue(drained == spitfire.cost(),
                    String.format(SHOULD_DRAIN_ONE_COST, spitfire.cost(), drained));
            int heldAtRelease = blazeHeld(player);
            helper.runAfterDelay(RELEASED_TICKS, () -> {
                int drainedAfter = heldAtRelease - blazeHeld(player);
                helper.getLevel().getServer().getPlayerList().remove(player);
                helper.assertTrue(drainedAfter == 0, String.format(SHOULD_STOP_DRAINING, drainedAfter));
                helper.succeed();
            });
        });
    }

    private static int blazeHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.BLAZE, 0);
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer streamer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_AT_ZOMBIE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.BLAZE, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
