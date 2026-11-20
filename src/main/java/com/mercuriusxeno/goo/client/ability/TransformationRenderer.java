package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Plays each live model transformation: draws its goo blob hopping and
 * shrinking, and scales the entity it becomes through the render-state
 * modifier, or the block it becomes through that block's renderer, from
 * nothing to full size, so the target is hidden at scale zero yet never culled.
 * Decision model-transformation-is-one-animation.
 */
public final class TransformationRenderer {

    private TransformationRenderer() {
    }

    /**
     * Scales an entity's model by the transformation into it, its shadow
     * with it; an entity no transformation names keeps its size.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void stampTransformation(Entity entity, EntityRenderState state) {
        if (!(state instanceof LivingEntityRenderState living)) {
            return;
        }
        float scale = Transformations.CLIENT.modelScaleOf(entity.getId(), living.isBaby,
                gameTime(Minecraft.getInstance()));
        living.scale *= scale;
        living.shadowRadius *= scale;
    }

    /**
     * The size a block's renderer draws its model at while a blob transforms
     * into it: nothing as the blob lands, full once the blob is gone, and
     * full where no transformation names the block.
     * decision prism-blob-becomes-a-milky-quartz-crystal
     *
     * @param pos the block's position
     * @return 0 to 1
     */
    public static float blockModelScale(BlockPos pos) {
        return Transformations.CLIENT.modelScaleAt(pos, gameTime(Minecraft.getInstance()));
    }

    /**
     * The transformation playing into a block, for the block's renderer to morph
     * the blob into its model.
     * decision prism-is-one-pointed-quartz-column
     *
     * @param pos the block's position
     * @return the transformation, or null where none plays
     */
    public static Transformations.@Nullable Transformation transformationInto(BlockPos pos) {
        return Transformations.CLIENT.intoBlockAt(pos, gameTime(Minecraft.getInstance()));
    }

    /**
     * Drops the finished transformations and hands the shared path one draw
     * per blob still showing, each sorted by where its blob is this frame.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where each blob's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        List<Transformations.Transformation> transformations = Transformations.CLIENT.live(mc.level.getGameTime());
        float gameTime = gameTime(mc);
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        ImmediateBuffers buffers = ImmediateBuffers.shared();
        for (Transformations.Transformation transformation : transformations) {
            float blobScale = transformation.blobScale(gameTime);
            if (blobScale > 0f) {
                Vec3 blob = transformation.blobPosition(gameTime);
                draws.accept(new SortedDraw(blob.distanceToSqr(camera), () -> drawBlob(frame.poseStack(), buffers,
                        blob.subtract(camera), transformation, gameTime, blobScale)));
            }
        }
    }

    private static void drawBlob(PoseStack poseStack, ImmediateBuffers buffers, Vec3 blob,
                                 Transformations.Transformation transformation, float gameTime, float blobScale) {
        poseStack.pushPose();
        poseStack.translate(blob.x, blob.y, blob.z);
        GooFlightRenderer.renderBlob(poseStack, buffers, transformation.gooType(), gameTime, blobScale);
        poseStack.popPose();
        buffers.endBatch();
    }

    private static float gameTime(Minecraft mc) {
        if (mc.level == null) {
            return 0f;
        }
        return mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }
}
