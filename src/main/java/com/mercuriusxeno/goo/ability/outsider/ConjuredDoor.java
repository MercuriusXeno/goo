package com.mercuriusxeno.goo.ability.outsider;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * The door a player conjures while channeling Outsider toward the pocket:
 * it stands in front of them, the way they face, and unzips from a pixel
 * at the doorway's head down to the floor across the charge; over the
 * charge's last ticks it widens as it moves into them, reaching full width
 * on their feet the tick the charge completes, so it envelops them as they
 * go. A channel let go early collapses it.
 * decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player
 * decision door-unzips-from-a-starfield-pixel-then-widens-into-you
 * The share crossed travels with the door so the caster's own client can
 * stand it from the look of each frame it draws.
 * decision conjured-door-follows-the-look-smoothly
 *
 * @param at       the middle of the door's foot
 * @param yaw      the way the caster faced, so the door faces them, in degrees
 * @param unzipped how far it has unzipped from the doorway's head, 0 for a pixel to 1 for a slash on the floor
 * @param widened  how far it has widened, 0 for a pixel wide to 1 for the doorway's full width
 * @param crossed  the share of its stand-off it has moved into the caster, 0 before them to 1 on their feet
 * @param phase    whether it grows before the caster or moves into them
 */
public record ConjuredDoor(Vec3 at, float yaw, double unzipped, double widened, double crossed, Phase phase) {

    /** How far before the caster's feet the door stands while it grows, in blocks. */
    public static final double STANDS_OFF = 1.5;
    /** The charge's last ticks, over which the door moves into the caster. */
    static final int ENVELOP_TICKS = 6;

    /** Sends a door. */
    public static final StreamCodec<ByteBuf, ConjuredDoor> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, ConjuredDoor::at,
            ByteBufCodecs.FLOAT, ConjuredDoor::yaw,
            ByteBufCodecs.DOUBLE, ConjuredDoor::unzipped,
            ByteBufCodecs.DOUBLE, ConjuredDoor::widened,
            ByteBufCodecs.DOUBLE, ConjuredDoor::crossed,
            ByteBufCodecs.idMapper(ordinal -> Phase.values()[ordinal], Phase::ordinal), ConjuredDoor::phase,
            ConjuredDoor::new);

    /** Where a conjured door stands in its life. */
    public enum Phase {
        /** Standing before the caster, unzipping with the charge. */
        GROWING,
        /** Unzipped to the floor, widening as it moves into the caster. */
        ENVELOPING,
        /** In the room, moving off the arriver the way they face, narrowing to a slash, then zipping up. */
        RELEASING
    }

    /** The ticks a releasing door takes to move off its arriver, narrowing to a slash. */
    public static final int MOVE_OFF_TICKS = 10;
    /** The ticks the slash then takes to zip up to a pixel: a starting value for the operator to judge. */
    public static final int ZIP_UP_TICKS = 10;
    /** The ticks a whole release takes. */
    public static final int RELEASE_TICKS = MOVE_OFF_TICKS + ZIP_UP_TICKS;

    /**
     * The door releasing a player into their room, the entry played in
     * full reverse on one clock: on their feet at full width as they arrive,
     * moving off them the way they face to its stand-off, narrowing back to
     * a slash as it goes, then zipping up from the floor to a pixel at the
     * doorway's head.
     * decision matching-doorway-opens-in-the-room-and-releases-you
     * decision release-doorway-plays-the-unzip-in-full-reverse
     *
     * @param feet the arriver's feet
     * @param yaw  the arriver's yaw, in degrees
     * @param tick the release's ticks so far, partial between ticks
     * @return the door
     */
    public static ConjuredDoor releasing(Vec3 feet, float yaw, double tick) {
        if (tick <= MOVE_OFF_TICKS) {
            double crossed = 1 - Math.clamp(tick / MOVE_OFF_TICKS, 0, 1);
            return new ConjuredDoor(placedAt(feet, yaw, crossed), yaw, 1, crossed, crossed, Phase.RELEASING);
        }
        double unzipped = 1 - Math.clamp((tick - MOVE_OFF_TICKS) / ZIP_UP_TICKS, 0, 1);
        return new ConjuredDoor(placedAt(feet, yaw, 0), yaw, unzipped, 0, 0, Phase.RELEASING);
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
            return new ConjuredDoor(placedAt(feet, yaw, 0), yaw, Math.max(0, held) / (double) growing, 0, 0,
                    Phase.GROWING);
        }
        double crossed = Math.min(1, (held - growing) / (double) (charge - growing));
        return new ConjuredDoor(placedAt(feet, yaw, crossed), yaw, 1, crossed, crossed, Phase.ENVELOPING);
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
