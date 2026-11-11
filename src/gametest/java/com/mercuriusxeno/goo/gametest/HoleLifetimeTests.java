package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.hole.PortableHoles;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Gametests for how long Void's hole stands: 240 ticks when nothing is thrown
 * in, and each item it swallows raises the time left to 120 ticks when less
 * remains and leaves it be when more remains. Each test watches the hole every
 * tick from the tick it is first seen standing, so a check one tick either side
 * of a closing reads the closing itself, not the throw's flight.
 * decision hole-holds-twelve-seconds-and-each-throw-in-floors-six
 */
@RegistersGameTests
public final class HoleLifetimeTests {

    private static final String STANDS_ITS_LIFETIME = "hole_stands_its_lifetime";
    private static final String LATE_SWALLOW_FLOORS_TIME_LEFT = "hole_late_swallow_floors_time_left";
    private static final String EARLY_SWALLOW_CHANGES_NOTHING = "hole_early_swallow_changes_nothing";
    /** The hole's age a late item falls in at, with less than the refresh left. */
    private static final int LATE_DROP_AGE = 200;
    /** The hole's age a late-fed hole is checked still standing at, past its untouched lifetime. */
    private static final int LATE_CHECK_AGE = 300;
    /** The hole's age an early item falls in at, with more than the refresh left. */
    private static final int EARLY_DROP_AGE = 20;
    /** Ticks after the last check the test ends, so every check has its tick watched. */
    private static final int WATCH_PAST = 10;

    private HoleLifetimeTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, STANDS_ITS_LIFETIME, HoleLifetimeTests::standsItsLifetime);
        GooTestFunctions.registerTest(registrar, LATE_SWALLOW_FLOORS_TIME_LEFT,
                HoleLifetimeTests::lateSwallowFloorsTimeLeft);
        GooTestFunctions.registerTest(registrar, EARLY_SWALLOW_CHANGES_NOTHING,
                HoleLifetimeTests::earlySwallowChangesNothing);
    }

    /**
     * A hole nothing is thrown into stands at tick 239 of its life and is gone
     * after tick 240.
     *
     * @param helper the gametest helper
     */
    public static void standsItsLifetime(GameTestHelper helper) {
        HoleWatch watch = watchACast(helper, -1);
        helper.runAfterDelay(HoleTests.LANDED + HoleTests.LIFETIME + WATCH_PAST, () -> {
            watch.release(helper);
            assertStandsThenGone(helper, watch, watch.firstSeen + HoleTests.LIFETIME, "untouched");
            helper.succeed();
        });
    }

    /**
     * An item swallowed at tick 200, with 40 ticks left, raises the time left to
     * 120: the hole stands at tick 300 and is gone 120 ticks after the swallow.
     *
     * @param helper the gametest helper
     */
    public static void lateSwallowFloorsTimeLeft(GameTestHelper helper) {
        HoleWatch watch = watchACast(helper, LATE_DROP_AGE);
        helper.runAfterDelay(HoleTests.LANDED + LATE_DROP_AGE + HoleTests.REFRESH + WATCH_PAST, () -> {
            watch.release(helper);
            helper.assertTrue(watch.swallowedAt > 0, "The item dropped into the mouth should be swallowed");
            helper.assertTrue(watch.standsAt(watch.firstSeen + LATE_CHECK_AGE),
                    "The hole fed at tick " + LATE_DROP_AGE + " should stand at tick " + LATE_CHECK_AGE);
            assertStandsThenGone(helper, watch, watch.swallowedAt + HoleTests.REFRESH, "fed late");
            helper.succeed();
        });
    }

    /**
     * An item swallowed at tick 20, with 220 ticks left, changes nothing: the
     * hole is gone after tick 240 as an untouched hole is.
     *
     * @param helper the gametest helper
     */
    public static void earlySwallowChangesNothing(GameTestHelper helper) {
        HoleWatch watch = watchACast(helper, EARLY_DROP_AGE);
        helper.runAfterDelay(HoleTests.LANDED + HoleTests.LIFETIME + WATCH_PAST, () -> {
            watch.release(helper);
            helper.assertTrue(watch.swallowedAt > 0, "The item dropped into the mouth should be swallowed");
            assertStandsThenGone(helper, watch, watch.firstSeen + HoleTests.LIFETIME, "fed early");
            helper.succeed();
        });
    }

    /**
     * Asserts the hole stands the tick before a closing time and is gone the
     * tick after it, the one-tick slack covering whether the watch saw the
     * opening or the swallow in the tick it happened or the next.
     */
    private static void assertStandsThenGone(GameTestHelper helper, HoleWatch watch, long closesAt, String which) {
        helper.assertTrue(watch.standsAt(closesAt - 1), "The " + which + " hole should stand "
                + (closesAt - 1 - watch.firstSeen) + " ticks after it opened");
        helper.assertTrue(watch.goneAt(closesAt + 1), "The " + which + " hole should be gone "
                + (closesAt + 1 - watch.firstSeen) + " ticks after it opened");
    }

    /**
     * Casts a hole on the floor and watches it every tick, dropping an item
     * into its mouth at an age when one is asked for.
     *
     * @param dropAge the hole's age an item falls in at, negative for none
     */
    private static HoleWatch watchACast(GameTestHelper helper, int dropAge) {
        ServerPlayer caster = HoleTests.caster(helper, 1);
        HoleTests.castAt(caster, helper.absolutePos(HoleTests.FLOOR), Direction.UP);
        HoleWatch watch = new HoleWatch(caster);
        helper.onEachTick(() -> watch.observe(helper, dropAge));
        return watch;
    }

    /** Whether the caster's hole stood each game time the test watched, and when it opened and swallowed. */
    private static final class HoleWatch {

        private final ServerPlayer caster;
        private final Map<Long, Boolean> standing = new HashMap<>();
        private long firstSeen = -1;
        private long swallowedAt = -1;
        private ItemEntity dropped;

        HoleWatch(ServerPlayer caster) {
            this.caster = caster;
        }

        void observe(GameTestHelper helper, int dropAge) {
            long now = helper.getLevel().getGameTime();
            boolean stands = PortableHoles.of(caster.getUUID()).isPresent();
            standing.put(now, stands);
            if (stands && firstSeen < 0) {
                firstSeen = now;
            }
            if (dropped == null && dropAge >= 0 && firstSeen >= 0 && now - firstSeen == dropAge) {
                dropped = dropIntoTheMouth(helper);
            }
            if (dropped != null && swallowedAt < 0 && dropped.isRemoved()) {
                swallowedAt = now;
            }
        }

        boolean standsAt(long time) {
            return Boolean.TRUE.equals(standing.get(time));
        }

        boolean goneAt(long time) {
            return Boolean.FALSE.equals(standing.get(time));
        }

        void release(GameTestHelper helper) {
            helper.getLevel().getServer().getPlayerList().remove(caster);
        }

        private static ItemEntity dropIntoTheMouth(GameTestHelper helper) {
            Vec3 at = helper.absoluteVec(HoleTests.IN_THE_MOUTH);
            ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z,
                    new ItemStack(Items.COBBLESTONE, HoleTests.DROPPED));
            item.setDeltaMovement(Vec3.ZERO);
            helper.getLevel().addFreshEntity(item);
            return item;
        }
    }
}
