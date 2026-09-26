package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.canister.ICanisterHolder;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Renders an in-world HUD panel when the player's crosshair targets a canister
 * or hub canister. Shows multi-type goo contents (icon + amount per type)
 * and optional label for the targeted slot.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CanisterHudRenderer {

    private static final HudAnimator<Target> ANIMATOR =
            new HudAnimator<>((a, b) -> a.pos.equals(b.pos) && a.slot == b.slot);

    private CanisterHudRenderer() {
    }

    /**
     * Renders the canister HUD after entities.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        ANIMATOR.tick(CanisterTargetResolver.getTarget());
        Target target = ANIMATOR.tracked();
        if (target == null) {
            return;
        }
        SlotData data = lookupSlotData(target.pos, target.slot);
        if (data == null || (data.content.isEmpty() && data.compression <= 0)) {
            ANIMATOR.clear();
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 anchor = new Vec3(target.pos.getX() + target.cx, target.pos.getY() + target.lift,
                target.pos.getZ() + target.cz);
        PanelPlacement placement = PanelPlacement.onFace(anchor, target.hitFace,
                target.hasBlockAbove, ANIMATOR.pitch());
        PanelPainter.paint(event.getPoseStack(), camera, placement, CanisterPanelRows.rows(data));
    }

    /**
     * Looks up the goo contents, label, and compression at the given position and slot.
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
        ItemStack canister = holder.getCanister(slot);
        int compression = GooEnchantments.getCompressionLevel(canister);
        return new SlotData(holder.getSlotFluidContent(slot), meta.label(), compression);
    }

    /**
     * Targeted canister slot with XZ center offset, Y lift, hit face, and block-above state.
     */
    record Target(BlockPos pos, int slot, double cx, double cz,
                  double lift, Direction hitFace, boolean hasBlockAbove) {
    }

    /**
     * Fluid content, label, and compression level for a targeted slot.
     */
    record SlotData(CanisterFluidContent content, @Nullable String label, int compression) {
    }
}
