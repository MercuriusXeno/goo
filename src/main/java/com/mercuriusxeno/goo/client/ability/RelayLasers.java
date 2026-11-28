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
    /**
     * The laser's alpha while no signal crosses it: a faint trace, so a carrying link reads apart at a glance.
     * decision diagnose-and-correct-relay-ignoring-torch-and-metronome
     */
    private static final int IDLE_ALPHA = 30;
    /** The laser's alpha while a signal crosses it. */
    private static final int CARRYING_ALPHA = 255;
    /** The relays one link joins. */
    private static final int RELAYS_IN_A_LINK = 2;
    private static final int LASER_RGB = 0xFF2A1A;
    /** The beam's thickness in the world, in blocks: half a pixel. */
    static final double BEAM_THICKNESS = 1.0 / 32;
    /** The thinnest the beam draws on screen, in pixels, so it never vanishes far off. */
    static final double THINNEST_PIXELS = 1;
    /** The nearest the beam's width is figured from, in blocks, so it never balloons at the camera. */
    private static final double NEAREST_DISTANCE = 0.5;
    /** The longest segment a link draws in, in blocks, so its width follows the distance along it. */
    private static final double SEGMENT_BLOCKS = 1;
    private static final double HALF = 0.5;
    /** How far a lit tip's glow reaches from the cell center, in blocks: its 4 pixel head and half a pixel of glow. */
    static final double TIP_GLOW_REACH = 2.5 / 16;

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
        LineContext lines = new LineContext(frame.poseStack().last(), buffers.getBuffer(GooRenderTypes.LINES_ADDED_GLOW));
        double pixelsPerBlockAtOne = mc.getWindow().getHeight() * HALF
                / Math.tan(Math.toRadians(mc.options.fov().get()) * HALF);
        for (int i = 0; i < standing.size(); i++) {
            for (int j = i + 1; j < standing.size(); j++) {
                drawLink(mc.level, lines, standing.get(i), standing.get(j), camera, pixelsPerBlockAtOne);
            }
        }
        buffers.endBatch(GooRenderTypes.LINES_ADDED_GLOW);
    }

    /**
     * Draws a link in short segments, each as wide on screen as the beam's thickness in the world reads at that
     * segment's distance, so the beam thins as it runs away rather than holding one width on screen.
     * operator ruling 2026-11-28: the beam thins with distance rather than reading thicker far off
     *
     * @param level               the client level
     * @param lines               where the lines go
     * @param from                one relay
     * @param to                  the other
     * @param camera              the camera's position
     * @param pixelsPerBlockAtOne the screen pixels a block spans a block from the camera
     */
    private static void drawLink(ClientLevel level, LineContext lines, BlockPos from, BlockPos to, Vec3 camera,
                                 double pixelsPerBlockAtOne) {
        if (!RelayNetwork.inLinkReach(from, to) || !RelayNetwork.links(level, from, to)) {
            return;
        }
        int color = laserColor(carrying(level, from) || carrying(level, to));
        Vec3[] ends = linkLine(from, to);
        Vec3 along = ends[1].subtract(ends[0]);
        int segments = Math.max(1, (int) Math.ceil(along.length() / SEGMENT_BLOCKS));
        for (int i = 0; i < segments; i++) {
            Vec3 start = ends[0].add(along.scale((double) i / segments));
            Vec3 end = ends[0].add(along.scale((double) (i + 1) / segments));
            float width = lineWidthAt(start.add(end).scale(HALF).distanceTo(camera), pixelsPerBlockAtOne);
            lines.emitPolyline(camera, new Vec3[] {start, end}, color, width);
        }
    }

    /**
     * How wide on screen, in pixels, the beam draws at a distance: its thickness in the world seen from there, never
     * thinner than a pixel.
     *
     * @param distance            the camera's distance from the beam, in blocks
     * @param pixelsPerBlockAtOne the screen pixels a block spans a block from the camera
     * @return the line width in pixels
     */
    static float lineWidthAt(double distance, double pixelsPerBlockAtOne) {
        return (float) Math.max(THINNEST_PIXELS,
                BEAM_THICKNESS * pixelsPerBlockAtOne / Math.max(distance, NEAREST_DISTANCE));
    }

    /**
     * The line a link draws: from the edge of one relay's tip glow to the edge of the other's, along the line
     * between their cell centers, where their tips sit. Drawn from the centers, the line ran inside the head and its
     * glow and showed over them (operator ruling 2026-11-28).
     * decision relay-shrinks-so-its-tip-sits-centered-and-beams-join-tips
     *
     * @param from one relay
     * @param to   the other
     * @return the link's two ends
     */
    static Vec3[] linkLine(BlockPos from, BlockPos to) {
        Vec3 start = Vec3.atCenterOf(from);
        Vec3 end = Vec3.atCenterOf(to);
        Vec3 glowEdge = end.subtract(start).normalize().scale(TIP_GLOW_REACH);
        return new Vec3[] {start.add(glowEdge), end.subtract(glowEdge)};
    }

    /**
     * The color a link draws in, faint while idle and bright while a signal crosses it.
     *
     * @param carrying whether a signal crosses the link
     * @return the link's ARGB color
     */
    static int laserColor(boolean carrying) {
        return ARGB.color(carrying ? CARRYING_ALPHA : IDLE_ALPHA, LASER_RGB);
    }

    private static boolean carrying(ClientLevel level, BlockPos relay) {
        return level.getBlockState(relay).getOptionalValue(BlockStateProperties.LIT).orElse(false);
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
