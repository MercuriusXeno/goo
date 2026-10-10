package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Splits a mob ability's program on where its blob lands: on a struck mob
 * the hit steps run on it, and on a landing that missed every mob the miss
 * steps run there, within the same tick; a host that is neither runs
 * nothing. Each branch is held at load to the host it runs on alone, so one
 * program serves both. Leaf Vines roots the mob it hits and, missing,
 * lingers as a trap rooting the first mob to walk in.
 * vines-unpack-root-and-thorn
 *
 * @param hit  the steps run on the struck mob
 * @param miss the steps run on the landing
 */
public record HitOrMissStep(List<Step> hit, List<Step> miss) implements Step {

    private static final String NAME = "hit_or_miss";
    private static final String FIELD_HIT = "hit";
    private static final String FIELD_MISS = "miss";

    /**
     * Codec for the step's params. The child list codec is read lazily
     * because {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<HitOrMissStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).optionalFieldOf(FIELD_HIT, List.of())
                    .forGetter(HitOrMissStep::hit),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).optionalFieldOf(FIELD_MISS, List.of())
                    .forGetter(HitOrMissStep::miss)
    ).apply(inst, HitOrMissStep::new));

    /**
     * The registered type.
     */
    public static final StepType<HitOrMissStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<HitOrMissStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        StepHost host = context.host();
        if (host instanceof TargetHost) {
            new ProgramBehavior(hit).tick(host);
        } else if (host instanceof LingerHost) {
            new ProgramBehavior(miss).tick(host);
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of();
    }

    @Override
    public Stream<Step> children() {
        return Stream.concat(hit.stream(), miss.stream());
    }

    @Override
    public Stream<HostedStep> hostedChildren(HostKind host) {
        return branchFor(host).stream().map(child -> new HostedStep(child, host));
    }

    /**
     * The branch a host kind runs: the hit steps where it holds a target,
     * the miss steps where it can stand the ability's block, none otherwise.
     *
     * @param host the host kind
     * @return the steps that run on it
     */
    List<Step> branchFor(HostKind host) {
        if (host.capabilities().contains(HostCapability.TARGET)) {
            return hit;
        }
        return host.capabilities().contains(HostCapability.LINGER) ? miss : List.of();
    }
}
