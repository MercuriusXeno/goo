package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.astral.LunarFixation;
import com.mercuriusxeno.goo.ability.astral.LunarLanding;
import com.mercuriusxeno.goo.ability.astral.LunarSight;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.GooStreamPayload;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Gametests for Lunar, a hold: holding the cast on the moon in the overworld holds the caster's gaze through
 * the fixation and then carries them to lunar at the landing time and phase set, paying the astral at the carry;
 * letting go early leaves them where they stood, free, and paying nothing; held on the blue overworld star in lunar it
 * carries them back to the exact place they left from, and let go early it leaves them on lunar with their way home
 * kept; held looking at neither, it refuses.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 * decision holding-right-click-pulls-the-moon-and-early-release-cancels
 * decision time-and-phase-set-x-and-your-overworld-spot-sets-z
 */
@RegistersGameTests
public final class AstralLunarTests {
    private static final String LUNAR_ON_THE_MOON_TAKES_YOU_TO_LUNAR = "lunar_on_the_moon_takes_you_to_lunar";
    private static final String LUNAR_LET_GO_EARLY_LEAVES_YOU_WHERE_YOU_STOOD =
            "lunar_let_go_early_leaves_you_where_you_stood";
    private static final String LUNAR_PRESS_AFTER_A_RELEASE_TAKES_AT_EVERY_GAP =
            "lunar_press_after_a_release_takes_at_every_gap";
    private static final String LUNAR_ON_THE_BLUE_STAR_RETURNS_YOU_EXACTLY ="lunar_on_the_blue_star_returns_you_exactly";
    private static final String LUNAR_ON_NEITHER_REFUSES = "lunar_on_neither_refuses";
    private static final String LUNAR_ON_THE_HIDDEN_PLANET_REFUSES = "lunar_on_the_hidden_planet_refuses";
    private static final String LUNAR_ON_THE_SUN_SAYS_THE_BIG_YELLOW_ONE = "lunar_on_the_sun_says_the_big_yellow_one";
    private static final String TERRAN_ON_THE_SUN_SAYS_THE_BIG_YELLOW_ONE = "terran_on_the_sun_says_the_big_yellow_one";
    private static final String TERRAN_OFF_THE_PLANET_SAYS_THATS_NOT_YOUR_WORLD =
            "terran_off_the_planet_says_thats_not_your_world";
    /** Where the hidden planet test stands its caster on lunar, high over the ground. */
    private static final Vec3 ON_LUNAR = new Vec3(0.5, 160, 0.5);

    private static final Identifier ASTRAL_LUNAR = Identifier.parse("goo:astral_lunar");
    /** astral_lunar.json's fixation, the held ticks a full pull takes. */
    private static final int FIXATION = 100;
    /** Held ticks past the fixation, so the hold still stands on the tick the fixation runs out. */
    private static final int HOLD_PAST = 3;
    /** astral_lunar.json's cost, the astral the moon or the star takes at the carry (mB). */
    private static final int COST = 1000;
    /** Past a full hold, so Lunar has carried the caster by then. */
    private static final int PAST_THE_FIXATION = FIXATION + 20;
    /** Inside the fixation, when the caster still stands held where they cast. */
    private static final int INSIDE_THE_FIXATION = 20;
    /** The held ticks an early release lets go after. */
    private static final int LET_GO_AFTER = 30;
    /** LunarTrip's run back: the share of the rise's ticks a trip let go of early takes to run back. */
    private static final float RUN_BACK_SHARE = 0.4F;
    /** The ticks after the last held tick the server lets a caster go, when no held tick has come since. */
    private static final int RELEASED_AFTER = 2;
    /** The ticks past the run back's end the widest gap reaches. */
    private static final int PAST_THE_RUN_BACK = 5;
    /** The server ticks a press has to stand its fixation in. */
    private static final int TAKES_WITHIN = 2;
    private static final String DEAD_PRESS = "gap %d: %s";
    private static final String DEAD_PRESSES = "A press after a release, at every gap from 0 to %d ticks, should"
            + " stand a fixation within two ticks; these stood none: %s";
    /** How far the freed caster steps once they let go, in blocks. */
    private static final double STEP = 1.0;
    /** Where the caster left from in the return test: off the bay, facing somewhere particular. */
    private static final Vec3 LEFT_FROM = new Vec3(0.25, 3, 0.75);
    private static final float LEFT_FACING_YAW = 37F;
    private static final float LEFT_FACING_PITCH = -12F;
    private static final double SAME_SPOT = 1e-6;

