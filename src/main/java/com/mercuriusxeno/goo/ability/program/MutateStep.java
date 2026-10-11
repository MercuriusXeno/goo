package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.xeno.Mutation;
import com.mercuriusxeno.goo.ability.xeno.MutationEvents;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Works one mutation, drawn at random from the table the ability JSON
 * names, on the host's target and finishes.
 * xeno-blob-mutates-the-struck
 *
 * @param table the mutations a strike can draw from
 */
public record MutateStep(List<Mutation> table) implements Step {

    private static final String NAME = "mutate";
    private static final String FIELD_TABLE = "table";

    /**
     * Codec for the step's params; the table names at least one mutation.
     */
    public static final MapCodec<MutateStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Mutation.CODEC.listOf(1, Integer.MAX_VALUE).fieldOf(FIELD_TABLE).forGetter(MutateStep::table)
    ).apply(inst, MutateStep::new));

    /**
     * The registered type.
     */
    public static final StepType<MutateStep> TYPE = new StepType<>(NAME, CODEC);

    /** @param table the mutations a strike can draw from, copied */
    public MutateStep {
        table = List.copyOf(table);
    }

    @Override
    public StepType<MutateStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        MutationEvents.mutate(target, table.get(target.getRandom().nextInt(table.size())));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
