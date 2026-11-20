package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.canister.ICanisterHolder;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Renders an in-world HUD panel when the player's crosshair targets a canister
 * or hub canister. Shows multi-type goo contents (icon + amount per type)
 * and optional label for the targeted slot.
 */
public final class CanisterHudRenderer {

    private static final HudAnimator<Target> ANIMATOR =
            new HudAnimator<>((a, b) -> a.pos.equals(b.pos) && a.slot == b.slot);

    private CanisterHudRenderer() {
    }

    /**
     * Moves the panel's animation on a frame and hands the shared path the
     * canister's panel, sorted by its anchor.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the panel's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        ANIMATOR.tick(CanisterTargetResolver.getTarget());
        Target target = ANIMATOR.tracked();
        if (target == null) {
            return;
        }
        SlotData data = lookupSlotData(target.pos, target.slot);
        // An empty labeled canister still shows its label's type (decision hud-panel-shows-the-label-type).
        if (data == null || data.content.isEmpty() && data.gooLabel() == null) {
            ANIMATOR.clear();
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        Vec3 anchor = new Vec3(target.pos.getX() + target.cx, target.pos.getY() + target.lift,
                target.pos.getZ() + target.cz);
        PanelPlacement placement = PanelPlacement.onFace(anchor, target.hitFace,
                target.hasBlockAbove, ANIMATOR.pitch(), ANIMATOR.opacity());
        draws.accept(new SortedDraw(anchor.distanceToSqr(camera.position()),
                () -> PanelPainter.paint(frame.poseStack(), camera, placement, CanisterPanelRows.rows(data))));
    }

    /**
     * Looks up the goo contents and label at the given position and slot.
     *
     * @param pos  the block position
     * @param slot the slot index
     * @return the slotData, or null if not found
     */
    private static @Nullable SlotData lookupSlotData(BlockPos pos, int slot) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof ICanisterHolder holder
                ? lookupContainerSlotData(holder, slot) : null;
    }

    /**
     * Extracts slot data from a slotted goo container at a specific slot index.
     *
     * @param holder the slotted goo container
     * @param slot   the slot index
     * @return the slot data, or null if the slot index is invalid
     */
    private static @Nullable SlotData lookupContainerSlotData(
            ICanisterHolder holder, int slot) {
        if (slot < 0) {
            return null;
        }
        CanisterMetadata meta = holder.getSlotMetadata(slot);
        return new SlotData(holder.getSlotFluidContent(slot), meta.label(), meta.gooLabel());
    }

    /**
     * Targeted canister slot with XZ center offset, Y lift, hit face, and block-above state.
     */
    record Target(BlockPos pos, int slot, double cx, double cz,
                  double lift, Direction hitFace, boolean hasBlockAbove) {
    }

    /**
     * Fluid content, label and goo label for a targeted slot.
     *
     * @param content  the slot's fluid content
     * @param label    the player-assigned label, or null
     * @param gooLabel the goo type the canister's goo label holds it to, or null
     */
    record SlotData(CanisterFluidContent content, @Nullable String label,
                    @Nullable ResourceKey<GooTypeDefinition> gooLabel) {
    }
}
