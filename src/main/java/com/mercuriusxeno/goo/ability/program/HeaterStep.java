package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.block.ability.PrismColumn;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.HeaterFlamePayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;
import java.util.Comparator;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Blaze's combo on a prism, the heater: a stationary heat source that spews
 * Flames' own fire from the prism's tip at the nearest mob it can see within
 * the radius, and burns everything that fire touches by Flames' rule, one
 * burn every {@code every} ticks, each setting the mob alight. Never a
 * player; a fire-immune mob is passed over. The fire grows out over Flames'
 * quarter second when a mob arrives; each tick it flames, the watchers are
 * told where it points, and they draw it and roar. The step never finishes,
 * so the prism heats until mined.
 * heater-prism-flames-nearby-creatures
 * <p>
 * The operator's rulings (idea heater-prism-rulings): mobs only, never
 * players; six blocks, Flames' own reach; Flames' own fire, bouncing like
 * the hand-held one; the drawn fire burns everything it touches.
 *
 * @param radius      the reach the heater finds mobs within and pours its fire to, in blocks
 * @param damage      the fire damage each burn deals, evaluated when it runs
 * @param every       the ticks between burns
 * @param burnSeconds the seconds each burn keeps a mob alight
 * @param cone        the fire's cone angle in degrees, Flames' own
 */
public record HeaterStep(double radius, Expr damage, int every, int burnSeconds, double cone) implements Step {

    private static final String NAME = "heater";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_DAMAGE = "damage";
    private static final String FIELD_EVERY = "every";
    private static final String FIELD_BURN_SECONDS = "burn_seconds";
    private static final String FIELD_CONE = "cone";
    private static final double HALF = 0.5;
    /** Below this squared length a direction has no way. */
    private static final double NO_WAY = 1e-6;
    /** Blocks off a face a line of sight starts, so it never meets the face itself. */
    private static final double OFF_THE_FACE = 0.05;
    /** One pixel of the 16 to a block, the prism column's length being in pixels. */
    private static final double PIXEL = 1.0 / 16;
    /** Blocks past the column's end the fire pours from. */
    private static final double PAST_THE_TIP = 0.1;
    /** The deep ember orange-red a heater prism's quartz wears, and warms toward while Flames charges it. */
    public static final int EMBER = 0xE8501C;
    /** Joins a prism's level to its position in the key its flaming is held under. */
    private static final String KEY_JOIN = "@";

