package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Adds to a named counter the host's target keeps between hits, and
 * finishes; an expression reads the counter back by its id, so aeon's
 * ritual writes {@code counter id=goo:ritual add="100 / pow(max_health, 0.6)"}
 * and tests {@code goo:ritual} (decision aeon-mob-ritual-drops-spawn-egg).
 *
 * @param id  the counter id
 * @param add the amount to add, evaluated when the step runs
 */
public record CounterStep(Identifier id, Expr add) implements Step {

    private static final String NAME = "counter";
    private static final String FIELD_ID = "id";
    private static final String FIELD_ADD = "add";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<CounterStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_ID).forGetter(CounterStep::id),
            Expr.CODEC.fieldOf(FIELD_ADD).forGetter(CounterStep::add)
    ).apply(inst, CounterStep::new));

    /**
     * The registered type.
     */
    public static final StepType<CounterStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<CounterStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.host().addTargetCounter(id, add.evaluate(context));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(add);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
