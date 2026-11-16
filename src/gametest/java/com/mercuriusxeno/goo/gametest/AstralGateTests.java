package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.gate.GateSight;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
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
 * Gametests for Astral's gate: cast looking at the moon in the overworld, it holds the caster's gaze through its
 * fixation and then carries them to lunar over the spot they stood on; cast looking at the blue overworld star in
 * lunar, it carries them back to the exact place they left from; cast looking at neither, it refuses.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 */
@RegistersGameTests
public final class AstralGateTests {
    private static final String GATE_ON_THE_MOON_TAKES_YOU_TO_LUNAR = "gate_on_the_moon_takes_you_to_lunar";
    private static final String GATE_ON_THE_BLUE_STAR_RETURNS_YOU_EXACTLY = "gate_on_the_blue_star_returns_you_exactly";
    private static final String GATE_ON_NEITHER_REFUSES = "gate_on_neither_refuses";

    private static final Identifier ASTRAL_GATE = Identifier.parse("goo:astral_gate");
    /** Past astral_gate.json's fixation, so the gate has carried the caster by then. */
    private static final int PAST_THE_FIXATION = 120;
    /** Inside the fixation, when the caster still stands held where they cast. */
    private static final int INSIDE_THE_FIXATION = 20;
    /** Where the caster left from in the return test: off the bay, facing somewhere particular. */
    private static final Vec3 LEFT_FROM = new Vec3(0.25, 3, 0.75);
    private static final float LEFT_FACING_YAW = 37F;
    private static final float LEFT_FACING_PITCH = -12F;
    private static final double SAME_SPOT = 1e-6;

    private static final String ABILITY_REQUIRED = "goo:astral_gate must be loaded";
    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String HELD = "Inside the fixation the caster should stand held where they cast, %s, stands %s";
    private static final String IN_LUNAR = "The gate should carry the caster to lunar over %s, carried to %s at %s";
    private static final String HOME = "The gate should return the caster to %s at %s facing %s/%s, returned to %s at %s facing %s/%s";
    private static final String ARRIVAL_MARKED = "A caster the gate carried should hold the arrival mark at the far end";
    private static final String REFUSED ="A gate cast looking at neither moon nor star should hold no fixation";
    private static final String STAYED = "A refused gate should leave the caster where they stood, %s, stands %s";

