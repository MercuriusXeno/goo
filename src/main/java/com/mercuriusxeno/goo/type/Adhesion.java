package com.mercuriusxeno.goo.type;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.Locale;

/**
 * How a goo type's world goo holds where it lands, one of five rungs in ladder order, each keeping
 * the holds of every rung below it. A type's JSON names its rung by its lowercase name.
 * adhesion-is-a-ladder-of-holds
 */
public enum Adhesion {
    /** Clings to nothing: runs off any top with an open edge and leaves no film behind. */
    SLIPS(0),
    /** Holds tops, leaving one residual layer on a top beside an edge. */
    TOPS(1),
    /** Holds a block's side only where goo of its own type stands below it. */
    GELS(2),
    /** Holds a block's side where it strikes. */
    CLINGS(3),
    /**
     * Holds a block's underside where it strikes.
     * glow-goo-is-adhesion-four
     */
    HANGS(4);

    private static final String NOT_A_RUNG = "Not an adhesion rung, expected one of slips, tops, gels, clings "
            + "or hangs: ";

    /**
     * A rung spelled by its lowercase name; a number, or a name off the five rungs, is refused.
     */
    public static final Codec<Adhesion> CODEC = Codec.STRING.comapFlatMap(Adhesion::parse, Adhesion::spelling);

    private final int level;

    Adhesion(int level) {
        this.level = level;
    }

    /**
     * The rung's place on the ladder.
     *
     * @return the level, 0 for slips to 4 for hangs
     */
    public int level() {
        return level;
    }

    /**
     * Whether this rung keeps the holds of another, standing at it or above it on the ladder.
     *
     * @param rung the rung whose holds are asked for
     * @return true where this rung stands at or above {@code rung}
     */
    public boolean keeps(Adhesion rung) {
        return compareTo(rung) >= 0;
    }

    /**
     * Whether goo at this rung holds a block's side: at clings and above where it strikes, at
     * gels only over goo of its own type.
     *
     * @param overItsGoo whether goo of its own type stands below the side
     * @return true where the side holds the goo
     */
    public boolean holdsASide(boolean overItsGoo) {
        return keeps(CLINGS) || this == GELS && overItsGoo;
    }

    /**
     * The rung's name as a type JSON spells it.
     *
     * @return the lowercase name
     */
    public String spelling() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static DataResult<Adhesion> parse(String name) {
        return Arrays.stream(values()).filter(rung -> rung.spelling().equals(name)).findFirst()
                .map(DataResult::success).orElseGet(() -> DataResult.error(() -> NOT_A_RUNG + name));
    }
}
