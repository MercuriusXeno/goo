package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import com.mercuriusxeno.goo.network.PlayerKnowledge;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Renders an in-world HUD panel naming the plexer's target while the crosshair rests
 * anywhere on the plexer (decision plexer-target-shows-in-a-hud-element).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PlexerHudRenderer {

    private static final HudAnimator<BlockPos> ANIMATOR = new HudAnimator<>(BlockPos::equals);
    private static final double BLOCK_CENTER = 0.5;
    /** The block top plus a one-pixel gap under the panel's bottom edge, in blocks. */
    private static final double ABOVE_BLOCK_TOP = 1.0 + 1.0 / 16.0;

    private PlexerHudRenderer() {
    }

    /**
     * Renders the plexer HUD after opaque features are drawn.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        ANIMATOR.tick(targetPos());
        BlockPos pos = ANIMATOR.tracked();
        if (pos == null) {
            return;
        }
        PlexerBlockEntity plexer = lookupPlexer(pos);
        if (plexer == null || plexer.getTargetItem().isEmpty()) {
            ANIMATOR.clear();
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        paintPanel(event.getPoseStack(), camera, pos, plexer.getTargetItem());
    }

    /**
     * @return the position of the plexer holding a target the crosshair rests on, or null
     */
    private static @Nullable BlockPos targetPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        PlexerBlockEntity plexer = lookupPlexer(pos);
        if (plexer == null || plexer.getTargetItem().isEmpty()) {
            return null;
        }
        return pos;
    }

    /**
     * @param pos the block position
     * @return the plexer there, or null
     */
    private static @Nullable PlexerBlockEntity lookupPlexer(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        return level.getBlockEntity(pos, GooBlockEntities.PLEXER.get()).orElse(null);
    }

    /**
     * Paints the target's panel above the plexer's top center.
     *
     * @param poseStack the pose stack for rendering
     * @param camera    the render camera
     * @param pos       the plexer's position
     * @param target    the plexer's target item
     */
    private static void paintPanel(PoseStack poseStack, Camera camera, BlockPos pos, ItemStack target) {
        Vec3 anchor = new Vec3(pos.getX() + BLOCK_CENTER, pos.getY() + ABOVE_BLOCK_TOP, pos.getZ() + BLOCK_CENTER);
        PanelPainter.paint(poseStack, camera, PanelPlacement.onRim(anchor, ANIMATOR.pitch(), ANIMATOR.opacity()),
                PlexerPanelRows.rows(target.getHoverName().getString(),
                        ItemParticleIcons.of(PlayerKnowledge.idOf(target.getItem()))));
    }
}
