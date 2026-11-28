package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.lunar.WirelessEmitterBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The wireless emitter's beam: a thin violet line from every transmitting emitter to the receiver it powers, brighter
 * the higher the level it carries. The client finds the transmitting emitters near the player every few ticks, reading
 * each chunk section's palette before its blocks, finds each one's receiver by the emitter's own fixed scan, and draws
 * each beam every frame.
 * decision wireless-emitter-beams-its-signal-to-the-closest-receiver
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class WirelessBeams {

    /** Client ticks between scans for emitters. */
    static final int SCAN_EVERY_TICKS = 10;
    /** Chunks out from the player's chunk a scan reaches. */
    private static final int SCAN_CHUNKS = 3;
    /** The beam's alpha at level 1 and the rise for each level above it. */
    static final int LOWEST_ALPHA = 80;
    static final int ALPHA_PER_LEVEL = 11;
    private static final int BEAM_RGB = 0x9282F5;
    private static final float WIDTH_SCALE = 2f;
    private static final int SECTION_SIZE = 16;

    /** One beam: the emitter, the receiver it powers and the level it carries. */
    record Beam(BlockPos emitter, BlockPos receiver, int level) {
    }

    private static List<Beam> beams = List.of();
    private static int ticksToScan;

    private WirelessBeams() {
    }

    /**
     * Rescans for transmitting emitters every few client ticks.
     *
     * @param event the client tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            beams = List.of();
            return;
        }
        if (--ticksToScan <= 0) {
            ticksToScan = SCAN_EVERY_TICKS;
            beams = beamsNear(mc.level, mc.player.blockPosition());
        }
    }

    /**
     * Hands the shared path each beam, sorted by the nearest emitter.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the beams' draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        List<Beam> standing = beams;
        if (mc.level == null || standing.isEmpty()) {
            return;
        }
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        draws.accept(SortedDraw.nearestOf(standing.stream().map(beam -> Vec3.atCenterOf(beam.emitter())), camera,
                () -> drawBeams(mc, frame, standing, camera)));
    }

    private static void drawBeams(Minecraft mc, LevelFrame frame, List<Beam> standing, Vec3 camera) {
        ImmediateBuffers buffers = ImmediateBuffers.shared();
        LineContext lines = new LineContext(frame.poseStack().last(), buffers.getBuffer(GooRenderTypes.LINES_GLOW));
        float width = mc.getWindow().getAppropriateLineWidth() * WIDTH_SCALE;
        for (Beam beam : standing) {
            lines.emitPolyline(camera, new Vec3[] {Vec3.atCenterOf(beam.emitter()), Vec3.atCenterOf(beam.receiver())},
                    ARGB.color(alphaFor(beam.level()), BEAM_RGB), width);
        }
        buffers.endBatch(GooRenderTypes.LINES_GLOW);
    }

    /**
     * The beam's alpha for the level it carries, brighter the higher.
     *
     * @param level the level, 1 to 15
     * @return the alpha
     */
    static int alphaFor(int level) {
        return LOWEST_ALPHA + ALPHA_PER_LEVEL * (level - 1);
    }

    private static List<Beam> beamsNear(ClientLevel level, BlockPos center) {
        List<Beam> found = new ArrayList<>();
        Predicate<BlockState> transmitting = state -> state.is(GooBlocks.WIRELESS_EMITTER.get())
                && state.getValue(WirelessEmitterBlock.POWER) > 0;
        int chunkX = SectionPos.blockToSectionCoord(center.getX());
        int chunkZ = SectionPos.blockToSectionCoord(center.getZ());
        for (int x = chunkX - SCAN_CHUNKS; x <= chunkX + SCAN_CHUNKS; x++) {
            for (int z = chunkZ - SCAN_CHUNKS; z <= chunkZ + SCAN_CHUNKS; z++) {
                if (level.getChunk(x, z, ChunkStatus.FULL, false) instanceof LevelChunk chunk) {
                    addBeamsIn(level, chunk, transmitting, found);
                }
            }
        }
        return List.copyOf(found);
    }

    private static void addBeamsIn(ClientLevel level, LevelChunk chunk, Predicate<BlockState> transmitting,
                                   List<Beam> found) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(transmitting)) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    for (int x = 0; x < SECTION_SIZE; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (transmitting.test(state)) {
                            BlockPos emitter = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y,
                                    chunk.getPos().getMinBlockZ() + z);
                            WirelessEmitterBlock.receiverOf(level, emitter).ifPresent(receiver -> found.add(
                                    new Beam(emitter, receiver, state.getValue(WirelessEmitterBlock.POWER))));
                        }
                    }
                }
            }
        }
    }

    /** Forgets every beam, as a disconnect does. */
    public static void clear() {
        beams = List.of();
    }
}
