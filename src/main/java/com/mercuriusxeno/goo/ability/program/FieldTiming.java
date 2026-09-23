package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How long a field effect takes to expand after the fuse and to contract
 * once its budget is spent; the crystal cloud grows and shrinks over ten
 * ticks each, the metal trap does neither. Read inline from the
 * {@code field_effect} step's own fields.
 *
 * @param expandTicks   ticks the field takes to reach its full radius, zero for at once
 * @param contractTicks ticks the field takes to shrink away after its teardown, zero for at once
 */
public record FieldTiming(Expr expandTicks, Expr contractTicks) {

    private static final String FIELD_EXPAND_TICKS = "expand_ticks";
    private static final String FIELD_CONTRACT_TICKS = "contract_ticks";

    /**
     * Codec for the two fields, read from the enclosing step's object.
     */
    public static final MapCodec<FieldTiming> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.optionalFieldOf(FIELD_EXPAND_TICKS, Expr.literal(0)).forGetter(FieldTiming::expandTicks),
            Expr.CODEC.optionalFieldOf(FIELD_CONTRACT_TICKS, Expr.literal(0)).forGetter(FieldTiming::contractTicks)
    ).apply(inst, FieldTiming::new));

    /**
     * The timing of a field that neither expands nor contracts.
     */
    public static final FieldTiming INSTANT = new FieldTiming(Expr.literal(0), Expr.literal(0));
}
