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
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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
 * in shroom's color, while the local player holds fungal sight. The lines
 * draw on the main target at the opaque-features stage, as the held dome's
 * through-blocks pass does, so the world never hides them. The blocks are
 * scanned again once a second, chunk section by chunk section, skipping any
 * section whose palette holds no fungus, and the nearest are drawn up to a cap.
 * sight-lengthens-shift-and-outlines-fungus
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SightOutlines {

    private static final String FUNGAL_SHIFT = "goo:shroom_fungal_shift";
    /** Fungal Shift's range when the player holds no synced copy of it. */
    private static final double FALLBACK_RANGE = 64;
    private static final int RESCAN_TICKS = 20;
    private static final int MOST_OUTLINES = 512;
    private static final int SECTION_SIZE = LevelChunkSection.SECTION_WIDTH;

    private static final List<BlockPos> outlined = new ArrayList<>();
    private static long scannedAt = Long.MIN_VALUE;

    private SightOutlines() {
    }

    /**
     * Draws the outlines once the opaque world has drawn, while the local
     * player's sight stands.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
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
            rescan(level, player.getEyePosition(), ShiftStep.reachOf(player, shiftRange()));
            scannedAt = now;
        }
    }

    private static double shiftRange() {
        ClientAbility shift = AbilitySyncHandler.findAbility(FUNGAL_SHIFT);
        return shift == null ? FALLBACK_RANGE : ShiftStep.fungusRange(shift.behaviors()).orElse(FALLBACK_RANGE);
    }

    /**
     * Collects the nearest fungus blocks within the reach of the eye, reading
     * only the chunk sections whose palette may hold fungus.
     *
     * @param level the client level
     * @param eye   the player's eye
     * @param reach the reach in blocks
     */
    private static void rescan(Level level, Vec3 eye, double reach) {
        outlined.clear();
        SectionPos low = SectionPos.of(BlockPos.containing(eye.subtract(reach, reach, reach)));
        SectionPos high = SectionPos.of(BlockPos.containing(eye.add(reach, reach, reach)));
        for (int sx = low.x(); sx <= high.x(); sx++) {
            for (int sz = low.z(); sz <= high.z(); sz++) {
                ChunkAccess chunk = level.getChunk(sx, sz, ChunkStatus.FULL, false);
                if (chunk != null) {
                    scanChunk(chunk, low.y(), high.y(), eye, reach);
                }
            }
        }
        outlined.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(eye)));
        if (outlined.size() > MOST_OUTLINES) {
            outlined.subList(MOST_OUTLINES, outlined.size()).clear();
        }
    }

    private static void scanChunk(ChunkAccess chunk, int lowY, int highY, Vec3 eye, double reach) {
        for (int sy = Math.max(lowY, chunk.getMinSectionY()); sy <= Math.min(highY, chunk.getMaxSectionY()); sy++) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
            if (section.hasOnlyAir() || !section.getStates().maybeHas(state -> state.is(ShiftStep.FUNGUS))) {
                continue;
            }
            int baseX = chunk.getPos().getMinBlockX();
            int baseY = SectionPos.sectionToBlockCoord(sy);
            int baseZ = chunk.getPos().getMinBlockZ();
            scanSection(section, new BlockPos(baseX, baseY, baseZ), eye, reach);
        }
    }

    private static void scanSection(LevelChunkSection section, BlockPos base, Vec3 eye, double reach) {
        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    if (section.getBlockState(x, y, z).is(ShiftStep.FUNGUS)) {
                        BlockPos pos = base.offset(x, y, z);
                        if (Vec3.atCenterOf(pos).distanceTo(eye) <= reach) {
                            outlined.add(pos);
                        }
                    }
                }
            }
        }
    }
}
