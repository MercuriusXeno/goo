package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.FlamesStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

/**
 * Flames' heat wave: while right click holds Flames, the view shimmers like
 * air over a fire in a small box around the point the flame reaches, the
 * heated block or the burning mob, easing in as the hold starts and out
 * after it ends, through {@code goo:heat_wave}.
 * decision flames-absorbs-broil-and-cooks-blocks
 * decision heat-wave-shrinks-to-a-local-shimmer
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
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
    /** Flames' reach when no synced Flames names one, as blaze_flames.json ships it. */
    private static final double FLAMES_RANGE = 6;
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
    private static double range = FLAMES_RANGE;

    private HeatWaveEffect() {
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
     * otherwise, then draws the shimmer around the flame's reach point or
     * releases it.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        heat = easedHeat(heat, player != null && heatsUp(player), mc.getDeltaTracker().getRealtimeDeltaTicks());
        if (heat < COLD || mc.level == null || player == null) {
            SLOT.hide(mc.gameRenderer);
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        ShimmerRegion region = regionThisFrame(mc, player, event.getLevelRenderState().cameraRenderState,
                partialTick);
        if (region == null) {
            SLOT.hide(mc.gameRenderer);
            return;
        }
        showShimmer(mc, region, ((mc.level.getGameTime() % Integer.MAX_VALUE) + partialTick) / TICKS_PER_SECOND);
    }

    /**
     * Whether Flames runs this frame, keeping its reach for the shimmer to
     * ease out at once the hold ends.
     *
     * @param player the local player
     * @return true while right click holds Flames
     */
    private static boolean heatsUp(LocalPlayer player) {
        ClientAbility selected = runningAbility(player);
        if (selected == null || !holdsFlames(selected, GloveUseTracker.runsHeld(player))) {
            return false;
        }
        range = selected.delivery().range();
        return true;
    }

    private static @Nullable ShimmerRegion regionThisFrame(Minecraft mc, LocalPlayer player, CameraRenderState sight,
                                                           float partialTick) {
        Vec3 fromCamera = reachPoint(player, partialTick, range).subtract(sight.pos);
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
     * @param reach       the stream's reach
     * @return the point, in world coordinates
     */
    private static Vec3 reachPoint(LocalPlayer player, float partialTick, double reach) {
        return reachAlong(CameraEye.of(Minecraft.getInstance(), player, partialTick), reach,
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
     * the sight line meets within the reach, or the reach's end in open air.
     * decision diagnose-and-correct-eye-aimed-visuals-jumping-on-sneak
     *
     * @param sight  the eye and look the line leaves from, {@link CameraEye#of}'s
     * @param reach  the stream's reach
     * @param blocks the clip the line is traced against
     * @param mobs   the living things the line is traced against
     * @return the point, in world coordinates
     */
    static Vec3 reachAlong(CameraEye.Sight sight, double reach, CameraEye.ViewClip blocks, MobClip mobs) {
        Vec3 eye = sight.eye();
        Vec3 end = eye.add(sight.look().scale(reach));
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

    private static @Nullable ClientAbility runningAbility(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        return abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
    }

    /**
     * Whether Flames runs: the selected ability runs a flame and right click holds it.
     *
     * @param ability the selected ability's synced copy, or null when none
     * @param useHeld whether right click holds a live press
     * @return true while Flames runs
     */
    static boolean holdsFlames(@Nullable ClientAbility ability, boolean useHeld) {
        return useHeld && ability != null && ability.behaviors().stream().anyMatch(FlamesStep.class::isInstance);
    }
}
