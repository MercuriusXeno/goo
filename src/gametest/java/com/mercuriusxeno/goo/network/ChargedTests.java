package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.held.HeldEffectsEvents;
import com.mercuriusxeno.goo.ability.program.ChargedStep;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.PlayerHost;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
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
 * Gametests for Charged: blaze spitfire held at a zombie beyond its six-block
 * reach leaves it unburnt, and once the player casts Charged, the same hold
 * reaches it through the stream's charged area and sets it alight; Charged
 * held on the glove's unstable trickle ends when the unstable runs dry, the
 * charge clearing and the stream's cone back at its base reach
 * (decision charged-scales-channel-params-by-json).
 */
public final class ChargedTests {

    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** Eight blocks east of the player: past spitfire's six, inside its charged nine. */
    private static final BlockPos ZOMBIE_POS = STAND_POS.east(8);
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward the zombie's middle eight blocks off. */
    private static final float LOOKING_AT_ZOMBIE = 4f;
    private static final Identifier BLAZE_SPITFIRE = Identifier.parse("goo:blaze_spitfire");
    private static final Identifier UNSTABLE_CHARGED = Identifier.parse("goo:unstable_charged");
    private static final int HOLD_TICKS = 5;
    private static final int HELD_GOO = 2;
    private static final String ABILITY_REQUIRED = "Ability registry must hold %s";
    private static final String BURNT_UNCHARGED = "An uncharged stream should not reach the zombie eight blocks off";
    private static final String UNBURNT_CHARGED = "A charged stream should reach and ignite the zombie eight blocks off";
    /** The unstable the glove's Charged holds: three ticks of upkeep. */
    private static final int DRY_UNSTABLE = 3;
    /** Ticks after the start by which three mB of upkeep have run dry and the effect ended. */
    private static final int DRY_BY = DRY_UNSTABLE + 2;
    private static final String NOT_CHARGED = "Charged held on the glove should stand and charge the player";
    private static final String STILL_CHARGED = "Charged should end and clear once the unstable runs dry";
    private static final String BURNT_DRY = "A stream after Charged ran dry should not reach the zombie eight blocks off";

    private ChargedTests() {
    }

    /**
     * Holds spitfire at a far zombie uncharged, then charged, asserting the
     * zombie burns only under the charge.
     *
     * @param helper the gametest helper
     */
    public static void chargedLengthensTheStream(GameTestHelper helper) {
        AbilityRegistry abilities = AbilityRegistry.of(helper.getLevel());
        AbilityDefinition spitfire = abilities.getAbility(BLAZE_SPITFIRE);
        AbilityDefinition charged = abilities.getAbility(UNSTABLE_CHARGED);
        helper.assertTrue(spitfire != null, String.format(ABILITY_REQUIRED, BLAZE_SPITFIRE));
        helper.assertTrue(charged != null, String.format(ABILITY_REQUIRED, UNSTABLE_CHARGED));
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        zombie.setNoGravity(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        ServerPlayer player = streamer(helper);
        KnownRecipes.teachRequires(player, spitfire);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.BLAZE), BLAZE_SPITFIRE.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 1L, () -> {
            helper.assertFalse(zombie.isOnFire(), BURNT_UNCHARGED);
            ProgramBehavior.forHost(charged.behaviors(), HostKind.PLAYER).tick(new PlayerHost(helper.getLevel(), player));
        });
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(HOLD_TICKS + 1L + held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(2L * HOLD_TICKS + 2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(zombie.isOnFire(), UNBURNT_CHARGED);
            zombie.discard();
            helper.succeed();
        });
    }

    /**
     * Holds Charged on the glove with three mB of unstable, lets the upkeep
     * run it dry, then holds spitfire at the far zombie: the charge has
     * cleared with its held effect, so the stream's cone is back at its base
     * reach and the zombie stands unburnt.
     *
     * @param helper the gametest helper
     */
    public static void chargedEndsWhenUnstableRunsDry(GameTestHelper helper) {
        AbilityRegistry abilities = AbilityRegistry.of(helper.getLevel());
        AbilityDefinition spitfire = abilities.getAbility(BLAZE_SPITFIRE);
        AbilityDefinition charged = abilities.getAbility(UNSTABLE_CHARGED);
        helper.assertTrue(spitfire != null, String.format(ABILITY_REQUIRED, BLAZE_SPITFIRE));
        helper.assertTrue(charged != null, String.format(ABILITY_REQUIRED, UNSTABLE_CHARGED));
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        zombie.setNoGravity(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        ServerPlayer player = streamer(helper);
        player.getInventory().add(GooStacks.createForOutput(GooTypes.UNSTABLE, DRY_UNSTABLE));
        KnownRecipes.teachRequires(player, spitfire);
        HeldEffectsEvents.start(player, GooTypes.UNSTABLE, charged);
        ProgramBehavior.forHost(charged.behaviors(), HostKind.PLAYER).tick(new PlayerHost(helper.getLevel(), player));
        helper.assertTrue(ChargedStep.isCharged(player) && HeldEffectsEvents.holds(player, UNSTABLE_CHARGED),
                NOT_CHARGED);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.BLAZE), BLAZE_SPITFIRE.toString(),
                player.getEyePosition(), player.getEyePosition());
        helper.runAfterDelay(DRY_BY, () -> helper.assertTrue(!ChargedStep.isCharged(player)
                && !HeldEffectsEvents.holds(player, UNSTABLE_CHARGED), STILL_CHARGED));
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(DRY_BY + held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(DRY_BY + HOLD_TICKS + 1L, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertFalse(zombie.isOnFire(), BURNT_DRY);
            zombie.discard();
            helper.succeed();
        });
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
