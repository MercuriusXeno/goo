package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooEffectScheduler;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.GooStreamPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for aeon's Timekeeper: an aeon goo landing on a prism makes it a
 * timekeeper banking ticks; aeon landing on it again feeds the bank; Tick
 * streamed on it spends the bank moving the clock forward and winds every
 * online player's time since rest back as far; Rewind streamed on it costs
 * nothing and draws the standing-banked charge out as aeon goo.
 * timekeeper-prism-banks-ticks-forward-only
 */
public final class TimekeeperTests {

    private static final BlockPos FLOOR_POS = new BlockPos(1, 1, 1);
    private static final BlockPos PRISM_POS = FLOOR_POS.above();
    /** Three blocks east of the prism, its floor laid, within Tick's and Rewind's reach. */
    private static final BlockPos STAND_POS = FLOOR_POS.east(3);
    private static final int NO_ENTITY = -1;
    private static final String AEON_STASIS = "goo:aeon_stasis";
    private static final Identifier AEON_TICK = Identifier.parse("goo:aeon_tick");
    private static final Identifier AEON_REWIND = Identifier.parse("goo:aeon_rewind");
    /** aeon_timekeeper.json's spend per held tick of Tick. */
    private static final long SPEND_PER_TICK = 40;
    /** aeon_rewind.json's withdraw: the charge one mB is worth, and the most mB a held tick. */
    private static final int CHARGE_PER_MB = 20;
    private static final int MB_PER_TICK = 50;
    private static final int HOLD_TICKS = 5;
    /** Ticks the timekeeper stands before the stream, banking a tick each. */
    private static final int STAND_TICKS = 3;
    /** A standing bank large enough that Rewind's whole hold draws its full rate. */
    private static final int STANDING_CHARGE = 100_000;
    private static final int HELD_GOO = 1000;
    private static final int TIME_SINCE_REST = 5000;
    private static final String SHOULD_BANK = "The prism should bank ticks, holding combo '%s'";
    private static final String SHOULD_FEED = "An aeon landing should feed the bank its cost, fed %d";
    private static final String SHOULD_MOVE_FORWARD = "The clock should move forward by the %d spent, moved %d";
    private static final String SHOULD_WIND_BACK = "Time since rest should wind back by %d from %d, reads %d";
    private static final String SHOULD_WITHDRAW = "Rewind should pour %d mB of aeon into holdings for free, %d became %d";
    private static final String ABILITY_REQUIRED = "Ability registry must hold %s";

    private TimekeeperTests() {
    }

    /**
     * A timekeeper fed by an aeon landing spends its bank under a held Tick:
     * the clock moves forward by the charge spent, never back.
     *
     * @param helper the gametest helper
     */
    public static void timekeeperTickMovesDayForward(GameTestHelper helper) {
        PrismBlockEntity prism = timekeeper(helper);
        landAeon(helper);
        helper.assertTrue(prism.bank().fed() >= HELD_GOO, String.format(SHOULD_FEED, prism.bank().fed()));
        ServerPlayer player = streamer(helper, AEON_TICK);
        helper.runAfterDelay(STAND_TICKS, () -> {
            long before = clockOf(helper.getLevel());
            long banked = prism.bank().total();
            for (int held = 0; held < HOLD_TICKS; held++) {
                GooStreamHandler.streamTick(player, payload(player, AEON_TICK));
            }
            long spent = banked - prism.bank().total();
            long moved = clockOf(helper.getLevel()) - before;
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(spent == HOLD_TICKS * SPEND_PER_TICK && moved == spent,
                    String.format(SHOULD_MOVE_FORWARD, spent, moved));
            helper.succeed();
        });
    }

    /**
     * Tick's skip on a timekeeper winds an online player's time since rest
     * back by the ticks skipped.
     *
     * @param helper the gametest helper
     */
    public static void timekeeperOffsetsRestStat(GameTestHelper helper) {
        PrismBlockEntity prism = timekeeper(helper);
        landAeon(helper);
        ServerPlayer player = streamer(helper, AEON_TICK);
        Stat<Identifier> sinceRest = Stats.CUSTOM.get(Stats.TIME_SINCE_REST);
        helper.runAfterDelay(STAND_TICKS, () -> {
            player.getStats().setValue(player, sinceRest, TIME_SINCE_REST);
            long banked = prism.bank().total();
            GooStreamHandler.streamTick(player, payload(player, AEON_TICK));
            long spent = banked - prism.bank().total();
            int after = player.getStats().getValue(sinceRest);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(spent > 0 && after == TIME_SINCE_REST - spent,
                    String.format(SHOULD_WIND_BACK, spent, TIME_SINCE_REST, after));
            helper.succeed();
        });
    }

    /**
     * Rewind held on a timekeeper with standing charge pours that charge out
     * as aeon goo into the player's holdings, at no cost to them.
     *
     * @param helper the gametest helper
     */
    public static void timekeeperRewindWithdraws(GameTestHelper helper) {
        PrismBlockEntity prism = timekeeper(helper);
        prism.bankTicks(STANDING_CHARGE, (int) SPEND_PER_TICK);
        ServerPlayer player = streamer(helper, AEON_REWIND);
        helper.runAfterDelay(1, () -> {
            int before = aeonHeld(player);
            for (int held = 0; held < HOLD_TICKS; held++) {
                GooStreamHandler.streamTick(player, payload(player, AEON_REWIND));
            }
            int after = aeonHeld(player);
            int expected = HOLD_TICKS * MB_PER_TICK;
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(after - before == expected, String.format(SHOULD_WITHDRAW, expected, before, after));
            helper.succeed();
        });
    }

    /**
     * Stands a prism on a floor and lands an aeon goo on it, which makes it a
     * timekeeper banking ticks.
     */
    private static PrismBlockEntity timekeeper(GameTestHelper helper) {
        helper.setBlock(FLOOR_POS, Blocks.STONE);
        helper.setBlock(STAND_POS, Blocks.STONE);
        helper.setBlock(PRISM_POS, GooBlocks.PRISM.get());
        landAeon(helper);
        PrismBlockEntity prism = helper.getBlockEntity(PRISM_POS, PrismBlockEntity.class);
        helper.assertTrue(prism.banksTicks(), String.format(SHOULD_BANK, prism.getCombo()));
        return prism;
    }

    private static void landAeon(GameTestHelper helper) {
        GooEffectScheduler arrivals = GooServerState.of(helper.getLevel().getServer()).gooEffects();
        int arrivalTick = helper.getLevel().getServer().getTickCount();
        arrivals.enqueue(new PendingEffect(arrivalTick, helper.getLevel(), null, GooTypes.AEON, NO_ENTITY,
                helper.absolutePos(PRISM_POS), Direction.UP, AEON_STASIS));
        arrivals.drainArrivedEffects(arrivalTick);
    }

    private static long clockOf(ServerLevel level) {
        Holder<WorldClock> clock = level.dimensionType().defaultClock().orElseThrow();
        return level.clockManager().getTotalTicks(clock);
    }

    private static int aeonHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.AEON, 0);
    }

    private static GooStreamPayload payload(ServerPlayer player, Identifier ability) {
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.AEON), ability.toString(), player.getEyePosition(),
                player.getEyePosition());
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer streamer(GameTestHelper helper, Identifier abilityId) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(abilityId);
        helper.assertTrue(ability != null, String.format(ABILITY_REQUIRED, abilityId));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS.above()));
        player.setPos(stand.x, stand.y, stand.z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(PRISM_POS)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.AEON, HELD_GOO));
        KnownRecipes.teachRequires(player, ability);
        return player;
    }
}
