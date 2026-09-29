package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.ChainFootprint;
import com.mercuriusxeno.goo.ability.program.AreaShape;
import com.mercuriusxeno.goo.ability.program.ProgressiveAreaStep;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;

/**
 * The force wave a tunnel-mining marker's burnout sends into the wall in
 * place of a radial burst, the operator's settled design (decision
 * elemental-explosion-per-type): a train of thin round shock rings, each as
 * wide as the tunnel, launched from the marker every RING_SPACING ticks
 * through the walk's preview lead and traveling down the tunnel's axis one
 * layer per tick, the pace the layer walk previews at, so every ring reaches
 * each layer ahead of its strike and the wall shows ripples pulsing ahead of
 * the breaking. The rings are drawn through the wall, with depth test off,
 * the way the ghost outline is; each fades in as it leaves the wall's face
 * and out over the tunnel's last layers. Each goo type shades its rings
 * with its own fragment shader; the vertex color carries the wave's
 * progress in red, the vertex's place around the ring in green, its place
 * across the ring's band in blue, and the ring's strength in alpha.
 */
final class TunnelWave {

    /** Ticks between one ring's launch and the next. */
    static final int RING_SPACING = 2;
    /**
     * The tick the last ring launches: the rings launch through the layer
     * walk's preview lead, so every one of them leads the strikes.
     */
    static final int LAST_LAUNCH = 6;
    /** Ticks a ring takes to fade in as it leaves the wall's face. */
    static final float FADE_IN_TICKS = 2f;
    /** Layers over which a ring fades out as it reaches the tunnel's end. */
    static final float FADE_OUT_LAYERS = 3f;
    /** The ring band's inner edge, as a fraction of the ring's radius. */
    static final float BAND_INNER = 0.7f;
    /** How far the wall's face sits from the marker's center along the blast: half a block. */
    private static final float WALL_FACE = 0.5f;
    private static final int RING_SEGMENTS = 40;
    private static final int OPAQUE = 0xFF;
    private static final double HALF = 0.5;
    private static final double TWO_PI = 2 * Math.PI;

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
     * How long the wave runs: until the last ring has traveled the tunnel's
     * length, and one tick to leave it.
     *
     * @param stacks the marker's stack count
     * @return the wave's duration in ticks
     */
    static int durationTicks(int stacks) {
        return LAST_LAUNCH + ChainFootprint.tunnelDepth(stacks) + 1;
    }

    /**
     * How far a ring has traveled into the wall from the marker's center:
     * the wall's face at its launch, then one layer per tick.
     *
     * @param elapsed ticks since burnout, partial tick included
     * @param launch  the tick the ring launched
     * @return the ring's distance from the marker's center along the blast, in blocks
     */
    static float ringDepth(float elapsed, int launch) {
        return WALL_FACE + Math.max(0f, elapsed - launch);
    }

    /**
     * A ring's strength: nothing before its launch, fading in over
     * FADE_IN_TICKS, whole down the tunnel, fading out over its last
     * FADE_OUT_LAYERS layers.
     *
     * @param elapsed ticks since burnout, partial tick included
     * @param launch  the tick the ring launched
     * @param layers  the tunnel's depth in layers
     * @return the strength in [0, 1]
     */
    static float ringStrength(float elapsed, int launch, int layers) {
        float traveled = elapsed - launch;
        float fadeIn = Math.min(1f, Math.max(0f, traveled / FADE_IN_TICKS));
        float fadeOut = Math.min(1f, Math.max(0f, (layers - traveled) / FADE_OUT_LAYERS));
        return fadeIn * fadeOut;
    }

    /**
     * The rings' radius: the tunnel's half-width across the face, so each
     * ring is as wide as the tunnel.
     *
     * @param section the tunnel's bounds, marker-local
     * @param face    the placed face
     * @return the radius in blocks
     */
    static float ringRadius(AABB section, Direction face) {
        double radius = 0;
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != face.getAxis()) {
                radius = Math.max(radius, (section.max(axis) - section.min(axis)) * HALF);
            }
        }
        return (float) radius;
    }

    /**
     * Draws a tunnel burnout's train of rings through the given render type.
     *
     * @param burnout the burnout
     * @param frame   the frame being drawn
     * @param type    the type's tunnel wave render type
     */
    static void render(ChainBurnouts.Burnout burnout, BurnoutFrame frame, RenderType type) {
        float elapsed = frame.gameTime() - burnout.startTick();
        int layers = ChainFootprint.tunnelDepth(burnout.stackCount());
        Direction face = burnout.placedFace();
        float radius = ringRadius(ChainFootprint.computeBounds(burnout.stackCount(), false, face), face);
        int progressByte = NetherDiscMesh.toByte(Math.min(1f, elapsed / durationTicks(burnout.stackCount())));
        BurnoutGeometry.drawAtMarker(frame, burnout.pos(), type, (pose, c) -> {
            for (int launch = 0; launch <= LAST_LAUNCH; launch += RING_SPACING) {
                float strength = ringStrength(elapsed, launch, layers);
                if (strength > 0f) {
                    int alpha = NetherDiscMesh.toByte(strength);
                    BurnoutGeometry.emitAnnulus(pose, c, face, -ringDepth(elapsed, launch), radius * BAND_INNER,
                            radius, RING_SEGMENTS, (angle, outer) -> ARGB.color(alpha, progressByte,
                                    NetherDiscMesh.toByte((float) (angle / TWO_PI)), outer ? OPAQUE : 0));
                }
            }
        });
    }
}
