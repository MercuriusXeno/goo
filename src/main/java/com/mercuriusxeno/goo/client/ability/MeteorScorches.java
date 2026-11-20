package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.network.MeteorScorchPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Meteo's scorch on every watching client: an overlay laid over the faces
 * the crater left open to air, glowing ember at first, cooling to char and
 * fading to nothing over about four seconds; no block changes.
 * decision meteo-winds-up-then-drops-in-a-second-trailing-fire
 */
public final class MeteorScorches {

    /** Ticks the scorch takes to fade to nothing: about four seconds (operator ruling 2026-11-06). */
    static final int SCORCH_TICKS = 80;
    /** Ticks after the strike the scorch waits before reading the crater, so the blast's block changes arrive. */
    static final int SETTLE_TICKS = 3;
    /** Ticks the scorch glows as embers before it reads as char. */
    private static final int EMBER_TICKS = 20;
    /** The scorch's alpha at full strength, at the strike's center. */
    private static final int CHAR_ALPHA = 200;
    /** How much of its alpha the scorch keeps at its rim, so it feathers out rather than ending in an edge. */
    private static final float RIM_SHARE = 0.25f;
    private static final int EMBER_RGB = 0xFF5A14;
    private static final int CHAR_RGB = 0x140C08;
    /** The low bytes of a packed color, its red, green and blue. */
    private static final int RGB_MASK = 0xFFFFFF;
    /** How far a scorch quad stands off its face, so it never sinks into it. */
    private static final float FACE_LIFT = 0.003f;
    /** The most faces one scorch keeps. */
    private static final int MOST_BLOCKS = 4096;

    private static final List<Scorch> SCORCHES = new ArrayList<>();

    private MeteorScorches() {
    }

    /**
     * One strike's scorch: where it struck, how far it reaches, when, and
     * the blocks it lies on, filled once the crater settles.
     *
     * @param center   the strike
     * @param radius   how far the scorch reaches
     * @param struckAt the game time of the strike
     * @param blocks   the blocks it lies on, empty until read
     */
    private record Scorch(Vec3 center, double radius, long struckAt, List<ScorchedBlock> blocks) {
    }

    /**
     * A block the scorch lies on: its shape's boxes and the sides of it the
     * crater left open to air.
     *
     * @param pos      the block
     * @param sides    its sides open to air
     * @param boxes    its voxel shape's boxes, block-local
     * @param distance its center's distance from the strike
     */
    record ScorchedBlock(BlockPos pos, List<Direction> sides, List<AABB> boxes, double distance) {
    }

    /**
     * Handles a strike's scorch on the client thread.
     *
     * @param payload the scorch payload
     * @param context the network context
     */
    public static void onPayload(MeteorScorchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                SCORCHES.add(new Scorch(payload.center(), payload.radius(), mc.level.getGameTime(),
                        new ArrayList<>()));
            }
        });
    }

    /** Drops every scorch, as a disconnect does. */
    public static void clear() {
        SCORCHES.clear();
    }

    /**
     * The blocks the scorch lies on: every block within the radius of the
     * strike with a side open to air, and those sides.
     *
     * @param center the strike
     * @param radius how far the scorch reaches
     * @param isAir  whether a position holds air
     * @return the scorched blocks, block shapes left as full cubes
     */
    static List<ScorchedBlock> facesWithin(Vec3 center, double radius, Predicate<BlockPos> isAir) {
        List<ScorchedBlock> scorched = new ArrayList<>();
        for (BlockPos pos : ScryReveal.shell(center, 0, radius)) {
            List<Direction> sides = ScryReveal.exposedFaces(isAir, pos);
            if (!sides.isEmpty() && scorched.size() < MOST_BLOCKS) {
                scorched.add(new ScorchedBlock(pos, sides, List.of(new AABB(0, 0, 0, 1, 1, 1)),
                        Vec3.atCenterOf(pos).distanceTo(center)));
            }
        }
        return scorched;
    }

    /**
     * How strongly the scorch shows a while after the strike: whole at the
     * strike, fading to nothing over its ticks.
     *
     * @param age ticks since the strike
     * @return the strength in [0, 1]
     */
    static float strengthAt(long age) {
        return (float) Math.clamp(1.0 - (double) age / SCORCH_TICKS, 0, 1);
    }

    /**
     * The scorch's color on a face a while after the strike: ember cooling
     * to char, darkest at the strike and feathered toward its rim.
     *
     * @param age      ticks since the strike
     * @param distance the face's block's distance from the strike
     * @param radius   how far the scorch reaches
     * @return the packed ARGB color
     */
    static int colorAt(long age, double distance, double radius) {
        float cooled = (float) Math.clamp((double) age / EMBER_TICKS, 0, 1);
        int rgb = ARGB.srgbLerp(cooled, ARGB.opaque(EMBER_RGB), ARGB.opaque(CHAR_RGB)) & RGB_MASK;
        float toRim = (float) Math.clamp(distance / radius, 0, 1);
        float feather = 1f - toRim * (1f - RIM_SHARE);
        return ARGB.color(Math.round(CHAR_ALPHA * feather * strengthAt(age)), rgb);
    }

    /**
     * Drops the scorches that have faded, reads each crater once its blocks
     * have settled, and hands the shared path every scorch, to draw on its
     * opaque-face route right after the faces it lies flush on, whose depth
     * already stands, so the scorches sort as one batch.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the scorches' draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || SCORCHES.isEmpty()) {
            return;
        }
        long now = mc.level.getGameTime();
        SCORCHES.removeIf(scorch -> strengthAt(now - scorch.struckAt()) <= 0f);
        for (Scorch scorch : SCORCHES) {
            // a crater with no open face reads again each frame until its scorch fades, a few hundred blocks
            if (scorch.blocks().isEmpty() && now - scorch.struckAt() >= SETTLE_TICKS) {
                readCrater(mc.level, scorch);
            }
        }
        List<Scorch> standing = List.copyOf(SCORCHES);
        draws.accept(new SortedDraw(0, () -> drawScorches(mc, frame.poseStack().last(), standing, now)));
    }

    private static void drawScorches(Minecraft mc, PoseStack.Pose pose, List<Scorch> standing, long now) {
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        ImmediateBuffers buffers = ImmediateBuffers.shared();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.METEOR_SCORCH_TYPE);
        for (Scorch scorch : standing) {
            drawScorch(pose, consumer, camera, scorch, now - scorch.struckAt());
        }
        buffers.endBatch(GooRenderTypes.METEOR_SCORCH_TYPE);
    }

    private static void readCrater(ClientLevel level, Scorch scorch) {
        for (ScorchedBlock block : facesWithin(scorch.center(), scorch.radius(),
                at -> level.getBlockState(at).isAir())) {
            // the scorch traces the block's own shape, so slabs and stairs scorch as they stand
            List<AABB> boxes = level.getBlockState(block.pos()).getShape(level, block.pos()).toAabbs();
            scorch.blocks().add(new ScorchedBlock(block.pos(), block.sides(), boxes, block.distance()));
        }
    }

    private static void drawScorch(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera, Scorch scorch,
                                   long age) {
        for (ScorchedBlock block : scorch.blocks()) {
            int color = colorAt(age, block.distance(), scorch.radius());
            Vec3 corner = Vec3.atLowerCornerOf(block.pos()).subtract(camera);
            for (AABB box : block.boxes()) {
                for (Direction side : block.sides()) {
                    HeldSweepVisual.emitBoxFace(pose, consumer, box.move(corner).inflate(FACE_LIFT), side, color);
                }
            }
        }
    }
}
