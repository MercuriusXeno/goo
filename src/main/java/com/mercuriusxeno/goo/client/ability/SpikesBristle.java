package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Spikes goo bristling as this client sees it: each Spikes cell near the
 * camera lies flat until a living thing comes near, as Shatter's shards
 * rise, then small chrome triangular pyramids rise out of its goo, rigid,
 * keeping their size, and sink back once nothing living is near. Each
 * cell's pyramids stand where a seed the client picks puts them.
 * urchin-rests-bristled-and-spears
 * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SpikesBristle {

    /** A spike's sides: a triangular pyramid. */
    static final int PYRAMID_SIDES = 3;
    /** The pyramids one cell bristles with. */
    static final int PYRAMIDS_PER_CELL = 5;
    /** How far in from the cell's edge a pyramid's center stands, so its base stays on the cell. */
    static final float EDGE_INSET = 0.18f;
    /** The shortest bristling pyramid, in blocks. */
    static final float LEAST_HEIGHT = 0.22f;
    /** How much taller than the shortest a bristling pyramid may stand, in blocks. */
    static final float HEIGHT_SPREAD = 0.2f;
    /** A bristling pyramid's base radius, in blocks. */
    static final float BASE_RADIUS = 0.08f;
    /** Ticks the chrome band takes to sweep down a spike once. */
    private static final float SWEEP_TICKS = 20f;
    private static final int OPAQUE = 0xFF;

    private static final NearCells<Layout> CELLS = new NearCells<>(SpikesBristle::isSpikes,
            SpikesBristle::layoutFromSeed);
    private static @Nullable Level cellsLevel;

    private SpikesBristle() {
    }

    /**
     * One bristling pyramid: where it stands on the cell's top and how tall it rises.
     *
     * @param x      its center's place across the cell, 0 to 1
     * @param z      its center's place along the cell, 0 to 1
     * @param height its height in blocks
     */
    record Pyramid(float x, float z, float height) {
    }

    /**
     * The pyramids one cell bristles with.
     *
     * @param pyramids the pyramids
     */
    record Layout(List<Pyramid> pyramids) {
    }

    /**
     * The pyramids a seed puts on a cell: each inside the cell's top, inset
     * from its edges, at a height between the shortest and the tallest.
     *
     * @param seed the cell's seed
     * @return the cell's layout
     */
    static Layout layoutFromSeed(long seed) {
        Random random = new Random(seed);
        List<Pyramid> pyramids = new ArrayList<>();
        float span = 1f - EDGE_INSET - EDGE_INSET;
        for (int pyramid = 0; pyramid < PYRAMIDS_PER_CELL; pyramid++) {
            pyramids.add(new Pyramid(EDGE_INSET + random.nextFloat() * span, EDGE_INSET + random.nextFloat() * span,
                    LEAST_HEIGHT + random.nextFloat() * HEIGHT_SPREAD));
        }
        return new Layout(List.copyOf(pyramids));
    }

    /**
     * Whether a block state is a Spikes cell.
     *
     * @param state the block state
     * @return true for a Spikes cell
     */
    static boolean isSpikes(BlockState state) {
        return state.is(GooBlocks.METAL_SPIKES.get());
    }

    /**
     * Client tick: rescans the Spikes cells near the camera when due, moves
     * each cell's bristle toward risen or sunk by what living things stand
     * near it, and forgets every cell once the client leaves the level.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level != cellsLevel) {
            CELLS.clear();
            cellsLevel = level;
        }
        if (level != null && mc.player != null) {
            Vec3 eye = mc.player.getEyePosition();
            CELLS.rescanIfDue(level, eye, level.getGameTime());
            double reach = NearCells.REACH + NearCells.NEAR_RADIUS;
            CELLS.tickShown(level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(reach),
                    living -> living.isAlive() && !living.isSpectator()).stream().map(LivingEntity::position)
                    .toList());
        }
    }

    /**
     * Draws every bristling cell's pyramids risen their share out of its goo, in Metal's chrome.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterOpaqueBlocks(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || CELLS.cells().isEmpty()) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int sweep = ColorBytes.toByte(Mth.frac((mc.level.getGameTime() + partial) / SWEEP_TICKS));
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer chrome = buffers.getBuffer(GooRenderTypes.METAL_CHROME_TYPE);
        Level level = mc.level;
        CELLS.cells().forEach((pos, cell) -> {
            float shown = cell.shownAt(partial);
            BlockState state = level.getBlockState(pos);
            if (shown > 0f && isSpikes(state)) {
                drawCell(pose, chrome, pos, gooTop(pos, state), cell.layout(), shown, camera, sweep);
            }
        });
        buffers.endBatch(GooRenderTypes.METAL_CHROME_TYPE);
    }

    /**
     * The height of a Spikes cell's goo surface.
     *
     * @param pos   the cell
     * @param state the cell's state
     * @return the surface's height in world blocks
     */
    static double gooTop(BlockPos pos, BlockState state) {
        return pos.getY() + (double) VolumeCellBlock.layersOf(state) / VolumeCellBlock.MAX_LAYERS;
    }

    private static void drawCell(PoseStack.Pose pose, VertexConsumer chrome, BlockPos pos, double gooTop,
                                 Layout layout, float shown, Vec3 camera, int sweep) {
        for (Pyramid pyramid : layout.pyramids()) {
            double sunk = (1f - shown) * pyramid.height();
            Vector3f base = new Vector3f((float) (pos.getX() + pyramid.x() - camera.x),
                    (float) (gooTop - sunk - camera.y), (float) (pos.getZ() + pyramid.z() - camera.z));
            emitPyramid(pose, chrome, base, pyramid.height(), BASE_RADIUS, sweep);
        }
    }

    /**
     * Emits one rigid chrome triangular pyramid standing straight up from its base.
     *
     * @param pose   the pose, camera-relative
     * @param chrome Metal's chrome vertex consumer
     * @param base   the pyramid's base center, camera-relative
     * @param height its height in blocks
     * @param radius its base radius in blocks
     * @param sweep  the chrome band's place, a byte
     */
    static void emitPyramid(PoseStack.Pose pose, VertexConsumer chrome, Vector3f base, float height, float radius,
                            int sweep) {
        MetalExplosionVisual.Spike spike = new MetalExplosionVisual.Spike(base, new Vector3f(0f, 1f, 0f), height,
                radius);
        MetalExplosionVisual.emitSpike(pose, chrome, spike, sweep, OPAQUE, PYRAMID_SIDES);
    }
}
