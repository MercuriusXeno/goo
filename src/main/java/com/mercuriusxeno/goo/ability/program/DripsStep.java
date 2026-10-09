package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Counts each drip landing on the block below a tap, and once the JSON's
 * drips have accumulated starts the count over and runs its steps on the
 * same landing. Aeon's Tick tap is {@code drips drips=4 then=[tick_block]}.
 * tick-drip-splashes-a-small-tick-effect
 *
 * @param drips the drips that run the steps once
 * @param then  the steps run once the drips have accumulated
 */
public record DripsStep(int drips, List<Step> then) implements Step {

    private static final String NAME = "drips";
    private static final String FIELD_DRIPS = "drips";
    private static final String FIELD_THEN = "then";

    /**
     * Codec for the step's params. The list codec is read lazily because
     * {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<DripsStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.fieldOf(FIELD_DRIPS).forGetter(DripsStep::drips),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).fieldOf(FIELD_THEN).forGetter(DripsStep::then)
    ).apply(inst, DripsStep::new));

    /**
     * The registered type.
     */
    public static final StepType<DripsStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<DripsStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        DripHost host = context.hostAs(DripHost.class);
        if (host.countDrip() >= drips) {
            host.resetDrips();
            new ProgramBehavior(then).tick(context.host());
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        Set<HostCapability> needs = EnumSet.of(HostCapability.DRIP);
        children().forEach(child -> needs.addAll(child.requires()));
        return needs;
    }

    @Override
    public Stream<Step> children() {
        return then.stream();
    }
}
