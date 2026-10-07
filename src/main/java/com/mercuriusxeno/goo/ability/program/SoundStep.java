package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Plays a sound at an anchor and finishes. Ender teleport follows its
 * jump with {@code sound id=minecraft:entity.enderman.teleport source=hostile at=target}.
 * A frame plays it that many ticks after the program reaches the step,
 * the program holding it while the steps after it run, as Urchin's
 * retract shink waits for its spikes (decisions ability-json-names-its-choreography,
 * urchin-spikes-shink-out-and-shink-back).
 *
 * @param sound  the sound event id
 * @param at     the anchor the sound plays at
 * @param source the category the sound plays under
 * @param volume the volume, evaluated when the step runs
 * @param pitch  the pitch, evaluated when the step runs
 * @param frame  the ticks after the step is reached before the sound plays, evaluated when the step runs
 */
public record SoundStep(Identifier sound, FxAnchor at, SoundKind source, Expr volume, Expr pitch, Expr frame)
        implements Step {

    private static final String NAME = "sound";
    private static final String FIELD_ID = "id";
    private static final String FIELD_AT = "at";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_VOLUME = "volume";
    private static final String FIELD_PITCH = "pitch";
    private static final String FIELD_FRAME = "frame";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SoundStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_ID).forGetter(SoundStep::sound),
            FxAnchor.CODEC.optionalFieldOf(FIELD_AT, FxAnchor.HOST).forGetter(SoundStep::at),
            SoundKind.CODEC.optionalFieldOf(FIELD_SOURCE, SoundKind.BLOCKS).forGetter(SoundStep::source),
            Expr.CODEC.optionalFieldOf(FIELD_VOLUME, Expr.literal(1)).forGetter(SoundStep::volume),
            Expr.CODEC.optionalFieldOf(FIELD_PITCH, Expr.literal(1)).forGetter(SoundStep::pitch),
            Expr.CODEC.optionalFieldOf(FIELD_FRAME, Expr.literal(0)).forGetter(SoundStep::frame)
    ).apply(inst, SoundStep::new));

    /**
     * A sound that plays the tick the program reaches it.
     *
     * @param sound  the sound event id
     * @param at     the anchor the sound plays at
     * @param source the category the sound plays under
     * @param volume the volume
     * @param pitch  the pitch
     */
    public SoundStep(Identifier sound, FxAnchor at, SoundKind source, Expr volume, Expr pitch) {
        this(sound, at, source, volume, pitch, Expr.literal(0));
    }

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
        context.playSound(new SoundCue(sound, source, volume.evaluateFloat(context), pitch.evaluateFloat(context)),
                frame.evaluateInt(context));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(volume, pitch, frame);
    }

    @Override
    public Set<HostCapability> requires() {
        return at == FxAnchor.TARGET ? Set.of(HostCapability.TARGET) : Set.of();
    }
}
