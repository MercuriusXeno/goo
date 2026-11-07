package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Flames' held pass, run each held tick of the stream: every {@code every}
 * held ticks it casts a spray of {@link FlameRays} from the eye down the look
 * and burns each living thing a ray's line crosses, keeping it alight for the
 * JSON's seconds and dealing the JSON's fire damage, which waits out its
 * invulnerability window. A fire-immune thing is left alone, and no fire
 * block is placed.
 * flames-burns-by-a-spray-of-rays
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
            List<LivingEntity> inReach = new ArrayList<>();
            host.forEachLivingIn(cellsAround(host.eye(), aim), Set.of(), target -> inReach.add(target.target()));
            for (LivingEntity target : FlameRays.crossed(sprayOf(host, aim), inReach, LivingEntity::getBoundingBox)) {
                burn(host.level(), target, amount, burnSeconds);
            }
        });
        return true;
    }

    /**
     * This pulse's spray: rays from the eye down the look, inside the
     * stream's cone, each reaching as far as the aim does.
     *
     * @param host the channel host
     * @param aim  the stream's aim, its reach along the look
     * @return the rays
     */
    private static List<FlameRays.Ray> sprayOf(ChannelHost host, ChannelAim aim) {
        Vec3 eye = host.eye();
        Vec3 look = aim.aimPoint().subtract(eye);
        double range = look.length();
        Vec3 axis = range > 0 ? look.scale(1 / range) : new Vec3(0, 0, 1);
        return FlameRays.spray(eye, axis, aim.coneDegrees(), range, host.level().getRandom(),
                FlameRays.clipIn(host.level()));
    }

    /**
     * The cells around everything the spray can reach: the reach along the
     * look, widened by the cone's widest and a block, so every living thing
     * a ray may cross stands in one of them.
     *
     * @param eye the eye
     * @param aim the stream's aim
     * @return the cells
     */
    private static List<BlockPos> cellsAround(Vec3 eye, ChannelAim aim) {
        double range = aim.aimPoint().distanceTo(eye);
        double widest = range * Math.tan(Math.toRadians(aim.coneDegrees() * HALF)) + 1;
        AABB reach = new AABB(eye, aim.aimPoint()).inflate(widest);
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(reach.minX, reach.minY, reach.minZ),
                BlockPos.containing(reach.maxX, reach.maxY, reach.maxZ))) {
            cells.add(cell.immutable());
        }
        return cells;
    }

    /**
     * Sets one living thing alight and burns it, alight before the hit lands
     * so a killing burn drops cooked loot. The hit waits out the thing's
     * invulnerability window, so a burn lands at most once per half second;
     * Broil's scald alone clears that window. A fire-immune thing is left alone.
     * flames-and-heater-respect-i-frames
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
        target.hurtServer(level, target.damageSources().inFire(), amount);
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
