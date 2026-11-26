package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.SimpleParticles;
import com.mercuriusxeno.goo.client.model.GloveSpecialRenderer;
import com.mercuriusxeno.goo.client.particle.BlendPalette;
import com.mercuriusxeno.goo.client.particle.SporeParticle;
import com.mercuriusxeno.goo.client.particle.SprayDripParticle;
import com.mercuriusxeno.goo.item.GooCasters;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.SprayerNozzle;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.StreamMotesPayload;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.ConeShape;
import com.mercuriusxeno.goo.throwing.DripArc;
import com.mercuriusxeno.goo.throwing.StreamCone;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaternionf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleFunction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A Goop or Slick spray drip draws nothing before its first tick, the frames
 * the lifted palm blob alone stands where it is, and is first drawn in
 * flight from the blob, as the blob stood at the tick's start, at the launch
 * its server drip took, even on a spray's first ticks while the blob still eases off the
 * palm toward the spray source, a point held in the caster's view ahead of
 * the eye, out to the glove's side and a little below the look; for the
 * local caster and for another player, standing, walking, strafing and
 * turning. The client runs a frame's network tasks, then its ticks, then
 * draws: a drip the stream's tick launches joins the particle engine at the
 * next tick, after the engine has ticked the drips already flying, so it
 * reaches a frame a tick or more after its launch, before its own first tick.
 * goop-and-slick-spray-emanates-from-the-glove
 * diagnose-and-correct-the-spray-not-leaving-the-glove
 * palm-blob-lifts-as-the-spray-source
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 */
class StreamMotesTest {

    private static final int CASTER = 7;
    private static final Vec3 EYE = new Vec3(0, 1.62, 0);
    /** Looking south and thirty degrees down. */
    private static final float PITCH = 30f;
    private static final double SPEED = 0.2;
    /** Ticks from the stream's tick being handled to the frame that first draws its drips. */
    private static final double FIRST_DRAWN = 1.5;
    /** Ticks from the stream's tick being handled to the drips' first tick. */
    private static final double FIRST_TICK = 2;
    private static final float PARTIAL_TICK = 0.5f;
    /** Goop's throw speed, blocks per tick. */
    private static final double THROW_SPEED = 0.84;
    /** The spray's axis, south and down. */
    private static final Vec3 SPRAY_AXIS = new Vec3(0, -0.5, 1).normalize();
    /** Goop's delivery, whose cone the drips land in. */
    private static final Delivery GOOP = AbilityJson.decode("weird_goop").delivery();
    private static final double CONE_DEGREES = GOOP.coneDegrees();
    /** How far from the eye the spray's cone lands, in blocks. */
    private static final double SPRAY_LANDS = 3;
    /** The hold's tick the spray tick is sent on, which seeds the tick's drips' places in the cone. */
    private static final int SPRAY_HELD = 9;
    /** The drips the server throws on the spray tick. */
    private static final int SPRAY_DRIPS = 6;
    /** How far a drawn drip's launch may stray from its share's: double rounding. */
    private static final double SAME_LAUNCH = 1e-9;
    /** A sprint-jumping player's average speed, about 7.1 blocks a second, in blocks a tick. */
    private static final double SPRINT_JUMPING = 0.355;
    /** Where the first-person glove's goo draws off the eye: ahead, out to the right and down. */
    private static final Vec3 GLOVE_OFF_THE_EYE = new Vec3(-0.35, -0.4, 0.6);
    /** How far behind the glove a mote may draw before it reads as left behind: a mote quad's width. */
    private static final double BEHIND_SLACK = 0.1;
    /** The least gap between two of a tick's drawn drips, past a drip quad's half-width. */
    private static final double DRIP_SPACING = 0.1;
    /** How far a drip's first drawn quad may stray from half its launch out of the blob: the quad's float precision. */
    private static final double ON_THE_LAUNCH = 1e-3;
    /** How far short of the spray source the blob stands while it still eases off the palm. */
    private static final double STILL_EASING = 0.05;
    /** How near the blob's place a drip's flight starts: the quad's float precision. */
    private static final double LEFT_THE_BLOB = 1e-4;

    private static final float WORLD_FOV = 70f;
    /** A falling player's speed, blocks a tick, well under terminal velocity. */
    private static final double FALLING = 0.4;
    /** Ticks the blob has stood lifted before the stream's tick, past its hop and ease. */
    private static final double LIFTED_FOR = 20;

