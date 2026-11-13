package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SimpleParticles;
import com.mercuriusxeno.goo.client.particle.BlendPalette;
import com.mercuriusxeno.goo.client.particle.SprayDripParticle;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mercuriusxeno.goo.network.KnifeRainSling;
import com.mercuriusxeno.goo.network.StreamMotesPayload;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.DripArc;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
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
    private static final double THROW_SPEED = 0.6;
    /** How far a drip's first drawn quad may stray from half its launch out of the blob: the quad's float precision. */
    private static final double ON_THE_LAUNCH = 1e-3;
    /** How far short of the spray source the blob stands while it still eases off the palm. */
    private static final double STILL_EASING = 0.05;
    /** How near the blob's place a drip's flight starts: the quad's float precision. */
    private static final double LEFT_THE_BLOB = 1e-4;

    private static final float WORLD_FOV = 70f;

    /**
     * A caster over time, in ticks from the launch.
     *
     * @param eye    where the eye stands
     * @param yaw    the look's yaw in degrees
     * @param travel the travel velocity the server sent, blocks per tick
     */
    private record Caster(DoubleFunction<Vec3> eye, DoubleFunction<Float> yaw, Vec3 travel) {
    }

    private static Caster moving(Vec3 travel) {
        return new Caster(time -> EYE.add(travel.scale(time)), time -> 0f, travel);
    }

    /** A caster turning ten degrees a tick. */
    private static Caster turning() {
        return new Caster(time -> EYE, time -> (float) (10 * time), Vec3.ZERO);
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

    private static void assertFirstDrawnInFlightFromTheBlob(Caster caster, boolean local) {
        double[] now = {0};
        List<SprayDripParticle> drips = new ArrayList<>();
        List<Vec3> launches = new ArrayList<>();
        Minecraft minecraft = clientDrawing(drips, caster, local, now);
        try (MockedStatic<Minecraft> mc = mockStatic(Minecraft.class);
             MockedStatic<SimpleParticles> particles = mockStatic(SimpleParticles.class);
             MockedStatic<CoatRay> rays = mockStatic(CoatRay.class, CALLS_REAL_METHODS);
             MockedStatic<DripArc> arcs = mockStatic(DripArc.class, CALLS_REAL_METHODS);
             MockedStatic<KnifeRainSling> sling = mockStatic(KnifeRainSling.class);
             MockedStatic<GooGloveItem> gloves = mockStatic(GooGloveItem.class)) {
            mc.when(Minecraft::getInstance).thenReturn(minecraft);
            gloves.when(() -> GooGloveItem.getSelectedType(any())).thenReturn(GooTypes.ZOO);
            particles.when(() -> SimpleParticles.resolve(any())).thenReturn(Optional.of(mock(SimpleParticleType.class)));
            rays.when(() -> CoatRay.clipping(any())).thenReturn((CoatRay.BlockClip) (from, to) -> Optional.empty());
            arcs.when(() -> DripArc.launch(any(), anyDouble(), any(), anyDouble(), any())).thenAnswer(call -> {
                Vec3 launch = (Vec3) call.callRealMethod();
                launches.add(launch);
                return launch;
            });
            sling.when(() -> KnifeRainSling.gloveOnTheRight(any())).thenReturn(true);

            // the server's echo carries the glove a tick before the stream's tick reaches this client
            StreamMotes.handle(new StreamMotesPayload(CASTER, sourceOf(caster, -1), new Vec3(0, -0.5, 1).normalize(),
                    Identifier.fromNamespaceAndPath("goo", "goop_spray_drip"), 16, 0, caster.travel(), THROW_SPEED, false),
                    runningAtOnce());
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
                Vec3 left = drawnAt(drip, 0f);
                assertTrue(left.distanceTo(blob) <= LEFT_THE_BLOB, "a drip should leave the blob where it stands, "
                        + blob + ", left from " + left);
                assertDrawnOnItsLaunch(drawnAt(drip, PARTIAL_TICK), blob, launches.get(i));
            }
        }
    }

    /**
     * Asserts a drawn point stands half its launch out of the blob, as half
     * a tick of flight carries a drip thrown from the blob at that launch.
     */
    private static void assertDrawnOnItsLaunch(Vec3 drawn, Vec3 blob, Vec3 launch) {
        Vec3 halfway = blob.add(launch.scale(PARTIAL_TICK));
        assertTrue(drawn.distanceTo(halfway) <= ON_THE_LAUNCH, "a drip's first quad should stand half its launch "
                + launch + " out of the blob " + blob + ", at " + halfway + ", drawn at " + drawn);
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
        when(renderer.getMainCamera()).thenReturn(camera);
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
    private static Vec3 drawnAt(SprayDripParticle drip, float partialTick) {
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
    private static int quadsDrawn(SprayDripParticle drip) {
        QuadParticleRenderState frame = drawFrame(drip, PARTIAL_TICK);
        return (int) mockingDetails(frame).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("add")).count();
    }

    /** A frame at a partial tick that has drawn a drip, the camera at the world's origin. */
    private static QuadParticleRenderState drawFrame(SprayDripParticle drip, float partialTick) {
        Camera camera = mock(Camera.class);
        when(camera.position()).thenReturn(Vec3.ZERO);
        when(camera.rotation()).thenReturn(new Quaternionf());
        QuadParticleRenderState frame = mock(QuadParticleRenderState.class);
        drip.extract(frame, camera, partialTick);
        return frame;
    }
}
