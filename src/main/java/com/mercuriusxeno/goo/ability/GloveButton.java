package com.mercuriusxeno.goo.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The mouse button a glove press came from: the attack button, left click,
 * or the use button, right click.
 * portal-takes-over-left-and-right-click-for-blue-and-orange
 */
public enum GloveButton {
    /** Left click, the attack button: the hit. */
    ATTACK,
    /** Right click, the use button. */
    USE;

    /** Sends a button. */
    public static final StreamCodec<ByteBuf, GloveButton> STREAM_CODEC = ByteBufCodecs.BOOL.map(
            use -> use ? USE : ATTACK, button -> button == USE);
}
