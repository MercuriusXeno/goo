package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.UnmakeStep;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Unmake's destabilizing waves: while right click holds Unmake, thin ripple
 * rings leave the glove and travel out, widening down the cone, with clear
 * gaps between them and no filled cone. Each ring is a thin band square to
 * the look, its edges softened by {@code unmake_waves.fsh}.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class UnmakeWaves {

    /** Rings in flight down the cone at once. */
    static final int RINGS = 4;
    /** Cone lengths a ring travels each tick. */
    static final double RING_SPEED = 0.06;
    /** A ring's band width, in blocks. */
    static final double RING_WIDTH = 0.07;
    /** Where a ring leaves the glove, in blocks past it. */
    static final double FROM_THE_GLOVE = 0.05;
    private static final int SEGMENTS = 48;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double HALF = 0.5;
    private static final int OPAQUE = 0xFF;
    /** A look this close to straight up or down crosses x rather than up to find its sides. */
    private static final double NEAR_VERTICAL = 0.99;

    private UnmakeWaves() {
    }

    /**
     * Draws the waves after the translucent blocks while Unmake is held.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientAbility unmake = player == null ? null : heldUnmake(player);
        if (unmake == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        Vec3 apex = GloveAim.handPosition(mc.gameRenderer.getMainCamera()).subtract(camera);
        Vec3 axis = player.getViewVector(partialTick);
        double tan = Math.tan(Math.toRadians(unmake.delivery().coneDegrees() * HALF));
        double range = unmake.delivery().range();
        double ticks = player.level().getGameTime() + partialTick;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(event.getPoseStack().last(),
                buffers.getBuffer(GooRenderTypes.UNMAKE_WAVES_TYPE));
        for (int ring = 0; ring < RINGS; ring++) {
            double along = ringAlong(ring, ticks);
            double distance = FROM_THE_GLOVE + (range - FROM_THE_GLOVE) * along;
            emitRing(quads, apex.add(axis.scale(distance)), axis, distance * tan, (float) along);
        }
        buffers.endBatch(GooRenderTypes.UNMAKE_WAVES_TYPE);
    }

    /**
     * How far down the cone a ring stands, the rings spaced evenly and all
     * travelling outward, each leaving the glove again once it reaches the end.
     *
     * @param ring  the ring's index
     * @param ticks the game time including the partial tick
     * @return the share of the cone's length, 0 at the glove to 1 at its reach
     */
    static double ringAlong(int ring, double ticks) {
        double phase = ticks * RING_SPEED + (double) ring / RINGS;
        return phase - Math.floor(phase);
    }

    /**
     * Emits one ring: a thin band square to the look. Each vertex carries how
     * far down the cone the ring stands in red, and which edge of the band it
     * sits on in green, 0 inner to 1 outer.
     *
     * @param quads  the quad emitter
     * @param center the ring's center, camera relative
     * @param axis   the look's unit vector
     * @param radius the ring's radius
     * @param along  how far down the cone it stands, 0 to 1
     */
    private static void emitRing(FlatQuadContext quads, Vec3 center, Vec3 axis, double radius, float along) {
        Vec3 side = axis.cross(Math.abs(axis.y) < NEAR_VERTICAL ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 up = side.cross(axis);
        double inner = Math.max(0, radius - RING_WIDTH * HALF);
        double outer = radius + RING_WIDTH * HALF;
        int innerColor = ARGB.color(OPAQUE, Math.round(along * OPAQUE), 0, 0);
        int outerColor = ARGB.color(OPAQUE, Math.round(along * OPAQUE), OPAQUE, 0);
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double a0 = TWO_PI * segment / SEGMENTS;
            double a1 = TWO_PI * (segment + 1) / SEGMENTS;
            vertex(quads, onRing(center, side, up, inner, a0), axis, innerColor);
            vertex(quads, onRing(center, side, up, outer, a0), axis, outerColor);
            vertex(quads, onRing(center, side, up, outer, a1), axis, outerColor);
            vertex(quads, onRing(center, side, up, inner, a1), axis, innerColor);
        }
    }

    private static Vec3 onRing(Vec3 center, Vec3 side, Vec3 up, double radius, double angle) {
        return center.add(side.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
    }

    private static void vertex(FlatQuadContext quads, Vec3 at, Vec3 axis, int color) {
        quads.vertex((float) at.x, (float) at.y, (float) at.z, color, (float) axis.x, (float) axis.y,
                (float) axis.z);
    }

    /**
     * The Unmake the local player's glove holds while right click holds it.
     *
     * @param player the local player
     * @return the ability, or null while no Unmake is held
     */
    static @Nullable ClientAbility heldUnmake(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        boolean unmakes = ability != null && ability.behaviors().stream().anyMatch(UnmakeStep.class::isInstance);
        return unmakes && GloveUseTracker.showsArea() ? ability : null;
    }
}
