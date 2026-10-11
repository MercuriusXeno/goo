package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Drops one glass shard from the host's tap down the column beneath it and
 * runs the child steps on the first living mob it strikes, within the same
 * tick; a shard striking only the landing runs nothing. Crystal's tap counts
 * its drips first:
 * {@code drips count=4 steps=[shard_fall steps=[damage ...]]}.
 * decision shards-drip-falls-as-a-glass-shard
 *
 * @param steps the steps run on the struck mob
 */
public record ShardFallStep(List<Step> steps) implements Step {

    private static final String NAME = "shard_fall";
    private static final String FIELD_STEPS = "steps";

    /**
     * Codec for the step's params, the child list read lazily since
     * {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<ShardFallStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).fieldOf(FIELD_STEPS).forGetter(ShardFallStep::steps)
    ).apply(inst, ShardFallStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ShardFallStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ShardFallStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ShardFallHost host = context.hostAs(ShardFallHost.class);
        host.fallShard().ifPresent(struck -> host.forEntity(struck, target -> new ProgramBehavior(steps).tick(target)));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.SHARD_FALL);
    }

    @Override
    public Stream<Step> children() {
        return steps.stream();
    }

    @Override
    public Stream<HostedStep> hostedChildren(HostKind host) {
        return steps.stream().map(child -> new HostedStep(child, HostKind.ENTITY));
    }
}
