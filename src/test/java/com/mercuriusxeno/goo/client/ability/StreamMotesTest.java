package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SimpleParticles;
import com.mercuriusxeno.goo.client.particle.BlendPalette;
import com.mercuriusxeno.goo.client.particle.GloveSwing;
import com.mercuriusxeno.goo.client.particle.SprayDripParticle;
import com.mercuriusxeno.goo.network.CoatSpray;
import com.mercuriusxeno.goo.network.StreamMotesPayload;
import com.mercuriusxeno.goo.throwing.CoatRay;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaternionf;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A Goop or Slick spray drip is first drawn on its caster's glove as the
 * glove is drawn that frame, for a caster standing, walking, strafing and
 * turning. The client runs a frame's network tasks, then its ticks, then
 * draws: a drip the stream's tick launches joins the particle engine at the
 * next tick, after the engine has ticked the drips already flying, so it is
 * first drawn a tick or more after its launch, before its own first tick.
 * goop-and-slick-spray-emanates-from-the-glove
 * diagnose-and-correct-the-spray-not-leaving-the-glove
 */
class StreamMotesTest {

    private static final int CASTER = 7;
    private static final Vec3 EYE = new Vec3(0, 1.62, 0);
    private static final Vec3 LOOK = new Vec3(0, -0.5, 1).normalize();
    /** Where the drawn glove stands off the eye, held in the look's frame: right of, below and before the eye. */
    private static final Vec3 HAND = new Vec3(-0.35, -0.3, 0.5);
    private static final double SPEED = 0.2;
    /** Ticks from the stream's tick being handled to the frame that first draws its drips. */
    private static final double FIRST_DRAWN = 1.5;
    private static final float PARTIAL_TICK = 0.5f;
    private static final double WITHIN = 0.01;

    /**
     * A caster's glove over time, in ticks from the launch.
     *
     * @param glove where the glove is drawn at a time
     * @param look  where the caster looks at a time
     * @param travel the caster's travel velocity the server sent, blocks per tick
     */
    private record Caster(DoubleFunction<Vec3> glove, DoubleFunction<Vec3> look, Vec3 travel) {
    }

    private static Caster moving(Vec3 travel) {
        return new Caster(time -> EYE.add(travel.scale(time)).add(HAND), time -> LOOK, travel);
    }

    /** A caster turning ten degrees a tick, the glove swinging round the eye with the look. */
    private static Caster turning() {
        DoubleFunction<Float> yaw = time -> (float) Math.toRadians(10 * time);
        return new Caster(time -> EYE.add(HAND.yRot(yaw.apply(time))), time -> LOOK.yRot(yaw.apply(time)),
                Vec3.ZERO);
    }

    @Test
    void aStandingCastersDripIsFirstDrawnOnTheGlove() {
        assertFirstDrawnOnTheGlove(moving(Vec3.ZERO));
    }

    @Test
    void aWalkingCastersDripIsFirstDrawnOnTheGlove() {
        assertFirstDrawnOnTheGlove(moving(LOOK.multiply(1, 0, 1).normalize().scale(SPEED)));
    }

    @Test
    void aStrafingCastersDripIsFirstDrawnOnTheGlove() {
        assertFirstDrawnOnTheGlove(moving(new Vec3(-SPEED, 0, 0)));
    }

    @Test
    void aTurningCastersDripIsFirstDrawnOnTheGlove() {
        assertFirstDrawnOnTheGlove(turning());
    }

    private static void assertFirstDrawnOnTheGlove(Caster caster) {
        double[] now = {0};
        List<SprayDripParticle> drips = new ArrayList<>();
        Minecraft minecraft = clientDrawing(drips);
        try (MockedStatic<Minecraft> mc = mockStatic(Minecraft.class);
             MockedStatic<SimpleParticles> particles = mockStatic(SimpleParticles.class);
             MockedStatic<CoatRay> rays = mockStatic(CoatRay.class, CALLS_REAL_METHODS);
             MockedStatic<CasterPoses> poses = mockStatic(CasterPoses.class)) {
            mc.when(Minecraft::getInstance).thenReturn(minecraft);
            particles.when(() -> SimpleParticles.resolve(any())).thenReturn(Optional.of(mock(SimpleParticleType.class)));
            rays.when(() -> CoatRay.clipping(any())).thenReturn((CoatRay.BlockClip) (from, to) -> Optional.empty());
            poses.when(() -> CasterPoses.gloveOf(eq(CASTER), any())).thenAnswer(call -> caster.glove().apply(now[0]));
            poses.when(() -> CasterPoses.poseOf(CASTER)).thenAnswer(call ->
                    new GloveSwing.CasterPose(caster.glove().apply(now[0]), caster.look().apply(now[0])));
            // the server's echo carries the glove a tick before the stream's tick reaches this client
            StreamMotes.handle(new StreamMotesPayload(CASTER, caster.glove().apply(-1), LOOK,
                    Identifier.fromNamespaceAndPath("goo", "goop_spray_drip"), 16, 0, caster.travel(),
                    Optional.of(new CoatSpray(EYE, LOOK, 8))), runningAtOnce());
            now[0] = FIRST_DRAWN;

            assertTrue(!drips.isEmpty(), "the stream's tick should launch spray drips");
            Vec3 glove = caster.glove().apply(now[0]);
            for (SprayDripParticle drip : drips) {
                Vec3 drawn = drawnAt(drip);
                assertTrue(drawn.distanceTo(glove) <= WITHIN, "a drip should be first drawn on the glove at "
                        + glove + ", drawn at " + drawn + ", " + drawn.distanceTo(glove) + " blocks off");
            }
        }
    }

    private static Minecraft clientDrawing(List<SprayDripParticle> drips) {
        Minecraft minecraft = mock(Minecraft.class);
        ClientLevel level = mock(ClientLevel.class, RETURNS_DEEP_STUBS);
        when(level.getRandom()).thenReturn(RandomSource.create(1L));
        minecraft.level = level;
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
        setParticleEngine(minecraft, engine);
        return minecraft;
    }

    private static void setParticleEngine(Minecraft minecraft, ParticleEngine engine) {
        try {
            Field field = Minecraft.class.getDeclaredField("particleEngine");
            field.setAccessible(true);
            field.set(minecraft, engine);
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
     * Where the frame draws a drip: the camera-relative quad it hands the
     * renderer, back in the world.
     */
    private static Vec3 drawnAt(SprayDripParticle drip) {
        Camera camera = mock(Camera.class);
        when(camera.position()).thenReturn(Vec3.ZERO);
        when(camera.rotation()).thenReturn(new Quaternionf());
        QuadParticleRenderState frame = mock(QuadParticleRenderState.class);
        drip.extract(frame, camera, PARTIAL_TICK);
        ArgumentCaptor<Float> x = ArgumentCaptor.forClass(Float.class);
        ArgumentCaptor<Float> y = ArgumentCaptor.forClass(Float.class);
        ArgumentCaptor<Float> z = ArgumentCaptor.forClass(Float.class);
        verify(frame).add(any(), x.capture(), y.capture(), z.capture(), anyFloat(), anyFloat(), anyFloat(),
                anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyInt(), anyInt());
        assertEquals(1, x.getAllValues().size(), "the frame should draw the drip once");
        return new Vec3(x.getValue(), y.getValue(), z.getValue());
    }
}
