package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.program.FieldEffectState;
import com.mercuriusxeno.goo.ability.program.FieldEffectStep;
import com.mercuriusxeno.goo.ability.program.MarkerVariables;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ber.ChainMarkerRenderState;
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
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Renders the crystal shard cloud as scattered glass splinters floating
 * in air within the cloud volume. Each sliver is a thin elongated quad
 * at a random position and orientation. Some tumble slowly, most are still.
 * The cloud's radius, its expand and contract fraction and its charge
 * density come from the crystal_cloud field effect's
 * {@link FieldEffectState} on the marker.
 */
public final class CrystalCloudVisual {

    private static final float BLOCK_CENTER = 0.5f;

    /**
     * Total slivers at full charge.
     */
    private static final int MAX_SLIVERS = 256;
    /**
     * Half-length of each sliver quad along its long axis.
     */
    private static final float SLIVER_HALF_LENGTH = 0.08f;
    /**
     * Full opacity alpha for ARGB packing.
     */
    private static final int FULL_ALPHA = 0xFF;
    /**
     * Max value for packing a float [0-1] into a color byte.
     */
    private static final float BYTE_SCALE = 255f;
    /**
     * Seed for deterministic sliver placement.
     */
    private static final long SLIVER_SEED = 0xC5745_5A4DL;

    /**
     * Probability that a sliver has zero spin.
     */
    private static final float NO_SPIN_CHANCE = 0.3f;
    /**
     * Maximum spin speed in radians per tick for spinning shards.
     */
    private static final float MAX_SPIN_SPEED = 0.06f;
    /**
     * Minimum spin speed when spinning.
     */
    private static final float MIN_SPIN_SPEED = 0.008f;

    /**
     * Pre-computed per-sliver data stride. Layout:
     * [cx, cy, cz, axisX, axisY, axisZ, perpX, perpY, perpZ, halfLen,
     * spinAxisX, spinAxisY, spinAxisZ, spinSpeed]
     */
    private static final int SLIVER_STRIDE = 17;
    private static final int OFF_CY = 1;
    private static final int OFF_CZ = 2;
    private static final int OFF_AX = 3;
    private static final int OFF_AY = 4;
    private static final int OFF_AZ = 5;
    private static final int OFF_PX = 6;
    private static final int OFF_PY = 7;
    private static final int OFF_PZ = 8;
    private static final int OFF_HALF_LEN = 9;
    private static final int OFF_SPIN_AX = 10;
    private static final int OFF_SPIN_AY = 11;
    private static final int OFF_SPIN_AZ = 12;
    private static final int OFF_SPIN_SPEED = 13;
    /**
     * 0 = single spike (triangle), 1 = diamond, 2 = asymmetric diamond.
     */
    private static final int OFF_SHAPE = 14;
    /**
     * Width ratio: how wide the perp arm is relative to half-length.
     */
    private static final int OFF_WIDTH_RATIO = 15;
    /**
     * Pyramid depth: how far the apex protrudes along the face normal.
     */
    private static final int OFF_DEPTH = 16;

