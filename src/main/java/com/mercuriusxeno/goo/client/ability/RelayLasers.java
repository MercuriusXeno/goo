package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.pulse.RelayNetwork;
import com.mercuriusxeno.goo.block.alientech.RelayBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The relay link's mark: a thin red laser between every two relays
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
     * Hands the shared path each link between relays, sorted by the nearest relay.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the links' draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        List<BlockPos> standing = relays;
        if (mc.level == null || standing.size() < RELAYS_IN_A_LINK) {
            return;
        }
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        draws.accept(SortedDraw.nearestOf(standing.stream().map(Vec3::atCenterOf), camera,
                () -> drawLinks(mc, frame, standing, camera)));
    }

    private static void drawLinks(Minecraft mc, LevelFrame frame, List<BlockPos> standing, Vec3 camera) {
        ImmediateBuffers buffers = ImmediateBuffers.shared();
        LineContext lines = new LineContext(frame.poseStack().last(), buffers.getBuffer(GooRenderTypes.LINES_GLOW));
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
        if (!RelayNetwork.inLinkReach(from, to) || !RelayNetwork.links(level, from, to)) {
            return;
        }
        int alpha = carrying(level, from) || carrying(level, to) ? CARRYING_ALPHA : IDLE_ALPHA;
        lines.emitPolyline(camera, new Vec3[] {Vec3.atCenterOf(from), Vec3.atCenterOf(to)},
                ARGB.color(alpha, LASER_RGB), width);
    }

    private static boolean carrying(ClientLevel level, BlockPos relay) {
        return level.getBlockState(relay).getOptionalValue(BlockStateProperties.POWER).orElse(0) > 0
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
            if (entity instanceof RelayBlockEntity) {
                found.add(entity.getBlockPos());
            }
        }
    }

    /** Forgets every relay, as a disconnect does. */
    public static void clear() {
        relays = List.of();
    }
}