    /**
     * A caster over time, in ticks from the launch.
     *
     * @param eye where the eye stands
     * @param yaw the look's yaw in degrees
     */
    private record Caster(DoubleFunction<Vec3> eye, DoubleFunction<Float> yaw) {
    }

    private static Caster moving(Vec3 travel) {
        return new Caster(time -> EYE.add(travel.scale(time)), time -> 0f);
    }

    /** A caster turning ten degrees a tick. */
    private static Caster turning() {
        return new Caster(time -> EYE, time -> (float) (10 * time));
    }

    /**
     * The spray source worked out apart from the code under test: 0.7 ahead
     * along the look, 0.3 to the right glove's side, 0.15 below the look.
     */
    private static Vec3 sourceOf(Caster caster, double time) {
        double yaw = Math.toRadians(caster.yaw().apply(time));
        double pitch = Math.toRadians(PITCH);
        Vec3 ahead = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = right.cross(ahead);
        return caster.eye().apply(time).add(ahead.scale(0.7)).add(right.scale(0.3)).subtract(up.scale(0.15));
    }

    @AfterEach
    void forgetTheSprays() {
        LiftedPalmBlob.forgetEverySpray();
    }

    @Test
    void aStandingCastersDripIsFirstDrawnInFlightFromItsEasingBlob() {
        assertFirstDrawnInFlightFromTheBlob(moving(Vec3.ZERO), true);
    }

    @Test
    void aWalkingCastersDripIsFirstDrawnInFlightFromItsEasingBlob() {
        assertFirstDrawnInFlightFromTheBlob(moving(new Vec3(0, 0, SPEED)), true);
    }

    @Test
    void aStrafingCastersDripIsFirstDrawnInFlightFromItsEasingBlob() {
        assertFirstDrawnInFlightFromTheBlob(moving(new Vec3(-SPEED, 0, 0)), true);
    }

    @Test
    void aTurningCastersDripIsFirstDrawnInFlightFromItsEasingBlob() {
        assertFirstDrawnInFlightFromTheBlob(turning(), true);
    }

    @Test
    void anotherWalkingPlayersDripIsFirstDrawnInFlightFromThatPlayersEasingBlob() {
        assertFirstDrawnInFlightFromTheBlob(moving(new Vec3(-SPEED, 0, SPEED)), false);
    }

    @Test
    void anotherTurningPlayersDripIsFirstDrawnInFlightFromThatPlayersEasingBlob() {
        assertFirstDrawnInFlightFromTheBlob(turning(), false);
    }

    @Test
    void aMovingAndRisingCastersDrawnDripsLaunchAtTheirSharesLaunchAlone() {
        Caster caster = moving(new Vec3(SPEED, 0.42, -SPEED));
        Minecraft minecraft = clientDrawing(new ArrayList<>(), caster, true, new double[] {0});
        whileDrawing(minecraft, new ArrayList<>(), () -> StreamMotes.handle(sprayTick(caster), runningAtOnce()));

        ArgumentCaptor<Double> x = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> y = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> z = ArgumentCaptor.forClass(Double.class);
        verify(minecraft.particleEngine, atLeastOnce()).createParticle(any(), anyDouble(), anyDouble(), anyDouble(),
                x.capture(), y.capture(), z.capture());
        List<Vec3> shareLaunches = CoatRay.scatteredShares(SPRAY_HELD, SPRAY_DRIPS).stream()
                .map(share -> serverLaunch(sprayTick(caster), share)).toList();
        for (int i = 0; i < x.getAllValues().size(); i++) {
            Vec3 drawn = new Vec3(x.getAllValues().get(i), y.getAllValues().get(i), z.getAllValues().get(i));
            assertTrue(shareLaunches.stream().anyMatch(launch -> launch.distanceTo(drawn) <= SAME_LAUNCH),
                    "a drawn drip should launch at its share's launch, one of " + shareLaunches + ", launches at "
                            + drawn);
        }
    }

