package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapDripGrade;
import com.mercuriusxeno.goo.block.tap.TapHitRegion;
import com.mercuriusxeno.goo.block.tap.TapValve;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * Renders a one-row panel above a tap's valve while the crosshair targets
 * the valve, reading the rate its valve runs at, or off
 * (decision valve-panel-reads-rate). The canister panel above the tap's
 * canister draws on its own.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class TapValveHudRenderer {

    private static final HudAnimator<BlockPos> ANIMATOR = new HudAnimator<>(BlockPos::equals);

    private TapValveHudRenderer() {
    }

    /**
     * Renders the valve panel after opaque features are drawn.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        Minecraft mc = Minecraft.getInstance();
        ANIMATOR.tick(targetedTap(mc));
        BlockPos pos = ANIMATOR.tracked();
        if (pos == null || mc.level == null) {
            return;
        }
        BlockState state = mc.level.getBlockState(pos);
        if (!(mc.level.getBlockEntity(pos) instanceof TapBlockEntity tap) || !state.hasProperty(TapBlock.OPEN)) {
            ANIMATOR.clear();
            return;
        }
        Optional<TapDripGrade> grade = state.getValue(TapBlock.OPEN) ? Optional.of(tap.dripGrade()) : Optional.empty();
        Direction facing = state.getValue(TapBlock.FACING);
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 anchor = TapValve.panelAnchor(pos, facing, camera.position(), !tap.getCanister().isEmpty());
        PanelPlacement placement = PanelPlacement.onFace(anchor, Direction.UP, false, ANIMATOR.pitch());
        PanelPainter.paint(event.getPoseStack(), camera, placement, TapPanelRows.rows(grade));
    }

    /**
     * @param mc the Minecraft client
     * @return the position of the tap whose valve the crosshair targets, or null
     */
    private static @Nullable BlockPos targetedTap(Minecraft mc) {
        if (mc.level == null || mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof TapBlock)) {
            return null;
        }
        return TapHitRegion.of(hit, pos, state.getValue(TapBlock.FACING), false) == TapHitRegion.VALVE ? pos : null;
    }
}
