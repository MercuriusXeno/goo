package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.alientech.LightBeaconColumn;
import com.mercuriusxeno.goo.client.GlowQueue;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.ability.GlowBeamMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.function.Consumer;

/**
 * The light beacon's far-seen column: glow's beam of light ({@link GlowBeamMesh}) leaving the beacon along its
 * facing, as far as a vanilla beacon's beam reaches or the first block that stops it, standing still and breathing as
 * it scrolls as a reflector's beam does, and fading with the camera's distance. The beam writes no depth,
 * so it queues for the shared path, which draws it after the clouds (operator ruling 2026-11-18).
 * decision bulb-one-model-max-light-beacon-combo
 * decision every-glow-beam-breathes-in-brightness
 */
public final class BeaconBeams {

    /** How far the beam reaches out of the beacon, in blocks: a vanilla beacon's reach. */
    public static final int BEAM_REACH = BeaconRenderer.MAX_RENDER_Y;
    private static final float HALF = 0.5f;
    private static final float SCROLL_PER_TICK = 0.2f;
    /** The share of the fade-out distance a column stays whole to. */
    static final double FADE_START_SHARE = 0.5;
    /** The power the column's fade falls by past its start: the square, gentle at first and steep at the end. */
    static final double FADE_POWER = 2;
    /** The segments a column draws in, each fading on from the last. */
    static final int FADE_SEGMENTS = 48;
    /** The camera distance past which a beam widens, in blocks, vanilla's beacon threshold. */
    static final float BEAM_WIDEN_DISTANCE = 96f;
    private static final GlowQueue QUEUED = new GlowQueue();

    private BeaconBeams() {
    }

    /**
     * How much a beam widens at a camera distance: not at all within 96 blocks, then in step with the distance, as
     * vanilla's beacon beam widens.
     *
     * @param horizontalDistance the camera's horizontal distance from the beam's foot, in blocks
     * @return the factor the beam's radii take
     */
    public static float radiusScale(float horizontalDistance) {
        return Math.max(1f, horizontalDistance / BEAM_WIDEN_DISTANCE);
    }

    /**
     * How long a beacon's column draws, in blocks from the beacon's center: to the face of the first block that stops
     * it, or its whole reach through open air.
     * decision beacon-column-into-a-reflector-counts-as-max-light
     *
     * @param level  the level
     * @param beacon the beacon's position
     * @param facing the side the column leaves by
     * @return the column's length
     */
    public static float columnLength(Level level, BlockPos beacon, Direction facing) {
        BlockPos stop = LightBeaconColumn.stoppedAt(level, beacon, facing, BEAM_REACH);
        return stop == null ? BEAM_REACH : stop.distManhattan(beacon) - HALF;
    }

    /**
     * The box a beam draws within: its cell, stretched along the facing by the beam's reach, so the beam draws while
     * its block is off screen.
     *
     * @param pos    the block's position
     * @param facing the side the beam leaves by
     * @param reach  how far the beam draws out of the cell, in blocks
     * @return the render bounding box
     */
    public static AABB drawnBounds(BlockPos pos, Direction facing, int reach) {
        return new AABB(pos).expandTowards(facing.getStepX() * reach, facing.getStepY() * reach,
                facing.getStepZ() * reach);
    }

    /**
     * Queues one beacon's column: still, as a reflector's beam is, rather than spinning as a vanilla beacon's does,
     * and fading with the camera's distance.
     * operator ruling 2026-11-28: the beam stands still and fades out toward the render distance
     *
     * @param poseStack     the pose at the beacon's cell corner, camera-relative
     * @param center        the beacon's center in the world, which the column sorts by
     * @param facing        the side the column leaves by
     * @param animationTime the column's scroll and breath clock, in ticks
     * @param radiusScale   the factor the column's radii take, widening it with the camera's distance
     * @param length        how far the column draws from the beacon's center, in blocks
     * @param camera        the camera's position in the world
     * @param fadeOut       the camera distance the column has faded to nothing by, in blocks
     */
    public static void queue(PoseStack poseStack, Vec3 center, Direction facing, float animationTime,
                             float radiusScale, float length, Vec3 camera, double fadeOut) {
        poseStack.pushPose();
        standAtFoot(poseStack, facing);
        float scroll = Mth.frac(-animationTime * SCROLL_PER_TICK);
        float breath = GlowBeamMesh.breath(animationTime);
        float[] stops = fadeStops(center.distanceTo(camera), length, fadeOut);
        float[] shares = distanceShares(stops, center, Vec3.atLowerCornerOf(facing.getUnitVec3i()), camera, fadeOut);
        QUEUED.queue(poseStack.last().copy(), center, GooRenderTypes.glowBeam(BeaconRenderer.BEAM_LOCATION),
                (pose, buffer) -> {
                    for (GlowBeamMesh.Layer layer : GlowBeamMesh.LAYERS) {
                        emitBeaconLayer(pose, buffer, layer, radiusScale, scroll, breath, stops, shares);
                    }
                });
        poseStack.popPose();
    }

