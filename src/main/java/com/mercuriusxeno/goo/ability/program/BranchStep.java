package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Runs one of two step lists the tick it is reached, then finishes: the
 * {@code then} list when {@code when} reads nonzero, the {@code otherwise}
 * list when it reads zero. Aeon's ritual branches on
 * {@code at_least(goo:ritual, 100)} (decision aeon-mob-ritual-drops-spawn-egg).
 *
 * <p>The chosen list runs to completion within the tick, as a target
 * selection's children do, so both lists hold instant steps.
 *
 * @param when      the condition, true when nonzero
 * @param then      the steps run when the condition holds
 * @param otherwise the steps run when it does not
 */
public record BranchStep(Expr when, List<Step> then, List<Step> otherwise) implements Step {

    private static final String NAME = "branch";
    private static final String FIELD_WHEN = "when";
    private static final String FIELD_THEN = "then";
    private static final String FIELD_OTHERWISE = "otherwise";

    /**
     * Codec for the step's params. The list codec is read lazily because
     * {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<BranchStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_WHEN).forGetter(BranchStep::when),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).optionalFieldOf(FIELD_THEN, List.of())
                    .forGetter(BranchStep::then),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).optionalFieldOf(FIELD_OTHERWISE, List.of())
                    .forGetter(BranchStep::otherwise)
    ).apply(inst, BranchStep::new));

    /**
     * The registered type.
     */
    public static final StepType<BranchStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<BranchStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        List<Step> chosen = when.evaluate(context) != 0 ? then : otherwise;
        new ProgramBehavior(chosen).tick(context.host());
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(when);
    }

    @Override
    public Set<HostCapability> requires() {
        Set<HostCapability> needs = new HashSet<>();
        children().forEach(child -> needs.addAll(child.requires()));
        return needs;
    }

    @Override
    public Stream<Step> children() {
        return Stream.concat(then.stream(), otherwise.stream());
    }
}
