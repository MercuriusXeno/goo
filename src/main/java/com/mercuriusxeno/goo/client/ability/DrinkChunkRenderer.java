package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Draws every peeling block's chunks as solid boxes of the block's own
 * faces, a standing chunk lit as its block stood and a flying one by the
 * brighter of that and the light at the point, never by the light inside a
 * solid block it passes through, which read black where streams crossed;
 * all in one submission a frame: a cell's face draws only where it
 * bounds its chunk and no standing chunk covers it, so a whole block costs
 * its shell alone, and each face's texture cell was cut once as the block
 * began to peel.
 * decision unmake-peels-the-block-into-chunks-that-stream-to-the-hand
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DrinkChunkRenderer {

    /**
     * Each face's corners as shares of a chunk's edges along x, y and z, by
     * {@link Direction#get3DDataValue()}, wound counter-clockwise seen from outside.
     */
    private static final int[][][] CORNERS = {
        {{1, 0, 0}, {1, 0, 1}, {0, 0, 1}, {0, 0, 0}},
        {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
        {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
        {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
        {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
        {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}},
    };
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;
    private static final Direction[] FACES = Direction.values();
    private static final float HALF = 0.5f;

    private DrinkChunkRenderer() {
    }

    /**
     * Submits every peeling block's chunks this frame.
     *
     * @param event the custom geometry submit event
     */
    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        double now = level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<DrinkPeels.Peel> peels = List.copyOf(DrinkPeels.CLIENT.live(now));
        if (peels.isEmpty()) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), GooSubmitter.cutoutOnBlockAtlas(),
                (pose, consumer) -> {
                    for (DrinkPeels.Peel peel : peels) {
                        drawPeel(new Draw(pose, consumer, camera, level), peel, now);
                    }
                });
    }

    /**
     * What one frame draws chunks into.
     *
     * @param pose     the level's pose, camera relative
     * @param consumer the cutout buffer on the block atlas
     * @param camera   the camera's world position
     * @param level    the client level, for the light where a chunk stands
     */
    private record Draw(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera, ClientLevel level) {
    }

    /**
     * Draws one block's chunks: those standing in its light, showing only
     * their bare faces, and those flying where they fly, whole.
     *
     * @param draw what the frame draws into
     * @param peel the block peeling
     * @param now  the game time, with the partial tick
     */
    private static void drawPeel(Draw draw, DrinkPeels.Peel peel, double now) {
        Vec3 point = DrinkPeels.CLIENT.destinationOf(peel.blob());
        List<DrinkRide.Chunk> chunks = peel.breakup().chunks();
        boolean[] standing = DrinkRide.standingOf(chunks, now);
        int standingLight = LevelRenderer.getLightCoords(draw.level(), peel.pos());
        int flyingLight = point == null ? standingLight : DrinkPeels.brighterOf(standingLight,
                LevelRenderer.getLightCoords(draw.level(), BlockPos.containing(point)));
        Drawn[] drawn = new Drawn[chunks.size()];
        for (int index = 0; index < chunks.size(); index++) {
            DrinkRide.Chunk chunk = chunks.get(index);
            Vec3 at = standing[index] ? chunk.place() : flightOf(chunk, point, now);
            if (at != null) {
                // unmake-converges-near-the-unmade-blocks-into-a-goo-item: a flying chunk tapers into its blob
                double scale = standing[index] || point == null ? 1 : DrinkRide.shrinkOf(chunk, point, now);
                drawn[index] = new Drawn(chunk.place(), at.subtract(draw.camera()), scale,
                        standing[index] ? standingLight : flyingLight);
            }
        }
        drawCells(draw, peel, standing, drawn);
    }

    /**
     * How one chunk draws this frame.
     *
     * @param place  its middle while it stood in the block
     * @param middle its middle now, camera relative
     * @param scale  how large it is, 1 for whole
     * @param light  the packed light it is lit by
     */
    private record Drawn(Vec3 place, Vec3 middle, double scale, int light) {

        /**
         * @param cellPlace a cell's middle while the chunk stood in the block
         * @return that cell's middle now, camera relative
         */
        Vec3 cellAt(Vec3 cellPlace) {
            return middle.add(cellPlace.subtract(place).scale(scale));
        }
    }

    /**
     * @param chunk a chunk that has left its block
     * @param point where its blob stands, or null while it goes unplaced
     * @param now   the game time, with the partial tick
     * @return where its middle flies, or null once it reached the point or while the point goes unplaced
     */
    private static @Nullable Vec3 flightOf(DrinkRide.Chunk chunk, @Nullable Vec3 point, double now) {
        return point == null ? null : DrinkRide.pointOf(chunk, point, now);
    }

    /**
     * Draws every cell of a block whose chunk is drawn this frame.
     *
     * @param draw     what the frame draws into
     * @param peel     the block peeling
     * @param standing by chunk index, whether that chunk still stands
     * @param drawn    by chunk index, how it draws, or null where undrawn
     */
    private static void drawCells(Draw draw, DrinkPeels.Peel peel, boolean[] standing, Drawn[] drawn) {
        for (int cell = 0; cell < DrinkRide.CELLS; cell++) {
            Drawn chunk = drawn[peel.breakup().chunkOfCell()[cell]];
            if (chunk != null) {
                drawCell(draw, peel, cell, standing, chunk.cellAt(peel.breakup().placeOf(cell)), chunk);
            }
        }
    }

    /**
     * Draws one cell's showing faces, where it bounds its chunk.
     *
     * @param draw     what the frame draws into
     * @param peel     the block it peels from
     * @param cell     the cell's grid index
     * @param standing by chunk index, whether that chunk still stands
     * @param middle   its middle, camera relative
     * @param chunk    how its chunk draws
     */
    private static void drawCell(Draw draw, DrinkPeels.Peel peel, int cell, boolean[] standing, Vec3 middle,
                                 Drawn chunk) {
        int light = chunk.light();
        Vec3 size = peel.breakup().cellSize().scale(chunk.scale());
        Vec3 least = middle.subtract(size.scale(HALF));
        for (Direction face : FACES) {
            if (DrinkRide.shows(peel.breakup(), cell, face, standing)) {
                DrinkPeels.FaceLook look = peel.faces()[face.get3DDataValue()];
                SpriteCrop crop = look.at(DrinkRide.faceCellOf(cell, face));
                DrinkRide.FaceLayout layout = DrinkRide.FaceLayout.of(face);
                for (int[] corner : CORNERS[face.get3DDataValue()]) {
                    draw.consumer().addVertex(draw.pose(), (float) (least.x + corner[X] * size.x),
                                    (float) (least.y + corner[Y] * size.y), (float) (least.z + corner[Z] * size.z))
                            .setColor(look.tint())
                            .setUv(crop.u0() + (crop.u1() - crop.u0()) * layout.across(corner, 1),
                                    crop.v0() + (crop.v1() - crop.v0()) * layout.down(corner, 1))
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light)
                            .setNormal(draw.pose(), face.getStepX(), face.getStepY(), face.getStepZ());
                }
            }
        }
    }
}
