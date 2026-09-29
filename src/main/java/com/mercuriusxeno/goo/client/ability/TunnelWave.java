package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.ChainFootprint;
import com.mercuriusxeno.goo.ability.program.AreaShape;
import com.mercuriusxeno.goo.ability.program.ProgressiveAreaStep;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;

/**
 * The force wave a tunnel-mining marker's burnout sends into the wall in
 * place of a radial burst (decision elemental-explosion-per-type): a plate
 * the tunnel's cross-section wide drives into the wall one layer per tick
 * from burnout, the pace the layer walk previews at, so it reaches each
 * layer as that layer is previewed and leads its strike by the walk's
 * preview delay. It is drawn through the wall, with depth test off, the way
 * the ghost outline is, and fades in over its first ticks and out over the
 * tunnel's last layers. Each goo type shades the plate with its own
 * fragment shader; the vertex color carries the wave's progress in red,
 * the plate-local position in green and blue, and its strength in alpha.
 */
final class TunnelWave {

    /** Ticks the wave takes to fade in from the wall's face. */
    static final float FADE_IN_TICKS = 2f;
    /** Layers over which the wave fades out as it reaches the tunnel's end. */
    static final float FADE_OUT_LAYERS = 3f;
    /** How far the wall's face sits from the marker's center along the blast: half a block. */
    private static final float WALL_FACE = 0.5f;
    private static final int OPAQUE = 0xFF;

    private TunnelWave() {
    }

    /**
     * Answers whether the burnout's ability mines a tunnel, read off its
     * synced progressive-area step.
     *
     * @param burnout the burnout
     * @return true for a tunnel
     */
    static boolean isTunnel(ChainBurnouts.Burnout burnout) {
        return SyncedSteps.first(burnout.abilityId(), ProgressiveAreaStep.class)
                .map(step -> step.shape() == AreaShape.TUNNEL).orElse(false);
    }

    /**
     * How long the wave runs: one tick per layer of the tunnel, and one to leave the last.
     *
     * @param stacks the marker's stack count
     * @return the wave's duration in ticks
     */
    static int durationTicks(int stacks) {
        return ChainFootprint.tunnelDepth(stacks) + 1;
    }

    /**
     * How far the wave has driven into the wall from the marker's center:
     * the wall's face, then one layer per tick.
     *
     * @param elapsed ticks since burnout, partial tick included
     * @return the plate's distance from the marker's center along the blast, in blocks
     */
    static float depth(float elapsed) {
        return WALL_FACE + Math.max(0f, elapsed);
    }

    /**
     * The wave's strength: fading in over FADE_IN_TICKS, whole down the
     * tunnel, fading out over its last FADE_OUT_LAYERS layers.
     *
     * @param elapsed ticks since burnout, partial tick included
     * @param layers  the tunnel's depth in layers
     * @return the strength in [0, 1]
     */
    static float strength(float elapsed, int layers) {
        float fadeIn = Math.min(1f, Math.max(0f, elapsed / FADE_IN_TICKS));
        float fadeOut = Math.min(1f, Math.max(0f, (layers - elapsed) / FADE_OUT_LAYERS));
        return fadeIn * fadeOut;
    }

    /**
     * Draws the wave plate for a tunnel burnout through the given render type.
     *
     * @param burnout the burnout
     * @param frame   the frame being drawn
     * @param type    the type's tunnel wave render type
     */
    static void render(ChainBurnouts.Burnout burnout, BurnoutFrame frame, RenderType type) {
        float elapsed = frame.gameTime() - burnout.startTick();
        int layers = ChainFootprint.tunnelDepth(burnout.stackCount());
        float strength = strength(elapsed, layers);
        if (strength <= 0f) {
            return;
        }
        Direction face = burnout.placedFace();
        AABB section = ChainFootprint.computeBounds(burnout.stackCount(), false, face);
        float along = depth(elapsed);
        int progressByte = NetherDiscMesh.toByte(Math.min(1f, elapsed / layers));
        int alpha = NetherDiscMesh.toByte(strength);
        BurnoutGeometry.drawAtMarker(frame, burnout.pos(), type, (pose, c) ->
                emitPlate(pose, c, face, section, along, alpha, progressByte));
    }

    /**
     * Emits the plate: the tunnel's cross-section, square to the blast, at
     * the given distance into the wall.
     *
     * @param pose         the pose entry
     * @param c            the vertex consumer
     * @param face         the placed face
     * @param section      the tunnel's bounds, marker-local
     * @param along        the plate's distance from the marker's center along the blast
     * @param alpha        the wave's strength as a byte
     * @param progressByte the wave's progress as a byte
     */
    private static void emitPlate(PoseStack.Pose pose, VertexConsumer c, Direction face, AABB section,
                                  float along, int alpha, int progressByte) {
        Direction.Axis axis = face.getAxis();
        Direction.Axis first = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        Direction.Axis second = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
        float plane = BurnoutGeometry.BLOCK_CENTER - face.getAxisDirection().getStep() * along;
        FlatQuadContext quads = new FlatQuadContext(pose, c);
        int[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int[] corner : corners) {
            float a = (float) (corner[0] == 0 ? section.min(first) : section.max(first));
            float b = (float) (corner[1] == 0 ? section.min(second) : section.max(second));
            int color = ARGB.color(alpha, progressByte, corner[0] * OPAQUE, corner[1] * OPAQUE);
            quads.vertex(coordinate(Direction.Axis.X, axis, first, plane, a, b),
                    coordinate(Direction.Axis.Y, axis, first, plane, a, b),
                    coordinate(Direction.Axis.Z, axis, first, plane, a, b), color,
                    face.getStepX(), face.getStepY(), face.getStepZ());
        }
    }

    /**
     * One coordinate of a plate corner: the plate's plane on the blast axis,
     * the corner's first or second in-plane coordinate on the others.
     *
     * @param of    the axis whose coordinate this is
     * @param blast the blast axis
     * @param first the first in-plane axis
     * @param plane the plate's coordinate on the blast axis
     * @param a     the corner's coordinate on the first in-plane axis
     * @param b     the corner's coordinate on the second in-plane axis
     * @return the coordinate
     */
    private static float coordinate(Direction.Axis of, Direction.Axis blast, Direction.Axis first,
                                    float plane, float a, float b) {
        if (of == blast) {
            return plane;
        }
        return of == first ? a : b;
    }
}
