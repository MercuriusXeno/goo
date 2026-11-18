package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Consumer;

/**
 * Spikes goo bristling as this client sees it: each Spikes cell near the
 * camera lies flat until a living thing comes near, as Shatter's shards
 * rise, then steel triangular pyramids grow out of the blob's midline,
 * keeping their shape, and shrink back into it once nothing living is near.
 * Each cell draws its own seed, and from it its own number of pyramids and
 * their spots at random, a spot too near one already placed drawn again, so
 * no two tiles look alike and none stands nearer another than the least
 * spacing; keeping half that spacing in from the cell's edge holds it
 * against the neighbouring cells too.
 * Each face carries one shade under a fixed light, dark steel to bright,
 * so the faces read apart and the edges show, with no highlight sweeping
 * (operator UAT 2026-11-17).
 * urchin-rests-bristled-and-spears
 * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SpikesBristle {

    /** A spike's sides: a triangular pyramid. */
    static final int PYRAMID_SIDES = 3;
    /** The fewest pyramids a cell bristles with (operator pick 2026-11-17). */
    static final int FEWEST_PYRAMIDS = 3;
    /** The most pyramids a cell bristles with (operator pick 2026-11-17). */
    static final int MOST_PYRAMIDS = 7;
    /** The closest two pyramids stand, in blocks, in a cell or across neighbouring cells (operator pick 2026-11-17). */
    static final double LEAST_SPACING = 0.25;
    /** How far in from the cell's edge a pyramid's center stands: half the least spacing, so neighbours keep it too. */
    static final float EDGE_INSET = (float) (LEAST_SPACING / 2);
    /** The draws a pyramid's spot gets before the cell settles for fewer pyramids. */
    private static final int DRAWS_PER_SPOT = 40;
    /** The shortest bristling pyramid, in blocks. */
    static final float LEAST_HEIGHT = 0.22f;
    /** How much taller than the shortest a bristling pyramid may stand, in blocks. */
    static final float HEIGHT_SPREAD = 0.2f;
    /** A bristling pyramid's base radius, in blocks. */
    static final float BASE_RADIUS = 0.08f;
    /** The least summed RGB difference between two faces' shades, so their shared edge shows. */
    static final int LEAST_FACE_CONTRAST = 60;
    /** The shade of a face turned from the light. */
    private static final int SHADOW_STEEL = 0xFF3A3F4A;
    /** The shade of a face turned square to the light. */
    private static final int LIT_STEEL = 0xFFF0F2F6;
    /** The light every face is shaded under, from above, a little to one side. */
    private static final Vector3f LIGHT = new Vector3f(-0.5f, 0.8f, -0.3f).normalize();
    /** The share of the light a face turned from it still catches. */
    private static final float AMBIENT = 0.15f;
    /** Half, the blob's midline as a share of its height and a face's middle between its corners. */
    private static final float HALF = 0.5f;
    private static final double TWO_PI = 2 * Math.PI;
    private static final int RGB = 0xFFFFFF;

    private static final NearCells<Layout> CELLS = new NearCells<>(SpikesBristle::isSpikes,
            SpikesBristle::layoutFromSeed);
    private static @Nullable Level cellsLevel;

    private SpikesBristle() {
    }

    /**
     * One bristling pyramid: where it stands on the cell's top and how tall it grows.
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
     * The pyramids a seed puts on a cell: as many as the seed draws between
     * the fewest and the most, each at a random spot inset from the cell's
     * edge, a spot within the least spacing of one already placed drawn
     * again, at a random height between the shortest and the tallest. The
     * seed is mixed before use, so cells whose seeds lie close still differ.
     *
     * @param seed the cell's seed
     * @return the cell's layout
     */
    static Layout layoutFromSeed(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        int wanted = FEWEST_PYRAMIDS + random.nextInt(MOST_PYRAMIDS - FEWEST_PYRAMIDS + 1);
        List<Pyramid> pyramids = new ArrayList<>();
        for (int draw = 0; draw < wanted * DRAWS_PER_SPOT && pyramids.size() < wanted; draw++) {
            float x = spotFrom(random);
            float z = spotFrom(random);
            if (pyramids.stream().allMatch(placed -> Math.hypot(placed.x() - x, placed.z() - z) >= LEAST_SPACING)) {
                pyramids.add(new Pyramid(x, z, LEAST_HEIGHT + (float) random.nextDouble() * HEIGHT_SPREAD));
            }
        }
        return new Layout(List.copyOf(pyramids));
    }

    private static float spotFrom(SplittableRandom random) {
        return EDGE_INSET + (float) random.nextDouble() * (1f - EDGE_INSET - EDGE_INSET);
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
     * The height of a Spikes cell's blob midline, half way up its goo, where its spikes grow from.
     *
     * @param pos    the cell
     * @param layers the layers the cell holds
     * @return the midline's height in world blocks
     */
    static double midline(BlockPos pos, int layers) {
        return pos.getY() + (double) HALF * layers / VolumeCellBlock.MAX_LAYERS;
    }

    /**
     * Client tick: rescans the Spikes cells near the camera when due, moves
     * each cell's bristle toward grown or shrunk by what living things stand
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
     * Hands the shared path every bristling cell's pyramids grown their share
     * out of its blob's midline: opaque steel writing its own depth, drawn
     * with the opaque blocks.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the pyramids' draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || CELLS.cells().isEmpty()) {
            return;
        }
        draws.accept(new SortedDraw(0, () -> drawPyramids(mc, level, frame.poseStack().last())));
    }

    private static void drawPyramids(Minecraft mc, Level level, PoseStack.Pose pose) {
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer steel = buffers.getBuffer(GooRenderTypes.SPIKES_STEEL_TYPE);
        CELLS.cells().forEach((pos, cell) -> {
            float shown = cell.shownAt(partial);
            BlockState state = level.getBlockState(pos);
            if (shown > 0f && isSpikes(state)) {
                double mid = midline(pos, VolumeCellBlock.layersOf(state));
                for (Pyramid pyramid : cell.layout().pyramids()) {
                    Vector3f base = new Vector3f((float) (pos.getX() + pyramid.x() - camera.x),
                            (float) (mid - camera.y), (float) (pos.getZ() + pyramid.z() - camera.z));
                    emitPyramid(pose, steel, base, pyramid.height() * shown, BASE_RADIUS * shown);
                }
            }
        });
        buffers.endBatch(GooRenderTypes.SPIKES_STEEL_TYPE);
    }

    /**
     * Emits one steel triangular pyramid standing straight up from its base,
     * each face in its own shade under the fixed light.
     *
     * @param pose   the pose, camera-relative
     * @param steel  the steel vertex consumer
     * @param base   the pyramid's base center, camera-relative
     * @param height its height in blocks
     * @param radius its base radius in blocks
     */
    static void emitPyramid(PoseStack.Pose pose, VertexConsumer steel, Vector3f base, float height, float radius) {
        Vector3f apex = new Vector3f(base).add(0f, height, 0f);
        Vector3f[] corners = cornersOf(base, radius);
        for (int side = 0; side < PYRAMID_SIDES; side++) {
            Vector3f from = corners[side];
            Vector3f to = corners[(side + 1) % PYRAMID_SIDES];
            int tone = faceShade(from, to, apex, base);
            vertex(pose, steel, from, tone);
            vertex(pose, steel, to, tone);
            vertex(pose, steel, apex, tone);
            vertex(pose, steel, apex, tone);
        }
    }

    /**
     * The shades a reference pyramid's three faces take under the fixed light, face by face.
     *
     * @return each face's RGB shade
     */
    static int[] faceTones() {
        Vector3f base = new Vector3f();
        Vector3f apex = new Vector3f(0f, LEAST_HEIGHT, 0f);
        Vector3f[] corners = cornersOf(base, BASE_RADIUS);
        int[] tones = new int[PYRAMID_SIDES];
        for (int side = 0; side < PYRAMID_SIDES; side++) {
            tones[side] = faceShade(corners[side], corners[(side + 1) % PYRAMID_SIDES], apex, base) & RGB;
        }
        return tones;
    }

    private static Vector3f[] cornersOf(Vector3f base, float radius) {
        Vector3f[] corners = new Vector3f[PYRAMID_SIDES];
        for (int corner = 0; corner < PYRAMID_SIDES; corner++) {
            double angle = TWO_PI * corner / PYRAMID_SIDES;
            corners[corner] = new Vector3f(base).add((float) Math.cos(angle) * radius, 0f,
                    (float) Math.sin(angle) * radius);
        }
        return corners;
    }

    /**
     * One face's shade: dark steel turned from the light, bright steel turned square to it.
     *
     * @param from the face's first base corner
     * @param to   its second base corner
     * @param apex the pyramid's apex
     * @param base the pyramid's base center, which the face's normal points away from
     * @return the face's opaque ARGB shade
     */
    private static int faceShade(Vector3f from, Vector3f to, Vector3f apex, Vector3f base) {
        Vector3f normal = new Vector3f(to).sub(from).cross(new Vector3f(apex).sub(from)).normalize();
        Vector3f outward = new Vector3f(from).add(to).mul(HALF).sub(base);
        if (normal.dot(outward) < 0f) {
            normal.negate();
        }
        float lit = AMBIENT + (1f - AMBIENT) * Math.max(0f, normal.dot(LIGHT));
        return ARGB.srgbLerp(lit, SHADOW_STEEL, LIT_STEEL);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer steel, Vector3f at, int tone) {
        steel.addVertex(pose, at.x, at.y, at.z).setColor(tone);
    }
}
