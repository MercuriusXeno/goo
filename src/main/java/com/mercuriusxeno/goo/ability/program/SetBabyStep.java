package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Mob;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Makes the host's target a baby or an adult and finishes; a target with
 * no baby form is left as it is, so aeon's ritual guards the step with
 * {@code target where=[has_baby_form, not_baby]} (decision
 * aeon-mob-ritual-drops-spawn-egg).
 *
 * @param enabled whether the target becomes a baby
 */
public record SetBabyStep(boolean enabled) implements Step {

    private static final String NAME = "set_baby";
    private static final String FIELD_ENABLED = "enabled";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SetBabyStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.BOOL.fieldOf(FIELD_ENABLED).forGetter(SetBabyStep::enabled)
    ).apply(inst, SetBabyStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SetBabyStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SetBabyStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (context.hostAs(TargetHost.class).target() instanceof Mob mob) {
            mob.setBaby(enabled);
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
