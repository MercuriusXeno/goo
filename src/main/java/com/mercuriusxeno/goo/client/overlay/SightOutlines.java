package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.ShiftStep;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Outlines every fungus block within Fungal Shift's reach through walls,
 * in shroom's color, while the local player holds fungal sight. The blocks
 * are scanned again once a second rather than every frame, and the nearest
 * are drawn up to a cap.
 * sight-lengthens-shift-and-outlines-fungus
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SightOutlines {

    private static final String FUNGAL_SHIFT = "goo:shroom_fungal_shift";
    /** Fungal Shift's range when the player holds no synced copy of it. */
    private static final double FALLBACK_RANGE = 16;
    private static final int RESCAN_TICKS = 20;
    private static final int MOST_OUTLINES = 256;

    private static final List<BlockPos> outlined = new ArrayList<>();
    private static long scannedAt = Long.MIN_VALUE;

    private SightOutlines() {
    }

    /**
     * Draws the outlines after the level while the local player's sight stands.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        if (!player.getData(GooAttachments.SIGHT).standsAt(now)) {
            outlined.clear();
            return;
        }
        rescanEverySecond(mc.level, player, now);
        Camera camera = mc.gameRenderer.getMainCamera();
        int rgb = ClientGooTypes.edge(GooTypes.SHROOM);
        for (BlockPos pos : outlined) {
            VoxelHighlightRenderer.renderOutlineThroughWalls(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                    camera, pos, rgb);
        }
    }

    private static void rescanEverySecond(Level level, LocalPlayer player, long now) {
        if (now - scannedAt >= RESCAN_TICKS || now < scannedAt) {
            rescan(level, player.blockPosition(), ShiftStep.reachOf(player, shiftRange()));
            scannedAt = now;
        }
    }

    private static double shiftRange() {
        ClientAbility shift = AbilitySyncHandler.findAbility(FUNGAL_SHIFT);
        return shift == null ? FALLBACK_RANGE : ShiftStep.fungusRange(shift.behaviors()).orElse(FALLBACK_RANGE);
    }

    /**
     * Collects the nearest fungus blocks within the reach of the player.
     *
     * @param level  the client level
     * @param center the player's block
     * @param reach  the reach in blocks
     */
    private static void rescan(Level level, BlockPos center, double reach) {
        outlined.clear();
        int radius = (int) Math.ceil(reach);
        Vec3 middle = Vec3.atCenterOf(center);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (Vec3.atCenterOf(pos).distanceTo(middle) <= reach && level.getBlockState(pos).is(ShiftStep.FUNGUS)) {
                outlined.add(pos.immutable());
            }
        }
        outlined.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(middle)));
        if (outlined.size() > MOST_OUTLINES) {
            outlined.subList(MOST_OUTLINES, outlined.size()).clear();
        }
    }
}
