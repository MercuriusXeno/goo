package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Mob;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Toggles the host's target's AI and finishes; a target that is not a
 * mob has no AI to toggle and is left alone, so pulse short circuit
 * wraps {@code set_ai enabled=false} in {@code target where=[mob]}.
 *
 * @param enabled whether the target's AI runs after the step
 */
public record SetAiStep(boolean enabled) implements Step {

    private static final String NAME = "set_ai";
    private static final String FIELD_ENABLED = "enabled";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SetAiStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.BOOL.fieldOf(FIELD_ENABLED).forGetter(SetAiStep::enabled)
    ).apply(inst, SetAiStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SetAiStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SetAiStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (context.hostAs(TargetHost.class).target() instanceof Mob mob) {
            mob.setNoAi(!enabled);
        }
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
