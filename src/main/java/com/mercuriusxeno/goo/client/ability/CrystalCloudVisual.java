package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.FieldEffectState;
import com.mercuriusxeno.goo.ability.program.FieldEffectStep;
import com.mercuriusxeno.goo.ability.program.MarkerVariables;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ber.ChainMarkerRenderState;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders the crystal shard cloud as scattered glass splinters floating
 * in air within the cloud volume. Each sliver is a thin elongated pyramid,
 * a sixth to a half of a block long and as wide as a tenth of its length,
 * at a position and orientation drawn from its cloud's seed. Some tumble
 * slowly, most are still. The cloud's radius, its expand and contract fraction and its charge
 * density come from the crystal_cloud field effect's
 * {@link FieldEffectState} on the marker. Each cloud's shards come from
 * its own layout in {@link CloudShardTables}.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CrystalCloudVisual {

    private static final float BLOCK_CENTER = 0.5f;

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
     * Base alpha for shards.
     */
    private static final float BASE_ALPHA = 0.8f;
    /**
     * Fallback color when raycast misses (sky blue).
     */
    private static final int SKY_COLOR = 0x87CEEB;
    /**
     * Minimum density floor for alpha calculation.
     */
    private static final float MIN_DENSITY_FLOOR = 0.2f;
    /**
     * Reflection formula coefficient (v - 2*(v.n)*n).
     */
    private static final double REFLECT_COEFF = 2.0;

    /**
     * The most faces one sliver's pyramid carries: a diamond's four.
     */
    static final int MAX_FACES_PER_SLIVER = 4;
    /**
     * Ticks a cloud may go undrawn before its eased reflections are dropped.
     */
    private static final double STALE_EASING_TICKS = 100;

    private static final CloudShardTables TABLES = new CloudShardTables();
    private static @Nullable Level tablesLevel;

    /**
     * Each drawn marker's eased reflections, keyed by its packed block position.
     */
    private static final Map<Long, ReflectionEasing> EASINGS = new HashMap<>();

    /**
     * What one frame of one cloud draws.
     *
     * @param visibleCount how many slivers show
     * @param alpha        the vertex alpha [0-255]
     * @param time         the game time the spin reads
     * @param radius       the current cloud radius
     * @param shards       the cloud's shard layout
     * @param origin       the marker block's world corner
     * @param camPos       the camera eye position
     * @param probe        casts the reflection rays
     * @param clock        the client tick with its partial tick, the clock reflections ease on
     * @param easing       the shown reflection color of every face of this cloud
     */
    record CloudDraw(int visibleCount, int alpha, float time, float radius, ShardTable shards,
                     Vec3 origin, Vec3 camPos, BlockColorProbe probe,
                     double clock, ReflectionEasing easing) {
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

    private CrystalCloudVisual() {
    }

    /**
     * Populates {@code state} with crystal-cloud fields: the density and
     * the expand and contract progress from the marker's field-effect state,
     * the radius and animation lengths off the field-effect step of the
     * marker's synced ability.
     *
     * @param be    the chain marker block entity
     * @param state the render state to populate
     */
    public static void extract(ChainMarkerBlockEntity be, ChainMarkerRenderState state) {
        FieldEffectStep cloud = runningCloud(be);
        if (cloud == null) {
            TABLES.dropAt(be.getBlockPos());
            clear(state);
            return;
        }
        MarkerVariables variables = new MarkerVariables(be);
        int expandTicks = cloud.timing().expandTicks().evaluateInt(variables);
        int contractTicks = cloud.timing().contractTicks().evaluateInt(variables);
        FieldEffectState field = be.getFieldEffect();
        if (field.density() <= 0f && !field.isAnimating(expandTicks, contractTicks)) {
            TABLES.dropAt(be.getBlockPos());
            clear(state);
            return;
        }
        state.crystalActive = true;
        state.crystalDensity = field.density();
        state.crystalRadiusFraction = field.radiusFraction(expandTicks, contractTicks);
        state.crystalRadius = cloud.radius().evaluateFloat(variables);
        long gameTime = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        state.crystalAnimationTime = gameTime;
        state.crystalReflectionClock = gameTime;
    }

    /**
     * Finds the field-effect step a crystal marker runs, read off its
     * synced ability.
     *
     * @param be the chain marker block entity
     * @return the step, or null when the marker is no running crystal field
     */
    private static @Nullable FieldEffectStep runningCloud(ChainMarkerBlockEntity be) {
        if (!GooTypes.CRYSTAL.equals(be.getGooType()) || be.getBehavior() == null) {
            return null;
        }
        return SyncedSteps.first(be, FieldEffectStep.class).orElse(null);
    }

    /**
     * Clears the crystal-cloud fields of a marker drawing no cloud.
     *
     * @param state the render state to clear
     */
    private static void clear(ChainMarkerRenderState state) {
        state.crystalActive = false;
        state.crystalDensity = 0f;
        state.crystalRadiusFraction = 0f;
        state.crystalRadius = 0f;
        state.crystalAnimationTime = 0f;
        state.crystalReflectionClock = 0;
    }

    /**
     * Client tick: forgets the layout of every cloud whose marker left or
     * stopped running a crystal field, and every layout once the client
     * leaves the level it drew them in.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Level level = Minecraft.getInstance().level;
        if (level != tablesLevel) {
            TABLES.dropAll();
            tablesLevel = level;
        }
        if (level != null) {
            TABLES.retainClouds(pos -> holdsCloud(level, pos));
        }
    }

    /**
     * Whether a position still holds a crystal marker running its cloud.
     *
     * @param level the client level
     * @param pos   the marker position
     * @return true while the cloud stands
     */
    private static boolean holdsCloud(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be && runningCloud(be) != null;
    }

    /**
     * Submits crystal shard splinters for rendering.
     *
     * @param state         the chain marker render state
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     */
    public static void submit(ChainMarkerRenderState state,
                              PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        if (state.crystalRadiusFraction <= 0f) {
            TABLES.dropAt(state.blockPos);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        CloudDraw draw = drawOf(state, mc.level, mc.player.getEyePosition(state.partialTick));
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.CRYSTAL_SHARD_TYPE,
                (pose, c) -> emitCloud(new FlatQuadContext(pose, c), draw));
    }

    /**
     * What this frame of the marker's cloud draws.
     *
     * @param state  the chain marker render state
     * @param level  the client level the rays are cast in
     * @param camPos the camera eye position
     * @return the frame's draw
     */
    private static CloudDraw drawOf(ChainMarkerRenderState state, Level level, Vec3 camPos) {
        float radiusFrac = state.crystalRadiusFraction;
        float density = state.crystalDensity;
        double clock = state.crystalReflectionClock + state.partialTick;
        return new CloudDraw(
                Math.max(1, (int) (ShardTable.MAX_SLIVERS * Math.max(density, radiusFrac))),
                (int) (BASE_ALPHA * Math.max(density, MIN_DENSITY_FLOOR) * radiusFrac * BYTE_SCALE),
                state.crystalAnimationTime,
                state.crystalRadius * radiusFrac,
                TABLES.tableAt(state.blockPos),
                Vec3.atLowerCornerOf(state.blockPos),
                camPos,
                (origin, direction) -> raycastBlockColor(level, origin, direction),
                clock,
                easingFor(state.blockPos.asLong(), clock));
    }

    /**
     * The eased reflections of the marker at a position, dropping every
     * cloud's that has gone undrawn long enough to have left the view.
     *
     * @param packedPos the marker's packed block position
     * @param clock     this frame's clock
     * @return the marker's eased reflections
     */
    private static ReflectionEasing easingFor(long packedPos, double clock) {
        EASINGS.values().removeIf(e -> Math.abs(clock - e.lastClock()) > STALE_EASING_TICKS);
        return EASINGS.computeIfAbsent(packedPos,
                p -> new ReflectionEasing(ShardTable.MAX_SLIVERS * MAX_FACES_PER_SLIVER));
    }

    /**
     * Emits every visible sliver of one cloud.
     *
     * @param face the context the shard faces emit through
     * @param draw what this frame draws
     */
    static void emitCloud(FlatQuadContext face, CloudDraw draw) {
        draw.easing().beginFrame(draw.clock());
        for (int i = 0; i < draw.visibleCount(); i++) {
            emitSliver(face, i, draw);
        }
    }

    /**
     * Emits one sliver as a pyramid over its shape's base ring.
     *
     * @param face  the context the shard faces emit through
     * @param index the sliver index
     * @param draw  what this frame draws
     */
    private static void emitSliver(FlatQuadContext face, int index, CloudDraw draw) {
        ShardFrame frame = frameOf(index, draw);
        Vec3 worldCenter = draw.origin().add(frame.center().x(), frame.center().y(), frame.center().z());
        Vector3f apex = frame.apex(draw.shards().depthRatio(index) * frame.halfLength());
        emitPyramid(face, baseRing(draw.shards().shape(index), frame), apex, draw,
                new ShardPlace(worldCenter, index * MAX_FACES_PER_SLIVER));
    }

    /**
     * The sliver's frame this frame: its center scaled by the cloud radius,
     * its axes turned by its spin.
     *
     * @param index the sliver index
     * @param draw  what this frame draws
     * @return the sliver's frame
     */
    private static ShardFrame frameOf(int index, CloudDraw draw) {
        ShardTable shards = draw.shards();
        float halfLen = shards.halfLength(index);
        Vector3f center = shards.center(index).mul(draw.radius()).add(BLOCK_CENTER, BLOCK_CENTER, BLOCK_CENTER);
        Vector3f axis = shards.axis(index);
        Vector3f perp = shards.perp(index);
        float spinSpeed = shards.spinSpeed(index);
        if (spinSpeed > 0f) {
            Vector3f spinAxis = shards.spinAxis(index);
            float angle = draw.time() * spinSpeed;
            axis.rotateAxis(angle, spinAxis.x, spinAxis.y, spinAxis.z);
            perp.rotateAxis(angle, spinAxis.x, spinAxis.y, spinAxis.z);
        }
        return new ShardFrame(center, axis, perp, halfLen, halfLen * shards.widthRatio(index));
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
     * @param face        the context the faces emit through
     * @param ring        the base corners in winding order
     * @param apex        the apex
     * @param draw        what this frame draws
     * @param place       where the shard stands and where its faces start in the cloud
     */
    private static void emitPyramid(FlatQuadContext face, Vector3f[] ring, Vector3f apex,
                                    CloudDraw draw, ShardPlace place) {
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
     * Where one shard stands and where its faces start among the cloud's faces.
     *
     * @param worldCenter the shard's world position
     * @param firstFace   the cloud-wide index of the shard's first face
     */
    private record ShardPlace(Vec3 worldCenter, int firstFace) {
    }

    /**
     * The color a face reflects: the camera's view of the shard bounced off
     * the face's normal and cast into the world, eased from the face's last
     * shown color, brightened, at the draw's alpha.
     *
     * @param draw   what this frame draws, carrying the camera, the probe and the easing
     * @param place  where the shard stands and where its faces start
     * @param side   the face's index within its shard
     * @param normal the face normal (unnormalized)
     * @return packed ARGB with the reflected block color and the draw's alpha
     */
    private static int reflectColor(CloudDraw draw, ShardPlace place, int side, Vector3f normal) {
        Vec3 worldCenter = place.worldCenter();
        float len = normal.length();
        if (len < NORMALIZE_EPSILON) {
            return ARGB.color(draw.alpha(), SKY_COLOR);
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
        return ARGB.color(Math.max(1, draw.alpha()), r, g, b);
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
                origin, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return SKY_COLOR;
        }
        MapColor mc = level.getBlockState(hit.getBlockPos()).getMapColor(level, hit.getBlockPos());
        return mc.calculateARGBColor(MapColor.Brightness.NORMAL);
    }
}
