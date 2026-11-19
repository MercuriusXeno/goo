package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The glows a renderer emitted in vanilla's main pass, held for
 * {@link TranslucentVisualPath} instead: each entry keeps a copy of the
 * renderer's camera-relative pose, the world point it sorts by, its render
 * type and its geometry, and replays them unchanged once the level is drawn.
 * The pose holds the model transform alone, the level's view living on
 * RenderSystem's model view stack, which the path sets around its draws,
 * so the replay lands where the main pass would have drawn it.
 * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
 * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
 */
public final class GlowQueue {

    private final List<Entry> queued = new ArrayList<>();

    /**
     * Queues a glow.
     *
     * @param pose       a copy of the renderer's camera-relative pose, which the queue keeps
     * @param at         the world point the glow sorts by
     * @param renderType the render type it draws through
     * @param geometry   its vertices about the pose
     */
    public void queue(PoseStack.Pose pose, Vec3 at, RenderType renderType,
                      SubmitNodeCollector.CustomGeometryRenderer geometry) {
        queued.add(new Entry(pose, at, renderType, geometry));
    }

    /**
     * Hands the path one draw per queued glow, each sorted by the camera's
     * distance to its world point, then empties the queue.
     *
     * @param frame the frame being drawn
     * @param draws where each glow's draw goes
     */
    public void drain(LevelFrame frame, Consumer<SortedDraw> draws) {
        Vec3 camera = frame.levelRenderState().cameraRenderState.pos;
        for (Entry entry : queued) {
            draws.accept(new SortedDraw(entry.at().distanceToSqr(camera), () -> draw(entry)));
        }
        queued.clear();
    }

    private static void draw(Entry entry) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        entry.geometry().render(entry.pose(), buffers.getBuffer(entry.renderType()));
        buffers.endBatch(entry.renderType());
    }

    private record Entry(PoseStack.Pose pose, Vec3 at, RenderType renderType,
                         SubmitNodeCollector.CustomGeometryRenderer geometry) {
    }
}
