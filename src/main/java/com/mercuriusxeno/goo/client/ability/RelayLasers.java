package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.pulse.RelayNetwork;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.client.ber.style.PulsePrismStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The relay link's mark: a thin red laser between every two relay prisms
 * that link through air, dim while idle and bright while a signal crosses it.
 * The client finds the relays near the player every few ticks and draws each
 * link every frame.
 * relay-prism-carries-the-signal-through-air
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class RelayLasers {

    /** Client ticks between scans for relays. */
    static final int SCAN_EVERY_TICKS = 10;
    /** Chunks out from the player's chunk a scan reaches. */
    private static final int SCAN_CHUNKS = 3;
    /** The laser's alpha while no signal crosses it. */
    static final int IDLE_ALPHA = 90;
    /** The laser's alpha while a signal crosses it. */
    static final int CARRYING_ALPHA = 235;
    /** The relays one link joins. */
    private static final int RELAYS_IN_A_LINK = 2;
    private static final int LASER_RGB = 0xFF2A1A;
    private static final float WIDTH_SCALE = 2f;

    private static List<BlockPos> relays = List.of();
    private static int ticksToScan;

    private RelayLasers() {
    }

    /**
     * Rescans for relays every few client ticks.
     *
     * @param event the client tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            relays = List.of();
            return;
        }
        if (--ticksToScan <= 0) {
            ticksToScan = SCAN_EVERY_TICKS;
            relays = relaysNear(mc.level, mc.player.blockPosition());
        }
    }

    /**
     * Draws each link between relays after the translucent blocks.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        List<BlockPos> standing = relays;
        if (mc.level == null || standing.size() < RELAYS_IN_A_LINK) {
            return;
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        LineContext lines = new LineContext(event.getPoseStack().last(), buffers.getBuffer(GooRenderTypes.LINES_GLOW));
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        float width = mc.getWindow().getAppropriateLineWidth() * WIDTH_SCALE;
        for (int i = 0; i < standing.size(); i++) {
            for (int j = i + 1; j < standing.size(); j++) {
                drawLink(mc.level, lines, standing.get(i), standing.get(j), camera, width);
            }
        }
        buffers.endBatch(GooRenderTypes.LINES_GLOW);
    }

    private static void drawLink(ClientLevel level, LineContext lines, BlockPos from, BlockPos to, Vec3 camera,
                                 float width) {
        if (!RelayNetwork.inLinkReach(from, to) || !RelayNetwork.cellsBetween(from, to).stream()
                .allMatch(cell -> level.getBlockState(cell).isAir())) {
            return;
        }
        int alpha = carrying(level, from) || carrying(level, to) ? CARRYING_ALPHA : IDLE_ALPHA;
        lines.emitPolyline(camera, new Vec3[] {Vec3.atCenterOf(from), Vec3.atCenterOf(to)},
                ARGB.color(alpha, LASER_RGB), width);
    }

    private static boolean carrying(ClientLevel level, BlockPos relay) {
        return level.getBlockState(relay).getOptionalValue(PrismBlock.POWER).orElse(0) > 0
                || level.hasNeighborSignal(relay);
    }

    private static List<BlockPos> relaysNear(ClientLevel level, BlockPos center) {
        List<BlockPos> found = new ArrayList<>();
        int chunkX = SectionPos.blockToSectionCoord(center.getX());
        int chunkZ = SectionPos.blockToSectionCoord(center.getZ());
        for (int x = chunkX - SCAN_CHUNKS; x <= chunkX + SCAN_CHUNKS; x++) {
            for (int z = chunkZ - SCAN_CHUNKS; z <= chunkZ + SCAN_CHUNKS; z++) {
                if (level.getChunk(x, z, ChunkStatus.FULL, false) instanceof LevelChunk chunk) {
                    addRelaysIn(chunk, found);
                }
            }
        }
        return List.copyOf(found);
    }

    private static void addRelaysIn(LevelChunk chunk, List<BlockPos> found) {
        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            if (entity instanceof PrismBlockEntity prism && PulsePrismStyle.RELAY_COMBO.equals(prism.getCombo())) {
                found.add(prism.getBlockPos());
            }
        }
    }

    /** Forgets every relay, as a disconnect does. */
    public static void clear() {
        relays = List.of();
    }
}