    private static final float SHAPE_SINGLE_SPIKE = 0f;
    private static final float SHAPE_DIAMOND = 1f;
    private static final float SHAPE_ASYMMETRIC = 2f;
    /**
     * Chance of single spike vs diamond shapes.
     */
    private static final float SINGLE_SPIKE_CHANCE = 0.2f;
    /**
     * Chance of symmetric diamond (of the non-spike remainder).
     */
    private static final float SYMMETRIC_CHANCE = 0.15f;
    /**
     * Min width ratio for perpendicular arm.
     */
    private static final float MIN_WIDTH_RATIO = 0.02f;
    /**
     * Max width ratio for perpendicular arm.
     */
    private static final float MAX_WIDTH_RATIO = 0.10f;
    /**
     * Min pyramid depth as fraction of half-length.
     */
    private static final float MIN_DEPTH_RATIO = 0.08f;
    /**
     * Max pyramid depth as fraction of half-length.
     */
    private static final float MAX_DEPTH_RATIO = 0.25f;
    /**
     * Short arm multiplier for asymmetric diamonds - very short to create sliver shapes.
     */
    private static final float ASYM_SHORT_FACTOR = 0.1f;
    /**
     * An arm reaching its shard's full half-length or half-width.
     */
    private static final float REACH_FULL = 1f;
    /**
     * Maps [-1,1] random floats into [-radius, radius] range.
     */
    private static final float RNG_RANGE = 2f;
    /**
     * Minimum half-length scale factor for size variation.
     */
    private static final float MIN_LEN_SCALE = 0.5f;
    /**
     * Range of half-length scale variation added to min.
     */
    private static final float LEN_SCALE_RANGE = 1.0f;
    /**
     * Threshold for near-parallel detection in perpendicular vector construction.
     */
    private static final float PARALLEL_THRESHOLD = 0.9f;
    /**
     * Minimum vector length to avoid normalizing near-zero vectors.
     */
    private static final float NORMALIZE_EPSILON = 0.001f;
    /**
     * Z index in a 3-element vector array.
     */
    private static final int VEC_Z = 2;
    /**
     * Offset of perp X in the resolved axes array {ax,ay,az,px,py,pz}.
     */
    private static final int AXES_PX = 3;
    /**
     * Offset of perp Y in the resolved axes array.
     */
    private static final int AXES_PY = 4;
    /**
     * Offset of perp Z in the resolved axes array.
     */
    private static final int AXES_PZ = 5;

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
     * Client ticks a cloud may go undrawn before its sampler is dropped.
     */
    private static final long SAMPLER_IDLE_TICKS = 20L;

    private static final float[] SLIVER_DATA = buildSliverData();

    /** Each drawn cloud's reflection sampler, keyed by its marker. */
    private static final Map<BlockPos, ReflectionSampler> SAMPLERS = new HashMap<>();