    private static final String ABILITY_REQUIRED = "goo:astral_lunar must be loaded";
    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String HELD = "Inside the fixation the caster should stand held where they cast, %s, stands %s";
    private static final String IN_LUNAR = "Lunar should carry the caster to lunar, carried to %s at %s";
    private static final String LANDED = "Lunar should land the caster on lunar at the landing %s, carried to %s at %s";
    private static final String HOME = "Lunar should return the caster to %s at %s facing %s/%s, returned to %s at %s"
            + " facing %s/%s";
    private static final String ARRIVAL_MARKED = "A caster Lunar carried should hold the arrival mark at the far end";
    private static final String UNPAID_BEFORE_THE_CARRY = "Lunar should spend no astral before the carry, held %d"
            + " of %d";
    private static final String PAID_ONCE = "Lunar should spend %d astral at the carry, spent %d";
    private static final String STAYED_HOME = "A caster who let go should stay in the overworld where they stood, %s,"
            + " stands in %s at %s";
    private static final String NO_FIXATION = "A caster who let go should hold no standing fixation";
    private static final String UNPAID = "A caster who let go should keep their astral, held %d, now %d";
    private static final String STAYED_ON_LUNAR = "A caster who let go of the star should stay on lunar, stands in %s";
    private static final String WAY_HOME_KEPT = "A caster who let go of the star should keep their way home";
    private static final String REFUSED_UNPAID = "A refused caster should keep their astral, held %d,"
            + " now %d";
    private static final String TERRAN_REFUSED_ONCE = "A press held %d ticks on lunar off the planet should show"
            + " \"That's not your world\" once, showed it %d times";
    private static final String SUN_JOKED_ONCE = "A press held %d ticks on the sun should show the sun joke once,"
            + " showed it %d times";
    private static final String SUN_HELD = "Lunar held on the sun should hold no fixation";
    private static final String STAYED_FOR_THE_SUN = "A caster held on the sun should stay in %s, stands in %s";
    private static final String HIDDEN_HELD = "Lunar held toward the hidden planet should hold no fixation";
    private static final String REFUSED = "Lunar held looking at neither moon nor star should hold no fixation";
    private static final String REFUSED_ONCE = "A press held %d ticks at neither moon nor star should show the refusal"
            + " once, showed it %d times";
    /** The lang key Lunar's refusal reads, the one LunarSight.LUNAR_REFUSED names. */
    private static final String REFUSAL_KEY = "goo.ability.astral.lunar.refused";
    /** The lang key the sun joke reads, the one LunarSight.SUN_REFUSED names. */
    private static final String SUN_JOKE_KEY = "goo.ability.astral.sun.refused";
    /** The lang key Terran's refusal reads on the moon, the one LunarSight.TERRAN_REFUSED names. */
    private static final String TERRAN_REFUSAL_KEY = "goo.ability.astral.terran.refused";
    private static final String STAYED ="A refused Lunar should leave the caster where they stood, %s, stands %s";

