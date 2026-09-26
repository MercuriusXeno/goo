package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Plays a sound at an anchor and finishes. Ender teleport follows its
 * jump with {@code sound id=minecraft:entity.enderman.teleport source=hostile at=target}.
 *
 * @param sound  the sound event id
 * @param at     the anchor the sound plays at
 * @param source the category the sound plays under
 * @param volume the volume, evaluated when the step runs
 * @param pitch  the pitch, evaluated when the step runs
 */
public record SoundStep(Identifier sound, FxAnchor at, SoundKind source, Expr volume, Expr pitch) implements Step {

    private static final String NAME = "sound";
    private static final String FIELD_ID = "id";
    private static final String FIELD_AT = "at";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_VOLUME = "volume";
    private static final String FIELD_PITCH = "pitch";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SoundStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_ID).forGetter(SoundStep::sound),
            FxAnchor.CODEC.optionalFieldOf(FIELD_AT, FxAnchor.HOST).forGetter(SoundStep::at),
            SoundKind.CODEC.optionalFieldOf(FIELD_SOURCE, SoundKind.BLOCKS).forGetter(SoundStep::source),
            Expr.CODEC.optionalFieldOf(FIELD_VOLUME, Expr.literal(1)).forGetter(SoundStep::volume),
            Expr.CODEC.optionalFieldOf(FIELD_PITCH, Expr.literal(1)).forGetter(SoundStep::pitch)
    ).apply(inst, SoundStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SoundStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SoundStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.host().playSound(new SoundCue(sound, source, volume.evaluateFloat(context), pitch.evaluateFloat(context)));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(volume, pitch);
    }

    @Override
    public Set<HostCapability> requires() {
        return at == FxAnchor.TARGET ? Set.of(HostCapability.TARGET) : Set.of();
    }
}
