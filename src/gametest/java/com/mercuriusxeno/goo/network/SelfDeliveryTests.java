package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.program.ShiftStep;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.SelfEatRoute;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.ability.held.HeldEffects;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/**
 * Gametests for the self delivery through the real throw path. A self
 * ability wearing the self badge runs on command: its cost at stack zero
 * drains and its programs run on the invoking player the tick it is invoked
 * (decision self-delivery-runs-on-player). A self + brew ability, one
 * wearing the brew badge, starts the player eating the glove, and drains
 * and runs when the eat finishes; an eat let go before then runs nothing
 * and drains nothing (decision self-brew-goos-eat-before-the-effect). The
 * mock server player has no connection ticking it, so the eat tests tick it
 * the way the connection would, through doTick. A finished eat holds the
 * effect, paying its upkeep each tick after, until invoked again or dry
 * (decision self-effects-trickle-until-ended).
 */
public final class SelfDeliveryTests {

    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    private static final int NO_ENTITY = -1;
    /** Two thousand mB, two casts' worth. */
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
    private static final Identifier BLAZE_KINDLE = Identifier.parse("goo:blaze_kindle");
    private static final Identifier SHROOM_SIGHT = Identifier.parse("goo:shroom_sight");
    private static final Identifier AEON_HASTE = Identifier.parse("goo:aeon_haste");
    private static final String SHOULD_HASTE_HELD = "Held Haste should stand endless with no particles, stands %s";
    private static final String SHOULD_END_HASTE = "Invoking Haste again should end it and its speed and haste";
    private static final String SHOULD_SEE = "Once the eat finishes the player should hold fungal sight";
    private static final String SHOULD_SEE_PAID = "Sight should stand through tick %d, which the shroom pays for";
    private static final String SHOULD_END_SIGHT_DRY = "Sight should end and clear once shroom runs dry";
    private static final String SHOULD_FALL_BACK = "The shift's reach should fall back to %.1f, reads %.1f";
    /** shroom_fungal_shift.json's range, the reach a shift falls back to without sight. */
    private static final double BASE_SHIFT_RANGE = 64;
    /** One ember heart, which Kindle primes before its crawl embers the rest. */
    private static final int PRIMED_EMBERS = HeartOverlay.FULL_SHIELD;
    /** The ticks a held Kindle is watched paying its upkeep. */
    private static final int UPKEEP_TICKS = 20;
    /** The ticks of upkeep the dry player holds goo for. */
    private static final int PAID_TICKS = 3;
    /** Halfway through the eat, when nothing has landed yet. */
    private static final int MID_EAT = SelfEatRoute.EAT_TICKS / 2;
    /** The tick after the eat's last tick, when the finish has run. */
    private static final int AFTER_EAT = SelfEatRoute.EAT_TICKS + 1;
    /** The tick a letting-go player releases the use at. */
    private static final int RELEASE_AT = 5;
    private static final String SURVIVAL_PLAYER_NAME = "test-survival-player";
    private static final String SHOULD_BE_SURVIVAL = "The eating player should read survival, not creative";
    private static final String ABILITY_REQUIRED = "Ability registry must hold %s";
    private static final String SHOULD_RUN_ON_COMMAND = "A self-badged ability should run on command, not eat";
    private static final String SHOULD_BLINK_EAST = "The player should move %.1f east the tick it blinks, moved %.3f";
    private static final String SHOULD_DRAIN_COST = "The cast should drain the stack-zero cost of %d mB, drained %d";
    private static final String SHOULD_PROPEL = "The player's motion should read %s, read %s";
    private static final String SHOULD_CLEAR_FALL = "Propulsion should clear the fall, read %.1f";
    private static final String SHOULD_START_EATING = "Invoking a self + brew ability should start the player eating";
    private static final String SHOULD_LAY_NOTHING_MID_EAT = "Mid-eat no ember should stand, %d halves stand";
    private static final String SHOULD_DRAIN_NOTHING_MID_EAT = "Mid-eat no goo should drain, drained %d";
    private static final String SHOULD_LAY_EMBERS = "The finished eat should lay %d ember halves, laid %d";
    private static final String SHOULD_HOLD_WITHOUT_EXPIRY = "The glove's Kindle should never expire, ends at %d";
    private static final String SHOULD_DRAIN_NOTHING_AT_EAT = "The finished eat should drain no one-shot cost, drained %d";
    private static final String SHOULD_PAY_UPKEEP = "%d ticks held should drain %d mB of upkeep, drained %d";
    private static final String SHOULD_BE_HELD = "Kindle should be held after the eat";
    private static final String SHOULD_SHOW_TIME_LEFT =
            "The effect list should show held Kindle with %d ticks left and no particles, held as a glove effect: %s";
    private static final String SHOULD_END_HELD = "Invoking held Kindle again should end it, held reads %s";
    private static final String SHOULD_CLEAR_OVERLAY = "An ended Kindle should clear its embers, %d halves stand";
    private static final String SHOULD_NOT_EAT_AGAIN = "Invoking held Kindle again should start no eat";
    private static final String SHOULD_STAND_PAID = "Kindle should stand while its upkeep is paid, tick %d";
    private static final String SHOULD_END_DRY = "Kindle should end on the tick the inventory cannot pay, held reads %s";
    private static final String SHOULD_STOP_EATING = "A released eat should leave the player out of the using state";
    private static final String SHOULD_LAY_NOTHING = "A released eat should lay no ember, %d halves stand";
    private static final String SHOULD_DRAIN_NOTHING = "A released eat should drain nothing, drained %d";
    private static final String SHOULD_NOT_EAT_UNKNOWN = "A refused blink should start no eat";
    private static final String SHOULD_STAY_REFUSED = "A refused blink should leave the player where it stood, moved ";
    private static final String SHOULD_DRAIN_NOTHING_REFUSED = "A refused blink should drain nothing, drained ";