    private AstralLunarTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_ON_THE_MOON_TAKES_YOU_TO_LUNAR,
                AstralLunarTests::lunarOnTheMoonTakesYouToLunar);
        GooTestFunctions.registerTest(registrar, LUNAR_LET_GO_EARLY_LEAVES_YOU_WHERE_YOU_STOOD,
                AstralLunarTests::lunarLetGoEarlyLeavesYouWhereYouStood);
        GooTestFunctions.registerTest(registrar, LUNAR_PRESS_AFTER_A_RELEASE_TAKES_AT_EVERY_GAP,
                AstralLunarTests::lunarPressAfterAReleaseTakesAtEveryGap);
        GooTestFunctions.registerTest(registrar, LUNAR_ON_THE_BLUE_STAR_RETURNS_YOU_EXACTLY,
                AstralLunarTests::lunarOnTheBlueStarReturnsYouExactly);
        GooTestFunctions.registerTest(registrar, LUNAR_ON_NEITHER_REFUSES, AstralLunarTests::lunarOnNeitherRefuses);
        GooTestFunctions.registerTest(registrar, LUNAR_ON_THE_HIDDEN_PLANET_REFUSES,
                AstralLunarTests::lunarOnTheHiddenPlanetRefuses);
        GooTestFunctions.registerTest(registrar, TERRAN_OFF_THE_PLANET_SAYS_THATS_NOT_YOUR_WORLD,
                AstralLunarTests::terranOffThePlanetSaysThatsNotYourWorld);
        GooTestFunctions.registerTest(registrar, LUNAR_ON_THE_SUN_SAYS_THE_BIG_YELLOW_ONE,
                AstralLunarTests::lunarOnTheSunSaysTheBigYellowOne);
        GooTestFunctions.registerTest(registrar, TERRAN_ON_THE_SUN_SAYS_THE_BIG_YELLOW_ONE,
                AstralLunarTests::terranOnTheSunSaysTheBigYellowOne);
    }

    /**
     * A press held on the moon stands a fixation within two server ticks whatever came before it: one caster a gap,
     * each holding the moon, letting go, and pressing again after their gap, from the very next tick to past the end of
     * the trip's run back, as a player clicking again in short succession does.
     * decision diagnose-and-correct-the-lunar-hold-that-does-nothing
     *
     * @param helper the gametest helper
     */
    public static void lunarPressAfterAReleaseTakesAtEveryGap(GameTestHelper helper) {
        int runBack = (int) Math.ceil(LET_GO_AFTER * RUN_BACK_SHARE);
        int widestGap = RELEASED_AFTER + runBack + PAST_THE_RUN_BACK;
        int checkAt = 1 + LET_GO_AFTER + widestGap + TAKES_WITHIN;
        List<String> dead = new ArrayList<>();
        for (int gap = 0; gap <= widestGap; gap++) {
            ServerPlayer caster = caster(helper);
            face(caster, moonFrom(caster));
            holdOverTheNetworkFor(helper, caster, 1, LET_GO_AFTER);
            int pressAt = 1 + LET_GO_AFTER + gap;
            holdOverTheNetworkFor(helper, caster, pressAt, checkAt - pressAt + 1);
            int atGap = gap;
            helper.runAfterDelay(pressAt + TAKES_WITHIN, () -> {
                LunarFixation fixation = caster.getData(GooAttachments.LUNAR_FIXATION);
                if (!fixation.stands()) {
                    dead.add(String.format(DEAD_PRESS, atGap, fixation));
                }
            });
            helper.runAfterDelay(checkAt + 1, () -> helper.getLevel().getServer().getPlayerList().remove(caster));
        }
        helper.runAfterDelay(checkAt + 2, () -> {
            helper.assertTrue(dead.isEmpty(), String.format(DEAD_PRESSES, widestGap, dead));
            helper.succeed();
        });
    }

    /**
     * By the overworld's night a caster holding the cast on the moon stands held through the fixation, spending
     * nothing, then stands in lunar, the astral spent once at the carry.
     *
     * @param helper the gametest helper
     */
    public static void lunarOnTheMoonTakesYouToLunar(GameTestHelper helper) {
        ServerPlayer caster = caster(helper);
        ServerLevel overworld = helper.getLevel();
        helper.assertTrue(overworld.getServer().getLevel(Lunar.LEVEL) != null, LUNAR_REQUIRED);
        face(caster, moonFrom(caster));
        Vec3 castFrom = caster.position();
        int astralBefore = astral(caster);
        holdFor(helper, caster, 1, FIXATION + HOLD_PAST);
        helper.runAfterDelay(INSIDE_THE_FIXATION, () -> helper.assertTrue(
                caster.level() == overworld && caster.position().distanceTo(castFrom) < SAME_SPOT,
                String.format(HELD, castFrom, caster.position())));
        helper.runAfterDelay(FIXATION - 1, () -> helper.assertTrue(astral(caster) == astralBefore,
                String.format(UNPAID_BEFORE_THE_CARRY, astral(caster), astralBefore)));
        helper.runAfterDelay(PAST_THE_FIXATION, () -> {
            helper.assertTrue(caster.level().dimension() == Lunar.LEVEL, String.format(IN_LUNAR,
                    caster.level().dimension(), caster.position()));
            helper.assertTrue(caster.getData(GooAttachments.LUNAR_ARRIVAL).stands(), ARRIVAL_MARKED);
            int spent = astralBefore - astral(caster);
            helper.assertTrue(spent == COST, String.format(PAID_ONCE, COST, spent));
            overworld.getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * A caster who lets go of the moon partway stays in the overworld where they stood, holds no standing fixation,
     * steps freely and keeps the astral they started with, past the tick the carry would have come.
     *
     * @param helper the gametest helper
     */
    public static void lunarLetGoEarlyLeavesYouWhereYouStood(GameTestHelper helper) {
        ServerPlayer caster = caster(helper);
        ServerLevel overworld = helper.getLevel();
        face(caster, moonFrom(caster));
        Vec3 castFrom = caster.position();
        int astralBefore = astral(caster);
        holdFor(helper, caster, 1, LET_GO_AFTER);
        Vec3 steppedTo = castFrom.add(STEP, 0, 0);
        helper.runAfterDelay(LET_GO_AFTER + HOLD_PAST, () -> caster.teleportTo(steppedTo.x, steppedTo.y, steppedTo.z));
        helper.runAfterDelay(PAST_THE_FIXATION, () -> {
            helper.assertTrue(caster.level() == overworld && caster.position().distanceTo(steppedTo) < SAME_SPOT,
                    String.format(STAYED_HOME, steppedTo, caster.level().dimension(), caster.position()));
            helper.assertFalse(caster.getData(GooAttachments.LUNAR_FIXATION).stands(), NO_FIXATION);
            helper.assertTrue(astral(caster) == astralBefore, String.format(UNPAID, astralBefore, astral(caster)));
            overworld.getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * By a night the overworld's clock stands still, a caster the moon carries stands on lunar at the landing the
     * night's phase and tick and their overworld spot answer; letting go of a pull on the blue star they stay on lunar
     * with their way home kept, and holding the star through after that, they stand at the exact place and facing they
     * left from, marked as arrived anew.
     * decision time-and-phase-set-x-and-your-overworld-spot-sets-z
     *
     * @param helper the gametest helper
     */
    public static void lunarOnTheBlueStarReturnsYouExactly(GameTestHelper helper) {
        ServerPlayer caster = caster(helper);
        ServerLevel overworld = helper.getLevel();
        ServerLevel lunar = overworld.getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        Vec3 leftFrom = helper.absoluteVec(LEFT_FROM);
        caster.snapTo(leftFrom.x, leftFrom.y, leftFrom.z, LEFT_FACING_YAW, LEFT_FACING_PITCH);
        face(caster, moonFrom(caster));
        float castYaw = caster.getYRot();
        float castPitch = caster.getXRot();
        // The return carries the caster to where they stood and the way they faced as the moon took them.
        caster.snapTo(leftFrom.x, leftFrom.y, leftFrom.z, castYaw, castPitch);
        holdFor(helper, caster, 1, FIXATION + HOLD_PAST);
        long[] arrivedInLunarAt = new long[1];
        int starFrom = PAST_THE_FIXATION;
        helper.runAfterDelay(starFrom, () -> {
            LunarLanding.Spot landing = LunarLanding.at(overworld.environmentAttributes()
                    .getValue(EnvironmentAttributes.MOON_PHASE, leftFrom).index(),
                    LunarLanding.nightTickAt(overworld.getOverworldClockTime()), leftFrom.x, leftFrom.z);
            boolean landed = caster.level().dimension() == Lunar.LEVEL
                    && Math.abs(caster.getX() - landing.x()) < SAME_SPOT
                    && Math.abs(caster.getZ() - landing.z()) < SAME_SPOT;
            helper.assertTrue(landed, String.format(LANDED, landing, caster.level().dimension(), caster.position()));
            arrivedInLunarAt[0] = caster.getData(GooAttachments.LUNAR_ARRIVAL).arrivedAt();
            dropRemainders(caster);
            face(caster, moonFrom(caster));
        });
        holdFor(helper, caster, starFrom + 1, LET_GO_AFTER);
        int fullFrom = starFrom + LET_GO_AFTER + INSIDE_THE_FIXATION;
        helper.runAfterDelay(fullFrom, () -> {
            helper.assertTrue(caster.level().dimension() == Lunar.LEVEL,
                    String.format(STAYED_ON_LUNAR, caster.level().dimension()));
            helper.assertTrue(caster.getData(GooAttachments.WAY_HOME).point().isPresent(), WAY_HOME_KEPT);
        });
        holdFor(helper, caster, fullFrom + 1, FIXATION + HOLD_PAST);
        helper.runAfterDelay(fullFrom + PAST_THE_FIXATION, () -> {
            boolean exactly = caster.level() == overworld && caster.position().distanceTo(leftFrom) < SAME_SPOT
                    && caster.getYRot() == castYaw && caster.getXRot() == castPitch;
            helper.assertTrue(exactly, String.format(HOME, Level.OVERWORLD.identifier(), leftFrom, castYaw, castPitch,
                    caster.level().dimension().identifier(), caster.position(), caster.getYRot(), caster.getXRot()));
            // The return leaves its own mark, set after the one lunar's arrival left.
            helper.assertTrue(caster.getData(GooAttachments.LUNAR_ARRIVAL).arrivedAt() > arrivedInLunarAt[0],
                    ARRIVAL_MARKED);
            overworld.getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * On lunar by a night the planet stands dark, the overworld's moon full, a caster holding the cast where the
     * planet stands stays on lunar, holds no fixation, keeps every drop of their astral and is shown Terran's
     * refusal, "That's not your world".
     * decision lunar-refuses-the-return-while-the-blue-star-is-hidden
     * decision terran-refusal-says-thats-not-your-world
     *
     * @param helper the gametest helper
     */
    public static void lunarOnTheHiddenPlanetRefuses(GameTestHelper helper) {
        assertTerranRefuses(helper, AstralLunarTests::moonFrom);
    }

    /**
     * On lunar by a night the planet shows full, a caster holding the cast at the ground stays on lunar, holds no
     * fixation, keeps every drop of their astral and is shown Terran's refusal, "That's not your world".
     * decision terran-refusal-says-thats-not-your-world
     *
     * @param helper the gametest helper
     */
    public static void terranOffThePlanetSaysThatsNotYourWorld(GameTestHelper helper) {
        assertTerranRefuses(helper, caster -> new Vec3(0, -1, 0));
    }

    /**
     * In the overworld by day, a caster holding Lunar toward the sun stays where they stood, holds no fixation, keeps
     * every drop of their astral and is shown "The big yellow one is the sun!".
     * decision pointing-at-the-sun-says-the-big-yellow-one-is-the-sun
     *
     * @param helper the gametest helper
     */
    public static void lunarOnTheSunSaysTheBigYellowOne(GameTestHelper helper) {
        assertSunJoke(helper, caster(helper));
    }

    /**
     * On lunar by day, a caster holding Terran toward lunar's sun stays on lunar, holds no fixation, keeps every drop
     * of their astral and is shown "The big yellow one is the sun!".
     * decision pointing-at-the-sun-says-the-big-yellow-one-is-the-sun
     *
     * @param helper the gametest helper
     */
    public static void terranOnTheSunSaysTheBigYellowOne(GameTestHelper helper) {
        ServerPlayer caster = caster(helper);
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        caster.teleport(new TeleportTransition(lunar, ON_LUNAR, Vec3.ZERO, 0F, 0F, TeleportTransition.DO_NOTHING));
        assertSunJoke(helper, caster);
    }

    /**
     * Faces a caster at the sun of the level they stand in, holds the cast, and asserts the sun joke refused it.
     *
     * @param helper the gametest helper
     * @param caster the caster, standing where the test wants them
     */
    private static void assertSunJoke(GameTestHelper helper, ServerPlayer caster) {
        ResourceKey<Level> stoodIn = caster.level().dimension();
        face(caster, sunFrom(caster));
        int astralBefore = astral(caster);
        refusalsSentTo(caster, SUN_JOKE_KEY);
        holdFor(helper, caster, 1, LET_GO_AFTER);
        helper.runAfterDelay(LET_GO_AFTER, () -> {
            helper.assertTrue(caster.level().dimension() == stoodIn,
                    String.format(STAYED_FOR_THE_SUN, stoodIn, caster.level().dimension()));
            helper.assertFalse(caster.getData(GooAttachments.LUNAR_FIXATION).stands(), SUN_HELD);
            helper.assertTrue(astral(caster) == astralBefore,
                    String.format(REFUSED_UNPAID, astralBefore, astral(caster)));
            int jokes = refusalsSentTo(caster, SUN_JOKE_KEY);
            helper.assertTrue(jokes == 1, String.format(SUN_JOKED_ONCE, LET_GO_AFTER, jokes));
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    private static Vec3 sunFrom(ServerPlayer caster) {
        return LunarSight.moonDirection(caster.level().environmentAttributes()
                .getValue(EnvironmentAttributes.SUN_ANGLE, caster.position()));
    }

    /**
     * Stands a caster on lunar facing where a look takes them, holds the cast, and asserts Terran refused it.
     *
     * @param helper the gametest helper
     * @param look   the look the caster holds, from where they stand on lunar
     */
    private static void assertTerranRefuses(GameTestHelper helper, Function<ServerPlayer, Vec3> look) {
        ServerPlayer caster = caster(helper);
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        caster.teleport(new TeleportTransition(lunar, ON_LUNAR, Vec3.ZERO, 0F, 0F, TeleportTransition.DO_NOTHING));
        face(caster, look.apply(caster));
        int astralBefore = astral(caster);
        refusalsSentTo(caster, TERRAN_REFUSAL_KEY);
        holdFor(helper, caster, 1, LET_GO_AFTER);
        helper.runAfterDelay(LET_GO_AFTER, () -> {
            helper.assertTrue(caster.level().dimension() == Lunar.LEVEL,
                    String.format(STAYED_ON_LUNAR, caster.level().dimension()));
            helper.assertFalse(caster.getData(GooAttachments.LUNAR_FIXATION).stands(), HIDDEN_HELD);
            helper.assertTrue(astral(caster) == astralBefore,
                    String.format(REFUSED_UNPAID, astralBefore, astral(caster)));
            int refusals = refusalsSentTo(caster, TERRAN_REFUSAL_KEY);
            helper.assertTrue(refusals == 1, String.format(TERRAN_REFUSED_ONCE, LET_GO_AFTER, refusals));
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * A caster holding the cast looking at the ground holds no fixation, stays where they stood, and is shown the
     * refusal once for the whole press, not once a held tick.
     * decision lunar-refusal-says-thats-no-moon
     *
     * @param helper the gametest helper
     */
    public static void lunarOnNeitherRefuses(GameTestHelper helper) {
        ServerPlayer caster = caster(helper);
        face(caster, new Vec3(0, -1, 0));
        Vec3 stood = caster.position();
        refusalsSentTo(caster);
        holdFor(helper, caster, 1, LET_GO_AFTER);
        helper.runAfterDelay(LET_GO_AFTER, () -> {
            helper.assertFalse(caster.getData(GooAttachments.LUNAR_FIXATION).stands(), REFUSED);
            int refusals = refusalsSentTo(caster);
            helper.assertTrue(refusals == 1, String.format(REFUSED_ONCE, LET_GO_AFTER, refusals));
        });
        helper.runAfterDelay(PAST_THE_FIXATION, () -> {
            helper.assertTrue(caster.level() == helper.getLevel() && caster.position().distanceTo(stood) < SAME_SPOT,
                    String.format(STAYED, stood, caster.position()));
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * Counts, and drains, the action bar lines carrying Lunar's refusal the server has sent the caster since the
     * last count: the mock connection's embedded channel keeps every packet sent through it.
     *
     * @param caster the caster
     * @return the refusals sent
     */
    private static int refusalsSentTo(ServerPlayer caster) {
        return refusalsSentTo(caster, REFUSAL_KEY);
    }

    /**
     * Counts, and drains, the action bar lines carrying one refusal the server has sent the caster since the last
     * count.
     *
     * @param caster the caster
     * @param key    the refusal's lang key
     * @return the refusals sent
     */
    private static int refusalsSentTo(ServerPlayer caster, String key) {
        EmbeddedChannel channel = (EmbeddedChannel) caster.connection.getConnection().channel();
        int refusals = 0;
        for (Object sent = channel.readOutbound(); sent != null; sent = channel.readOutbound()) {
            if (sent instanceof ClientboundSystemChatPacket line && line.overlay()
                    && line.content().getContents() instanceof TranslatableContents said
                    && key.equals(said.getKey())) {
                refusals++;
            }
        }
        return refusals;
    }

    private static ServerPlayer caster(GameTestHelper helper) {
        helper.assertTrue(AbilityRegistry.of(helper.getLevel()).getAbility(ASTRAL_LUNAR) != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ASTRAL);
        caster.connection.markClientLoaded();
        GooGloveItem.setSelection(caster.getMainHandItem(), GloveSelection.ofAbility(GooTypes.ASTRAL, ASTRAL_LUNAR));
        return caster;
    }

    /**
     * Holds the cast from the caster's glove for a run of ticks, one held tick a tick, as a client holding right
     * click sends them.
     *
     * @param helper the gametest helper
     * @param caster the caster
     * @param from   the delay of the first held tick
     * @param ticks  how many ticks the hold lasts
     */
    private static void holdFor(GameTestHelper helper, ServerPlayer caster, int from, int ticks) {
        for (int tick = from; tick < from + ticks; tick++) {
            helper.runAfterDelay(tick, () -> GooStreamHandler.streamTick(caster, holdOf(caster)));
        }
    }

    /**
     * Holds the cast for a run of ticks as the held ticks reach a server over the network: GooStreamHandler.handle
     * queues each as server work, which the server runs between its ticks, after the tick's end and under its count.
     *
     * @param helper the gametest helper
     * @param caster the caster
     * @param from   the delay of the first held tick
     * @param ticks  how many ticks the hold lasts
     */
    private static void holdOverTheNetworkFor(GameTestHelper helper, ServerPlayer caster, int from, int ticks) {
        MinecraftServer server = helper.getLevel().getServer();
        for (int tick = from; tick < from + ticks; tick++) {
            helper.runAfterDelay(tick, () -> server.schedule(server.wrapRunnable(
                    () -> GooStreamHandler.streamTick(caster, holdOf(caster)))));
        }
    }

    private static GooStreamPayload holdOf(ServerPlayer caster) {
        return new GooStreamPayload(GooTypes.id(GooTypes.ASTRAL), ASTRAL_LUNAR.toString(), caster.getEyePosition(),
                caster.getEyePosition().add(caster.getLookAngle()), caster.blockPosition().below(),
                GooStreamPayload.NO_FACE);
    }

    /**
     * Drops the remainders paying the cost laid. NeoForge syncs that attachment in full on every dimension change;
     * a real client negotiates the channel it rides, the mock connection cannot, so the test drops the remainders the
     * ability never reads before Lunar carries the caster again.
     *
     * @param caster the caster
     */
    private static void dropRemainders(ServerPlayer caster) {
        caster.removeData(GooAttachments.REMAINDERS);
    }

    private static int astral(ServerPlayer caster) {
        return GooSourceScanner.aggregateAvailable(caster).getOrDefault(GooTypes.ASTRAL, 0);
    }

    private static Vec3 moonFrom(ServerPlayer caster) {
        return LunarSight.moonDirection(caster.level().environmentAttributes()
                .getValue(EnvironmentAttributes.MOON_ANGLE, caster.position()));
    }

    private static void face(ServerPlayer caster, Vec3 direction) {
        Vec2 facing = direction.rotation();
        caster.setXRot(facing.x);
        caster.setYRot(facing.y);
    }
}
