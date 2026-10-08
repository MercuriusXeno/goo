package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Grows the shroom network the host's blob landed on, and where it landed
 * on none runs the {@code otherwise} steps on the same host instead, then
 * finishes. Colonize grows a cluster it hits and buds and spores the ground
 * where it hits none: {@code colonize radius=3 otherwise=[...]}
 * (decision colonize-blob-grows-the-network).
 *
 * @param radius    the spread's reach in blocks, evaluated when the step runs
 * @param otherwise the steps run where the blob landed on no network
 */
public record ColonizeStep(Expr radius, List<Step> otherwise) implements Step {

    private static final String NAME = "colonize";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_OTHERWISE = "otherwise";

    /**
     * Codec for the step's params. The list codec is read lazily because
     * {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<ColonizeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(ColonizeStep::radius),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).optionalFieldOf(FIELD_OTHERWISE, List.of())
                    .forGetter(ColonizeStep::otherwise)
    ).apply(inst, ColonizeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ColonizeStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ColonizeStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (!context.hostAs(ColonizeHost.class).colonize(radius.evaluateInt(context))) {
            new ProgramBehavior(otherwise).tick(context.host());
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius);
    }

    @Override
    public Set<HostCapability> requires() {
        Set<HostCapability> needs = new HashSet<>(Set.of(HostCapability.COLONIZE));
        otherwise.forEach(child -> needs.addAll(child.requires()));
        return needs;
    }

    @Override
    public Stream<Step> children() {
        return otherwise.stream();
    }
}