    private AstralGateTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, GATE_ON_THE_MOON_TAKES_YOU_TO_LUNAR,
                AstralGateTests::gateOnTheMoonTakesYouToLunar);
        GooTestFunctions.registerTest(registrar, GATE_ON_THE_BLUE_STAR_RETURNS_YOU_EXACTLY,
                AstralGateTests::gateOnTheBlueStarReturnsYouExactly);
        GooTestFunctions.registerTest(registrar, GATE_ON_NEITHER_REFUSES, AstralGateTests::gateOnNeitherRefuses);
    }

    /**
     * By the overworld's night a caster looking at the moon stands held through the fixation, then stands in lunar
     * over the column they cast from.
     *
     * @param helper the gametest helper
     */
    public static void gateOnTheMoonTakesYouToLunar(GameTestHelper helper) {
        ServerPlayer caster = caster(helper);
        ServerLevel overworld = helper.getLevel();
        helper.assertTrue(overworld.getServer().getLevel(Lunar.LEVEL) != null, LUNAR_REQUIRED);
        face(caster, moonFrom(caster));
        Vec3 castFrom = caster.position();
        cast(caster);
        helper.runAfterDelay(INSIDE_THE_FIXATION, () -> helper.assertTrue(
                caster.level() == overworld && caster.position().distanceTo(castFrom) < SAME_SPOT,
                String.format(HELD, castFrom, caster.position())));
        helper.runAfterDelay(PAST_THE_FIXATION, () -> {
            boolean overTheColumn = caster.level().dimension() == Lunar.LEVEL
                    && Math.floor(caster.getX()) == Math.floor(castFrom.x)
                    && Math.floor(caster.getZ()) == Math.floor(castFrom.z);
            helper.assertTrue(overTheColumn, String.format(IN_LUNAR, castFrom, caster.level().dimension(),
                    caster.position()));
            helper.assertTrue(caster.getData(GooAttachments.GATE_ARRIVAL).stands(), ARRIVAL_MARKED);
            overworld.getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * A caster who left the overworld for lunar and looks at the blue star there stands, past the fixation, at the
     * exact place and facing they left from.
     *
     * @param helper the gametest helper
     */
    public static void gateOnTheBlueStarReturnsYouExactly(GameTestHelper helper) {
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
        cast(caster);
        long[] arrivedInLunarAt = new long[1];
        helper.runAfterDelay(PAST_THE_FIXATION, () -> {
            helper.assertTrue(caster.level().dimension() == Lunar.LEVEL, String.format(IN_LUNAR, leftFrom,
                    caster.level().dimension(), caster.position()));
            arrivedInLunarAt[0] = caster.getData(GooAttachments.GATE_ARRIVAL).arrivedAt();
            face(caster, Lunar.OVERWORLD_STAR_DIRECTION);
            cast(caster);
        });
        helper.runAfterDelay(2 * PAST_THE_FIXATION, () -> {
            boolean exactly = caster.level() == overworld && caster.position().distanceTo(leftFrom) < SAME_SPOT
                    && caster.getYRot() == castYaw && caster.getXRot() == castPitch;
            helper.assertTrue(exactly, String.format(HOME, Level.OVERWORLD.identifier(), leftFrom, castYaw, castPitch,
                    caster.level().dimension().identifier(), caster.position(), caster.getYRot(), caster.getXRot()));
            // The return leaves its own mark, set after the one lunar's arrival left.
            helper.assertTrue(caster.getData(GooAttachments.GATE_ARRIVAL).arrivedAt() > arrivedInLunarAt[0],
                    ARRIVAL_MARKED);
            overworld.getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * A caster looking at the ground holds no fixation and stays where they stood.
     *
     * @param helper the gametest helper
     */
    public static void gateOnNeitherRefuses(GameTestHelper helper) {
        ServerPlayer caster = caster(helper);
        face(caster, new Vec3(0, -1, 0));
        Vec3 stood = caster.position();
        cast(caster);
        helper.assertFalse(caster.getData(GooAttachments.GATE_FIXATION).stands(), REFUSED);
        helper.runAfterDelay(PAST_THE_FIXATION, () -> {
            helper.assertTrue(caster.level() == helper.getLevel() && caster.position().distanceTo(stood) < SAME_SPOT,
                    String.format(STAYED, stood, caster.position()));
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    private static ServerPlayer caster(GameTestHelper helper) {
        helper.assertTrue(AbilityRegistry.of(helper.getLevel()).getAbility(ASTRAL_GATE) != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ASTRAL);
        caster.connection.markClientLoaded();
        return caster;
    }

    /**
     * Casts the gate from the caster's glove. Paying the cost lays the caster's remainders, an attachment NeoForge
     * syncs in full on every dimension change; a real client negotiates the channel it rides, the mock connection
     * cannot, so the test drops the remainders the gate never reads before the gate carries the caster.
     *
     * @param caster the caster
     */
    private static void cast(ServerPlayer caster) {
        SelfDeliveryTests.invoke(caster, GooTypes.ASTRAL, ASTRAL_GATE);
        caster.removeData(GooAttachments.REMAINDERS);
    }

    private static Vec3 moonFrom(ServerPlayer caster) {
        return GateSight.moonDirection(caster.level().environmentAttributes()
                .getValue(EnvironmentAttributes.MOON_ANGLE, caster.position()));
    }

    private static void face(ServerPlayer caster, Vec3 direction) {
        Vec2 facing = direction.rotation();
        caster.setXRot(facing.x);
        caster.setYRot(facing.y);
    }
}
