package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.CellSurface;
import com.mercuriusxeno.goo.block.ability.GooShares;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
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
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Shows the ore the Dowsing cells near the eye lens, through each cell as a
 * window: every client tick it finds the Dowsing cells in the loaded chunks
 * around the eye and asks the lens which ore each layer count's cells may
 * show, so the sight follows the camera, the cells' layers and the blocks
 * behind them within a tick, with no payload from the server. Each layer
 * count draws on its own: its cells' goo marks the stencil where the goo is
 * the nearest surface, then its ore draws as itself, a little translucent
 * and fully lit, and its starlight glow, only on those marked pixels, so the
 * ore is cut off at the goo's edge on screen.
 * astral-world-goo-is-dowsing-an-x-ray-of-ore
 * dowsing-shows-ore-one-block-deep-per-layer
 * dowsing-goo-is-a-window-onto-the-ore-behind-it
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
    /** The mask writes no color, so any color serves its vertices. */
    private static final int MASK_COLOR = -1;

    /**
     * One layer count's window: the goo its cells hold and the ore they may show.
     *
     * @param goo the cells' goo
     * @param ore the ore candidates behind it
     */
    private record Window(List<AABB> goo, Set<BlockPos> ore) {
    }

    /** The windows the Dowsing cells opened at the last tick, by layer count. */
    private static List<Window> windows = List.of();

    private DowsingXray() {
    }

    /**
     * Asks every Dowsing cell near the eye which ore it may show.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            windows = List.of();
            return;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().position();
        List<DowsingLens.Lens> lenses = lensesAround(mc.level, eye);
        Map<Integer, Set<BlockPos>> ore = DowsingLens.oreShownByLayers(eye, lenses, mc.level::getBlockState);
        Map<Integer, List<AABB>> goo = new TreeMap<>();
        lenses.forEach(lens -> goo.computeIfAbsent(lens.layers(), layers -> new ArrayList<>()).add(lens.goo()));
        List<Window> opened = new ArrayList<>();
        goo.forEach((layers, boxes) -> {
            Set<BlockPos> behind = ore.getOrDefault(layers, Set.of());
            if (!behind.isEmpty()) {
                opened.add(new Window(List.copyOf(boxes), Set.copyOf(behind)));
            }
        });
        windows = List.copyOf(opened);
    }

    /**
     * Draws each window's ore, cut to the goo, once the world has drawn; a
     * main target holding no stencil draws none, since the ore would spill
     * past the goo.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        List<Window> open = windows;
        RenderTarget main = mc.getMainRenderTarget();
        GpuTexture depth = main.getDepthTexture();
        if (mc.level == null || open.isEmpty() || !main.useStencil || depth == null) {
            return;
        }
        Identifier atlas = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location();
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Window window : open) {
            clearTheMark(depth);
            markTheGoo(buffers, poseStack, camera, window.goo());
            drawPass(mc, poseStack, GooRenderTypes.dowsingOre(atlas), window.ore(), color(ORE_RGB, ORE_ALPHA), 1f);
            drawPass(mc, poseStack, GooRenderTypes.dowsingGlow(atlas), window.ore(), color(GLOW_RGB, GLOW_ALPHA),
                    GLOW_SWELL);
        }
        clearTheMark(depth);
    }

    /**
     * Clears the Dowsing stencil bit over the whole target, leaving every
     * other bit as it stands, since the game never clears the stencil.
     *
     * @param depth the main target's depth and stencil texture
     */
    private static void clearTheMark(GpuTexture depth) {
        GlStateManager._stencilMask(GooRenderTypes.DOWSING_STENCIL_BIT);
        RenderSystem.getDevice().createCommandEncoder().clearStencilTexture(depth, 0);
    }

    /**
     * Marks the stencil where the goo boxes are the nearest surface.
     *
     * @param buffers   the buffers
     * @param poseStack the pose stack
     * @param camera    the camera's position
     * @param goo       the goo boxes
     */
    private static void markTheGoo(MultiBufferSource.BufferSource buffers, PoseStack poseStack, Vec3 camera,
                                   List<AABB> goo) {
        RenderType mask = GooRenderTypes.DOWSING_MASK_TYPE;
        VertexConsumer consumer = buffers.getBuffer(mask);
        Matrix4f pose = poseStack.last().pose();
        for (AABB box : goo) {
            BoxFaces.emit(consumer, pose, box.move(camera.reverse()), MASK_COLOR);
        }
        buffers.endBatch(mask);
    }

    private static void drawPass(Minecraft mc, PoseStack poseStack, RenderType pass, Set<BlockPos> ore, int color,
                                 float swell) {
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        VertexConsumer consumer = buffers.getBuffer(pass);
        for (BlockPos pos : ore) {
            FungusXray.drawBlock(mc, poseStack, consumer, camera, pos, FungusXray.instance(color), swell);
        }
        buffers.endBatch(pass);
    }

    private static int color(int rgb, float alpha) {
        return ARGB.color(Math.round(alpha * OPAQUE), rgb);
    }

    /**
     * Every Dowsing cell within the lens reach of the eye, reading only the
     * chunk sections whose palette may hold one.
     *
     * @param level the client level
     * @param eye   the camera's eye
     * @return each cell's goo and layers
     */
    private static List<DowsingLens.Lens> lensesAround(Level level, Vec3 eye) {
        List<DowsingLens.Lens> lenses = new ArrayList<>();
        SectionPos low = SectionPos.of(BlockPos.containing(eye.subtract(LENS_REACH, LENS_REACH, LENS_REACH)));
        SectionPos high = SectionPos.of(BlockPos.containing(eye.add(LENS_REACH, LENS_REACH, LENS_REACH)));
        for (int sx = low.x(); sx <= high.x(); sx++) {
            for (int sz = low.z(); sz <= high.z(); sz++) {
                ChunkAccess chunk = level.getChunk(sx, sz, ChunkStatus.FULL, false);
                if (chunk != null) {
                    lensesInChunk(level, chunk, low.y(), high.y(), lenses);
                }
            }
        }
        return lenses;
    }

    private static void lensesInChunk(Level level, ChunkAccess chunk, int lowY, int highY,
                                      List<DowsingLens.Lens> lenses) {
        for (int sy = Math.max(lowY, chunk.getMinSectionY()); sy <= Math.min(highY, chunk.getMaxSectionY()); sy++) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
            if (section.hasOnlyAir() || !section.getStates().maybeHas(DowsingXray::isLens)) {
                continue;
            }
            BlockPos base = new BlockPos(chunk.getPos().getMinBlockX(), SectionPos.sectionToBlockCoord(sy),
                    chunk.getPos().getMinBlockZ());
            lensesInSection(level, section, base, lenses);
        }
    }

    private static void lensesInSection(Level level, LevelChunkSection section, BlockPos base,
                                        List<DowsingLens.Lens> lenses) {
        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    BlockState state = section.getBlockState(x, y, z);
                    if (isLens(state)) {
                        lensesOfCell(level, base.offset(x, y, z), state, lenses);
                    }
                }
            }
        }
    }

    // One lens for each box of the cell's goo, each reaching as far past the goo as the cell holds layers.
    private static void lensesOfCell(Level level, BlockPos cell, BlockState state, List<DowsingLens.Lens> lenses) {
        VolumeCellBlock block = (VolumeCellBlock) state.getBlock();
        GooShares shares = VolumeCellBlock.sharesOf(state, block.onAFloor(level, cell), block.cohesionIn(level));
        int layers = VolumeCellBlock.layersOf(state);
        gooOf(cell, shares).forEach(goo -> lenses.add(new DowsingLens.Lens(goo, layers)));
    }

    private static boolean isLens(BlockState state) {
        return VolumeCellBlock.hasSurface(state.getBlock(), CellSurface.LENS);
    }

    /**
     * The goo a cell holds, in world coordinates, where the cell draws it: a
     * slab on each wall or ceiling it hangs from and a fill on its floor.
     * dowsing-goo-is-a-window-onto-the-ore-behind-it
     * attached-goo-draws-per-attachment-and-divides-its-volume
     *
     * @param cell   the cell
     * @param shares how the cell's volume divides between its surfaces
     * @return its goo's boxes
     */
    static List<AABB> gooOf(BlockPos cell, GooShares shares) {
        return VolumeCellBlock.boxOf(shares).toAabbs().stream().map(box -> box.move(cell)).toList();
    }

    /** A box's six faces, each wound to face outward so culling keeps only the faces toward the eye. */
    private static final class BoxFaces {

        private BoxFaces() {
        }

        /**
         * Emits a box's six faces as quads.
         *
         * @param consumer the position-color consumer
         * @param pose     the pose
         * @param box      the box, relative to the camera
         * @param color    the vertex color
         */
        static void emit(VertexConsumer consumer, Matrix4f pose, AABB box, int color) {
            float x0 = (float) box.minX;
            float y0 = (float) box.minY;
            float z0 = (float) box.minZ;
            float x1 = (float) box.maxX;
            float y1 = (float) box.maxY;
            float z1 = (float) box.maxZ;
            quad(consumer, pose, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
            quad(consumer, pose, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
            quad(consumer, pose, color, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
            quad(consumer, pose, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
            quad(consumer, pose, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
            quad(consumer, pose, color, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        }

        @SuppressWarnings("checkstyle:ParameterNumber") // four corners of three coordinates each
        private static void quad(VertexConsumer consumer, Matrix4f pose, int color, float ax, float ay, float az,
                                 float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy,
                                 float dz) {
            consumer.addVertex(pose, ax, ay, az).setColor(color);
            consumer.addVertex(pose, bx, by, bz).setColor(color);
            consumer.addVertex(pose, cx, cy, cz).setColor(color);
            consumer.addVertex(pose, dx, dy, dz).setColor(color);
        }
    }
}
