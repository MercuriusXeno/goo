package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Plays each live model transformation: draws its goo blob hopping and
 * shrinking, and scales the entity it becomes through the render-state
 * modifier, or the block it becomes through that block's renderer, from
 * nothing to full size, so the target is hidden at scale zero yet never culled.
 * Decision model-transformation-is-one-animation.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
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
        float scale = Transformations.CLIENT.modelScaleOf(entity.getId(), gameTime(Minecraft.getInstance()));
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
     * Draws the blobs of the live transformations.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        List<Transformations.Transformation> transformations = Transformations.CLIENT.live(mc.level.getGameTime());
        if (transformations.isEmpty()) {
            return;
        }
        float gameTime = gameTime(mc);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        for (Transformations.Transformation transformation : transformations) {
            float blobScale = transformation.blobScale(gameTime);
            if (blobScale <= 0f) {
                continue;
            }
            Vec3 blob = transformation.blobPosition(gameTime).subtract(camera);
            poseStack.pushPose();
            poseStack.translate(blob.x, blob.y, blob.z);
            GooFlightRenderer.renderBlob(poseStack, buffers, transformation.gooType(), gameTime, blobScale);
            poseStack.popPose();
        }
    }

    private static float gameTime(Minecraft mc) {
        if (mc.level == null) {
            return 0f;
        }
        return mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }
}
