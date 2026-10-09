package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.held.Haste;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Aeon's Haste, laid as its held effect starts: the target holds speed and
 * haste with no particles, for a drunk brew's duration or, on the glove,
 * until the held effect ends, and wears the golden haste overlay meanwhile.
 * haste-stacks-speed-under-the-golden-overlay
 *
 * @param speed the speed amplifier
 * @param haste the haste amplifier
 */
public record HasteStep(int speed, int haste) implements Step {

    private static final String NAME = "haste";
    private static final String FIELD_SPEED = "speed";
    private static final String FIELD_HASTE = "haste";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<HasteStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.optionalFieldOf(FIELD_SPEED, 0).forGetter(HasteStep::speed),
            Codec.INT.optionalFieldOf(FIELD_HASTE, 0).forGetter(HasteStep::haste)
    ).apply(inst, HasteStep::new));

    /**
     * The registered type.
     */
    public static final StepType<HasteStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<HasteStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        int duration = host instanceof PlayerHost player && player.brewDuration().isPresent()
                ? player.brewDuration().getAsInt() : Haste.HELD;
        Haste.lay(host.target(), speed, haste, duration);
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
