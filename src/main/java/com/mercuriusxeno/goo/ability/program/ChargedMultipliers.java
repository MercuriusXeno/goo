package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * What Charged does to one channeled ability while its caster stands
 * charged: each multiplier scales the params of its kind that the ability's
 * JSON names, and one the JSON leaves out stays 1. A program reads them as
 * the variables {@code charged_area}, {@code charged_duration},
 * {@code charged_speed} and {@code charged_efficacy}, which read 1 for an
 * uncharged caster.
 * decision charged-scales-channel-params-by-json
 *
 * @param area     scales the ability's reach: a stream's range and cone, an area's size
 * @param duration scales how long its effects last
 * @param speed    scales how fast its work goes
 * @param efficacy scales how strongly it acts: damage, healing, potency
 */
public record ChargedMultipliers(double area, double duration, double speed, double efficacy) {

    /** The multipliers of an uncharged cast, or of an ability naming none: every one 1. */
    public static final ChargedMultipliers NONE = new ChargedMultipliers(1, 1, 1, 1);

    /** The variable a program reads the area multiplier through. */
    public static final String VAR_AREA = "charged_area";
    /** The variable a program reads the duration multiplier through. */
    public static final String VAR_DURATION = "charged_duration";
    /** The variable a program reads the speed multiplier through. */
    public static final String VAR_SPEED = "charged_speed";
    /** The variable a program reads the efficacy multiplier through. */
    public static final String VAR_EFFICACY = "charged_efficacy";

    private static final double UNCHANGED = 1.0;
    private static final String FIELD_AREA = "area";
    private static final String FIELD_DURATION = "duration";
    private static final String FIELD_SPEED = "speed";
    private static final String FIELD_EFFICACY = "efficacy";

    /**
     * Codec for an ability's {@code charged} object; each multiplier it leaves out reads 1.
     */
    public static final Codec<ChargedMultipliers> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf(FIELD_AREA, UNCHANGED).forGetter(ChargedMultipliers::area),
            Codec.DOUBLE.optionalFieldOf(FIELD_DURATION, UNCHANGED).forGetter(ChargedMultipliers::duration),
            Codec.DOUBLE.optionalFieldOf(FIELD_SPEED, UNCHANGED).forGetter(ChargedMultipliers::speed),
            Codec.DOUBLE.optionalFieldOf(FIELD_EFFICACY, UNCHANGED).forGetter(ChargedMultipliers::efficacy)
    ).apply(inst, ChargedMultipliers::new));

    /**
     * Reads a multiplier by the variable naming it.
     *
     * @param name the variable name
     * @return the multiplier, or empty when the name is no charged variable
     */
    public OptionalDouble read(String name) {
        Double value = Map.of(VAR_AREA, area, VAR_DURATION, duration, VAR_SPEED, speed,
                VAR_EFFICACY, efficacy).get(name);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    /**
     * Whether a name is one of the charged variables, which the runtime binds on every host.
     *
     * @param name the variable name
     * @return true for a charged variable
     */
    public static boolean isChargedVariable(String name) {
        return NONE.read(name).isPresent();
    }
}
