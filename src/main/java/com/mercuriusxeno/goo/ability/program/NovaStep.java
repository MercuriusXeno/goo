package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.frost.FrostCurve;
import com.mercuriusxeno.goo.ability.frost.FrozenEvents;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.NovaRingPayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Pulses a ring of frost out from the host's frost center: every mob within the radius
 * has its frozen gauge filled by the amount, shared only slightly among a
 * crowd, and is knocked slightly away; every client watching sees the ring
 * expand to the radius. Both the radius and the amount are evaluated on the
 * host, so Nova's release reads the charge its hold reached:
 * {@code nova radius="3 + 7 * charge" amount="4 + 12 * charge"}.
 * nova-ring-grows-with-the-hold
 *
 * @param radius the ring's reach in blocks, evaluated on the host
 * @param amount how much the ring freezes each mob, in health points, evaluated on the host
 * @param crowd  how much each further mob in the ring thins the freeze: the share is 1 / (1 + crowd * (n - 1))
 * @param push   the knockback away from the host each mob takes
 * @param curve  how the gauges hold, thaw and weaken the mobs
 */
public record NovaStep(Expr radius, Expr amount, float crowd, float push, FrostCurve curve) implements Step {

    private static final String NAME = "nova";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_AMOUNT = "amount";
    private static final String FIELD_CROWD = "crowd";
    private static final String FIELD_PUSH = "push";
    private static final Set<EntityFilter> AROUND_THE_HOST = Set.of(EntityFilter.LIVING, EntityFilter.NOT_TARGET);

    /**
     * Codec for the step's params, the curve's fields inline.
     */
    public static final MapCodec<NovaStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(NovaStep::radius),
            Expr.CODEC.fieldOf(FIELD_AMOUNT).forGetter(NovaStep::amount),
            Codec.FLOAT.optionalFieldOf(FIELD_CROWD, 0f).forGetter(NovaStep::crowd),
            Codec.FLOAT.optionalFieldOf(FIELD_PUSH, 0f).forGetter(NovaStep::push),
            FrostCurve.CODEC.forGetter(NovaStep::curve)
    ).apply(inst, NovaStep::new));

    /**
     * The registered type.
     */
    public static final StepType<NovaStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<NovaStep> type() {
        return TYPE;
    }

    /**
     * The share of the ring's freeze each mob takes with a crowd in it: whole
     * for one, thinning only slightly as more stand in it.
     *
     * @param crowd the thinning each further mob adds
     * @param mobs  how many mobs the ring crosses
     * @return the share, 0 to 1
     */
    static float crowdShare(float crowd, int mobs) {
        return 1f / (1f + crowd * Math.max(0, mobs - 1));
    }

    @Override
    public boolean tick(StepContext context) {
        float reach = radius.evaluateFloat(context);
        float freeze = amount.evaluateFloat(context);
        FrostHost host = context.hostAs(FrostHost.class);
        Vec3 center = host.frostCenter();
        List<LivingEntity> struck = new ArrayList<>();
        host.forEachEntityWithin(SelectionShape.SPHERE, reach, AROUND_THE_HOST, selected -> struck.add(selected.target()));
        float each = freeze * crowdShare(crowd, struck.size());
        for (LivingEntity living : struck) {
            if (living instanceof Mob mob) {
                FrozenEvents.freeze(mob, each, curve);
            }
            living.knockback(push, center.x - living.getX(), center.z - living.getZ());
        }
        EntityVisuals.sendToWatchersOf(host.level(), center, new NovaRingPayload(center, reach));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius, amount);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.FROST);
    }
}
