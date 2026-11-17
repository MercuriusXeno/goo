package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveSelection;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Lunar, a hold: holding the cast on the moon in the overworld holds the caster's gaze through
 * the fixation and then carries them to lunar over the spot they stood on, paying the astral at the carry; letting go
 * early leaves them where they stood, free, and paying nothing; held on the blue overworld star in lunar it carries
 * them back to the exact place they left from, and let go early it leaves them on lunar with their way home kept;
 * held looking at neither, it refuses.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 * decision holding-right-click-pulls-the-moon-and-early-release-cancels
 */
@RegistersGameTests
public final class AstralLunarTests {
    private static final String LUNAR_ON_THE_MOON_TAKES_YOU_TO_LUNAR = "lunar_on_the_moon_takes_you_to_lunar";
    private static final String LUNAR_LET_GO_EARLY_LEAVES_YOU_WHERE_YOU_STOOD =
            "lunar_let_go_early_leaves_you_where_you_stood";
    private static final String LUNAR_ON_THE_BLUE_STAR_RETURNS_YOU_EXACTLY = "lunar_on_the_blue_star_returns_you_exactly";
    private static final String LUNAR_ON_NEITHER_REFUSES = "lunar_on_neither_refuses";

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
    private static final String IN_LUNAR = "Lunar should carry the caster to lunar over %s, carried to %s at %s";
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
    private static final String REFUSED = "Lunar held looking at neither moon nor star should hold no fixation";
    private static final String REFUSED_ONCE = "A press held %d ticks at neither moon nor star should show the refusal"
            + " once, showed it %d times";
    /** The lang key Lunar's refusal reads, the one AstralLunar.TELL_REFUSED names. */
    private static final String REFUSAL_KEY = "goo.ability.astral.lunar.refused";
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
        GooTestFunctions.registerTest(registrar, LUNAR_ON_THE_BLUE_STAR_RETURNS_YOU_EXACTLY,
                AstralLunarTests::lunarOnTheBlueStarReturnsYouExactly);
        GooTestFunctions.registerTest(registrar, LUNAR_ON_NEITHER_REFUSES, AstralLunarTests::lunarOnNeitherRefuses);
    }

    /**
     * By the overworld's night a caster holding the cast on the moon stands held through the fixation, spending
     * nothing, then stands in lunar over the column they cast from, the astral spent once at the carry.
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
            boolean overTheColumn = caster.level().dimension() == Lunar.LEVEL
                    && Math.floor(caster.getX()) == Math.floor(castFrom.x)
                    && Math.floor(caster.getZ()) == Math.floor(castFrom.z);
            helper.assertTrue(overTheColumn, String.format(IN_LUNAR, castFrom, caster.level().dimension(),
                    caster.position()));
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
     * A caster who left the overworld for lunar and lets go of a pull on the blue star stays on lunar with their way
     * home kept; holding the star through after that, they stand at the exact place and facing they left from,
     * marked as arrived anew.
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
            helper.assertTrue(caster.level().dimension() == Lunar.LEVEL, String.format(IN_LUNAR, leftFrom,
                    caster.level().dimension(), caster.position()));
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
        EmbeddedChannel channel = (EmbeddedChannel) caster.connection.getConnection().channel();
        int refusals = 0;
        for (Object sent = channel.readOutbound(); sent != null; sent = channel.readOutbound()) {
            if (sent instanceof ClientboundSystemChatPacket line && line.overlay()
                    && line.content().getContents() instanceof TranslatableContents said
                    && REFUSAL_KEY.equals(said.getKey())) {
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
