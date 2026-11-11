package com.mercuriusxeno.goo.ability.outsider;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * The door a player conjures while channeling Outsider toward the pocket:
 * it stands in front of them, the way they face, and grows from nothing to
 * full size across the charge; over the charge's last ticks it moves into
 * them, reaching their feet the tick the charge completes, so it envelops
 * them as they go. A channel let go early collapses it.
 * decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player
 *
 * @param at    the middle of the door's foot
 * @param yaw   the way the caster faced, so the door faces them, in degrees
 * @param size  its size, 0 for nothing to 1 for full
 * @param phase whether it grows before the caster or moves into them
 */
public record ConjuredDoor(Vec3 at, float yaw, double size, Phase phase) {

    /** How far before the caster's feet the door stands while it grows, in blocks. */
    static final double STANDS_OFF = 1.5;
    /** The charge's last ticks, over which the door moves into the caster. */
    static final int ENVELOP_TICKS = 6;

    /** Sends a door. */
    public static final StreamCodec<ByteBuf, ConjuredDoor> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, ConjuredDoor::at,
            ByteBufCodecs.FLOAT, ConjuredDoor::yaw,
            ByteBufCodecs.DOUBLE, ConjuredDoor::size,
            ByteBufCodecs.idMapper(ordinal -> Phase.values()[ordinal], Phase::ordinal), ConjuredDoor::phase,
            ConjuredDoor::new);

    /** Where a conjured door stands in its life. */
    public enum Phase {
        /** Standing before the caster, growing with the charge. */
        GROWING,
        /** Full size, moving into the caster. */
        ENVELOPING
    }

    /**
     * The door a hold has conjured so far.
     *
     * @param feet   the caster's feet
     * @param yaw    the caster's yaw, in degrees
     * @param held   the hold's tick count
     * @param charge the ticks the charge takes
     * @return the door
     */
    public static ConjuredDoor standing(Vec3 feet, float yaw, int held, int charge) {
        double radians = Math.toRadians(yaw);
        Vec3 front = feet.add(-Math.sin(radians) * STANDS_OFF, 0, Math.cos(radians) * STANDS_OFF);
        int growing = Math.max(1, charge - ENVELOP_TICKS);
        if (held <= growing) {
            return new ConjuredDoor(front, yaw, Math.max(0, held) / (double) growing, Phase.GROWING);
        }
        if (held >= charge) {
            return new ConjuredDoor(feet, yaw, 1, Phase.ENVELOPING);
        }
        return new ConjuredDoor(front.lerp(feet, (held - growing) / (double) (charge - growing)), yaw, 1,
                Phase.ENVELOPING);
    }
}
