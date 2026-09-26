package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Toggles the host's target's invulnerability and finishes; aeon time
 * stop is {@code set_ai enabled=false} then
 * {@code set_invulnerable enabled=true}.
 *
 * @param enabled whether the target is invulnerable after the step
 */
public record SetInvulnerableStep(boolean enabled) implements Step {

    private static final String NAME = "set_invulnerable";
    private static final String FIELD_ENABLED = "enabled";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SetInvulnerableStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.BOOL.fieldOf(FIELD_ENABLED).forGetter(SetInvulnerableStep::enabled)
    ).apply(inst, SetInvulnerableStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SetInvulnerableStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SetInvulnerableStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(TargetHost.class).target().setInvulnerable(enabled);
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
