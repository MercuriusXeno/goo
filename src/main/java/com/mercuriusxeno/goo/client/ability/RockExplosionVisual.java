package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;

/**
 * Rock goo's burnout explosion, the design the operator settled (decision
 * elemental-explosion-per-type): a dust shock disc. A flat disc in the
 * placed face's plane spreads out from the marker to 3 blocks over 14
 * ticks on an ease-out. Its fragment shader ({@code rock_explosion.fsh})
 * draws billowing dust from animated noise, C2A868 with EAD090 highlights,
 * alpha blended so the dust hides what is behind it, thinning to nothing at
 * the edge and over time. A thin bright sonic ring pulses out across the
 * disc once over the first half. The
 * vertex color carries progress in red and the disc-local position in green
 * and blue, since a core pipeline takes no per-draw uniforms.
 *
 * The same disc is Flatten's cursor while its hold runs, looping the dust's
 * early drift so it never thins out
 * (decision flatten-disc-cursor-breaks-above-the-plane).
 */
public final class RockExplosionVisual implements BurnoutVisual {

    /** The one instance the burnout registry holds. */
    public static final RockExplosionVisual INSTANCE = new RockExplosionVisual();

    /** Ticks the explosion plays. */
    static final int DURATION_TICKS = 14;
    /** The disc's full radius in blocks. */
    static final float DISC_REACH = 3f;
    /**
     * The share of the explosion the sonic ring takes to cross the disc;
     * must match {@code SONIC_SPAN} in {@code rock_explosion.fsh}.
     */
    static final float SONIC_SPAN = 0.5f;
    /** How far the disc sits from the block center along the face's step: just off the face plane. */
    private static final float DISC_LIFT = -0.47f;
    private static final int OPAQUE = 0xFF;
    private static final int DISC_SEGMENTS = 48;
    /** The cursor disc's radius in blocks, about one block face. */
    static final float CURSOR_RADIUS = 0.75f;
    /** The share of the explosion the cursor loops over, short of where the dust starts thinning. */
    static final float CURSOR_SPAN = 0.4f;
    /** Maps a disc-local coordinate in [-1, 1] onto [0, 1] for a color byte. */
    private static final float SIGNED_TO_UNIT = 0.5f;

    private RockExplosionVisual() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.ROCK;
    }

    @Override
    public int durationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void render(ChainBurnouts.Burnout burnout, BurnoutFrame frame) {
        float progress = burnout.progress(frame.gameTime());
        drawDisc(frame, burnout.pos(), burnout.placedFace(), progress, discRadius(progress));
    }

    /**
     * Draws the dust disc as Flatten's cursor on the face the cursor rests on.
     *
     * @param frame the frame being drawn
     * @param cell  the cell in front of the aimed face
     * @param face  the aimed face
     */
    public void renderCursor(BurnoutFrame frame, BlockPos cell, Direction face) {
        drawDisc(frame, cell, face, cursorProgress(frame.gameTime()), CURSOR_RADIUS);
    }

    /**
     * The progress the cursor disc shows: the explosion's opening share, looped.
     *
     * @param gameTime the level's game time including the partial tick
     * @return the progress in [0, CURSOR_SPAN)
     */
    static float cursorProgress(float gameTime) {
        return gameTime % DURATION_TICKS / DURATION_TICKS * CURSOR_SPAN;
    }

    /**
     * Draws the dust disc in front of a face.
     *
     * @param frame    the frame being drawn
     * @param cell     the cell the disc is drawn about
     * @param face     the face the disc lies flat against
     * @param progress the disc's progress in [0, 1]
     * @param radius   the disc's radius in blocks
     */
    private static void drawDisc(BurnoutFrame frame, BlockPos cell, Direction face, float progress, float radius) {
        int progressByte = NetherDiscMesh.toByte(progress);
        int center = ARGB.color(OPAQUE, progressByte, NetherDiscMesh.toByte(SIGNED_TO_UNIT),
                NetherDiscMesh.toByte(SIGNED_TO_UNIT));
        BurnoutGeometry.drawAtMarker(frame, cell, GooRenderTypes.ROCK_EXPLOSION_TYPE, (pose, c) ->
                BurnoutGeometry.emitAnnulus(pose, c, face, DISC_LIFT, 0f, radius, DISC_SEGMENTS,
                        (angle, outer) -> outer ? edgeColor(progressByte, angle) : center));
    }

    /**
     * The dust disc's radius: an ease-out spread to its full reach.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the disc's radius in blocks
     */
    static float discRadius(float progress) {
        return DISC_REACH * BurnoutGeometry.easeOutCubic(progress);
    }

    /**
     * Where the sonic ring stands across the disc, as a fraction of its
     * radius: it crosses once over the first SONIC_SPAN of the explosion,
     * then rests at the edge, faded.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the ring's radial position in [0, 1]
     */
    static float sonicRingRadial(float progress) {
        return Math.min(1f, progress / SONIC_SPAN);
    }

    /**
     * The color of a vertex on the disc's edge: progress, and its disc-local position.
     *
     * @param progressByte the explosion's progress as a byte
     * @param angle        the vertex's angle about the face axis
     * @return the packed color
     */
    private static int edgeColor(int progressByte, double angle) {
        int u = NetherDiscMesh.toByte(((float) Math.cos(angle) + 1f) * SIGNED_TO_UNIT);
        int v = NetherDiscMesh.toByte(((float) Math.sin(angle) + 1f) * SIGNED_TO_UNIT);
        return ARGB.color(OPAQUE, progressByte, u, v);
    }
}