    /** The ticks each heating prism has flamed without a break, by level and position. */
    private static final Map<String, Integer> FLAMING = new ConcurrentHashMap<>();

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<HeaterStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.DOUBLE.fieldOf(FIELD_RADIUS).forGetter(HeaterStep::radius),
            Expr.CODEC.fieldOf(FIELD_DAMAGE).forGetter(HeaterStep::damage),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_EVERY).forGetter(HeaterStep::every),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf(FIELD_BURN_SECONDS).forGetter(HeaterStep::burnSeconds),
            Codec.DOUBLE.fieldOf(FIELD_CONE).forGetter(HeaterStep::cone)
    ).apply(inst, HeaterStep::new));

    /**
     * The registered type.
     */
    public static final StepType<HeaterStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<HeaterStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        MarkerHost host = context.hostAs(MarkerHost.class);
        ServerLevel level = host.level();
        BlockPos prism = host.position();
        String key = keyOf(level, prism);
        Vec3 tip = tipOf(prism, facingOf(level.getBlockState(prism)));
        Mob target = nearestTarget(level, tip, radius);
        if (target == null) {
            FLAMING.remove(key);
            return false;
        }
        int flamed = FLAMING.merge(key, 1, Integer::sum);
        Vec3 axis = target.getBoundingBox().getCenter().subtract(tip).normalize();
        FlameShape shape = shapeOf(level, tip, axis, flamed);
        if (flamed % every == 0) {
            float amount = damage.evaluateFloat(context);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(tip, tip).inflate(radius * FlameShape.SPLAY),
                    mob -> mob.isAlive() && shape.touches(mob.getBoundingBox()) && reachable(level, tip, shape, mob))) {
                FlamesStep.burn(level, mob, amount, burnSeconds);
            }
        }
        EntityVisuals.sendToWatchersOf(level, tip, new HeaterFlamePayload(prism, tip, axis));
        return false;
    }

    /**
     * Whether a prism is flaming a mob now.
     *
     * @param level the prism's level
     * @param prism the prism
     * @return whether it flamed one this tick
     */
    public static boolean flaming(ServerLevel level, BlockPos prism) {
        return FLAMING.containsKey(keyOf(level, prism));
    }

    private static String keyOf(ServerLevel level, BlockPos prism) {
        return level.dimension().identifier() + KEY_JOIN + prism.asLong();
    }

    /**
     * The point the heater's fire pours from: the tip of the prism's column,
     * its length out from the face it stands on.
     *
     * @param prism  the prism's cell
     * @param facing the way its column points
     * @return the tip
     */
    public static Vec3 tipOf(BlockPos prism, Direction facing) {
        // Just past the column's end: a trace starting on the crystal's own face meets the prism at once.
        double out = PrismColumn.LENGTH * PIXEL - HALF + PAST_THE_TIP;
        return Vec3.atCenterOf(prism).add(facing.getStepX() * out, facing.getStepY() * out, facing.getStepZ() * out);
    }

    private static Direction facingOf(BlockState state) {
        return state.hasProperty(PrismBlock.FACING) ? state.getValue(PrismBlock.FACING) : Direction.UP;
    }

    /**
     * The nearest mob within the radius the tip can see: never a player, and
     * a fire-immune mob passed over.
     */
    private static @Nullable Mob nearestTarget(ServerLevel level, Vec3 tip, double radius) {
        return level.getEntitiesOfClass(Mob.class, new AABB(tip, tip).inflate(radius),
                        mob -> mob.isAlive() && !mob.fireImmune()
                                && mob.getBoundingBox().getCenter().distanceTo(tip) <= radius
                                && clearBetween(level, tip, mob.getBoundingBox().getCenter()))
                .stream().min(Comparator.comparingDouble(mob -> mob.getBoundingBox().getCenter().distanceToSqr(tip)))
                .orElse(null);
    }

    /**
     * The fire's shape this tick: Flames' own, poured from the tip at the
     * target, its front grown out as far as the flaming has carried it, and
     * bouncing off any block its line meets within the reach.
     */
    private FlameShape shapeOf(ServerLevel level, Vec3 tip, Vec3 axis, int flamed) {
        Vec3 side = new Vec3(-axis.z, 0, axis.x);
        side = side.lengthSqr() > NO_WAY ? side.normalize() : new Vec3(1, 0, 0);
        BlockHitResult hit = level.clip(new ClipContext(tip, tip.add(axis.scale(radius)), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        boolean meets = hit.getType() == HitResult.Type.BLOCK;
        Direction face = hit.getDirection();
        return new FlameShape(tip, axis, side, Math.tan(Math.toRadians(cone * HALF)),
                FlameShape.frontAfter(flamed, radius), meets ? hit.getLocation() : null,
                meets ? new Vec3(face.getStepX(), face.getStepY(), face.getStepZ()) : null);
    }

    private static boolean reachable(ServerLevel level, Vec3 tip, FlameShape shape, Mob mob) {
        Vec3 middle = mob.getBoundingBox().getCenter();
        if (clearBetween(level, tip, middle)) {
            return true;
        }
        Vec3 hit = shape.hit();
        return hit != null && clearBetween(level, hit.add(shape.normal().scale(OFF_THE_FACE)), middle);
    }

    private static boolean clearBetween(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty())).getType() != HitResult.Type.BLOCK;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(damage);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TICKING, HostCapability.ENTITY_SCAN);
    }

    @Override
    public OptionalInt prismTint() {
        return OptionalInt.of(EMBER);
    }
}
