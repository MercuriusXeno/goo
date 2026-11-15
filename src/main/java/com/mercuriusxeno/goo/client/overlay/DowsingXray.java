package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.CellSurface;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shows the ore each Dowsing cell near the eye lenses: every client tick it
 * finds the Dowsing cells in the loaded chunks around the eye and asks each
 * lens which ore it shows, so the sight follows the camera, the cells'
 * layers and the blocks behind them within a tick, with no payload from the
 * server. Each shown ore draws through walls as itself, a little
 * translucent and fully lit, in a starlight glow, through the pipelines
 * Sight shows fungus by.
 * astral-world-goo-is-dowsing-an-x-ray-of-ore
 * dowsing-shows-ore-one-block-deep-per-layer
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DowsingXray {

    /** How far from the eye, in blocks on each axis, a Dowsing cell lenses ore. */
    private static final int LENS_REACH = 24;
    private static final int SECTION_SIZE = LevelChunkSection.SECTION_WIDTH;
    /** The ore's own color, a little translucent. */
    private static final int ORE_RGB = 0xFFFFFF;
    private static final float ORE_ALPHA = 0.8f;
    /** The glow's Astral starlight, its bright. */
    private static final int GLOW_RGB = 0xFFF4B0;
    private static final float GLOW_ALPHA = 0.3f;
    /** How far the glow swells past the block. */
    private static final float GLOW_SWELL = 1.1f;
    private static final int OPAQUE = 255;

    /** The ore the Dowsing cells showed at the last tick. */
    private static List<BlockPos> shown = List.of();

    private DowsingXray() {
    }

    /**
     * Asks every Dowsing cell near the eye which ore it shows.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            shown = List.of();
            return;
        }
        shown = List.copyOf(oreShownAround(mc.level, mc.gameRenderer.getMainCamera().position()));
    }

    /**
     * Draws the shown ore through walls once the world has drawn.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        List<BlockPos> showing = shown;
        if (mc.level == null || showing.isEmpty()) {
            return;
        }
        Identifier atlas = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location();
        drawPass(mc, event.getPoseStack(), GooRenderTypes.fungusXray(atlas), showing, color(ORE_RGB, ORE_ALPHA), 1f);
        drawPass(mc, event.getPoseStack(), GooRenderTypes.fungusGlow(atlas), showing, color(GLOW_RGB, GLOW_ALPHA),
                GLOW_SWELL);
    }

    private static void drawPass(Minecraft mc, PoseStack poseStack, RenderType pass, List<BlockPos> showing,
                                 int color, float swell) {
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        VertexConsumer consumer = buffers.getBuffer(pass);
        for (BlockPos ore : showing) {
            FungusXray.drawBlock(mc, poseStack, consumer, camera, ore, FungusXray.instance(color), swell);
        }
        buffers.endBatch(pass);
    }

    private static int color(int rgb, float alpha) {
        return ARGB.color(Math.round(alpha * OPAQUE), rgb);
    }

    /**
     * The ore every Dowsing cell within the lens reach of the eye shows,
     * reading only the chunk sections whose palette may hold a Dowsing cell.
     *
     * @param level the client level
     * @param eye   the camera's eye
     * @return the ore shown
     */
    private static Set<BlockPos> oreShownAround(Level level, Vec3 eye) {
        Set<BlockPos> ore = new HashSet<>();
        SectionPos low = SectionPos.of(BlockPos.containing(eye.subtract(LENS_REACH, LENS_REACH, LENS_REACH)));
        SectionPos high = SectionPos.of(BlockPos.containing(eye.add(LENS_REACH, LENS_REACH, LENS_REACH)));
        for (int sx = low.x(); sx <= high.x(); sx++) {
            for (int sz = low.z(); sz <= high.z(); sz++) {
                ChunkAccess chunk = level.getChunk(sx, sz, ChunkStatus.FULL, false);
                if (chunk != null) {
                    lensChunk(level, chunk, low.y(), high.y(), eye, ore);
                }
            }
        }
        return ore;
    }

    private static void lensChunk(Level level, ChunkAccess chunk, int lowY, int highY, Vec3 eye, Set<BlockPos> ore) {
        for (int sy = Math.max(lowY, chunk.getMinSectionY()); sy <= Math.min(highY, chunk.getMaxSectionY()); sy++) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
            if (section.hasOnlyAir() || !section.getStates().maybeHas(DowsingXray::isLens)) {
                continue;
            }
            BlockPos base = new BlockPos(chunk.getPos().getMinBlockX(), SectionPos.sectionToBlockCoord(sy),
                    chunk.getPos().getMinBlockZ());
            lensSection(level, section, base, eye, ore);
        }
    }

    private static void lensSection(Level level, LevelChunkSection section, BlockPos base, Vec3 eye,
                                    Set<BlockPos> ore) {
        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    BlockState state = section.getBlockState(x, y, z);
                    if (isLens(state)) {
                        BlockPos cell = base.offset(x, y, z);
                        int layers = VolumeCellBlock.layersOf(state);
                        ore.addAll(DowsingLens.oreShown(eye, gooOf(cell, layers), layers, level::getBlockState));
                    }
                }
            }
        }
    }

    private static boolean isLens(BlockState state) {
        return VolumeCellBlock.hasSurface(state.getBlock(), CellSurface.LENS);
    }

    /**
     * The goo a cell holds, in world coordinates: a full block across, its
     * layers' fifteenths of a block high.
     *
     * @param cell   the cell
     * @param layers the layers it holds
     * @return its goo's box
     */
    private static AABB gooOf(BlockPos cell, int layers) {
        return new AABB(cell.getX(), cell.getY(), cell.getZ(), cell.getX() + 1,
                cell.getY() + (double) layers / VolumeCellBlock.MAX_LAYERS, cell.getZ() + 1);
    }
}
