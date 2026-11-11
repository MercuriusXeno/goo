package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Sets off a vibration at the host's anchor that carries no source entity,
 * and finishes. Deep's Click lands {@code vibrate} beside a {@code sound} at
 * the host, so sensors trip and a warden walks to the spot while no anger
 * reaches the thrower (decision click-vibration-carries-no-source-entity).
 *
 * @param event the game event id, one the vibrations tag carries with a nonzero sculk frequency
 */
public record VibrateStep(Identifier event) implements TakesNoEntity {

    private static final String NAME = "vibrate";
    private static final String FIELD_EVENT = "event";
    /** The event a vibrate step naming none sets off: a thrown thing landing, heard by every sculk listener. */
    static final Identifier PROJECTILE_LAND = Identifier.withDefaultNamespace("projectile_land");

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<VibrateStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.optionalFieldOf(FIELD_EVENT, PROJECTILE_LAND).forGetter(VibrateStep::event)
    ).apply(inst, VibrateStep::new));

    /**
     * The registered type.
     */
    public static final StepType<VibrateStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<VibrateStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(VibrationHost.class).vibrate(event);
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.VIBRATE);
    }
}
