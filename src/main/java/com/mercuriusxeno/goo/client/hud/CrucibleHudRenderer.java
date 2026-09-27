package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Renders a compact in-world HUD panel when the player's crosshair targets
 * a crucible's basin. The panel sits on the basin rim at the point farthest
 * from the player and billboards to face the camera. Each goo type shows as
 * an icon with "reservoir / total" volumes.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CrucibleHudRenderer {

    private static final HudAnimator<BlockPos> ANIMATOR = new HudAnimator<>(BlockPos::equals);

    private CrucibleHudRenderer() {}

    /**
     * Renders the crucible HUD after entities are drawn.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        ANIMATOR.tick(getTargetPos());
        BlockPos pos = ANIMATOR.tracked();
        if (pos == null) {
            return;
        }
        CrucibleBlockEntity be = lookupCrucible(pos);
        if (be == null) {
            ANIMATOR.clear();
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        renderRimPanel(event.getPoseStack(), be, camera);
    }

    /**
     * Returns the block position of the targeted crucible basin, or null when
     * the crosshair misses a crucible or rests on its fuel-rod area.
     *
     * @return the targetPos
     */
    private static @Nullable BlockPos getTargetPos() {
        Minecraft mc = Minecraft.getInstance();
        BlockHitResult hit = getBlockHitResult(mc);
        if (hit == null) { return null; }
        if (mc.level.getBlockEntity(hit.getBlockPos(), GooBlockEntities.CRUCIBLE.get()).isEmpty()) { return null; }
        return CrucibleHudTarget.basinTarget(hit);
    }

    /**
     * Returns the current block hit result, or null if the crosshair is not targeting a block.
     *
     * @param mc the Minecraft client instance
     * @return the block hit result, or null
     */
    private static @Nullable BlockHitResult getBlockHitResult(Minecraft mc) {
        if (mc.level == null || mc.hitResult == null) { return null; }
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) { return null; }
        return (BlockHitResult) mc.hitResult;
    }

    /**
     * Looks up the CrucibleBlockEntity at the given position, or null.
     *
     * @param pos the block position
     * @return the crucible, or null if not found
     */
    private static @Nullable CrucibleBlockEntity lookupCrucible(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level == null) { return null; }
        return level.getBlockEntity(pos, GooBlockEntities.CRUCIBLE.get()).orElse(null);
    }

    /**
     * Paints the crucible panel on the basin rim point chosen for the camera,
     * billboarded with the smoothed emerge pitch.
     *
     * @param poseStack the pose stack for rendering
     * @param be        the crucible block entity
     * @param camera    the render camera
     */
    private static void renderRimPanel(PoseStack poseStack, CrucibleBlockEntity be, Camera camera) {
        List<PanelRow> rows = CruciblePanelRows.rows(be);
        if (rows.isEmpty()) {
            return;
        }
        Vec3 anchor = CrucibleRimMath.rimAnchor(be.getBlockPos(), camera);
        PanelPainter.paint(poseStack, camera, PanelPlacement.onRim(anchor, ANIMATOR.pitch(), ANIMATOR.opacity()), rows);
    }
}
