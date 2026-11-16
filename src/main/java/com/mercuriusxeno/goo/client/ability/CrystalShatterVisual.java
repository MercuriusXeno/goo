package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the glass shards hanging over every Shatter cell near the camera as
 * thin elongated pyramids, a sixth to a half of a block long and as wide as
 * a tenth of their length, each lying and spinning as its cell's seeded
 * layout draws it, at the place {@link ShatterCellShards} answers: rising
 * out of the goo and fading in while a living thing stands within 3 blocks
 * of the cell, at rest whole above its surface within its column, and
 * sinking back once nothing living is near; a cell whose shards stand
 * hidden draws nothing. Each face reflects what its own ray off the camera's
 * view meets in the world, eased across ticks. The cells come from
 * {@link ShatterCells}, and the shards draw after the translucent blocks.
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 * razor-shards-appear-in-layers-ahead-of-the-dome
 * razor-keeps-its-look-gated-on-glass
 * diagnose-then-ease-crystal-reflection
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CrystalShatterVisual {

    /**
     * Full opacity alpha for ARGB packing.
     */
    private static final int FULL_ALPHA = 0xFF;
    /**
     * Max value for packing a float [0-1] into a color byte.
     */
    private static final float BYTE_SCALE = 255f;
    /**
     * Short arm multiplier for asymmetric diamonds - very short to create sliver shapes.
     */
    private static final float ASYM_SHORT_FACTOR = 0.1f;
    /**
     * An arm reaching its shard's full half-length or half-width.
     */
    private static final float REACH_FULL = 1f;
    /**
     * Minimum vector length to avoid normalizing near-zero vectors.
     */
    private static final float NORMALIZE_EPSILON = 0.001f;

    /**
     * How far to raycast for reflected block color (in blocks).
     */
    private static final double RAYCAST_RANGE = 16.0;
    /**
     * Brightness multiplier on reflected block colors.
     */
    private static final float REFLECT_BRIGHTNESS = 1.3f;
    /**
     * Alpha of a shard at rest.
     */
    private static final float BASE_ALPHA = 0.8f;
    /**
     * Fallback color when raycast misses (sky blue).
     */
    private static final int SKY_COLOR = 0x87CEEB;
    /**
     * Reflection formula coefficient (v - 2*(v.n)*n).
     */
    private static final double REFLECT_COEFF = 2.0;

    /**
     * The most faces one sliver's pyramid carries: a diamond's four.
     */
    static final int MAX_FACES_PER_SLIVER = 4;
    /**
     * Ticks a cell may go undrawn before its eased reflections are dropped.
     */
    private static final double STALE_EASING_TICKS = 100;

    private static final ShatterCells CELLS = new ShatterCells();
    private static @Nullable Level cellsLevel;

    /**
     * Each drawn cell's eased reflections, keyed by its position.
     */
    private static final Map<BlockPos, ReflectionEasing> EASINGS = new HashMap<>();

    /**
     * What one frame of one cell's shards draws.
     *
     * @param cell   the cell's block position, the corner the shards emit from
     * @param shards the cell's shards at their places this frame
     * @param layout the cell's shard layout
     * @param time   the game time the spin reads
     * @param camPos the camera eye position
     * @param probe  casts the reflection rays
     * @param clock  the client tick with its partial tick, the clock reflections ease on
     * @param easing the shown reflection color of every face of this cell
     */
    record CellDraw(BlockPos cell, List<ShatterCellShards.CellShard> shards, ShardTable layout, float time,
                    Vec3 camPos, BlockColorProbe probe, double clock, ReflectionEasing easing) {
    }

    /**
     * The color a ray finds in the world.
     */
    @FunctionalInterface
    interface BlockColorProbe {
        /**
         * Casts a ray and answers the RGB color of what it hits.
         *
         * @param origin    the ray start
         * @param direction the ray direction
         * @return the RGB color of the block hit, or the sky's on a miss
         */
        int colorAlong(Vec3 origin, Vec3 direction);
    }

    private CrystalShatterVisual() {
    }

    /**
     * Client tick: rescans the Shatter cells near the camera when due, and
     * forgets every cell once the client leaves the level it found them in.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level != cellsLevel) {
            CELLS.clear();
            EASINGS.clear();
            cellsLevel = level;
        }
        if (level != null && mc.player != null) {
            Vec3 eye = mc.player.getEyePosition();
            CELLS.rescanIfDue(level, eye, level.getGameTime());
            CELLS.tickShown(livingNear(level, eye));
        }
    }

    /**
     * Where each living thing that could raise a scanned cell's shards stands.
     *
     * @param level the client level
     * @param eye   the camera's position
     * @return the feet of every living thing within the scan's reach and a near radius past it
     */
    private static List<Vec3> livingNear(Level level, Vec3 eye) {
        double reach = ShatterCells.REACH + ShatterCells.NEAR_RADIUS;
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(reach),
                living -> living.isAlive() && !living.isSpectator()).stream().map(LivingEntity::position).toList();
    }

    /**
     * Draws the shards over every Shatter cell still standing, after the translucent blocks.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || CELLS.cells().isEmpty()) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double clock = mc.level.getGameTime() + (double) partialTick;
        EASINGS.values().removeIf(e -> Math.abs(clock - e.lastClock()) > STALE_EASING_TICKS);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.CRYSTAL_SHARD_TYPE);
        Level level = mc.level;
        Frame frame = new Frame(level, partialTick, clock, mc.player.getEyePosition(partialTick),
                (origin, direction) -> raycastBlockColor(level, origin, direction));
        CELLS.cells().forEach((pos, cell) -> drawCell(event.getPoseStack(), consumer, pos, cell, frame));
        buffers.endBatch(GooRenderTypes.CRYSTAL_SHARD_TYPE);
    }

    /**
     * What every cell drawn this frame shares.
     *
     * @param level       the client level
     * @param partialTick the frame's share of the way from the last tick to the next
     * @param clock       the game time with its partial tick
     * @param eye         the camera eye position
     * @param probe       casts the reflection rays
     */
    private record Frame(Level level, float partialTick, double clock, Vec3 eye, BlockColorProbe probe) {
    }

    /**
     * Draws one cell's shards where its cell still stands and its shards stand risen at all.
     *
     * @param poseStack the level's pose stack, at the camera
     * @param consumer  the shard render type's vertex consumer
     * @param pos       the cell's position
     * @param cell      the cell's layout and shown share
     * @param frame     what every cell drawn this frame shares
     */
    private static void drawCell(PoseStack poseStack, VertexConsumer consumer, BlockPos pos, ShatterCells.Cell cell,
                                 Frame frame) {
        BlockState state = frame.level().getBlockState(pos);
        float shown = cell.shownAt(frame.partialTick());
        if (shown <= 0f || !ShatterCells.isShatter(state)) {
            return;
        }
        List<ShatterCellShards.CellShard> shards = ShatterCellShards.shardsOver(pos,
                state.getValue(VolumeCellBlock.LAYERS), shown, cell.layout());
        submitCell(poseStack, consumer, drawOf(pos, cell.layout(), shards, frame.clock(), frame.eye(),
                frame.probe()));
    }

    /**
     * Emits one cell's shards about the cell's corner, offset from the camera.
     *
     * @param poseStack the level's pose stack, at the camera
     * @param consumer  the shard render type's vertex consumer
     * @param draw      what this frame of the cell draws
     */
    private static void submitCell(PoseStack poseStack, VertexConsumer consumer, CellDraw draw) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        poseStack.pushPose();
        poseStack.translate(draw.cell().getX() - camera.x, draw.cell().getY() - camera.y,
                draw.cell().getZ() - camera.z);
        emitCell(new FlatQuadContext(poseStack.last(), consumer), draw);
        poseStack.popPose();
    }

    /**
     * What this frame of one cell's shards draws.
     *
     * @param pos    the cell's position
     * @param layout the cell's shard layout
     * @param shards the cell's shards at their places this frame
     * @param clock  the game time with its partial tick
     * @param eye    the camera eye position
     * @param probe  casts the reflection rays
     * @return the frame's draw
     */
    private static CellDraw drawOf(BlockPos pos, ShardTable layout, List<ShatterCellShards.CellShard> shards,
                                   double clock, Vec3 eye, BlockColorProbe probe) {
        return new CellDraw(pos, shards, layout, (float) clock, eye, probe, clock, EASINGS.computeIfAbsent(pos,
                p -> new ReflectionEasing(layout.size() * MAX_FACES_PER_SLIVER)));
    }

    /**
     * Emits every shard over one cell, relative to the cell's corner.
     *
     * @param face the context the shard faces emit through
     * @param draw what this frame draws
     */
    static void emitCell(FlatQuadContext face, CellDraw draw) {
        draw.easing().beginFrame(draw.clock());
        for (ShatterCellShards.CellShard shard : draw.shards()) {
            emitSliver(face, shard, draw);
        }
    }

    /**
     * Emits one sliver as a pyramid over its shape's base ring.
     *
     * @param face  the context the shard faces emit through
     * @param shard the shard and its place this frame
     * @param draw  what this frame draws
     */
    private static void emitSliver(FlatQuadContext face, ShatterCellShards.CellShard shard, CellDraw draw) {
        int index = shard.index();
        ShardFrame frame = frameOf(shard, draw);
        Vec3 worldCenter = new Vec3(shard.center().x(), shard.center().y(), shard.center().z());
        Vector3f apex = frame.apex(draw.layout().depthRatio(index) * frame.halfLength());
        emitPyramid(face, baseRing(draw.layout().shape(index), frame), apex, draw,
                new ShardPlace(worldCenter, index * MAX_FACES_PER_SLIVER,
                        (int) (BASE_ALPHA * BYTE_SCALE * shard.opacity())));
    }

    /**
     * The sliver's frame this frame: its center relative to the cell's
     * corner, its axes turned by its spin.
     *
     * @param shard the shard and its place this frame
     * @param draw  what this frame draws
     * @return the sliver's frame
     */
    private static ShardFrame frameOf(ShatterCellShards.CellShard shard, CellDraw draw) {
        ShardTable layout = draw.layout();
        int index = shard.index();
        float halfLen = layout.halfLength(index);
        Vector3f center = new Vector3f(shard.center()).sub(draw.cell().getX(), draw.cell().getY(),
                draw.cell().getZ());
        Vector3f axis = layout.axis(index);
        Vector3f perp = layout.perp(index);
        float spinSpeed = layout.spinSpeed(index);
        if (spinSpeed > 0f) {
            Vector3f spinAxis = layout.spinAxis(index);
            float angle = draw.time() * spinSpeed;
            axis.rotateAxis(angle, spinAxis.x, spinAxis.y, spinAxis.z);
            perp.rotateAxis(angle, spinAxis.x, spinAxis.y, spinAxis.z);
        }
        return new ShardFrame(center, axis, perp, halfLen, halfLen * layout.widthRatio(index));
    }

    /**
     * The base ring a shard shape stands on, in winding order: a single
     * spike's tip and two back corners, or a diamond's four arm ends, the
     * asymmetric diamond's back arm cut short.
     *
     * @param shape the shape type (0=spike, 1=diamond, 2=asymmetric)
     * @param frame the shard's frame
     * @return the base corners
     */
    private static Vector3f[] baseRing(float shape, ShardFrame frame) {
        if (shape < ShardTable.SHAPE_DIAMOND) {
            return new Vector3f[]{frame.at(REACH_FULL, 0f),
                frame.at(-REACH_FULL, -REACH_FULL), frame.at(-REACH_FULL, REACH_FULL)};
        }
        float backArm = shape < ShardTable.SHAPE_ASYMMETRIC ? REACH_FULL : ASYM_SHORT_FACTOR;
        return new Vector3f[]{frame.at(REACH_FULL, 0f), frame.at(0f, REACH_FULL),
            frame.at(-backArm, 0f), frame.at(0f, -REACH_FULL)};
    }

    /**
     * Emits a pyramid: one triangle from each edge of the base ring up to
     * the apex, each lit by its own face normal and colored by what that
     * face reflects this frame.
     *
     * @param face  the context the faces emit through
     * @param ring  the base corners in winding order
     * @param apex  the apex
     * @param draw  what this frame draws
     * @param place where the shard stands and where its faces start in the cell
     */
    private static void emitPyramid(FlatQuadContext face, Vector3f[] ring, Vector3f apex,
                                    CellDraw draw, ShardPlace place) {
        for (int i = 0; i < ring.length; i++) {
            Vector3f start = ring[i];
            Vector3f end = ring[(i + 1) % ring.length];
            Vector3f normal = new Vector3f(end).sub(start).cross(new Vector3f(apex).sub(start));
            int color = reflectColor(draw, place, i, normal);
            ConeGeometry.emitTriangle(corner -> {
                Vector3f at = switch (corner) {
                    case ConeGeometry.BASE_START -> start;
                    case ConeGeometry.BASE_END -> end;
                    default -> apex;
                };
                face.vertex(at.x, at.y, at.z, color, normal.x, normal.y, normal.z);
            });
        }
    }

    /**
     * Where one shard stands and where its faces start among the cell's faces.
     *
     * @param worldCenter the shard's world position
     * @param firstFace   the cell-wide index of the shard's first face
     * @param alpha       the shard's vertex alpha [0-255], faded as its goo lands
     */
    private record ShardPlace(Vec3 worldCenter, int firstFace, int alpha) {
    }

    /**
     * The color a face reflects: the camera's view of the shard bounced off
     * the face's normal and cast into the world, eased from the face's last
     * shown color, brightened, at the shard's alpha.
     *
     * @param draw   what this frame draws, carrying the camera, the probe and the easing
     * @param place  where the shard stands and where its faces start
     * @param side   the face's index within its shard
     * @param normal the face normal (unnormalized)
     * @return packed ARGB with the reflected block color and the shard's alpha
     */
    private static int reflectColor(CellDraw draw, ShardPlace place, int side, Vector3f normal) {
        Vec3 worldCenter = place.worldCenter();
        float len = normal.length();
        if (len < NORMALIZE_EPSILON) {
            return ARGB.color(place.alpha(), SKY_COLOR);
        }
        float invLen = 1f / len;
        float fnx = normal.x * invLen;
        float fny = normal.y * invLen;
        float fnz = normal.z * invLen;
        Vec3 toShard = worldCenter.subtract(draw.camPos()).normalize();
        double dot = toShard.x * fnx + toShard.y * fny + toShard.z * fnz;
        Vec3 reflected = new Vec3(
                toShard.x - REFLECT_COEFF * dot * fnx,
                toShard.y - REFLECT_COEFF * dot * fny,
                toShard.z - REFLECT_COEFF * dot * fnz);
        int rgb = draw.easing().shownColor(place.firstFace() + side,
                draw.probe().colorAlong(worldCenter, reflected));
        int r = Math.min((int) (ARGB.red(rgb) * REFLECT_BRIGHTNESS), FULL_ALPHA);
        int g = Math.min((int) (ARGB.green(rgb) * REFLECT_BRIGHTNESS), FULL_ALPHA);
        int b = Math.min((int) (ARGB.blue(rgb) * REFLECT_BRIGHTNESS), FULL_ALPHA);
        return ARGB.color(Math.max(1, place.alpha()), r, g, b);
    }

    /**
     * Raycasts along a direction and returns the hit block's map color, or sky.
     *
     * @param level  the client level
     * @param origin the ray start position
     * @param dir    the ray direction
     * @return the hit block's ARGB map color, or sky color on miss
     */
    private static int raycastBlockColor(Level level, Vec3 origin, Vec3 dir) {
        Vec3 end = origin.add(dir.scale(RAYCAST_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(
                origin, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return SKY_COLOR;
        }
        MapColor mc = level.getBlockState(hit.getBlockPos()).getMapColor(level, hit.getBlockPos());
        return mc.calculateARGBColor(MapColor.Brightness.NORMAL);
    }
}