    /**
     * operator UAT 2026-11-17: every drawn drip lands; a drip you see splat lays its layer where it splats
     */
    @Test
    void everyDrawnDripFliesOneOfTheServersDripsArcsOnceEach() {
        Caster caster = moving(Vec3.ZERO);
        Minecraft minecraft = clientDrawing(new ArrayList<>(), caster, true, new double[] {0});
        whileDrawing(minecraft, new ArrayList<>(), () -> StreamMotes.handle(sprayTick(caster), runningAtOnce()));

        ArgumentCaptor<Double> x = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> y = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> z = ArgumentCaptor.forClass(Double.class);
        verify(minecraft.particleEngine, atLeastOnce()).createParticle(any(), anyDouble(), anyDouble(), anyDouble(),
                x.capture(), y.capture(), z.capture());
        List<Vec3> unmatched = new ArrayList<>(CoatRay.scatteredShares(SPRAY_HELD, SPRAY_DRIPS).stream()
                .map(share -> serverLaunch(sprayTick(caster), share)).toList());
        List<Vec3> strays = new ArrayList<>();
        for (int i = 0; i < x.getAllValues().size(); i++) {
            Vec3 drawn = new Vec3(x.getAllValues().get(i), y.getAllValues().get(i), z.getAllValues().get(i));
            if (!unmatched.removeIf(launch -> launch.distanceTo(drawn) <= SAME_LAUNCH)) {
                strays.add(drawn);
            }
        }
        assertTrue(unmatched.isEmpty() && strays.isEmpty(), "the drawn drips should fly the server's "
                + SPRAY_DRIPS + " arcs once each; unmatched server arcs " + unmatched + ", drawn drips the server"
                + " throws none on " + strays);
    }

    /**
     * A tick carrying a nozzle's shape draws each drip on the launch the server throws it at under that nozzle.
     * spray-item-takes-different-nozzles-doing-different-things
     */
    @Test
    void drawnDripsTakeTheNozzlesShape() {
        Caster caster = moving(Vec3.ZERO);
        ConeShape fan = SprayerNozzle.LINE.shape(CONE_DEGREES);
        StreamMotesPayload tick = shapedSprayTick(caster, fan);
        Minecraft minecraft = clientDrawing(new ArrayList<>(), caster, true, new double[] {0});
        whileDrawing(minecraft, new ArrayList<>(), () -> StreamMotes.handle(tick, runningAtOnce()));

        ArgumentCaptor<Double> x = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> y = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> z = ArgumentCaptor.forClass(Double.class);
        verify(minecraft.particleEngine, atLeastOnce()).createParticle(any(), anyDouble(), anyDouble(), anyDouble(),
                x.capture(), y.capture(), z.capture());
        List<Vec3> unmatched = new ArrayList<>(CoatRay.scatteredShares(SPRAY_HELD, SPRAY_DRIPS).stream()
                .map(share -> GooStreamHandler.dripLaunch(new GooStreamHandler.DripThrow(tick.apex(), tick.cone(),
                        tick.throwSpeed(), DripArc.GRAVITY), fan, share)).toList());
        List<Vec3> strays = new ArrayList<>();
        for (int i = 0; i < x.getAllValues().size(); i++) {
            Vec3 drawn = new Vec3(x.getAllValues().get(i), y.getAllValues().get(i), z.getAllValues().get(i));
            if (!unmatched.removeIf(launch -> launch.distanceTo(drawn) <= SAME_LAUNCH)) {
                strays.add(drawn);
            }
        }
        assertTrue(unmatched.isEmpty() && strays.isEmpty(), "the drawn drips should fly the fan's arcs the server"
                + " throws; unmatched server arcs " + unmatched + ", drawn drips off them " + strays);
    }

