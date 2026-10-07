package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.PetrifyStep;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mojang.blaze3d.vertex.PoseStack;
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
 * Petrify's fog: while right click holds Petrify, undulating waves of dust
 * fog wash forward over everything in its cone, a medusa's gaze miasma. The
 * cone is drawn as stacked cross-sections square to the look, each filled by
 * {@code petrify_fog.fsh}; the cone itself, the area of effect, is never
 * outlined (decision petrify-stone-encasement-and-calcify-map).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PetrifyFog {

    /** Cross-sections stacked along the cone. */
    static final int SECTIONS = 12;
    /** Where the first cross-section stands, in blocks past the eye, clear of the view. */
    static final double NEAR = 0.6;
    private static final int SEGMENTS = 32;
    private static final int OPAQUE = 0xFF;
    private static final float SIGNED_TO_UNIT = 0.5f;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double HALF = 0.5;
    /** A look this close to straight up or down crosses x rather than up to find its sides. */
    private static final double NEAR_VERTICAL = 0.99;

    private PetrifyFog() {
    }

    /**
     * Draws the fog after the translucent blocks while Petrify is held.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientAbility petrify = player == null ? null : heldPetrify(player);
        if (petrify == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        Vec3 apex = player.getEyePosition(partialTick).subtract(camera);
        Vec3 axis = player.getViewVector(partialTick);
        double range = petrify.delivery().range();
        double halfAngle = Math.toRadians(petrify.delivery().coneDegrees() * HALF);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        FlatQuadContext quads = new FlatQuadContext(poseStack.last(), buffers.getBuffer(GooRenderTypes.PETRIFY_FOG_TYPE));
        for (int section = 0; section < SECTIONS; section++) {
            double distance = sectionDistance(section, range);
            emitSection(quads, apex.add(axis.scale(distance)), axis, distance * Math.tan(halfAngle),
                    (float) (distance / range));
        }
        buffers.endBatch(GooRenderTypes.PETRIFY_FOG_TYPE);
    }

    /**
     * How far past the eye a cross-section stands: the sections spread evenly
     * from just clear of the view to the cone's reach.
     *
     * @param section the section's index
     * @param range   the cone's reach
     * @return the section's distance in blocks
     */
    static double sectionDistance(int section, double range) {
        return NEAR + (range - NEAR) * (section + HALF) / SECTIONS;
    }

    /**
     * The Petrify the local player's glove holds while right click holds it.
     *
     * @param player the local player
     * @return the ability, or null while no Petrify is held
     */
    private static @Nullable ClientAbility heldPetrify(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        boolean petrifies = ability != null && ability.behaviors().stream().anyMatch(PetrifyStep.class::isInstance);
        return petrifies && GloveUseTracker.showsArea() ? ability : null;
    }

    /**
     * Emits one cross-section: a disc square to the axis, each vertex's color
     * carrying how far along the cone it stands and where on the disc it sits.
     *
     * @param quads  the quad emitter
     * @param center the section's center, camera relative
     * @param axis   the cone's unit axis
     * @param radius the section's radius
     * @param along  how far along the cone the section stands, 0 to 1
     */
    private static void emitSection(FlatQuadContext quads, Vec3 center, Vec3 axis, double radius, float along) {
        Vec3 side = axis.cross(Math.abs(axis.y) < NEAR_VERTICAL ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 up = side.cross(axis);
        int middle = sectionColor(along, 0, 0);
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double a0 = TWO_PI * segment / SEGMENTS;
            double a1 = TWO_PI * (segment + 1) / SEGMENTS;
            vertex(quads, center, axis, middle);
            rim(quads, center, side, up, axis, radius, a0, along);
            rim(quads, center, side, up, axis, radius, a1, along);
            vertex(quads, center, axis, middle);
        }
    }

    private static void rim(FlatQuadContext quads, Vec3 center, Vec3 side, Vec3 up, Vec3 axis, double radius,
                            double angle, float along) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Vec3 at = center.add(side.scale(cos * radius)).add(up.scale(sin * radius));
        vertex(quads, at, axis, sectionColor(along, (float) cos, (float) sin));
    }

    private static void vertex(FlatQuadContext quads, Vec3 at, Vec3 axis, int color) {
        quads.vertex((float) at.x, (float) at.y, (float) at.z, color, (float) axis.x, (float) axis.y,
                (float) axis.z);
    }

    /**
     * A vertex's color: how far along the cone in red, its place on the disc
     * in green and blue.
     *
     * @param along how far along the cone, 0 to 1
     * @param u     the disc-local x, -1 to 1
     * @param v     the disc-local y, -1 to 1
     * @return the packed color
     */
    static int sectionColor(float along, float u, float v) {
        return ARGB.color(OPAQUE, NetherDiscMesh.toByte(along), NetherDiscMesh.toByte((u + 1f) * SIGNED_TO_UNIT),
                NetherDiscMesh.toByte((v + 1f) * SIGNED_TO_UNIT));
    }
}
