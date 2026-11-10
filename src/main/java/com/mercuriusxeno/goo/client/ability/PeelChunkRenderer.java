package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import java.util.List;

/**
 * Draws every peeling block's standing chunks as solid boxes of the block's
 * own faces, lit as the block's cell is lit, all in one submission a frame:
 * a cell's face draws only where it bounds its chunk and no standing chunk
 * covers it, so a whole block costs its shell alone, and each face's texture
 * cell was cut once as the block began to peel.
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PeelChunkRenderer {

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

    private PeelChunkRenderer() {
    }

    /**
     * Submits every peeling block's standing chunks this frame.
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
        List<BlockPeels.Peel> peels = List.copyOf(BlockPeels.CLIENT.live(now));
        if (peels.isEmpty()) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), GooSubmitter.cutoutOnBlockAtlas(),
                (pose, consumer) -> {
                    for (BlockPeels.Peel peel : peels) {
                        drawPeel(new Draw(pose, consumer, camera, LevelRenderer.getLightCoords(level, peel.pos())),
                                peel, now);
                    }
                });
    }

    /**
     * What one frame draws a peel's chunks into.
     *
     * @param pose     the level's pose, camera relative
     * @param consumer the cutout buffer on the block atlas
     * @param camera   the camera's world position
     * @param light    the packed light of the peel's cell
     */
    private record Draw(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera, int light) {
    }

    /**
     * Draws every cell of a block whose chunk still stands, showing only its bare faces.
     *
     * @param draw what the frame draws into
     * @param peel the block peeling
     * @param now  the game time, with the partial tick
     */
    private static void drawPeel(Draw draw, BlockPeels.Peel peel, double now) {
        BlockBreakup.Breakup breakup = peel.breakup();
        boolean[] standing = BlockBreakup.standingOf(breakup.chunks(), now);
        for (int cell = 0; cell < BlockBreakup.CELLS; cell++) {
            if (standing[breakup.chunkOfCell()[cell]]) {
                drawCell(draw, peel, cell, standing, breakup.placeOf(cell).subtract(draw.camera()));
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
     */
    private static void drawCell(Draw draw, BlockPeels.Peel peel, int cell, boolean[] standing, Vec3 middle) {
        Vec3 size = peel.breakup().cellSize();
        Vec3 least = middle.subtract(size.scale(HALF));
        for (Direction face : FACES) {
            if (BlockBreakup.shows(peel.breakup(), cell, face, standing)) {
                BlockPeels.FaceLook look = peel.faces()[face.get3DDataValue()];
                SpriteCrop crop = look.at(BlockBreakup.faceCellOf(cell, face));
                BlockBreakup.FaceLayout layout = BlockBreakup.FaceLayout.of(face);
                for (int[] corner : CORNERS[face.get3DDataValue()]) {
                    draw.consumer().addVertex(draw.pose(), (float) (least.x + corner[X] * size.x),
                                    (float) (least.y + corner[Y] * size.y), (float) (least.z + corner[Z] * size.z))
                            .setColor(look.tint())
                            .setUv(crop.u0() + (crop.u1() - crop.u0()) * layout.across(corner, 1),
                                    crop.v0() + (crop.v1() - crop.v0()) * layout.down(corner, 1))
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(draw.light())
                            .setNormal(draw.pose(), face.getStepX(), face.getStepY(), face.getStepZ());
                }
            }
        }
    }
}
