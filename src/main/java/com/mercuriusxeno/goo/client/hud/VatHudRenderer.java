package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.client.machine.VatStackAggregator;
import com.mercuriusxeno.goo.client.machine.VatStackAggregator.VatStackData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Renders an in-world HUD panel when the player's crosshair targets a vat.
 * Shows aggregated stack contents, upgrade level, gasket info, and label.
 * Panel is placed on the face the player is looking at.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class VatHudRenderer {

    /** Y position for the HUD above the vat block top (full-block height). */
    private static final double BLOCK_TOP = 1.0;
    /** Y position for the HUD below the vat block bottom. */
    private static final double BLOCK_BOTTOM = 0.0;
    /** Mid-block Y for side-face anchoring. */
    private static final double MID_BLOCK = 0.5;
    /** Offset to push the panel to the face surface (half a block). */
    private static final double FACE_OFFSET = 0.5;
    /** Block center offset for centering calculations. */
    private static final double BLOCK_CENTER = 0.5;

    private static final HudAnimator<VatTarget> ANIMATOR =
            new HudAnimator<>((a, b) -> a.pos.equals(b.pos));

    private VatHudRenderer() {
    }

    /**
     * Renders the vat HUD after entities.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        ANIMATOR.tick(getTarget());
        VatTarget target = ANIMATOR.tracked();
        if (target == null) {
            return;
        }
        VatStackData data = lookupVatData(target.pos);
        if (isEmptyVat(data)) {
            ANIMATOR.clear();
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        renderPanel(event.getPoseStack(), camera, data, target);
    }

    /**
     * Returns true if the vat data is absent or has no displayable content.
     *
     * @param data the vat stack data, or null
     * @return true if there is nothing to render
     */
    private static boolean isEmptyVat(@Nullable VatStackData data) {
        return data == null || (data.contents().isEmpty()
                && data.compression() <= 0 && !data.hasLabel());
    }

    /**
     * Returns the targeted vat with hit face, or null if not looking at a vat.
     *
     * @return the target
     */
    private static @Nullable VatTarget getTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.hitResult == null) {
            return null;
        }
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockPos pos = hit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof VatBlockEntity)) {
            return null;
        }
        return resolveVatFace(mc, pos, hit.getDirection());
    }

    /**
     * Resolves a vat target from the hit face direction.
     *
     * @param mc   the Minecraft client instance
     * @param pos  the vat block position
     * @param face the face the player is looking at
     * @return the resolved target
     */
    private static VatTarget resolveVatFace(Minecraft mc, BlockPos pos, Direction face) {
        if (face == Direction.UP) {
            return new VatTarget(pos, Direction.UP, BLOCK_CENTER, BLOCK_CENTER, BLOCK_TOP);
        }
        if (face == Direction.DOWN) {
            return new VatTarget(pos, Direction.DOWN, BLOCK_CENTER, BLOCK_CENTER, BLOCK_BOTTOM);
        }
        Vec3 look = mc.player != null ? mc.player.getLookAngle() : Vec3.ZERO;
        Direction bestFace = InWorldHud.bestPerpendicularFace(look);
        double cx = BLOCK_CENTER + bestFace.getStepX() * FACE_OFFSET;
        double cz = BLOCK_CENTER + bestFace.getStepZ() * FACE_OFFSET;
        return new VatTarget(pos, bestFace, cx, cz, MID_BLOCK);
    }

    /**
     * Looks up aggregated vat stack data at the given position.
     *
     * @param pos the block position
     * @return the vatData, or null if not found
     */
    @Nullable
    private static VatStackData lookupVatData(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        return VatStackAggregator.aggregate(level, pos);
    }

    /**
     * Paints the vat panel on the tracked face of the vat block.
     *
     * @param poseStack the pose stack for rendering
     * @param camera    the render camera
     * @param data      the aggregated stack data
     * @param target    the resolved vat target with anchor offsets
     */
    private static void renderPanel(PoseStack poseStack, Camera camera, VatStackData data,
                                    VatTarget target) {
        Vec3 anchor = new Vec3(target.pos.getX() + target.cx, target.pos.getY() + target.lift,
                target.pos.getZ() + target.cz);
        PanelPlacement placement = PanelPlacement.onFace(anchor, target.face, false, ANIMATOR.pitch());
        PanelPainter.paint(poseStack, camera, placement, VatPanelRows.rows(data));
    }

    /**
     * Target: vat position, face, XZ center offset, and Y lift.
     */
    private record VatTarget(BlockPos pos, Direction face, double cx, double cz, double lift) {
    }
}
