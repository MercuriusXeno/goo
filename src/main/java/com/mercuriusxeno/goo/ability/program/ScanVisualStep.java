package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Names the visual a scan's wave plays on its way to the landing, part of
 * the ability's choreography beside its effect steps. The scan delivery
 * reads it at the throw and has every watching client draw the visual from
 * the glove to the landing over the wave's delay, so the step itself does
 * nothing when the landing runs the program: the visual has played by then.
 * Deep's Click is {@code scan_visual visual=sound_wave fade=8}
 * (decisions click-is-a-hit-scan-drawn-as-a-delayed-wave, ability-json-names-its-choreography).
 *
 * @param visual the visual's name, which a client renderer registers under
 * @param fade   the ticks the visual fades over at the landing
 */
public record ScanVisualStep(String visual, int fade) implements TakesNoEntity {

    private static final String NAME = "scan_visual";
    private static final String FIELD_VISUAL = "visual";
    private static final String FIELD_FADE = "fade";
    /** The ticks a visual naming no fade fades over. */
    static final int DEFAULT_FADE = 8;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ScanVisualStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.STRING.fieldOf(FIELD_VISUAL).forGetter(ScanVisualStep::visual),
            Codec.INT.optionalFieldOf(FIELD_FADE, DEFAULT_FADE).forGetter(ScanVisualStep::fade)
    ).apply(inst, ScanVisualStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ScanVisualStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The scan visual a program names, its first where it names several.
     *
     * @param program the ability's program
     * @return the visual step, empty for a program naming none
     */
    public static Optional<ScanVisualStep> in(List<Step> program) {
        return program.stream().filter(ScanVisualStep.class::isInstance).map(ScanVisualStep.class::cast)
                .findFirst();
    }

    @Override
    public StepType<ScanVisualStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of();
    }
}
