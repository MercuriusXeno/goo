package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.program.BlinkLanding;
import com.mercuriusxeno.goo.ability.program.LeaderStep;
import com.mercuriusxeno.goo.ability.zoo.Leader;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.UUID;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for the self delivery through the real throw path. A self
 * ability wearing the self badge runs on command: its cost at stack zero
 * drains and its programs run on the invoking player the tick it is invoked
 * (decision self-delivery-runs-on-player). A durative ability, one
 * wearing the durative badge, starts the player eating the glove, and drains
 * and runs when the eat finishes; an eat let go before then runs nothing
 * and drains nothing (decision self-brew-goos-eat-before-the-effect). The
 * mock server player has no connection ticking it, so the eat tests tick it
 * the way the connection would, through doTick. A finished eat holds the
 * effect, paying its upkeep each tick after, until invoked again or dry
 * (decision self-effects-trickle-until-ended).
 */
@RegistersGameTests
public final class SelfDeliveryTests {
    private static final String SELF_ENDER_BLINK = "self_ender_blink";
    private static final String SELF_KINDLE_EATS_FIRST = "self_kindle_eats_before_the_embers";
    private static final String SELF_KINDLE_HELD_PAYS_UPKEEP = "self_kindle_held_pays_upkeep_each_tick";
    private static final String SELF_KINDLE_INVOKED_AGAIN_ENDS = "self_kindle_invoked_again_ends";
    private static final String SELF_KINDLE_ENDS_WHEN_DRY = "self_kindle_ends_when_dry";
    private static final String SELF_SIGHT_EATS = "self_sight_eats_before_the_sight";
    private static final String LEADER_LAYS_AND_ENDING_CLEARS = "leader_lays_its_state_and_ending_clears_it";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SELF_ENDER_BLINK, SelfDeliveryTests::enderBlink);
        GooTestFunctions.registerTest(registrar, SELF_KINDLE_EATS_FIRST, SelfDeliveryTests::kindleEatsBeforeTheEmbers);
        GooTestFunctions.registerTest(registrar, SELF_KINDLE_HELD_PAYS_UPKEEP, SelfDeliveryTests::kindleHeldPaysUpkeepEachTick);
        GooTestFunctions.registerTest(registrar, SELF_KINDLE_INVOKED_AGAIN_ENDS, SelfDeliveryTests::kindleInvokedAgainEnds);
        GooTestFunctions.registerTest(registrar, SELF_KINDLE_ENDS_WHEN_DRY, SelfDeliveryTests::kindleEndsWhenDry);
        GooTestFunctions.registerTest(registrar, SELF_SIGHT_EATS, SelfDeliveryTests::sightEatsBeforeTheSight);
        GooTestFunctions.registerTest(registrar, LEADER_LAYS_AND_ENDING_CLEARS,
                SelfDeliveryTests::leaderLaysItsStateAndEndingClearsIt);
    }


    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    private static final int NO_ENTITY = -1;
    /** Two thousand mB, two casts' worth. */
    private static final int HELD_GOO = 2;
    /** Enough ender goo for a full-range blink through a wall, past what the player holds. */
    private static final int LONG_TRIP_GOO = 4 * GooStacks.THOUSAND;
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    private static final Identifier ENDER_BLINK = Identifier.parse("goo:ender_blink");
    private static final Identifier BLAZE_KINDLE = Identifier.parse("goo:blaze_kindle");
    private static final Identifier SHROOM_SIGHT = Identifier.parse("goo:shroom_sight");
    private static final String SHOULD_SEE = "Once the eat finishes the player should hold fungal sight";
    private static final Identifier ZOO_LEADER = Identifier.parse("goo:zoo_leader");
    private static final String SHOULD_HOLD_LEADER = "Once the eat finishes the player should hold Leader";
    private static final String SHOULD_LAY_LEADER = "Leader should lay its state with the shipped radius %.1f, laid %s";
    private static final String SHOULD_CLEAR_LEADER = "Invoking held Leader again should end it and clear its state, reads %s";
    /** One ember heart, which Kindle primes before its crawl embers the rest. */
    private static final int PRIMED_EMBERS = HeartOverlay.FULL_SHIELD;
    /** The ticks a held Kindle is watched paying its upkeep. */
    private static final int UPKEEP_TICKS = 20;
    /** The ticks of upkeep the dry player holds goo for. */
    private static final int PAID_TICKS = 3;
    /** The tick after the eat's last tick, when the finish has run. */
    private static final int AFTER_EAT = SelfEatRoute.EAT_TICKS + 1;
    private static final String SURVIVAL_PLAYER_NAME = "test-survival-player";
    private static final String SHOULD_BE_SURVIVAL = "The eating player should read survival, not creative";
    private static final String ABILITY_REQUIRED = "Ability registry must hold %s";
    private static final String SHOULD_BLINK_EAST = "The player should move east the tick it blinks, moved %.3f";
    private static final String SHOULD_DRAIN_COST = "The cast should drain the stack-zero cost of %d mB, drained %d";
    private static final String SHOULD_START_EATING = "Invoking a durative ability should start the player eating";
    private static final String SHOULD_LAY_EMBERS = "The finished eat should lay %d ember halves, laid %d";
    private static final String SHOULD_HOLD_WITHOUT_EXPIRY = "The glove's Kindle should never expire, ends at %d";
    private static final String SHOULD_PAY_UPKEEP = "%d ticks held should drain %d mB of upkeep, drained %d";
    private static final String SHOULD_BE_HELD = "Kindle should be held after the eat";
    private static final String SHOULD_SHOW_TIME_LEFT =
            "The effect list should show held Kindle with %d ticks left and no particles, held as a glove effect: %s";
    private static final String SHOULD_END_HELD = "Invoking held Kindle again should end it, held reads %s";
    private static final String SHOULD_CLEAR_OVERLAY = "An ended Kindle should clear its embers, %d halves stand";
    private static final String SHOULD_MIRROR_WHILE_HELD = "Held Kindle should stand in the effect list";
    private static final String SHOULD_LEAVE_THE_EFFECT_LIST =
            "An ended Kindle should leave the effect list, the blaze held effect reads %s";
    private static final String SHOULD_STAND_PAID = "Kindle should stand while its upkeep is paid, tick %d";
    private static final String SHOULD_END_DRY = "Kindle should end on the tick the inventory cannot pay, held reads %s";

    private SelfDeliveryTests() {
    }

    /**
     * A mock player facing east invokes ender blink and moves east in that
     * tick, through the bay's east wall into the free air past it; the goo
     * drained is the flat cost, the
     * per-block amount for the range and the wall surcharge
     * (decision blink-lands-safely-costed-by-distance).
     *
     * @param helper the gametest helper
     */
    public static void enderBlink(GameTestHelper helper) {
        AbilityDefinition blink = requireAbility(helper, ENDER_BLINK);
        ServerPlayer player = invoker(helper, GooTypes.ENDER, ENDER_BLINK);
        player.getInventory().add(GooStacks.createForOutput(GooTypes.ENDER, LONG_TRIP_GOO));
        KnownRecipes.teachRequires(player, blink);
        player.setYRot(FACING_EAST);
        player.setXRot(0);
        double xBefore = player.getX();
        int heldBefore = held(player, GooTypes.ENDER);

        invoke(player, GooTypes.ENDER, ENDER_BLINK);

        double moved = player.getX() - xBefore;
        int drained = heldBefore - held(player, GooTypes.ENDER);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(moved > 0, String.format(SHOULD_BLINK_EAST, moved));
        int pricedClear = blink.distancePrice().priceOf(blink.cost().in(GooValueTable.EMPTY),
                Optional.of(new BlinkLanding(Vec3.ZERO, moved, false)));
        int pricedThroughWall = blink.distancePrice().priceOf(blink.cost().in(GooValueTable.EMPTY),
                Optional.of(new BlinkLanding(Vec3.ZERO, moved, true)));
        helper.assertTrue(drained == pricedClear || drained == pricedThroughWall,
                String.format(SHOULD_DRAIN_COST, pricedClear, drained));
        helper.succeed();
    }

    /**
     * A mock player invokes blaze kindle: it starts eating, and wears one ember
     * heart over ash with no expiry when the eat finishes.
     *
     * @param helper the gametest helper
     */
    public static void kindleEatsBeforeTheEmbers(GameTestHelper helper) {
        AbilityDefinition kindle = requireAbility(helper, BLAZE_KINDLE);
        ServerPlayer player = survivalInvoker(helper, GooTypes.BLAZE, BLAZE_KINDLE);
        KnownRecipes.teachRequires(player, kindle);

        invoke(player, GooTypes.BLAZE, BLAZE_KINDLE);

        helper.assertTrue(player.isUsingItem(), SHOULD_START_EATING);
        tickThrough(helper, player);
        helper.runAfterDelay(AFTER_EAT, () -> {
            int embers = HeartOverlayTests.halves(player);
            long endsAt = player.getData(GooAttachments.HEART_OVERLAY).expiresAt();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(embers == PRIMED_EMBERS, String.format(SHOULD_LAY_EMBERS, PRIMED_EMBERS, embers));
            helper.assertTrue(endsAt == HeartOverlay.NEVER_EXPIRES, String.format(SHOULD_HOLD_WITHOUT_EXPIRY, endsAt));
            helper.succeed();
        });
    }

    /**
     * A player eats Kindle through: twenty ticks after the eat finished the
     * inventory holds twenty upkeeps less.
     * self-effects-trickle-until-ended
     *
     * @param helper the gametest helper
     */
    public static void kindleHeldPaysUpkeepEachTick(GameTestHelper helper) {
        AbilityDefinition kindle = requireAbility(helper, BLAZE_KINDLE);
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.BLAZE, BLAZE_KINDLE);
        int heldAtEat = held(player, GooTypes.BLAZE);
        helper.assertTrue(player.getData(GooAttachments.HELD_EFFECTS).holds(BLAZE_KINDLE), SHOULD_BE_HELD);
        // The effect list shows the held effect's time left, the ticks the held goo pays for.
        MobEffectInstance shown = player.getEffect(GooMobEffects.HELD_EFFECTS.get(GooTypes.BLAZE));
        int expectedTicks = heldAtEat / kindle.upkeep().in(GooValueTable.EMPTY);
        helper.assertTrue(shown != null && Math.abs(shown.getDuration() - expectedTicks) <= 1 && !shown.isVisible(),
                String.format(SHOULD_SHOW_TIME_LEFT, expectedTicks, shown));
        tickFor(helper, player, UPKEEP_TICKS);
        helper.runAfterDelay(UPKEEP_TICKS + 1, () -> {
            int drained = heldAtEat - held(player, GooTypes.BLAZE);
            int expected = UPKEEP_TICKS * kindle.upkeep().in(GooValueTable.EMPTY);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(drained == expected, String.format(SHOULD_PAY_UPKEEP, UPKEEP_TICKS, expected, drained));
            helper.succeed();
        });
    }

    /**
     * A player holding Kindle invokes it again: the effect ends and its
     * embers clear.
     * self-effects-trickle-until-ended
     *
     * @param helper the gametest helper
     */
    public static void kindleInvokedAgainEnds(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.BLAZE, BLAZE_KINDLE);
        helper.assertTrue(player.getData(GooAttachments.HELD_EFFECTS).holds(BLAZE_KINDLE), SHOULD_BE_HELD);
        MobEffectInstance shownWhileHeld = player.getEffect(GooMobEffects.HELD_EFFECTS.get(GooTypes.BLAZE));

        invoke(player, GooTypes.BLAZE, BLAZE_KINDLE);

        HeldEffects heldAfter = player.getData(GooAttachments.HELD_EFFECTS);
        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        MobEffectInstance shownAfter = player.getEffect(GooMobEffects.HELD_EFFECTS.get(GooTypes.BLAZE));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(heldAfter.isEmpty(), String.format(SHOULD_END_HELD, heldAfter));
        helper.assertTrue(overlay == HeartOverlay.NONE, String.format(SHOULD_CLEAR_OVERLAY, overlay.shieldHalves()));
        // brew-drink-path-leaves-the-mod: the effect list mirrors the hold, so the mirror leaves when the hold ends
        helper.assertTrue(shownWhileHeld != null, SHOULD_MIRROR_WHILE_HELD);
        helper.assertTrue(shownAfter == null, String.format(SHOULD_LEAVE_THE_EFFECT_LIST, shownAfter));
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
        int paid = PAID_TICKS * kindle.upkeep().in(GooValueTable.EMPTY);
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
     * A survival player eats Leader from the glove: the Leader state stands
     * with the radius zoo_leader.json gives its step, and invoking Leader
     * again ends the held effect and clears that state.
     * leader-is-zoos-brew-ability
     *
     * @param helper the gametest helper
     */
    public static void leaderLaysItsStateAndEndingClearsIt(GameTestHelper helper) {
        AbilityDefinition leaderAbility = requireAbility(helper, ZOO_LEADER);
        double shippedRadius = leaderAbility.behaviors().stream()
                .filter(LeaderStep.class::isInstance).map(LeaderStep.class::cast)
                .findFirst().map(LeaderStep::radius).orElse(Double.NaN);
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.ZOO, ZOO_LEADER);
        boolean held = player.getData(GooAttachments.HELD_EFFECTS).holds(ZOO_LEADER);
        Leader laid = player.getData(GooAttachments.LEADER);

        invoke(player, GooTypes.ZOO, ZOO_LEADER);

        boolean heldAfter = player.getData(GooAttachments.HELD_EFFECTS).holds(ZOO_LEADER);
        Leader after = player.getData(GooAttachments.LEADER);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(held, SHOULD_HOLD_LEADER);
        helper.assertTrue(laid.stands() && laid.radius() == shippedRadius,
                String.format(SHOULD_LAY_LEADER, shippedRadius, laid));
        helper.assertFalse(heldAfter || after.stands(), String.format(SHOULD_CLEAR_LEADER, after));
        helper.succeed();
    }
}
