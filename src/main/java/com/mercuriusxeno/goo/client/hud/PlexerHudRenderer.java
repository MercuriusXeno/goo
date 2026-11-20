package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.plexer.PlexerBlock;
import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import com.mercuriusxeno.goo.client.ClientGooValues;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.network.PlayerKnowledge;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Renders an in-world HUD panel on the plexer's front reading what one operation on its target
 * costs while the crosshair rests anywhere on the plexer (decision plexer-target-shows-in-a-hud-element).
 */
public final class PlexerHudRenderer {

    private static final HudAnimator<BlockPos> ANIMATOR = new HudAnimator<>(BlockPos::equals);

    private PlexerHudRenderer() {
    }

    /**
     * Moves the panel's animation on a frame and hands the shared path the
     * plexer's panel, sorted by its anchor in the plexer's mouth.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision plexer-panel-floats-in-the-mouth
     *
     * @param frame the frame being drawn
     * @param draws where the panel's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        ANIMATOR.tick(PlexerPanelTarget.trackedPos(hitPos(), PlexerHudRenderer::holdsTarget));
        BlockPos pos = ANIMATOR.tracked();
        if (pos == null) {
            return;
        }
        PlexerBlockEntity plexer = lookupPlexer(pos);
        if (plexer == null || plexer.getTargetItem().isEmpty()) {
            ANIMATOR.clear();
            return;
        }
        List<PanelRow> rows = PlexerPanelRows.rows(
                ClientGooValues.current().lookup(PlayerKnowledge.idOf(plexer.getTargetItem().getItem())));
        if (rows.isEmpty()) {
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        PanelPlacement placement = placementOf(pos, plexer.getBlockState().getValue(PlexerBlock.FACING), rows);
        draws.accept(new SortedDraw(placement.anchor().distanceToSqr(camera.position()),
                () -> PanelPainter.paint(frame.poseStack(), camera, placement, rows, PlexerPanelTarget.MOUTH_HEIGHT)));
    }

    /**
     * @return the block the crosshair rests on, or null
     */
    private static @Nullable BlockPos hitPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return ((BlockHitResult) mc.hitResult).getBlockPos();
    }

    /**
     * @param pos the block position
     * @return whether a plexer stands there holding a target
     */
    private static boolean holdsTarget(BlockPos pos) {
        PlexerBlockEntity plexer = lookupPlexer(pos);
        return plexer != null && !plexer.getTargetItem().isEmpty();
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
     * Where the target's cost lies flat on the plexer's front, floating in the mouth under the cutaway.
     * decision plexer-panel-floats-in-the-mouth
     * decision plexer-hud-shows-the-cost-alone
     *
     * @param pos    the plexer's position
     * @param facing the plexer's FACING
     * @param rows   the panel's rows
     * @return the panel's placement
     */
    private static PanelPlacement placementOf(BlockPos pos, Direction facing, List<PanelRow> rows) {
        double laidHeight = PanelPainter.measure(rows, Minecraft.getInstance().font::width).height()
                * InWorldHud.PIXEL_SCALE;
        return PlexerPanelTarget.placement(pos, facing, laidHeight, ANIMATOR.pitch(), ANIMATOR.opacity());
    }
}
