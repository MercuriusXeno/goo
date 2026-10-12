package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.LowerCaseEnumCodec;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The arm pose an ability's goo_abilities JSON names under {@code pose}, the
 * shared vocabulary every ability poses the glove arm from in place of the
 * swing: the frame the arm holds while the use key is held, and the frame
 * it plays on release. An ability naming none takes its delivery kind's
 * default (decision ability-json-names-its-arm-pose).
 * decision use-key-throw-swings-no-more
 */
public enum ArmPose {
    /** Held out toward the aim for the whole hold, eased down on release. */
    HELD_OUT(ArmPoseKind.HOLD_OUT, ArmPoseKind.NONE),
    /** The glove hand raised up for the whole hold, eased down on release. */
    HELD_UP(ArmPoseKind.HOLD_UP, ArmPoseKind.NONE),
    /** Eased across the chest while charging, backhanded outward on release: Shards' pose. */
    SHOULDER_CAST(ArmPoseKind.WIND_UP, ArmPoseKind.FLING),
    /** Held out toward the aim while held, flung forward on release. */
    THROWN(ArmPoseKind.HOLD_OUT, ArmPoseKind.THROW);

    private static final String WHAT = "arm pose";

    /** Datapack codec, reading the pose by its lower-case name. */
    public static final Codec<ArmPose> CODEC = LowerCaseEnumCodec.of(ArmPose.class, WHAT);

    /** Network codec, writing the pose as a varint ordinal. */
    public static final StreamCodec<ByteBuf, ArmPose> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ordinal -> values()[ordinal], ArmPose::ordinal);

    private final ArmPoseKind holdFrame;
    private final ArmPoseKind releaseFrame;

    ArmPose(ArmPoseKind holdFrame, ArmPoseKind releaseFrame) {
        this.holdFrame = holdFrame;
        this.releaseFrame = releaseFrame;
    }

    /**
     * The frame the arm holds while the use key is held.
     *
     * @return the held frame
     */
    public ArmPoseKind holdFrame() {
        return holdFrame;
    }

    /**
     * The frame the arm plays as a sent throw lets go.
     *
     * @return the release frame, {@link ArmPoseKind#NONE} where the arm only eases down
     */
    public ArmPoseKind releaseFrame() {
        return releaseFrame;
    }

    /**
     * The pose an ability naming none takes, by how it leaves the glove: an
     * arc held out then flung, a beam or a stream held out, a self ability
     * held up.
     *
     * @param kind the ability's delivery kind
     * @return the default pose
     */
    public static ArmPose defaultFor(DeliveryKind kind) {
        return switch (kind) {
            case ARC -> THROWN;
            case BEAM, STREAM -> HELD_OUT;
            case SELF -> HELD_UP;
        };
    }
}
