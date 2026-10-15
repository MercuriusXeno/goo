package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Flames' held pass, run each held tick of the stream: every {@code every}
 * held ticks it burns each living thing the fire's {@link FlameShape}
 * touches, the jet and its bounce off the face the crosshair meets, never
 * behind that face, keeping it alight for the JSON's seconds and dealing the
 * JSON's fire damage through its damage immunity. A fire-immune thing is left
 * alone, and no fire block is placed.
 * decision flames-pixelated-fire-stream
 *
 * @param damage      the fire damage each burn deals, evaluated when it runs
 * @param every       the held ticks between burns
 * @param burnSeconds the seconds each burn keeps the thing alight
 */
public record FlamesStep(Expr damage, int every, int burnSeconds) implements Step {

    private static final String NAME = "flames";
    private static final String FIELD_DAMAGE = "damage";
    private static final String FIELD_EVERY = "every";
    private static final String FIELD_BURN_SECONDS = "burn_seconds";
    private static final double HALF = 0.5;
    /** Below this squared length a direction has no way. */
    private static final double NO_WAY = 1e-6;
    /** Blocks off the face a bounce's line of sight starts, so it never meets the face itself. */
    private static final double OFF_THE_FACE = 0.05;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<FlamesStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_DAMAGE).forGetter(FlamesStep::damage),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_EVERY).forGetter(FlamesStep::every),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf(FIELD_BURN_SECONDS).forGetter(FlamesStep::burnSeconds)
    ).apply(inst, FlamesStep::new));

    /**
     * The registered type.
     */
    public static final StepType<FlamesStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<FlamesStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        host.channelAim().filter(aim -> BroilStep.pulsesOn(aim.held(), every)).ifPresent(aim -> {
            float amount = damage.evaluateFloat(context);
            FlameShape shape = shapeOf(host, aim);
            host.forEachLivingIn(cellsAround(host.eye(), aim), Set.of(), target -> {
                if (shape.touches(target.target().getBoundingBox()) && reachable(host, shape, target.target())) {
                    burn(host.level(), target.target(), amount, burnSeconds);
                }
            });
        });
        return true;
    }

    /**
     * The fire's shape this held tick: the operator's ruling, damage follows
     * the drawn fire. The jet pours down the look from the eye, its front
     * pushed out as far as the hold has carried it, and where the crosshair
     * meets a block, the fire bounces off the face the crosshair meets, as
     * the client draws it.
     *
     * @param host the channel host
     * @param aim  the stream's aim, its reach along the look
     * @return the shape
     */
    static FlameShape shapeOf(ChannelHost host, ChannelAim aim) {
        Vec3 eye = host.eye();
        Vec3 look = aim.aimPoint().subtract(eye);
        double range = look.length();
        Vec3 axis = range > 0 ? look.scale(1 / range) : new Vec3(0, 0, 1);
        Vec3 side = new Vec3(-axis.z, 0, axis.x);
        side = side.lengthSqr() > NO_WAY ? side.normalize() : new Vec3(1, 0, 0);
        double tan = Math.tan(Math.toRadians(aim.coneDegrees() * HALF));
        BlockHitResult hit = host.level().clip(new ClipContext(eye, aim.aimPoint(), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        boolean meets = hit.getType() == HitResult.Type.BLOCK;
        Direction face = hit.getDirection();
        return new FlameShape(eye, axis, side, tan, FlameShape.frontAfter(aim.held(), range),
                meets ? hit.getLocation() : null,
                meets ? new Vec3(face.getStepX(), face.getStepY(), face.getStepZ()) : null);
    }

    /**
     * The cells around everything the fire can reach: the reach along the
     * look, widened by the bounce's widest splay and a block, so every living
     * thing the shape may touch stands in one of them.
     *
     * @param eye the eye
     * @param aim the stream's aim
     * @return the cells
     */
    private static List<BlockPos> cellsAround(Vec3 eye, ChannelAim aim) {
        double range = aim.aimPoint().distanceTo(eye);
        double widest = range * Math.tan(Math.toRadians(aim.coneDegrees() * HALF)) * FlameShape.SPLAY + 1;
        AABB reach = new AABB(eye, aim.aimPoint()).inflate(widest);
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(reach.minX, reach.minY, reach.minZ),
                BlockPos.containing(reach.maxX, reach.maxY, reach.maxZ))) {
            cells.add(cell.immutable());
        }
        return cells;
    }

    /**
     * Whether the fire reaches a living thing through open air: seen from the
     * eye, or, for the bounce, from just off the face the fire bounced off,
     * so a block between never lets a burn through.
     *
     * @param host   the channel host
     * @param shape  the fire's shape
     * @param target the living thing the shape touches
     * @return whether the fire reaches it
     */
    private static boolean reachable(ChannelHost host, FlameShape shape, LivingEntity target) {
        Vec3 middle = target.getBoundingBox().getCenter();
        if (clearBetween(host, host.eye(), middle)) {
            return true;
        }
        Vec3 hit = shape.hit();
        return hit != null && clearBetween(host, hit.add(shape.normal().scale(OFF_THE_FACE)), middle);
    }

    private static boolean clearBetween(ChannelHost host, Vec3 from, Vec3 to) {
        return host.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty())).getType() != HitResult.Type.BLOCK;
    }

    /**
     * Sets one living thing alight and burns it through its damage immunity,
     * alight before the hit lands so a killing burn drops cooked loot. A
     * fire-immune thing is left alone.
     *
     * @param level       the server level
     * @param target      the living thing in the cone
     * @param amount      the fire damage
     * @param burnSeconds the seconds it stays alight
     */
    static void burn(ServerLevel level, LivingEntity target, float amount, int burnSeconds) {
        if (target.fireImmune()) {
            return;
        }
        target.igniteForSeconds(burnSeconds);
        BroilStep.scald(level, target, amount);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(damage);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL);
    }
}
