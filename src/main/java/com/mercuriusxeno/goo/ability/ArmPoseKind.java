package com.mercuriusxeno.goo.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The frames the glove arm is drawn in on the player's model, in place of
 * the swing: an {@link ArmPose} an ability names holds the arm in one frame
 * while the use key is held and plays another on release. The order is
 * synced, so a frame is appended last.
 * decision shards-sling-then-morph-to-flechettes
 * decision ability-json-names-its-arm-pose
 */
public enum ArmPoseKind {
    /** The arm eases back to the pose vanilla gives it. */
    NONE,
    /** The glove hand drawn across to the opposite shoulder, a charge being held. */
    WIND_UP,
    /** The glove arm sweeping out from the opposite shoulder to the glove side, a charge let go. */
    FLING,
    /** The glove arm held out toward the aim. */
    HOLD_OUT,
    /** The glove arm flung forward from held out, a throw let go. */
    THROW,
    /** The glove hand raised up overhead. */
    HOLD_UP;

    /** Network codec, writing the frame as a varint ordinal. */
    public static final StreamCodec<ByteBuf, ArmPoseKind> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ordinal -> values()[ordinal], ArmPoseKind::ordinal);

    /**
     * Whether the frame holds the arm for as long as the key is held, rather
     * than playing once and ending.
     *
     * @return true for a held frame
     */
    public boolean holds() {
        return this == WIND_UP || this == HOLD_OUT || this == HOLD_UP;
    }
}