    private SelfDeliveryTests() {
    }

    /**
     * A mock player facing east invokes ender blink and moves the blink's
     * range east in that tick, with the goo drained by its cost and no eat
     * started.
     *
     * @param helper the gametest helper
     */
    public static void enderBlink(GameTestHelper helper) {
        AbilityDefinition blink = requireAbility(helper, ENDER_BLINK);
        ServerPlayer player = invoker(helper, GooTypes.ENDER, ENDER_BLINK);
        KnownRecipes.teachRequires(player, blink);
        player.setYRot(FACING_EAST);
        player.setXRot(0);
        double xBefore = player.getX();
        int heldBefore = held(player, GooTypes.ENDER);

        invoke(player, GooTypes.ENDER, ENDER_BLINK);

        boolean using = player.isUsingItem();
        double moved = player.getX() - xBefore;
        int drained = heldBefore - held(player, GooTypes.ENDER);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertFalse(using, SHOULD_RUN_ON_COMMAND);
        helper.assertTrue(Math.abs(moved - BLINK_RANGE) < MOVE_TOLERANCE,
                String.format(SHOULD_BLINK_EAST, BLINK_RANGE, moved));
        helper.assertTrue(drained == blink.cost(), String.format(SHOULD_DRAIN_COST, blink.cost(), drained));
        helper.succeed();
    }

    /**
     * A mock player looking up and east invokes typhoon propulsion, and its
     * motion reads the push strength along its look with its fall cleared in
     * that tick, with no eat started.
     *
     * @param helper the gametest helper
     */
    public static void typhoonPropel(GameTestHelper helper) {
        AbilityDefinition propel = requireAbility(helper, TYPHOON_PROPEL);
        ServerPlayer player = invoker(helper, GooTypes.TYPHOON, TYPHOON_PROPEL);
        KnownRecipes.teachRequires(player, propel);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_UP);
        player.fallDistance = BUILT_UP_FALL;
        Vec3 expected = player.getLookAngle().scale(PROPEL_STRENGTH);

        invoke(player, GooTypes.TYPHOON, TYPHOON_PROPEL);

