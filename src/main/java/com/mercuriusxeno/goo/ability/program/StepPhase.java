package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;

/**
 * One phase of a {@link PhasedStep}: a name the host's renderer reads, a
 * duration, the steps run once on entering it, the steps run on each of
 * its ticks and the steps run once on leaving it. The nether black hole's
 * expand phase blinds and darkens on entering, pulls on each tick and
 * consumes the valued blocks in its sphere on leaving.
 *
 * @param name  the phase's name
 * @param ticks how many ticks the phase lasts, evaluated on entering it
 * @param enter the steps run once as the phase is entered
 * @param tick  the steps run on each tick of the phase
 * @param leave the steps run once as the phase is left
 */
public record StepPhase(String name, Expr ticks, List<Step> enter, List<Step> tick, List<Step> leave) {

    private static final String FIELD_NAME = "name";
    private static final String FIELD_TICKS = "ticks";
    private static final String FIELD_ENTER = "enter";
    private static final String FIELD_TICK = "tick";
    private static final String FIELD_LEAVE = "leave";

    /**
     * Codec for one phase. The step list codecs are read lazily because
     * {@link StepTypes} registers the phased step while building them.
     */
    public static final Codec<StepPhase> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf(FIELD_NAME).forGetter(StepPhase::name),
            Expr.CODEC.optionalFieldOf(FIELD_TICKS, Expr.literal(0)).forGetter(StepPhase::ticks),
            stepList(FIELD_ENTER).forGetter(StepPhase::enter),
            stepList(FIELD_TICK).forGetter(StepPhase::tick),
            stepList(FIELD_LEAVE).forGetter(StepPhase::leave)
    ).apply(inst, StepPhase::new));

    /**
     * Reads an optional step list field, empty when absent.
     *
     * @param field the field name
     * @return the field codec
     */
    private static MapCodec<List<Step>> stepList(String field) {
        return Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).optionalFieldOf(field, List.of());
    }

    /**
     * Streams every step the phase holds, entering, ticking and leaving.
     *
     * @return the steps
     */
    public Stream<Step> steps() {
        return Stream.of(enter, tick, leave).flatMap(List::stream);
    }
}
