package com.mercuriusxeno.goo.ability.end;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/**
 * The way back a player the End gate carried to the End keeps on them:
 * where they portalled in from, which the gate they summon in the End leads to.
 * decision end-gate-in-the-end-is-free-and-returns-you-where-you-portalled-in
 *
 * @param destination where they portalled in from, empty for a player the gate never carried
 */
public record EndWayBack(Optional<EndDestination> destination) {

    /** A player holding no way back. */
    public static final EndWayBack NONE = new EndWayBack(Optional.empty());

    /** Saves the way back with the player. */
    public static final Codec<EndWayBack> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            EndDestination.CODEC.optionalFieldOf("destination").forGetter(EndWayBack::destination)
    ).apply(inst, EndWayBack::new));

    /**
     * @return whether a way back stands, the only state worth saving
     */
    public boolean stands() {
        return destination.isPresent();
    }
}
