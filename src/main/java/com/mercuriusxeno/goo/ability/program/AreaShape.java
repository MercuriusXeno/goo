package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;

/**
 * The footprint a layer walk covers, each shape following the
 * {@code ChainFootprint} geometry from the placed face.
 */
public enum AreaShape {
    /**
     * A footprint of blocks perpendicular to the blast direction, one
     * layer per depth step into the wall.
     */
    TUNNEL,
    /**
     * A circle one block into the wall, one ring per layer out from its
     * center.
     */
    FLAT_CIRCLE,
    /**
     * A sphere centered one block into the wall, one shell per layer out
     * from its center.
     */
    SPHERE;

    private static final String WHAT = "area shape";

    /**
     * Codec reading the lower-case name.
     */
    public static final Codec<AreaShape> CODEC = LowerCaseEnumCodec.of(AreaShape.class, WHAT);

    /**
     * Returns the lower-case name the JSON writes, which is also the area
     * mode the marker's ghost outline reads.
     *
     * @return the key
     */
    public String key() {
        return LowerCaseEnumCodec.key(this);
    }
}
