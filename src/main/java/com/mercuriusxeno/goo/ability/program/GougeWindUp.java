package com.mercuriusxeno.goo.ability.program;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The Gouge wind-up the server runs for a player, published to their
 * own client so its ground shake follows the wind-up the server actually runs:
 * every one, even one beginning partway through a hold after a missed lift or
 * a throw's cooldown, and none while a hunk is held or a throw cools down.
 * decision terrakinesis-shakes-the-blocks-before-the-tear
 *
 * @param start the game time the wind-up began, or {@link #NO_START} while none runs
 */
public record GougeWindUp(long start) {

    /** The start a player with no wind-up running publishes. */
    public static final long NO_START = Long.MIN_VALUE;
    /** No wind-up running. */
    public static final GougeWindUp NONE = new GougeWindUp(NO_START);
    /** The wind-up as it syncs to its player's client. */
    public static final StreamCodec<ByteBuf, GougeWindUp> STREAM_CODEC =
            ByteBufCodecs.VAR_LONG.map(GougeWindUp::new, GougeWindUp::start);

    /**
     * The wind-up's tick count at a moment.
     *
     * @param now the game time
     * @return the tick, 1 on its first, or 0 while none runs
     */
    public int tickAt(long now) {
        return start == NO_START ? 0 : (int) (now - start) + 1;
    }
}