    /**
     * residual-spray-cone-centers-on-the-look-from-the-blob: every drip leaves the blob, and the client draws the
     * drips the server throws
     */
    @Test
    void drawnDripsLeaveTheBlobOnTheServersLaunches() {
        Caster caster = moving(Vec3.ZERO);
        double[] now = {-LIFTED_FOR};
        List<SprayDripParticle> drips = new ArrayList<>();
        Minecraft minecraft = clientDrawing(drips, caster, true, now);
        whileDrawing(minecraft, new ArrayList<>(), () -> {
            for (long tick = (long) -LIFTED_FOR; tick <= 0; tick++) {
                now[0] = tick;
                LiftedPalmBlob.held(CASTER, sourceOf(caster, tick), tick);
            }
            Vec3 blob = LiftedPalmBlob.placeOf(CASTER, 0f).orElseThrow();
            StreamMotesPayload tick = sprayTickFrom(blob, EYE);
            StreamMotes.handle(tick, runningAtOnce());
            now[0] = FIRST_TICK;

            List<CoatRay.ConeShare> shares = CoatRay.scatteredShares(SPRAY_HELD, SPRAY_DRIPS);
            assertEquals(shares.size(), drips.size(), "the client should draw each drip the server threw");
            ArgumentCaptor<Double> x = ArgumentCaptor.forClass(Double.class);
            ArgumentCaptor<Double> y = ArgumentCaptor.forClass(Double.class);
            ArgumentCaptor<Double> z = ArgumentCaptor.forClass(Double.class);
            verify(minecraft.particleEngine, atLeastOnce()).createParticle(any(), anyDouble(), anyDouble(),
                    anyDouble(), x.capture(), y.capture(), z.capture());
            for (int i = 0; i < drips.size(); i++) {
                Vec3 server = serverLaunch(tick, shares.get(i));
                Vec3 drawn = new Vec3(x.getAllValues().get(i), y.getAllValues().get(i), z.getAllValues().get(i));
                assertTrue(drawn.distanceTo(server) <= SAME_LAUNCH, "a drawn drip should launch as the server's"
                        + " drip for its share, " + server + ", launches at " + drawn);
                drips.get(i).tick();
                Vec3 onItsArc = arcFrom(tick.apex(), server, (double) i / shares.size());
                Vec3 left = drawnAt(drips.get(i), 0f);
                assertTrue(left.distanceTo(onItsArc) <= LEFT_THE_BLOB, "a drawn drip should leave the spray source "
                        + tick.apex() + " on its arc at " + onItsArc + ", left from " + left);
            }
        });
    }

    @Test
    void aTicksDripsStandApartSoNoTwoDrawInOnePlane() {
        Caster caster = moving(new Vec3(0, 0, SPEED));
        double[] now = {0};
        List<SprayDripParticle> drips = new ArrayList<>();
        Minecraft minecraft = clientDrawing(drips, caster, true, now);
        whileDrawing(minecraft, new ArrayList<>(), () -> {
            StreamMotes.handle(sprayTick(caster), runningAtOnce());
            now[0] = FIRST_TICK;
            List<Vec3> drawn = new ArrayList<>();
            for (SprayDripParticle drip : drips) {
                drip.tick();
                drawn.add(drawnAt(drip, PARTIAL_TICK));
            }
            for (int i = 0; i < drawn.size(); i++) {
                for (int j = i + 1; j < drawn.size(); j++) {
                    assertTrue(drawn.get(i).distanceTo(drawn.get(j)) >= DRIP_SPACING, "two of a tick's drips should"
                            + " stand apart, drawn at " + drawn.get(i) + " and " + drawn.get(j));
                }
            }
        });
    }

    @Test
    void aSprintJumpingCastersSporeIsNeverDrawnBehindTheGlove() {
        Caster caster = moving(new Vec3(0, 0, SPRINT_JUMPING));
        double[] now = {0};
        Minecraft minecraft = clientDrawing(new ArrayList<>(), caster, true, now);
        List<SporeParticle> spores = makingSpores(minecraft);
        try (MockedStatic<GloveSpecialRenderer> drawnGlove = mockStatic(GloveSpecialRenderer.class)) {
            drawnGlove.when(GloveSpecialRenderer::getLastGooCenterCamRel).thenReturn(GLOVE_OFF_THE_EYE);
            whileDrawing(minecraft, new ArrayList<>(), () -> {
                StreamMotes.handle(sporeTick(caster), runningAtOnce());
                assertTrue(!spores.isEmpty(), "the stream's tick should launch spores");
                now[0] = FIRST_DRAWN;
                assertNoneBehindTheGlove(spores, caster.eye().apply(now[0]).add(GLOVE_OFF_THE_EYE));
                now[0] = FIRST_TICK;
                spores.forEach(SporeParticle::tick);
                assertNoneBehindTheGlove(spores, caster.eye().apply(now[0]).add(GLOVE_OFF_THE_EYE));
            });
        }
    }

