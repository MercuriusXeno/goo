package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.ShiftStep;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
 * Shows every fungus block within Fungal Shift's reach through walls while
 * the local player holds fungal sight: each block's own model and texture,
 * ghosted and tinted toward shroom's mauve, full bright, with an additive
 * magenta halo of its own shape swollen past it and breathing slowly, both
 * drawn after the translucent world through pipelines that ignore depth. The blocks are
 * scanned again once a second, chunk section by chunk section, skipping any
 * section whose palette holds no fungus, and the nearest are drawn up to a cap.
 * sight-lengthens-shift-and-outlines-fungus
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class FungusXray {

    private static final String FUNGAL_SHIFT = "goo:shroom_fungal_shift";
    /** Fungal Shift's range when the player holds no synced copy of it. */
    private static final double FALLBACK_RANGE = 64;
    static final int RESCAN_TICKS = 20;
    private static final int MOST_SEEN = 512;
    /** The ghost's tint: a little translucent, leaning toward shroom's mauve. */
    private static final int GHOST_TINT = 0xB8E0B0F0;
    /** The halo's magenta. */
    private static final int GLOW_RGB = 0xFF40C0;
    /** The halo's alpha at full pulse. */
    private static final float HALO_ALPHA = 0.55f;
    /** How far the halo swells past the block. */
    private static final float HALO_SWELL = 1.12f;
    /** The halo's slow breath, radians per second. */
    private static final float HALO_PULSE_PER_SECOND = 2.2f;
    private static final float MILLIS_PER_SECOND = 1000f;
    private static final float HALF = 0.5f;
    private static final int OPAQUE = 255;
    private static final int SECTION_SIZE = LevelChunkSection.SECTION_WIDTH;

    private static final List<BlockPos> seen = new ArrayList<>();
    /** Marks that no scan has run. */
    static final long UNSCANNED = Long.MIN_VALUE;
    private static long scannedAt = UNSCANNED;

    private FungusXray() {
    }

    /**
     * Draws the fungus through walls once the world has drawn, while the local
     * player's sight stands.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        if (!player.getData(GooAttachments.SIGHT).standsAt(now)) {
            seen.clear();
            return;
        }
        rescanEverySecond(mc.level, player, now);
        if (!seen.isEmpty()) {
            drawAll(mc, event.getPoseStack());
        }
    }

    private static void drawAll(Minecraft mc, PoseStack poseStack) {
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Identifier atlas = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location();
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        RenderType xray = GooRenderTypes.fungusXray(atlas);
        VertexConsumer ghost = buffers.getBuffer(xray);
        QuadInstance ghostLook = instance(GHOST_TINT);
        for (BlockPos pos : seen) {
            drawBlock(mc, poseStack, ghost, camera, pos, ghostLook, 1f);
        }
        buffers.endBatch(xray);
        RenderType glow = GooRenderTypes.fungusGlow(atlas);
        VertexConsumer halo = buffers.getBuffer(glow);
        float seconds = Util.getMillis() / MILLIS_PER_SECOND;
        QuadInstance haloLook = instance(glowColor(pulse(seconds, HALO_PULSE_PER_SECOND), HALO_ALPHA));
        for (BlockPos pos : seen) {
            drawBlock(mc, poseStack, halo, camera, pos, haloLook, HALO_SWELL);
        }
        buffers.endBatch(glow);
    }

    /**
     * A quad instance drawing at full bright with no overlay, in a color.
     *
     * @param color the ARGB color the quads take
     * @return the instance
     */
    static QuadInstance instance(int color) {
        QuadInstance instance = new QuadInstance();
        instance.setColor(color);
        instance.setLightCoords(GooSubmitter.fullbrightLight());
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        return instance;
    }

    /**
     * A slow breath between half and full strength.
     *
     * @param seconds        seconds on the real-time clock
     * @param pulsePerSecond breaths per second, in radians
     * @return the strength, half to one
     */
    static float pulse(float seconds, float pulsePerSecond) {
        return HALF + HALF * (HALF + HALF * Mth.sin(seconds * pulsePerSecond));
    }

    /**
     * The glow's magenta at a strength and a peak alpha.
     *
     * @param strength the pulse's strength, zero to one
     * @param alpha    the alpha at full strength, zero to one
     * @return the ARGB color
     */
    static int glowColor(float strength, float alpha) {
        return ARGB.color(Math.round(Mth.clamp(strength * alpha, 0f, 1f) * OPAQUE), GLOW_RGB);
    }

    /**
     * Draws a block's own baked quads, swollen about its center.
     *
     * @param mc        the client
     * @param poseStack the pose stack
     * @param consumer  the vertex consumer
     * @param camera    the camera's position
     * @param pos       the block
     * @param instance  the color, light and overlay the quads take
     * @param swell     the scale about the block's center, one for its own size
     */
    static void drawBlock(Minecraft mc, PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
                          BlockPos pos, QuadInstance instance, float swell) {
        BlockState state = mc.level.getBlockState(pos);
        BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(mc.level, pos, state, RandomSource.create(pos.asLong()), parts);
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x + HALF, pos.getY() - camera.y + HALF, pos.getZ() - camera.z + HALF);
        poseStack.scale(swell, swell, swell);
        poseStack.translate(-HALF, -HALF, -HALF);
        for (BlockStateModelPart part : parts) {
            for (Direction side : Direction.values()) {
                part.getQuads(side).forEach(quad -> consumer.putBakedQuad(poseStack.last(), quad, instance));
            }
            part.getQuads(null).forEach(quad -> consumer.putBakedQuad(poseStack.last(), quad, instance));
        }
        poseStack.popPose();
    }

    private static void rescanEverySecond(Level level, LocalPlayer player, long now) {
        if (isDue(scannedAt, now)) {
            rescan(level, player.getEyePosition(), ShiftStep.reachOf(player, shiftRange()));
            scannedAt = now;
        }
    }

    /**
     * Whether a scan is due: none has run yet, a second has passed since the
     * last, or the clock ran back past it.
     *
     * @param lastScan the game time of the last scan, or {@link #UNSCANNED}
     * @param now      the game time
     * @return true when the fungus should be scanned again
     */
    static boolean isDue(long lastScan, long now) {
        return lastScan == UNSCANNED || now < lastScan || now - lastScan >= RESCAN_TICKS;
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
        seen.clear();
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
        seen.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(eye)));
        if (seen.size() > MOST_SEEN) {
            seen.subList(MOST_SEEN, seen.size()).clear();
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
                            seen.add(pos);
                        }
                    }
                }
            }
        }
    }
}
