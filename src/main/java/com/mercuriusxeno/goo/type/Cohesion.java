package com.mercuriusxeno.goo.type;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.Locale;

/**
 * How much a goo type's world goo spreads, one of six rungs, each the layers a cell stands
 * above its neighbour before it pours. A type's JSON names its rung by its lowercase name.
 * cohesion-is-the-step-the-goo-holds
 */
public enum Cohesion {
    /** Levels flat: a cell pours any layer standing above its neighbour. */
    RUNNY(0),
    /** Holds one layer of step. */
    LOOSE(1),
    /** Holds two layers of step. */
    SOFT(2),
    /** Holds four layers of step. */
    THICK(4),
    /** Holds eight layers of step. */
    STIFF(8),
    /** Holds a whole cell of fifteen layers: goo this firm never pours. */
    FIRM(15);

    private static final String NOT_A_RUNG = "Not a cohesion rung, expected one of runny, loose, soft, thick, "
            + "stiff or firm: ";

    /**
     * A rung spelled by its lowercase name; a number, or a name off the six rungs, is refused.
     */
    public static final Codec<Cohesion> CODEC = Codec.STRING.comapFlatMap(Cohesion::parse, Cohesion::spelling);

    private final int threshold;

    Cohesion(int threshold) {
        this.threshold = threshold;
    }

    /**
     * The layers a cell of this rung stands above its neighbour before it pours.
     *
     * @return the threshold in layers, 0 to 15
     */
    public int threshold() {
        return threshold;
    }

    /**
     * The rung's name as a type JSON spells it.
     *
     * @return the lowercase name
     */
    public String spelling() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static DataResult<Cohesion> parse(String name) {
        return Arrays.stream(values()).filter(rung -> rung.spelling().equals(name)).findFirst()
                .map(DataResult::success).orElseGet(() -> DataResult.error(() -> NOT_A_RUNG + name));
    }
}
