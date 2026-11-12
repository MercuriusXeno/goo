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
 * The share crossed travels with the door so the caster's own client can
 * stand it from the look of each frame it draws.
 * decision conjured-door-follows-the-look-smoothly
 *
 * @param at     the middle of the door's foot
 * @param yaw     the way the caster faced, so the door faces them, in degrees
 * @param size    its size, 0 for nothing to 1 for full
 * @param crossed the share of its stand-off it has moved into the caster, 0 before them to 1 on their feet
 * @param phase   whether it grows before the caster or moves into them
 */
public record ConjuredDoor(Vec3 at, float yaw, double size, double crossed, Phase phase) {

    /** How far before the caster's feet the door stands while it grows, in blocks. */
    public static final double STANDS_OFF = 1.5;
    /** The charge's last ticks, over which the door moves into the caster. */
    static final int ENVELOP_TICKS = 6;

    /** Sends a door. */
    public static final StreamCodec<ByteBuf, ConjuredDoor> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, ConjuredDoor::at,
            ByteBufCodecs.FLOAT, ConjuredDoor::yaw,
            ByteBufCodecs.DOUBLE, ConjuredDoor::size,
            ByteBufCodecs.DOUBLE, ConjuredDoor::crossed,
            ByteBufCodecs.idMapper(ordinal -> Phase.values()[ordinal], Phase::ordinal), ConjuredDoor::phase,
            ConjuredDoor::new);

    /** Where a conjured door stands in its life. */
    public enum Phase {
        /** Standing before the caster, growing with the charge. */
        GROWING,
        /** Full size, moving into the caster. */
        ENVELOPING,
        /** In the room, moving off the arriver the way they face and closing back to a crack. */
        RELEASING
    }

    /** The ticks a releasing door takes to move off its arriver and close. */
    public static final int RELEASE_TICKS = 10;

    /**
     * The door releasing a player into their room, the entry's motion
     * reversed: on their feet at full size as they arrive, then moving off
     * them the way they face to its stand-off, narrowing back to a crack as
     * it goes.
     * decision matching-doorway-opens-in-the-room-and-releases-you
     *
     * @param feet         the arriver's feet
     * @param yaw          the arriver's yaw, in degrees
     * @param tick         the release's ticks so far, partial between ticks
     * @param releaseTicks the ticks the release takes
     * @return the door
     */
    public static ConjuredDoor releasing(Vec3 feet, float yaw, double tick, int releaseTicks) {
        double released = Math.clamp(tick / Math.max(1, releaseTicks), 0, 1);
        double crossed = 1 - released;
        return new ConjuredDoor(placedAt(feet, yaw, crossed), yaw, crossed, crossed, Phase.RELEASING);
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
        int growing = Math.max(1, charge - ENVELOP_TICKS);
        if (held <= growing) {
            return new ConjuredDoor(placedAt(feet, yaw, 0), yaw, Math.max(0, held) / (double) growing, 0,
                    Phase.GROWING);
        }
        double crossed = Math.min(1, (held - growing) / (double) (charge - growing));
        return new ConjuredDoor(placedAt(feet, yaw, crossed), yaw, 1, crossed, Phase.ENVELOPING);
    }

    /**
     * Where a door's foot stands for a caster's feet and look: its stand-off
     * before them the way they face, less the share it has crossed into them.
     *
     * @param feet    the caster's feet
     * @param yaw     the caster's yaw, in degrees
     * @param crossed the share of its stand-off the door has moved into them
     * @return the middle of the door's foot
     */
    public static Vec3 placedAt(Vec3 feet, float yaw, double crossed) {
        double radians = Math.toRadians(yaw);
        double off = STANDS_OFF * (1 - crossed);
        return feet.add(-Math.sin(radians) * off, 0, Math.cos(radians) * off);
    }
}
