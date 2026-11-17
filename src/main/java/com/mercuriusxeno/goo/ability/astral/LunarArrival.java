package com.mercuriusxeno.goo.ability.astral;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The mark Lunar leaves on a caster once it has carried them and they stand at the far end. Synced to the
 * caster's client, whose white flood holds over the crossing until a fresh mark arrives, then clears; the client
 * tells a fresh mark from one it has seen by the game time Lunar set it at.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 *
 * @param arrivedAt the far end's game time as the caster stood there, zero for no arrival
 */
public record LunarArrival(long arrivedAt) {

    /** No arrival. */
    public static final LunarArrival NONE = new LunarArrival(0L);

    /** Carries an arrival to its caster's client. */
    public static final StreamCodec<ByteBuf, LunarArrival> STREAM_CODEC =
            ByteBufCodecs.VAR_LONG.map(LunarArrival::new, LunarArrival::arrivedAt);

    /**
     * @return true for a mark Lunar set, false for none
     */
    public boolean stands() {
        return arrivedAt != NONE.arrivedAt();
    }
}