    /**
     * The share of its alpha a column keeps at a camera distance: whole out to half the fade-out distance, then
     * falling as the square of what is left of the way, to nothing at the fade-out distance, so it thins gently at
     * first and is gone before its beacon's chunk unloads.
     * operator ruling 2026-11-28: the beam fades by a power law from about half the render distance
     *
     * @param distance the camera's distance from a point of the column, in blocks
     * @param fadeOut  the camera distance the column has faded to nothing by, in blocks
     * @return the alpha share, from 0 to 1
     */
    public static float distanceShare(double distance, double fadeOut) {
        double fadeStart = fadeOut * FADE_START_SHARE;
        if (distance <= fadeStart) {
            return 1f;
        }
        double left = Math.max(0, 1 - (distance - fadeStart) / (fadeOut - fadeStart));
        return (float) Math.pow(left, FADE_POWER);
    }

    /**
     * Where the column's rings stand along it: evenly out to its length, or to as far as any of it can still show,
     * the camera's distance from its foot plus the fade-out distance, if that is nearer.
     *
     * @param footDistance the camera's distance from the column's foot, in blocks
     * @param length       how far the column draws, in blocks
     * @param fadeOut      the camera distance the column has faded to nothing by, in blocks
     * @return the rings' distances along the column, rising from 0
     */
    static float[] fadeStops(double footDistance, float length, double fadeOut) {
        float drawn = (float) Math.min(length, footDistance + fadeOut);
        float[] stops = new float[FADE_SEGMENTS + 1];
        for (int i = 0; i <= FADE_SEGMENTS; i++) {
            stops[i] = drawn * i / FADE_SEGMENTS;
        }
        return stops;
    }

    private static float[] distanceShares(float[] stops, Vec3 foot, Vec3 along, Vec3 camera, double fadeOut) {
        float[] shares = new float[stops.length];
        for (int i = 0; i < stops.length; i++) {
            shares[i] = distanceShare(foot.add(along.scale(stops[i])).distanceTo(camera), fadeOut);
        }
        return shares;
    }

    /**
     * Moves the pose from the cell's corner to the column's foot at the beacon's center, turned so its +y runs
     * along the facing: up from a floor, away from a wall.
     *
     * @param poseStack the pose at the cell's corner
     * @param facing    the side the column leaves by
     */
    static void standAtFoot(PoseStack poseStack, Direction facing) {
        poseStack.translate(HALF, HALF, HALF);
        poseStack.rotate(facing.getRotation());
    }

    /**
     * Hands the shared path one draw for each column queued this frame, each sorted by the camera's distance to its
     * beacon's center, then empties the queue.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where each column's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        QUEUED.drain(frame, draws);
    }

    /**
     * Emits one layer of the column, its alpha and its width both scaled by the breath, so the whole column dims and
     * slims slightly at the ease and never grows past its own width (operator UAT 2026-11-10).
     * decision every-glow-beam-breathes-in-brightness
     *
     * @param pose        the pose at the column's foot, its +y along the column
     * @param buffer      the vertex consumer
     * @param layer       the layer
     * @param radiusScale the factor the layer's radius takes
     * @param scroll      the texture's scroll offset in [0, 1)
     * @param breath      the share of alpha and width {@link GlowBeamMesh#breath} answered
     * @param stops       the rings' distances along the column, rising from 0
     * @param shares      the share of its alpha the layer keeps at each ring, its fade with the camera's distance
     */
    static void emitBeaconLayer(PoseStack.Pose pose, VertexConsumer buffer, GlowBeamMesh.Layer layer,
                                float radiusScale, float scroll, float breath, float[] stops, float[] shares) {
        GlowBeamMesh.emitLayerShaded(pose, buffer, layer.radius() * radiusScale * breath,
                GlowBeamMesh.breathed(layer.color(), breath), scroll, stops, shares);
    }
}
