package com.mercuriusxeno.goo.ability.gate;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The mark Astral's gate leaves on a caster once it has carried them and they stand at the far end. Synced to the
 * caster's client, whose white flood holds over the crossing until a fresh mark arrives, then clears; the client
 * tells a fresh mark from one it has seen by the game time the gate set it at.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 *
 * @param arrivedAt the far end's game time as the caster stood there, zero for no arrival
 */
public record GateArrival(long arrivedAt) {

    /** No arrival. */
    public static final GateArrival NONE = new GateArrival(0L);

    /** Carries an arrival to its caster's client. */
    public static final StreamCodec<ByteBuf, GateArrival> STREAM_CODEC =
            ByteBufCodecs.VAR_LONG.map(GateArrival::new, GateArrival::arrivedAt);

    /**
     * @return true for a mark the gate set, false for none
     */
    public boolean stands() {
        return arrivedAt != NONE.arrivedAt();
    }
}
