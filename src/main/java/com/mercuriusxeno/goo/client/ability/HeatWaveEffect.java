package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mojang.blaze3d.buffers.Std140Builder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import java.util.function.Consumer;

/**
 * Flames' heat wave: while Flames pours, the view shimmers like
 * air over a fire in a small box around the point the flame reaches, the
 * heated block or the burning mob, easing in as the hold starts and out
 * after it ends, through {@code goo:heat_wave}.
 * decision flames-absorbs-broil-and-cooks-blocks
 * decision heat-wave-shrinks-to-a-local-shimmer
 */
public final class HeatWaveEffect {

    /** The chain at {@code assets/goo/post_effect/heat_wave.json}. */
    static final Identifier POST_EFFECT_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "heat_wave");
    private static final String HEAT_CONFIG_BLOCK = "HeatConfig";
    /** Std140 size of {@code HeatConfig}: two vec4s. */
    private static final int HEAT_CONFIG_SIZE = 32;
    /** The ripple's reach in UV at full heat. */
    static final float MAX_STRENGTH = 0.004f;
    /**
     * The shimmer box's side as a share of the screen's shorter side, so it
     * spans no more than this share of the screen either way.
     * decision heat-wave-shrinks-to-a-local-shimmer
     */
    static final float SHIMMER_SHARE = 0.25f;
    /** The share of the gap to full or no heat the wave closes each tick. */
    static final float EASE_PER_TICK = 0.25f;
    private static final float TICKS_PER_SECOND = 20f;
    /** Below this heat the wave is gone and the slot is released. */
    static final float COLD = 0.01f;
    private static final float HALF = 0.5f;

    private static final OwnedPostEffect SLOT = new OwnedPostEffect(POST_EFFECT_ID, HEAT_CONFIG_BLOCK);
    private static final Vector4f HEAT_PARAMS = new Vector4f();
    private static final Vector4f HEAT_REGION = new Vector4f();
    private static float heat;
    /** The cone Flames poured through since the last frame, taken by the frame, or null once the pour stops. */
    private static @Nullable SprayCone poured;
    /** The cone Flames last poured through, which the shimmer eases out at once the pour stops. */
    private static @Nullable SprayCone heating;

    private HeatWaveEffect() {
    }

    /**
     * Hands the wave the cone Flames pours through this frame, the glove's or a nozzle's.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param cone the cone poured through
     */
    static void pour(SprayCone cone) {
        poured = cone;
    }

    /**
     * The box on the screen the shimmer fills, in screen UV, the origin at
     * the bottom left.
     *
     * @param centerU     the box center's horizontal UV
     * @param centerV     the box center's vertical UV
     * @param widthShare  the box's width as a share of the screen's width
     * @param heightShare the box's height as a share of the screen's height
     */
    record ShimmerRegion(float centerU, float centerV, float widthShare, float heightShare) {
    }

    /**
     * Eases the heat toward full while Flames runs and toward none
     * otherwise, then shows the shimmer around the flame's reach point or
     * releases it: a post effect over the finished frame, which the shared
     * path hands the frame on its screen-space route and which hands back no draw.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws unused, the shimmer drawing over the finished frame
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        heat = easedHeat(heat, heatsUp(), mc.getDeltaTracker().getRealtimeDeltaTicks());
        SprayCone cone = heating;
        if (heat < COLD || mc.level == null || player == null || cone == null) {
            SLOT.hide(mc.gameRenderer);
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        ShimmerRegion region = regionThisFrame(mc, player, frame.levelRenderState().cameraRenderState,
                partialTick, cone);
        if (region == null) {
            SLOT.hide(mc.gameRenderer);
            return;
        }
        float seconds = ((mc.level.getGameTime() % Integer.MAX_VALUE) + partialTick) / TICKS_PER_SECOND;
        // The shimmer shows in its draw, so it runs after Sculker's dim, registered ahead of it on the same route.
        draws.accept(new SortedDraw(0, () -> showShimmer(mc, region, seconds)));
    }

    /**
     * Whether Flames pours this frame, keeping its cone for the shimmer to
     * ease out at once the pour stops.
     *
     * @return true while Flames pours
     */
    private static boolean heatsUp() {
        SprayCone cone = poured;
        poured = null;
        if (cone == null) {
            return false;
        }
        heating = cone;
        return true;
    }

    private static @Nullable ShimmerRegion regionThisFrame(Minecraft mc, LocalPlayer player, CameraRenderState sight,
                                                           float partialTick, SprayCone cone) {
        Vec3 fromCamera = reachPoint(player, partialTick, cone).subtract(sight.pos);
        return shimmerAround(new Vector3f((float) fromCamera.x, (float) fromCamera.y, (float) fromCamera.z),
                sight.viewRotationMatrix, sight.projectionMatrix, mc.getWindow().getWidth(),
                mc.getWindow().getHeight());
    }

    private static void showShimmer(Minecraft mc, ShimmerRegion region, float seconds) {
        HEAT_PARAMS.set(seconds, MAX_STRENGTH * heat, 0f, 0f);
        HEAT_REGION.set(region.centerU(), region.centerV(), region.widthShare() * HALF,
                region.heightShare() * HALF);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, HEAT_CONFIG_SIZE);
            builder.putVec4(HEAT_PARAMS);
            builder.putVec4(HEAT_REGION);
            SLOT.show(mc, builder.get());
        }
    }

    /**
     * The shimmer's box: centered where a camera-relative point projects on
     * the screen, square in pixels, its side {@link #SHIMMER_SHARE} of the
     * screen's shorter side.
     * decision heat-wave-shrinks-to-a-local-shimmer
     *
     * @param fromCamera the reach point, camera relative
     * @param view       the camera's view rotation
     * @param projection the level's projection
     * @param width      the screen's width in pixels
     * @param height     the screen's height in pixels
     * @return the box, or null where the point sits behind the camera
     */
    static @Nullable ShimmerRegion shimmerAround(Vector3fc fromCamera, Matrix4fc view, Matrix4fc projection,
                                                 int width, int height) {
        Vector4f clip = new Matrix4f(projection).mul(view).transform(new Vector4f(fromCamera, 1f));
        if (clip.w <= 0f) {
            return null;
        }
        float side = SHIMMER_SHARE * Math.min(width, height);
        return new ShimmerRegion(clip.x / clip.w * HALF + HALF, clip.y / clip.w * HALF + HALF,
                side / Math.max(1, width), side / Math.max(1, height));
    }

    /**
     * Where the flame reaches down the look: the first living thing or block
     * the look meets within the reach, or the reach's end in open air.
     *
     * @param player      the local player
     * @param partialTick the frame's partial tick
     * @param cone        the cone Flames pours through
     * @return the point, in world coordinates
     */
    private static Vec3 reachPoint(LocalPlayer player, float partialTick, SprayCone cone) {
        return reachAlong(CameraEye.of(Minecraft.getInstance(), player, partialTick), cone,
                CameraEye.blocksSeenBy(player), (from, to) -> {
                    EntityHitResult mob = ProjectileUtil.getEntityHitResult(player, from, to,
                            new AABB(from, to).inflate(1), LivingEntity.class::isInstance, from.distanceToSqr(to));
                    return mob == null ? null : mob.getLocation();
                });
    }

    /**
     * Finds the first living thing a line meets.
     */
    @FunctionalInterface
    interface MobClip {

        /**
         * The first living thing the line meets.
         *
         * @param from the line's start
         * @param to   the line's end
         * @return where the line meets it, null where it meets none
         */
        @Nullable Vec3 firstMob(Vec3 from, Vec3 to);
    }

    /**
     * Where the flame reaches along a sight: the first living thing or block
     * the sight line meets within the cone's reach, or the reach's end in open air.
     * decision diagnose-and-correct-eye-aimed-visuals-jumping-on-sneak
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param sight  the eye and look the line leaves from, {@link CameraEye#of}'s
     * @param cone   the cone Flames pours through
     * @param blocks the clip the line is traced against
     * @param mobs   the living things the line is traced against
     * @return the point, in world coordinates
     */
    static Vec3 reachAlong(CameraEye.Sight sight, SprayCone cone, CameraEye.ViewClip blocks, MobClip mobs) {
        Vec3 eye = sight.eye();
        Vec3 end = eye.add(sight.look().scale(cone.reach()));
        BlockHitResult block = blocks.clip(eye, end);
        Vec3 stop = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : end;
        Vec3 mob = mobs.firstMob(eye, stop);
        return mob != null ? mob : stop;
    }

    /**
     * The heat after a frame: it closes a share of the gap to full heat
     * while Flames runs, and to none otherwise.
     *
     * @param heat       the heat before the frame, 0 to 1
     * @param running    whether Flames runs
     * @param deltaTicks the frame's length in ticks
     * @return the heat after the frame
     */
    static float easedHeat(float heat, boolean running, float deltaTicks) {
        float target = running ? 1f : 0f;
        return heat + (target - heat) * Math.min(1f, deltaTicks * EASE_PER_TICK);
    }
}
