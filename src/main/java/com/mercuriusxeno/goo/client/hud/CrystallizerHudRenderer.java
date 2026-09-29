package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlock;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
 * Renders an in-world HUD panel when the player's crosshair lands on the crystal
 * growing on a crystallizer, growing or mature (decision crystal-hud-shows-on-crystal-look).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CrystallizerHudRenderer {

    private static final HudAnimator<BlockPos> ANIMATOR = new HudAnimator<>(BlockPos::equals);
    private static final int HEADER_COLOR = 0xFFE0C8FF;
    private static final String CRYSTALLIZER_NAME_KEY = "block.goo.crystallizer";
    private static final double BLOCK_CENTER = 0.5;
    private static final double BLOCK_TOP = 1.0;

    private CrystallizerHudRenderer() {
    }

    /**
     * Renders the crystal HUD after opaque features are drawn.
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
        CrystallizerBlockEntity crystallizer = lookupCrystallizer(pos);
        if (crystallizer == null) {
            ANIMATOR.clear();
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        paintPanel(event.getPoseStack(), camera, pos);
    }

    /**
     * @return the position of the crystallizer whose crystal the crosshair lands on, or null
     */
    private static @Nullable BlockPos targetPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        CrystallizerBlockEntity crystallizer = lookupCrystallizer(hit.getBlockPos());
        if (crystallizer == null) {
            return null;
        }
        Direction facing = crystallizer.getBlockState().getValue(CrystallizerBlock.FACING);
        return CrystallizerHudTarget.crystalTarget(hit, facing, crystallizer.crystallized());
    }

    /**
     * @param pos the block position
     * @return the crystallizer there, or null
     */
    private static @Nullable CrystallizerBlockEntity lookupCrystallizer(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        return level.getBlockEntity(pos, GooBlockEntities.CRYSTALLIZER.get()).orElse(null);
    }

    /**
     * Paints the crystal panel over the crystallizer's top, a header naming the crystallizer.
     *
     * @param poseStack the pose stack for rendering
     * @param camera    the render camera
     * @param pos       the crystallizer's position
     */
    private static void paintPanel(PoseStack poseStack, Camera camera, BlockPos pos) {
        Vec3 anchor = new Vec3(pos.getX() + BLOCK_CENTER, pos.getY() + BLOCK_TOP, pos.getZ() + BLOCK_CENTER);
        PanelPlacement placement = PanelPlacement.onFace(anchor, Direction.UP, false,
                ANIMATOR.pitch(), ANIMATOR.opacity());
        List<PanelRow> rows = List.of(PanelRow.header(
                Component.translatable(CRYSTALLIZER_NAME_KEY).getString(), HEADER_COLOR));
        PanelPainter.paint(poseStack, camera, placement, rows);
    }
}
