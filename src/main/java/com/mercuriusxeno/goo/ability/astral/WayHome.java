package com.mercuriusxeno.goo.ability.astral;

import com.mercuriusxeno.goo.ability.zone.VoidPockets;
import com.mojang.serialization.MapCodec;
import java.util.Optional;

/**
 * The place a caster left the overworld from when the moon took them, kept with the player until the blue star
 * carries them back to it.
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 *
 * @param point where and which way the caster stood, empty once used or never kept
 */
public record WayHome(Optional<VoidPockets.ReturnPoint> point) {

    /** No way home kept. */
    public static final WayHome NONE = new WayHome(Optional.empty());

    /** Saves the way home with the player. */
    public static final MapCodec<WayHome> CODEC = VoidPockets.ReturnPoint.CODEC.optionalFieldOf("point")
            .xmap(WayHome::new, WayHome::point);

    /**
     * @return true while a way home is kept
     */
    public boolean kept() {
        return point.isPresent();
    }
}