    /**
     * operator ruling: "the source of the particles hops up above the source blob, looking wrong", Mycosis while jumping
     * every-ability-casts-from-the-lifted-palm-blob: a spore leaves the lifted blob where it stands on its first tick
     */
    @Test
    void aFallingCastersSporeLeavesTheLiftedBlobWhereItStandsOnItsFirstTick() {
        Caster caster = moving(new Vec3(0, -FALLING, 0));
        double[] now = {-LIFTED_FOR};
        Minecraft minecraft = clientDrawing(new ArrayList<>(), caster, true, now);
        List<SporeParticle> spores = makingSpores(minecraft);
        try (MockedStatic<GloveSpecialRenderer> drawnGlove = mockStatic(GloveSpecialRenderer.class)) {
            drawnGlove.when(GloveSpecialRenderer::getLastGooCenterCamRel).thenReturn(GLOVE_OFF_THE_EYE);
            whileDrawing(minecraft, new ArrayList<>(), () -> {
                for (long tick = (long) -LIFTED_FOR; tick <= 0; tick++) {
                    now[0] = tick;
                    LiftedPalmBlob.held(CASTER, sourceOf(caster, tick), tick);
                }
                StreamMotes.handle(sporeTick(caster), runningAtOnce());
                assertTrue(!spores.isEmpty(), "the stream's tick should launch spores");
                now[0] = FIRST_TICK;
                spores.forEach(SporeParticle::tick);
                Vec3 blob = LiftedPalmBlob.placeOf(CASTER, 0f).orElseThrow();
                for (SporeParticle spore : spores) {
                    Vec3 left = drawnAt(spore, 0f);
                    assertTrue(left.distanceTo(blob) <= LEFT_THE_BLOB, "a spore should leave the lifted blob at "
                            + blob + " on its first tick, left from " + left);
                }
            });
        }
    }

    /**
     * The client launches every spore of a cast through a cone wide across and thin up and down inside that shape,
     * at the speed that rests it at the cast's reach
     * (decision cone-style-abilities-are-reviewed-for-consistency).
     */
    @Test
    void everySporeOfAShapedCastLaunchesInsideItsShapeToRestAtItsReach() {
        Caster caster = moving(Vec3.ZERO);
        ConeShape wideAndThin = new ConeShape(70, 10, false);
        double reach = 5.5;
        StreamMotesPayload tick = GooStreamHandler.motesOf(CASTER, sourceOf(caster, -1), SPRAY_AXIS,
                Identifier.fromNamespaceAndPath("goo", "spore"), new GooStreamHandler.SprayedCone(wideAndThin, reach),
                Delivery.NO_THROW_SPEED);
        Minecraft minecraft = clientDrawing(new ArrayList<>(), caster, true, new double[] {0});
        whileDrawing(minecraft, new ArrayList<>(), () -> StreamMotes.handle(tick, runningAtOnce()));
        ArgumentCaptor<Double> x = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> y = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> z = ArgumentCaptor.forClass(Double.class);
        verify(minecraft.particleEngine, atLeastOnce()).createParticle(any(), anyDouble(), anyDouble(), anyDouble(),
                x.capture(), y.capture(), z.capture());
        for (int i = 0; i < x.getAllValues().size(); i++) {
            Vec3 launch = new Vec3(x.getAllValues().get(i), y.getAllValues().get(i), z.getAllValues().get(i));
            assertTrue(StreamCone.contains(Vec3.ZERO, SPRAY_AXIS, reach, wideAndThin, launch.normalize()),
                    "a spore launches along " + launch + ", outside the shape");
            assertEquals(reach, launch.length() / GooStreamHandler.restingSpeed(1), 1e-9,
                    "a spore's flight ends off the reach");
        }
    }

    /** Asserts no spore a frame draws stands behind the glove along the caster's run, south. */
    private static void assertNoneBehindTheGlove(List<SporeParticle> spores, Vec3 glove) {
        for (SporeParticle spore : spores) {
            if (quadsDrawn(spore) > 0) {
                Vec3 drawn = drawnAt(spore, PARTIAL_TICK);
                assertTrue(drawn.z >= glove.z - BEHIND_SLACK, "a spore should draw from the glove at " + glove
                        + " or ahead of it, draws at " + drawn);
            }
        }
    }