        boolean using = player.isUsingItem();
        Vec3 motion = player.getDeltaMovement();
        double fall = player.fallDistance;
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertFalse(using, SHOULD_RUN_ON_COMMAND);
        helper.assertTrue(motion.distanceTo(expected) < MOVE_TOLERANCE, String.format(SHOULD_PROPEL, expected, motion));
        helper.assertTrue(fall == 0, String.format(SHOULD_CLEAR_FALL, fall));
        helper.succeed();
    }

    /**
     * A mock player invokes blaze kindle: it starts eating, holds no ember
     * and its goo whole mid-eat, and wears one ember heart over ash with no
     * expiry and no one-shot cost drained when the eat finishes.
     *
     * @param helper the gametest helper
     */
    public static void kindleEatsBeforeTheEmbers(GameTestHelper helper) {
        AbilityDefinition kindle = requireAbility(helper, BLAZE_KINDLE);
        ServerPlayer player = survivalInvoker(helper, GooTypes.BLAZE, BLAZE_KINDLE);
        KnownRecipes.teachRequires(player, kindle);
        int heldBefore = held(player, GooTypes.BLAZE);

        invoke(player, GooTypes.BLAZE, BLAZE_KINDLE);

        helper.assertTrue(player.isUsingItem(), SHOULD_START_EATING);
        tickThrough(helper, player);
        helper.runAfterDelay(MID_EAT, () -> {
            int embers = HeartOverlayTests.halves(player);
            int drained = heldBefore - held(player, GooTypes.BLAZE);
            helper.assertTrue(embers == 0, String.format(SHOULD_LAY_NOTHING_MID_EAT, embers));
            helper.assertTrue(drained == 0, String.format(SHOULD_DRAIN_NOTHING_MID_EAT, drained));
        });
        helper.runAfterDelay(AFTER_EAT, () -> {
            int embers = HeartOverlayTests.halves(player);
            int drained = heldBefore - held(player, GooTypes.BLAZE);
            long endsAt = player.getData(GooAttachments.HEART_OVERLAY).expiresAt();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(embers == PRIMED_EMBERS, String.format(SHOULD_LAY_EMBERS, PRIMED_EMBERS, embers));
            helper.assertTrue(endsAt == HeartOverlay.NEVER_EXPIRES, String.format(SHOULD_HOLD_WITHOUT_EXPIRY, endsAt));
            helper.assertTrue(drained == 0, String.format(SHOULD_DRAIN_NOTHING_AT_EAT, drained));
            helper.succeed();
        });
    }

    /**
     * A player eats Kindle through: the inventory holds what it held the
     * tick the eat finished, and twenty ticks later twenty upkeeps less.
     * self-effects-trickle-until-ended
     *
     * @param helper the gametest helper
     */
    public static void kindleHeldPaysUpkeepEachTick(GameTestHelper helper) {
        AbilityDefinition kindle = requireAbility(helper, BLAZE_KINDLE);
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.BLAZE, BLAZE_KINDLE);
        int heldAtEat = held(player, GooTypes.BLAZE);
        int drainedAtEat = HELD_GOO * GooStacks.THOUSAND - heldAtEat;
        helper.assertTrue(drainedAtEat == 0, String.format(SHOULD_DRAIN_NOTHING_AT_EAT, drainedAtEat));
        helper.assertTrue(player.getData(GooAttachments.HELD_EFFECTS).holds(BLAZE_KINDLE), SHOULD_BE_HELD);
        // brew-runs-the-crawl-prepaid-on-a-shown-clock: the effect list shows the glove effect's time left
        MobEffectInstance shown = player.getEffect(GooMobEffects.BREW_EFFECTS.get(GooTypes.BLAZE));
        int expectedTicks = heldAtEat / kindle.upkeep();
        boolean stillGlove = player.getData(GooAttachments.HELD_EFFECTS).held().stream()
                .noneMatch(HeldEffects.Held::prepaid);
        helper.assertTrue(shown != null && Math.abs(shown.getDuration() - expectedTicks) <= 1 && !shown.isVisible()
                && stillGlove, String.format(SHOULD_SHOW_TIME_LEFT, expectedTicks, shown));
        tickFor(helper, player, UPKEEP_TICKS);
        helper.runAfterDelay(UPKEEP_TICKS + 1, () -> {
            int drained = heldAtEat - held(player, GooTypes.BLAZE);
            int expected = UPKEEP_TICKS * kindle.upkeep();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(drained == expected, String.format(SHOULD_PAY_UPKEEP, UPKEEP_TICKS, expected, drained));
            helper.succeed();
        });
    }

    /**
     * A player holding Kindle invokes it again: no eat starts, the effect
     * ends and its embers clear.
     * self-effects-trickle-until-ended
     *
     * @param helper the gametest helper
     */
    public static void kindleInvokedAgainEnds(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.BLAZE, BLAZE_KINDLE);
        helper.assertTrue(player.getData(GooAttachments.HELD_EFFECTS).holds(BLAZE_KINDLE), SHOULD_BE_HELD);

        invoke(player, GooTypes.BLAZE, BLAZE_KINDLE);

        boolean using = player.isUsingItem();
        HeldEffects heldAfter = player.getData(GooAttachments.HELD_EFFECTS);
        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertFalse(using, SHOULD_NOT_EAT_AGAIN);
        helper.assertTrue(heldAfter.isEmpty(), String.format(SHOULD_END_HELD, heldAfter));
        helper.assertTrue(overlay == HeartOverlay.NONE, String.format(SHOULD_CLEAR_OVERLAY, overlay.shieldHalves()));
        helper.succeed();
    }

    /**
     * A player holding goo for exactly three ticks of upkeep eats Kindle:
     * it stands through the third tick, and on the fourth it ends and its
     * embers clear.
     * self-effects-trickle-until-ended
     *
     * @param helper the gametest helper
     */
    public static void kindleEndsWhenDry(GameTestHelper helper) {
        AbilityDefinition kindle = requireAbility(helper, BLAZE_KINDLE);
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.BLAZE, BLAZE_KINDLE);
        int paid = PAID_TICKS * kindle.upkeep();
        GooSourceScanner.deplete(player, GooTypes.BLAZE, held(player, GooTypes.BLAZE) - paid);
        tickFor(helper, player, PAID_TICKS - 1);
        // runnables sharing a delay run in no fixed order, so each checked tick ticks itself
        helper.runAfterDelay(PAID_TICKS, () -> {
            player.doTick();
            helper.assertTrue(player.getData(GooAttachments.HELD_EFFECTS).holds(BLAZE_KINDLE),
                    String.format(SHOULD_STAND_PAID, PAID_TICKS));
        });
        helper.runAfterDelay(PAID_TICKS + 1, () -> {
            player.doTick();
            HeldEffects heldAfter = player.getData(GooAttachments.HELD_EFFECTS);
            HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(heldAfter.isEmpty(), String.format(SHOULD_END_DRY, heldAfter));
            helper.assertTrue(overlay == HeartOverlay.NONE, String.format(SHOULD_CLEAR_OVERLAY, overlay.shieldHalves()));
            helper.succeed();
        });
    }

    /**
     * Ticks a player once per game tick for the given ticks, the way its
     * connection would; a check belongs at a later delay, since runnables
     * sharing a delay run in no fixed order.
     *
     * @param helper the gametest helper
     * @param player the player
     * @param ticks  the ticks to run
     */
    static void tickFor(GameTestHelper helper, ServerPlayer player, int ticks) {
        for (int tick = 1; tick <= ticks; tick++) {
            helper.runAfterDelay(tick, player::doTick);
        }
    }

    /**
     * A mock player invokes blaze kindle and lets go of the use before the
     * eat finishes: the eat ends, no ember lays and no goo drains.
     *
     * @param helper the gametest helper
     */
    public static void kindleLetGoMidEatRunsNothing(GameTestHelper helper) {
        AbilityDefinition kindle = requireAbility(helper, BLAZE_KINDLE);
        ServerPlayer player = survivalInvoker(helper, GooTypes.BLAZE, BLAZE_KINDLE);
        KnownRecipes.teachRequires(player, kindle);
        int heldBefore = held(player, GooTypes.BLAZE);

        invoke(player, GooTypes.BLAZE, BLAZE_KINDLE);

        helper.assertTrue(player.isUsingItem(), SHOULD_START_EATING);
        tickThrough(helper, player);
        helper.runAfterDelay(RELEASE_AT, player::releaseUsingItem);
        helper.runAfterDelay(AFTER_EAT, () -> {
            boolean using = player.isUsingItem();
            int embers = HeartOverlayTests.halves(player);
            int drained = heldBefore - held(player, GooTypes.BLAZE);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertFalse(using, SHOULD_STOP_EATING);
            helper.assertTrue(embers == 0, String.format(SHOULD_LAY_NOTHING, embers));
            helper.assertTrue(drained == 0, String.format(SHOULD_DRAIN_NOTHING, drained));
            helper.succeed();
        });
    }

    /**
     * A mock player who has never melted an ender pearl invokes ender blink:
     * the throw is refused whole, so the player stays put, no goo drains and
     * no eat starts (decision ability-hidden-until-recipes-known).
     *
     * @param helper the gametest helper
     */
    public static void gatedBlinkRefusedWithoutTheRecipe(GameTestHelper helper) {
        ServerPlayer player = invoker(helper, GooTypes.ENDER, ENDER_BLINK);
        player.setYRot(FACING_EAST);
        player.setXRot(0);
        double xBefore = player.getX();
        int heldBefore = held(player, GooTypes.ENDER);

        invoke(player, GooTypes.ENDER, ENDER_BLINK);

        boolean using = player.isUsingItem();
        double moved = player.getX() - xBefore;
        int drained = heldBefore - held(player, GooTypes.ENDER);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertFalse(using, SHOULD_NOT_EAT_UNKNOWN);
        helper.assertTrue(moved == 0, SHOULD_STAY_REFUSED + moved);
        helper.assertTrue(drained == 0, SHOULD_DRAIN_NOTHING_REFUSED + drained);
        helper.succeed();
    }

    /**
     * Sends the payload a real glove sends for its selection: the glove is
     * set to the ability first, as the radial sets it before any throw.
     *
     * @param player  the invoking player
     * @param gooType the ability's goo type
     * @param ability the ability's id
     */
    public static void invoke(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, Identifier ability) {
        GooGloveItem.setSelection(player.getMainHandItem(), GloveSelection.ofAbility(gooType, ability));
        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(gooType), NO_ENTITY,
                player.blockPosition(), NO_ENTITY, false, ability.toString(), player.getEyePosition()));
    }

    /**
     * Carries a player through a started eat at once, ticking it the way its
     * connection would until the eat finishes; a player not eating is left
     * untouched.
     *
     * @param player the player
     */
    public static void eatThrough(ServerPlayer player) {
        for (int tick = 0; tick <= SelfEatRoute.EAT_TICKS && player.isUsingItem(); tick++) {
            player.doTick();
        }
    }

    /**
     * Ticks the player once per game tick for the eat's duration, so the use
     * counts down and finishes on the server across real ticks.
     *
     * @param helper the gametest helper
     * @param player the eating player
     */
    private static void tickThrough(GameTestHelper helper, ServerPlayer player) {
        for (int tick = 1; tick <= SelfEatRoute.EAT_TICKS; tick++) {
            helper.runAfterDelay(tick, player::doTick);
        }
    }

    private static AbilityDefinition requireAbility(GameTestHelper helper, Identifier id) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(id);
        helper.assertTrue(ability != null, String.format(ABILITY_REQUIRED, id));
        return ability;
    }

    private static int held(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(gooType, 0);
    }

    /**
     * A survival mock server player standing in the bay, holding a glove
     * whose selection names the ability, with two costs of the type in its
     * inventory. The mock player helper's player answers creative from an
     * override setGameMode cannot reach, so this player is joined the way
     * the helper joins one, without the override, and set to survival.
     *
     * @param helper  the gametest helper
     * @param gooType the ability's goo type
     * @param ability the ability the glove selects
     * @return the player, reading survival
     */
    private static ServerPlayer survivalInvoker(GameTestHelper helper, ResourceKey<GooTypeDefinition> gooType,
            Identifier ability) {
        MinecraftServer server = helper.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), SURVIVAL_PLAYER_NAME), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(),
                cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(player.gameMode() == GameType.SURVIVAL && !player.isCreative(), SHOULD_BE_SURVIVAL);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        ItemStack glove = new ItemStack(GooItems.GOO_GLOVE.get());
        GooGloveItem.setSelection(glove, GloveSelection.ofAbility(gooType, ability));
        player.setItemInHand(InteractionHand.MAIN_HAND, glove);
        player.getInventory().add(GooStacks.createForOutput(gooType, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }

    /**
     * A mock player standing in the bay, holding a glove whose selection
     * names the ability, with two costs of the type in its inventory.
     *
     * @param helper  the gametest helper
     * @param gooType the ability's goo type
     * @param ability the ability the glove selects
     * @return the player
     */
    static ServerPlayer invoker(GameTestHelper helper, ResourceKey<GooTypeDefinition> gooType,
            Identifier ability) {
        ServerPlayer player = invoker(helper, gooType);
        GooGloveItem.setSelection(player.getMainHandItem(), GloveSelection.ofAbility(gooType, ability));
        return player;
    }

    /**
     * A mock player standing in the bay, holding a glove with no selection
     * and two costs of the type in its inventory.
     *
     * @param helper  the gametest helper
     * @param gooType the goo type the player holds
     * @return the player
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static ServerPlayer invoker(GameTestHelper helper, ResourceKey<GooTypeDefinition> gooType) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(gooType, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }

    /**
     * A survival player eats Haste from the glove: speed and haste stand with
     * their particles off for as long as it is held; invoking it again ends
     * it and both go (decisions self-effects-trickle-until-ended and
     * haste-stacks-speed-under-the-golden-overlay).
     *
     * @param helper the gametest helper
     */
    public static void hasteHoldsWithoutParticles(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.AEON, AEON_HASTE);
        boolean held = player.getData(GooAttachments.HELD_EFFECTS).holds(AEON_HASTE);
        MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
        MobEffectInstance haste = player.getEffect(MobEffects.HASTE);
        invoke(player, GooTypes.AEON, AEON_HASTE);
        boolean heldAfter = player.getData(GooAttachments.HELD_EFFECTS).holds(AEON_HASTE);
        boolean hastedAfter = player.hasEffect(MobEffects.SPEED) || player.hasEffect(MobEffects.HASTE);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(held, SHOULD_BE_HELD);
        for (MobEffectInstance effect : new MobEffectInstance[] {speed, haste}) {
            helper.assertTrue(effect != null && effect.isInfiniteDuration() && !effect.isVisible(),
                    String.format(SHOULD_HASTE_HELD, effect));
        }
        helper.assertFalse(heldAfter || hastedAfter, SHOULD_END_HASTE);
        helper.succeed();
    }

    /**
     * A survival player eats Sight from the glove: the sight is held, standing
     * until ended, and no goo drains at the eat
     * (decisions self-effects-trickle-until-ended and sight-lengthens-shift-and-outlines-fungus).
     *
     * @param helper the gametest helper
     */
    public static void sightEatsBeforeTheSight(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.SHROOM, SHROOM_SIGHT);
        boolean held = player.getData(GooAttachments.HELD_EFFECTS).holds(SHROOM_SIGHT);
        boolean sees = player.getData(GooAttachments.SIGHT).standsAt(player.level().getGameTime());
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(held, SHOULD_BE_HELD);
        helper.assertTrue(sees, SHOULD_SEE);
        helper.succeed();
    }

    /**
     * A player eats Sight holding shroom for exactly three ticks of upkeep: the
     * sight stands through the third tick, and on the fourth the held effect
     * ends, the sight clears and the shift's reach falls back to its base
     * (decisions self-effects-trickle-until-ended and sight-lengthens-shift-and-outlines-fungus).
     *
     * @param helper the gametest helper
     */
    public static void sightEndsWhenShroomRunsDry(GameTestHelper helper) {
        AbilityDefinition sight = requireAbility(helper, SHROOM_SIGHT);
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.SHROOM, SHROOM_SIGHT);
        int paid = PAID_TICKS * sight.upkeep();
        GooSourceScanner.deplete(player, GooTypes.SHROOM, held(player, GooTypes.SHROOM) - paid);
        tickFor(helper, player, PAID_TICKS - 1);
        helper.runAfterDelay(PAID_TICKS, () -> {
            player.doTick();
            helper.assertTrue(player.getData(GooAttachments.SIGHT).standsAt(player.level().getGameTime()),
                    String.format(SHOULD_SEE_PAID, PAID_TICKS));
        });
        helper.runAfterDelay(PAID_TICKS + 1, () -> {
            player.doTick();
            boolean held = player.getData(GooAttachments.HELD_EFFECTS).holds(SHROOM_SIGHT);
            boolean sees = player.getData(GooAttachments.SIGHT).standsAt(player.level().getGameTime());
            double reach = ShiftStep.reachOf(player, BASE_SHIFT_RANGE);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertFalse(held || sees, SHOULD_END_SIGHT_DRY);
            helper.assertTrue(reach == BASE_SHIFT_RANGE, String.format(SHOULD_FALL_BACK, BASE_SHIFT_RANGE, reach));
            helper.succeed();
        });
    }
}