    /**
     * What one frame of one cloud draws.
     *
     * @param visibleCount how many slivers show
     * @param alpha        the vertex alpha [0-255]
     * @param time         the game time the spin reads
     * @param radius       the current cloud radius
     * @param origin       the marker block's world corner
     * @param camPos       the camera eye position
     * @param tick         the client tick being drawn
     * @param probe        casts the reflection rays
     */
    record CloudDraw(int visibleCount, int alpha, float time, float radius,
                     Vec3 origin, Vec3 camPos, long tick, ReflectionSampler.BlockColorProbe probe) {
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
            clear(state);
            return;
        }
        MarkerVariables variables = new MarkerVariables(be);
        int expandTicks = cloud.timing().expandTicks().evaluateInt(variables);
        int contractTicks = cloud.timing().contractTicks().evaluateInt(variables);
        FieldEffectState field = be.getFieldEffect();
        if (field.density() <= 0f && !field.isAnimating(expandTicks, contractTicks)) {
            clear(state);
            return;
        }
        state.crystalActive = true;
        state.crystalDensity = field.density();
        state.crystalRadiusFraction = field.radiusFraction(expandTicks, contractTicks);
        state.crystalRadius = cloud.radius().evaluateFloat(variables);
        state.crystalAnimationTime = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
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
        float radiusFrac = state.crystalRadiusFraction;
        if (radiusFrac <= 0f) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        BlockPos bePos = state.blockPos.immutable();
        CloudDraw draw = drawOf(state, mc.level, mc.player.getEyePosition(state.partialTick));
        ReflectionSampler sampler = samplerFor(bePos, draw.tick());
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.CRYSTAL_SHARD_TYPE,
                (pose, c) -> emitCloud(new FlatQuadContext(pose, c), draw, sampler));
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
        return new CloudDraw(
                Math.max(1, (int) (MAX_SLIVERS * Math.max(density, radiusFrac))),
                (int) (BASE_ALPHA * Math.max(density, MIN_DENSITY_FLOOR) * radiusFrac * BYTE_SCALE),
                state.crystalAnimationTime,
                state.crystalRadius * radiusFrac,
                Vec3.atLowerCornerOf(state.blockPos),
                camPos,
                level.getGameTime(),
                (origin, direction) -> raycastBlockColor(level, origin, direction));
    }

    /**
     * The sampler holding a marker's reflections, dropping those of clouds
     * that went undrawn.
     *
     * @param pos  the marker position
     * @param tick the client tick being drawn
     * @return the marker's sampler
     */
    private static ReflectionSampler samplerFor(BlockPos pos, long tick) {
        SAMPLERS.values().removeIf(sampler -> sampler.idleSince(tick, SAMPLER_IDLE_TICKS));
        return SAMPLERS.computeIfAbsent(pos, p -> new ReflectionSampler(MAX_SLIVERS));
    }

    /**
     * Emits every visible sliver of one cloud.
     *
     * @param face    the context the shard faces emit through
     * @param draw    what this frame draws
     * @param sampler the cloud's reflection sampler
     */
    static void emitCloud(FlatQuadContext face, CloudDraw draw, ReflectionSampler sampler) {
        for (int i = 0; i < draw.visibleCount(); i++) {
            emitSliver(face, i, draw, sampler);
        }
    }

    /**
     * Emits one sliver as a pyramid over its shape's base ring, colored by
     * the reflection its sampler holds for this tick.
     *
     * @param face    the context the shard faces emit through
     * @param index   the sliver index
     * @param draw    what this frame draws
     * @param sampler the cloud's reflection sampler
     */
    private static void emitSliver(FlatQuadContext face, int index, CloudDraw draw, ReflectionSampler sampler) {
        int off = index * SLIVER_STRIDE;
        ShardFrame frame = frameOf(off, draw);
        Vec3 worldCenter = draw.origin().add(frame.center().x(), frame.center().y(), frame.center().z());
        int rgb = sampler.colorFor(index, draw.tick(),
                () -> reflectedColor(draw, worldCenter, frame.normal()));
        int color = ARGB.color(Math.max(1, draw.alpha()), rgb);
        Vector3f apex = frame.apex(SLIVER_DATA[off + OFF_DEPTH] * frame.halfLength());
        emitPyramid(face, baseRing(SLIVER_DATA[off + OFF_SHAPE], frame), apex, color);
    }

    /**
     * The sliver's frame this frame: its center scaled by the cloud radius,
     * its axes turned by its spin.
     *
     * @param off  the sliver data offset
     * @param draw what this frame draws
     * @return the sliver's frame
     */
    private static ShardFrame frameOf(int off, CloudDraw draw) {
        float halfLen = SLIVER_DATA[off + OFF_HALF_LEN];
        float[] axes = resolveAxes(off, draw.time());
        Vector3f center = new Vector3f(
                BLOCK_CENTER + SLIVER_DATA[off] * draw.radius(),
                BLOCK_CENTER + SLIVER_DATA[off + OFF_CY] * draw.radius(),
                BLOCK_CENTER + SLIVER_DATA[off + OFF_CZ] * draw.radius());
        return new ShardFrame(center,
                new Vector3f(axes[0], axes[1], axes[VEC_Z]),
                new Vector3f(axes[AXES_PX], axes[AXES_PY], axes[AXES_PZ]),
                halfLen, halfLen * SLIVER_DATA[off + OFF_WIDTH_RATIO]);
    }

    /**
     * Returns {ax, ay, az, px, py, pz} after applying spin rotation if any.
     *
     * @param off  the sliver data offset
     * @param time the game time for spin animation
     * @return the axis and perp vectors, possibly rotated
     */
    private static float[] resolveAxes(int off, float time) {
        float ax = SLIVER_DATA[off + OFF_AX];
        float ay = SLIVER_DATA[off + OFF_AY];
        float az = SLIVER_DATA[off + OFF_AZ];
        float px = SLIVER_DATA[off + OFF_PX];
        float py = SLIVER_DATA[off + OFF_PY];
        float pz = SLIVER_DATA[off + OFF_PZ];
        float spinSpeed = SLIVER_DATA[off + OFF_SPIN_SPEED];
        if (spinSpeed <= 0f) {
            return new float[]{ax, ay, az, px, py, pz};
        }
        float angle = time * spinSpeed;
        float sax = SLIVER_DATA[off + OFF_SPIN_AX];
        float say = SLIVER_DATA[off + OFF_SPIN_AY];
        float saz = SLIVER_DATA[off + OFF_SPIN_AZ];
        float[] ra = rodrigues(ax, ay, az, sax, say, saz, angle);
        float[] rp = rodrigues(px, py, pz, sax, say, saz, angle);
        return new float[]{ra[0], ra[1], ra[VEC_Z], rp[0], rp[1], rp[VEC_Z]};
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
        if (shape < SHAPE_DIAMOND) {
            return new Vector3f[]{frame.at(REACH_FULL, 0f),
                frame.at(-REACH_FULL, -REACH_FULL), frame.at(-REACH_FULL, REACH_FULL)};
        }
        float backArm = shape < SHAPE_ASYMMETRIC ? REACH_FULL : ASYM_SHORT_FACTOR;
        return new Vector3f[]{frame.at(REACH_FULL, 0f), frame.at(0f, REACH_FULL),
            frame.at(-backArm, 0f), frame.at(0f, -REACH_FULL)};
    }

    /**
     * Emits a pyramid: one triangle from each edge of the base ring up to
     * the apex, each lit by its own face normal.
     *
     * @param face  the context the faces emit through
     * @param ring  the base corners in winding order
     * @param apex  the apex
     * @param color the ARGB color every face takes
     */
    private static void emitPyramid(FlatQuadContext face, Vector3f[] ring, Vector3f apex, int color) {
        for (int i = 0; i < ring.length; i++) {
            Vector3f start = ring[i];
            Vector3f end = ring[(i + 1) % ring.length];
            Vector3f normal = new Vector3f(end).sub(start).cross(new Vector3f(apex).sub(start));
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
     * Samples the color the shard reflects: the camera's view of it bounced
     * off its face normal and cast into the world.
     *
     * @param draw        what this frame draws, carrying the camera and the probe
     * @param worldCenter the shard's world position
     * @param normal      the shard's unit face normal
     * @return the brightened RGB color the ray finds
     */
    private static int reflectedColor(CloudDraw draw, Vec3 worldCenter, Vector3f normal) {
        Vec3 toShard = worldCenter.subtract(draw.camPos()).normalize();
        double dot = toShard.x * normal.x + toShard.y * normal.y + toShard.z * normal.z;
        Vec3 reflected = new Vec3(
                toShard.x - REFLECT_COEFF * dot * normal.x,
                toShard.y - REFLECT_COEFF * dot * normal.y,
                toShard.z - REFLECT_COEFF * dot * normal.z);
        int rgb = draw.probe().colorAlong(worldCenter, reflected);
        return ARGB.color(0,
                Math.min((int) (ARGB.red(rgb) * REFLECT_BRIGHTNESS), FULL_ALPHA),
                Math.min((int) (ARGB.green(rgb) * REFLECT_BRIGHTNESS), FULL_ALPHA),
                Math.min((int) (ARGB.blue(rgb) * REFLECT_BRIGHTNESS), FULL_ALPHA));
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

    /**
     * Rodrigues rotation: rotates vector (vx,vy,vz) around unit axis (ux,uy,uz) by angle.
     *
     * @param vx    vector X
     * @param vy    vector Y
     * @param vz    vector Z
     * @param ux    rotation axis X (unit)
     * @param uy    rotation axis Y (unit)
     * @param uz    rotation axis Z (unit)
     * @param angle rotation angle in radians
     * @return rotated vector as {x, y, z}
     */
    private static float[] rodrigues(float vx, float vy, float vz,
                                     float ux, float uy, float uz, float angle) {
        float cosA = (float) Math.cos(angle);
        float sinA = (float) Math.sin(angle);
        float dot = ux * vx + uy * vy + uz * vz;
        // cross(u, v)
        float kx = uy * vz - uz * vy;
        float ky = uz * vx - ux * vz;
        float kz = ux * vy - uy * vx;
        return new float[]{
                vx * cosA + kx * sinA + ux * dot * (1f - cosA),
                vy * cosA + ky * sinA + uy * dot * (1f - cosA),
                vz * cosA + kz * sinA + uz * dot * (1f - cosA),
        };
    }


    /**
     * Builds deterministic sliver positions, orientations, sizes, and spin data.
     *
     * @return packed float array of sliver geometry data
     */
    private static float[] buildSliverData() {
        Random rng = new Random(SLIVER_SEED);
        float[] data = new float[MAX_SLIVERS * SLIVER_STRIDE];
        for (int i = 0; i < MAX_SLIVERS; i++) {
            buildOneSliver(rng, data, i * SLIVER_STRIDE);
        }
        return data;
    }

    /**
     * Populates one sliver's geometry and spin parameters.
     *
     * @param rng  the seeded random source
     * @param data the output float array
     * @param off  the write offset into data
     */
    private static void buildOneSliver(Random rng, float[] data, int off) {
        Vector3f pos = randomUnitSpherePoint(rng);
        data[off] = pos.x();
        data[off + OFF_CY] = pos.y();
        data[off + OFF_CZ] = pos.z();
        Vector3f axis = randomUnitVector(rng);
        data[off + OFF_AX] = axis.x();
        data[off + OFF_AY] = axis.y();
        data[off + OFF_AZ] = axis.z();
        Vector3f perp = buildPerp(axis);
        data[off + OFF_PX] = perp.x();
        data[off + OFF_PY] = perp.y();
        data[off + OFF_PZ] = perp.z();
        data[off + OFF_HALF_LEN] = SLIVER_HALF_LENGTH * (MIN_LEN_SCALE + rng.nextFloat() * LEN_SCALE_RANGE);
        buildShapeData(rng, data, off);
        buildSpinData(rng, data, off);
    }

    /**
     * Assigns a random shape type and width ratio to the shard.
     *
     * @param rng  the random source
     * @param data the output array
     * @param off  the sliver offset
     */
    private static void buildShapeData(Random rng, float[] data, int off) {
        float roll = rng.nextFloat();
        if (roll < SINGLE_SPIKE_CHANCE) {
            data[off + OFF_SHAPE] = SHAPE_SINGLE_SPIKE;
        } else if (roll < SINGLE_SPIKE_CHANCE + SYMMETRIC_CHANCE) {
            data[off + OFF_SHAPE] = SHAPE_DIAMOND;
        } else {
            data[off + OFF_SHAPE] = SHAPE_ASYMMETRIC;
        }
        data[off + OFF_WIDTH_RATIO] = MIN_WIDTH_RATIO + rng.nextFloat() * (MAX_WIDTH_RATIO - MIN_WIDTH_RATIO);
        data[off + OFF_DEPTH] = MIN_DEPTH_RATIO + rng.nextFloat() * (MAX_DEPTH_RATIO - MIN_DEPTH_RATIO);
    }

    /**
     * Assigns spin axis and speed. Many shards are still, some tumble slowly.
     *
     * @param rng  the random source
     * @param data the output array
     * @param off  the sliver offset
     */
    private static void buildSpinData(Random rng, float[] data, int off) {
        if (rng.nextFloat() < NO_SPIN_CHANCE) {
            data[off + OFF_SPIN_SPEED] = 0f;
            return;
        }
        Vector3f spinAxis = randomUnitVector(rng);
        data[off + OFF_SPIN_AX] = spinAxis.x();
        data[off + OFF_SPIN_AY] = spinAxis.y();
        data[off + OFF_SPIN_AZ] = spinAxis.z();
        data[off + OFF_SPIN_SPEED] = MIN_SPIN_SPEED + rng.nextFloat() * (MAX_SPIN_SPEED - MIN_SPIN_SPEED);
    }

    /**
     * Returns a random point uniformly distributed inside the unit sphere.
     *
     * @param rng the seeded random source
     * @return a point with length less than 1
     */
    private static Vector3f randomUnitSpherePoint(Random rng) {
        float px;
        float py;
        float pz;
        do {
            px = rng.nextFloat() * RNG_RANGE - 1f;
            py = rng.nextFloat() * RNG_RANGE - 1f;
            pz = rng.nextFloat() * RNG_RANGE - 1f;
        } while (px * px + py * py + pz * pz > 1f);
        return new Vector3f(px, py, pz);
    }

    private static Vector3f randomUnitVector(Random rng) {
        float x = rng.nextFloat() * RNG_RANGE - 1f;
        float y = rng.nextFloat() * RNG_RANGE - 1f;
        float z = rng.nextFloat() * RNG_RANGE - 1f;
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len < NORMALIZE_EPSILON) {
            return new Vector3f(0f, 1f, 0f);
        }
        return new Vector3f(x / len, y / len, z / len);
    }

    private static Vector3f buildPerp(Vector3f axis) {
        float ax = axis.x();
        float ay = axis.y();
        float az = axis.z();
        float sx = Math.abs(ay) < PARALLEL_THRESHOLD ? 0f : 1f;
        float sy = Math.abs(ay) < PARALLEL_THRESHOLD ? 1f : 0f;
        float px = ay * 0f - az * sy;
        float py = az * sx - ax * 0f;
        float pz = ax * sy - ay * sx;
        float len = (float) Math.sqrt(px * px + py * py + pz * pz);
        return new Vector3f(px / len, py / len, pz / len);
    }
}