    /** Hands the client's particle engine Mycosis's spores to make, collecting each it makes. */
    private static List<SporeParticle> makingSpores(Minecraft minecraft) {
        List<SporeParticle> spores = new ArrayList<>();
        SpriteSet sprites = mock(SpriteSet.class);
        when(sprites.get(anyInt(), anyInt())).thenReturn(mock(TextureAtlasSprite.class));
        SporeParticle.Provider provider = new SporeParticle.Provider(sprites);
        when(minecraft.particleEngine.createParticle(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble())).thenAnswer(call -> {
                    SporeParticle spore = provider.createParticle(mock(SimpleParticleType.class), minecraft.level,
                            call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4),
                            call.getArgument(5), call.getArgument(6), RandomSource.create(2L));
                    spores.add(spore);
                    return spore;
                });
        return spores;
    }

    /** Mycosis's tick as the server sends it, its glove a tick behind, its spores sprayed to rest three blocks off. */
    private static StreamMotesPayload sporeTick(Caster caster) {
        return new StreamMotesPayload(CASTER, sourceOf(caster, -1), SPRAY_AXIS,
                Identifier.fromNamespaceAndPath("goo", "spore"), ConeShape.round(50), GooStreamHandler.restingSpeed(3),
                Delivery.NO_THROW_SPEED, false, 0, 0, sourceOf(caster, -1), 0);
    }

    /** The stream's tick as the server sends it for a caster, its glove a tick before the tick reaches the client. */
    private static StreamMotesPayload sprayTick(Caster caster) {
        return sprayTickFrom(sourceOf(caster, -1), caster.eye().apply(-1));
    }

    /** The stream's tick as the server sends it, its drips leaving a place onto a cone from an eye. */
    private static StreamMotesPayload sprayTickFrom(Vec3 blob, Vec3 eye) {
        return new StreamMotesPayload(CASTER, blob, SPRAY_AXIS, Identifier.fromNamespaceAndPath("goo",
                "goop_spray_drip"), ConeShape.round(CONE_DEGREES), 0, THROW_SPEED, false, SPRAY_HELD, SPRAY_DRIPS, eye, SPRAY_LANDS);
    }

    /** The stream's tick as the server sends it for a caster, its drips thrown in a cone of a shape. */
    private static StreamMotesPayload shapedSprayTick(Caster caster, ConeShape shape) {
        return new StreamMotesPayload(CASTER, sourceOf(caster, -1), SPRAY_AXIS, Identifier.fromNamespaceAndPath("goo",
                "goop_spray_drip"), shape, 0, THROW_SPEED, false, SPRAY_HELD, SPRAY_DRIPS, caster.eye().apply(-1),
                SPRAY_LANDS);
    }

    /** The launch the server's drip takes for a share of a tick: from the tick's blob onto the tick's cone. */
    private static Vec3 serverLaunch(StreamMotesPayload tick, CoatRay.ConeShare share) {
        return GooStreamHandler.dripLaunch(new GooStreamHandler.DripThrow(tick.apex(), tick.cone(), tick.throwSpeed(),
                DripArc.GRAVITY), GOOP, share);
    }

    /**
     * Runs a body with the client's statics standing in for the game: the
     * client drawing, Goop's drip particle resolved, open air to fly through,
     * the right glove held, and each launch DripArc answers recorded.
     */
    private static void whileDrawing(Minecraft minecraft, List<Vec3> launches, Runnable body) {
        try (MockedStatic<Minecraft> mc = mockStatic(Minecraft.class);
             MockedStatic<SimpleParticles> particles = mockStatic(SimpleParticles.class);
             MockedStatic<CoatRay> rays = mockStatic(CoatRay.class, CALLS_REAL_METHODS);
             MockedStatic<DripArc> arcs = mockStatic(DripArc.class, CALLS_REAL_METHODS);
             MockedStatic<GooCasters> casters = mockStatic(GooCasters.class, CALLS_REAL_METHODS);
             MockedStatic<GooGloveItem> gloves = mockStatic(GooGloveItem.class)) {
            mc.when(Minecraft::getInstance).thenReturn(minecraft);
            gloves.when(() -> GooGloveItem.getSelectedType(any())).thenReturn(GooTypes.ZOO);
            particles.when(() -> SimpleParticles.resolve(any())).thenReturn(Optional.of(mock(SimpleParticleType.class)));
            rays.when(() -> CoatRay.clipping(any())).thenReturn((CoatRay.BlockClip) (from, to) -> Optional.empty());
            arcs.when(() -> DripArc.launchOnto(any(), any(), anyDouble(), anyDouble())).thenAnswer(call -> {
                Vec3 launch = (Vec3) call.callRealMethod();
                launches.add(launch);
                return launch;
            });
            casters.when(() -> GooCasters.casterOnTheRight(any())).thenReturn(true);
            body.run();
        }
    }

    private static void assertFirstDrawnInFlightFromTheBlob(Caster caster, boolean local) {
        double[] now = {0};
        List<SprayDripParticle> drips = new ArrayList<>();
        List<Vec3> launches = new ArrayList<>();
        Minecraft minecraft = clientDrawing(drips, caster, local, now);
        whileDrawing(minecraft, launches, () -> {
            // the server's echo carries the glove a tick before the stream's tick reaches this client
            StreamMotes.handle(sprayTick(caster), runningAtOnce());
            now[0] = FIRST_DRAWN;

            assertTrue(!drips.isEmpty(), "the stream's tick should launch spray drips");
            for (SprayDripParticle drip : drips) {
                assertEquals(0, quadsDrawn(drip), "a drip should draw nothing before its first tick throws it");
            }
            now[0] = FIRST_TICK;
            Vec3 blob = LiftedPalmBlob.blobOf(CASTER, 0f).orElseThrow().place();
            Vec3 source = sourceOf(caster, now[0]);
            assertTrue(blob.distanceTo(source) > STILL_EASING, "on the spray's first ticks the blob should still be "
                    + "easing off the palm, at " + blob + ", short of the source " + source);
            for (int i = 0; i < drips.size(); i++) {
                SprayDripParticle drip = drips.get(i);
                drip.tick();
                double lead = (double) i / StreamMotes.MOTES_PER_TICK;
                Vec3 left = drawnAt(drip, 0f);
                Vec3 onItsArc = arcFrom(blob, launches.get(i), lead);
                assertTrue(left.distanceTo(onItsArc) <= LEFT_THE_BLOB, "a drip should leave the blob where it stands, "
                        + blob + ", " + lead + " of a tick along its arc at " + onItsArc + ", left from " + left);
                assertDrawnOnItsLaunch(drawnAt(drip, PARTIAL_TICK), onItsArc.lerp(arcFrom(blob, launches.get(i),
                        lead + 1), PARTIAL_TICK), launches.get(i));
            }
        });
    }

    /**
     * Where a drip thrown from the blob at its launch stands a time into its
     * flight through open air, between the ticks either side.
     */
    private static Vec3 arcFrom(Vec3 blob, Vec3 launch, double ticks) {
        Vec3 at = blob;
        Vec3 velocity = launch;
        double flown = 0;
        while (true) {
            DripArc.Step step = DripArc.step(at, velocity, (from, to) -> Optional.empty(), point -> true);
            if (ticks <= flown + 1) {
                return at.lerp(step.position(), ticks - flown);
            }
            at = step.position();
            velocity = step.velocity();
            flown++;
        }
    }

    /** Asserts a drawn point stands halfway along the tick of flight its drip's arc carries it through. */
    private static void assertDrawnOnItsLaunch(Vec3 drawn, Vec3 onItsArc, Vec3 launch) {
        assertTrue(drawn.distanceTo(onItsArc) <= ON_THE_LAUNCH, "a drip's first quad should stand half a tick"
                + " along its arc at its launch " + launch + ", at " + onItsArc + ", drawn at " + drawn);
    }

    private static Minecraft clientDrawing(List<SprayDripParticle> drips, Caster caster, boolean local,
                                           double[] now) {
        Minecraft minecraft = mock(Minecraft.class);
        ClientLevel level = mock(ClientLevel.class, RETURNS_DEEP_STUBS);
        when(level.getRandom()).thenReturn(RandomSource.create(1L));
        when(level.hasChunk(anyInt(), anyInt())).thenReturn(true);
        minecraft.level = level;
        when(level.getGameTime()).thenAnswer(call -> (long) Math.floor(now[0]));
        Player player = local ? mock(LocalPlayer.class) : mock(Player.class);
        when(level.getEntity(CASTER)).thenReturn(player);
        when(player.getId()).thenReturn(CASTER);
        ItemStack glove = mock(ItemStack.class);
        when(glove.getItem()).thenReturn(mock(GooGloveItem.class));
        when(player.getMainHandItem()).thenReturn(glove);
        when(player.getPosition(anyFloat())).thenAnswer(call -> caster.eye().apply(now[0]).subtract(0, EYE.y, 0));
        when(player.getEyePosition(anyFloat())).thenAnswer(call -> caster.eye().apply(now[0]));
        when(player.getViewXRot(anyFloat())).thenReturn(PITCH);
        when(player.getViewYRot(anyFloat())).thenAnswer(call -> caster.yaw().apply(now[0]));
        Camera camera = mock(Camera.class);
        when(camera.position()).thenAnswer(call -> caster.eye().apply(now[0]));
        when(camera.xRot()).thenReturn(PITCH);
        when(camera.yRot()).thenAnswer(call -> caster.yaw().apply(now[0]));
        when(camera.rotation()).thenAnswer(call -> new Quaternionf().rotationYXZ(
                (float) Math.PI - (float) Math.toRadians(caster.yaw().apply(now[0])), -(float) Math.toRadians(PITCH),
                0f));
        when(camera.getFov()).thenReturn(WORLD_FOV);
        GameRenderer renderer = mock(GameRenderer.class);
        when(renderer.mainCamera()).thenReturn(camera);
        SprayingClient.setRenderState(renderer);
        Options options = mock(Options.class);
        when(options.getCameraType()).thenReturn(CameraType.FIRST_PERSON);
        setField(minecraft, "gameRenderer", renderer);
        setField(minecraft, "options", options);
        if (local) {
            minecraft.player = (LocalPlayer) player;
        }
        SpriteSet sprites = mock(SpriteSet.class);
        when(sprites.get(anyInt(), anyInt())).thenReturn(mock(TextureAtlasSprite.class));
        SprayDripParticle.Provider provider = new SprayDripParticle.Provider(sprites, BlendPalette.GOOP_SLIME_GREENS);
        ParticleEngine engine = mock(ParticleEngine.class);
        when(engine.createParticle(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble())).thenAnswer(call -> {
                    SprayDripParticle drip = provider.createParticle(mock(SimpleParticleType.class), level,
                            call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4),
                            call.getArgument(5), call.getArgument(6), RandomSource.create(2L));
                    drips.add(drip);
                    return drip;
                });
        setField(minecraft, "particleEngine", engine);
        return minecraft;
    }

    private static void setField(Minecraft minecraft, String name, Object value) {
        try {
            Field field = Minecraft.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(minecraft, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static IPayloadContext runningAtOnce() {
        IPayloadContext context = mock(IPayloadContext.class);
        doAnswer(call -> {
            call.<Runnable>getArgument(0).run();
            return null;
        }).when(context).enqueueWork(any(Runnable.class));
        return context;
    }

    /**
     * Where a frame at a partial tick draws a drip: the camera-relative quad
     * it hands the renderer, back in the world.
     */
    private static Vec3 drawnAt(SingleQuadParticle drip, float partialTick) {
        QuadParticleRenderState frame = drawFrame(drip, partialTick);
        ArgumentCaptor<Float> x = ArgumentCaptor.forClass(Float.class);
        ArgumentCaptor<Float> y = ArgumentCaptor.forClass(Float.class);
        ArgumentCaptor<Float> z = ArgumentCaptor.forClass(Float.class);
        verify(frame).add(any(), x.capture(), y.capture(), z.capture(), anyFloat(), anyFloat(), anyFloat(),
                anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyInt(), anyInt());
        assertEquals(1, x.getAllValues().size(), "the frame should draw the drip once");
        return new Vec3(x.getValue(), y.getValue(), z.getValue());
    }

    /** The quads a frame draws of a drip. */
    private static int quadsDrawn(SingleQuadParticle drip) {
        QuadParticleRenderState frame = drawFrame(drip, PARTIAL_TICK);
        return (int) mockingDetails(frame).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("add")).count();
    }

    /** A frame at a partial tick that has drawn a drip, the camera at the world's origin. */
    private static QuadParticleRenderState drawFrame(SingleQuadParticle drip, float partialTick) {
        Camera camera = mock(Camera.class);
        when(camera.position()).thenReturn(Vec3.ZERO);
        when(camera.rotation()).thenReturn(new Quaternionf());
        QuadParticleRenderState frame = mock(QuadParticleRenderState.class);
        drip.extract(frame, camera, partialTick);
        return frame;
    }
}
