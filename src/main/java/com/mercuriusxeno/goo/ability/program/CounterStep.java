package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Adds to or sets a named counter the host's target keeps between hits,
 * and finishes; an expression reads the counter back by its id, so aeon's
 * ritual writes {@code counter id=goo:ritual add="100 / pow(max_health, 0.6)"},
 * tests {@code goo:ritual}, and restarts it with {@code counter set=0}
 * (decision aeon-mob-ritual-drops-spawn-egg). A step names exactly one of
 * {@code add} and {@code set}.
 *
 * @param id  the counter id
 * @param add the amount to add, evaluated when the step runs
 * @param set the value to set, evaluated when the step runs
 */
public record CounterStep(Identifier id, Optional<Expr> add, Optional<Expr> set) implements Step {

    private static final String NAME = "counter";
    private static final String FIELD_ID = "id";
    private static final String FIELD_ADD = "add";
    private static final String FIELD_SET = "set";
    private static final String ERR_ONE_WRITE = "Counter step %s names exactly one of add and set";

    /**
     * Codec for the step's params, refusing a step naming both or neither
     * of {@code add} and {@code set}.
     */
    public static final MapCodec<CounterStep> CODEC = RecordCodecBuilder.<CounterStep>mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_ID).forGetter(CounterStep::id),
            Expr.CODEC.optionalFieldOf(FIELD_ADD).forGetter(CounterStep::add),
            Expr.CODEC.optionalFieldOf(FIELD_SET).forGetter(CounterStep::set)
    ).apply(inst, CounterStep::new)).validate(CounterStep::refuseAmbiguousWrite);

    /**
     * The registered type.
     */
    public static final StepType<CounterStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * Builds a step adding to the counter.
     *
     * @param id     the counter id
     * @param amount the amount to add
     * @return the step
     */
    public static CounterStep adding(Identifier id, Expr amount) {
        return new CounterStep(id, Optional.of(amount), Optional.empty());
    }

    /**
     * Builds a step setting the counter.
     *
     * @param id    the counter id
     * @param value the value to set
     * @return the step
     */
    public static CounterStep setting(Identifier id, Expr value) {
        return new CounterStep(id, Optional.empty(), Optional.of(value));
    }

    /**
     * Refuses a step naming both or neither write.
     *
     * @param step the decoded step
     * @return the step, or an error naming its counter
     */
    private static DataResult<CounterStep> refuseAmbiguousWrite(CounterStep step) {
        if (step.add.isPresent() == step.set.isPresent()) {
            return DataResult.error(() -> String.format(ERR_ONE_WRITE, step.id));
        }
        return DataResult.success(step);
    }

    @Override
    public StepType<CounterStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.host().target();
        EntityCounters counters = target.getData(GooAttachments.ENTITY_COUNTERS);
        add.ifPresent(amount -> target.setData(GooAttachments.ENTITY_COUNTERS,
                counters.withAdded(id, amount.evaluate(context))));
        set.ifPresent(value -> target.setData(GooAttachments.ENTITY_COUNTERS,
                counters.withValue(id, value.evaluate(context))));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.concat(add.stream(), set.stream());
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
