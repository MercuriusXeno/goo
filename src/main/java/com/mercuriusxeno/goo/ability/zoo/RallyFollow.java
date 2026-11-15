package com.mercuriusxeno.goo.ability.zoo;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * What a rallied mob does each tick: it fights a foe in reach; with none it
 * walks to its slot in the formation on the side its rallier heads toward and
 * holds there once the rallier stops, or with no spot walks to its rallier
 * from beyond the near distance and holds within it. The formation's slots
 * come from rally order, the count of minions and their widths alone. A
 * minion far from its slot catches up faster than its rallier; one at its
 * slot locks in and marches at the rallier's speed.
 * rallied-mobs-follow-the-player-who-rallied-them
 * unengaged-minions-move-where-you-move
 * each-rallied-mob-locks-into-a-fixed-slot
 * slots-follow-rally-order-size-and-count
 * rallied-mobs-share-their-ralliers-speed
 * rallied-mobs-walk-around-their-rallier
 */
public final class RallyFollow {

    /** How close to its rallier a rallied mob comes before it holds, in blocks. */
    static final double NEAR_DISTANCE = 4.0;
    /** The horizontal speed below which a rallier counts as standing, in blocks a tick; a sneak runs above it. */
    static final double STANDING_SPEED = 0.03;
    /** How far from its rallier the near edge of the formation's first shell stands, in blocks. */
    static final double FIRST_SHELL_EDGE = 2.5;
    /** The space left between two minions' sides, along a shell and from one shell to the next, in blocks. */
    static final double SLOT_GAP = 0.5;
    /** The arc each shell spreads its minions over, a third of a turn centred on the rallier's travel. */
    static final double SHELL_ARC = 2.0 * Math.PI / 3.0;
    /** The share of each tick's step a smoothed velocity takes, so one stutter cannot swing the group. */
    static final double STEP_WEIGHT = 0.3;
    /** How near its slot a minion comes before it locks in and marches, in blocks. */
    public static final double ARRIVAL_DISTANCE = 0.5;
    /**
     * How far from its slot a locked-in minion falls before it catches up
     * again, in blocks: wider than the arrival, so it does not flicker between the two.
     */
    static final double RELEASE_DISTANCE = 1.0;
    /** How many times its rallier's speed a minion catching up walks at. */
    static final double CATCH_UP_SHARE = 1.5;
    /** The least ground speed a minion catches up at, so it closes on a standing rallier, in blocks a tick. */
    static final double CATCH_UP_FLOOR = 0.15;
    /**
     * The ground speed a walking mob reaches per square of its speed on a
     * plain block: its input keeps 0.98 of its speed after each tick's decay,
     * and the block's 0.6 friction under the 0.91 drag keeps 0.546 of its
     * motion a tick, which a steady walk holds at one over the 0.454 lost.
     */
    static final double WALK_GAIN = 0.98 / (1.0 - 0.6 * 0.91);
    /** How much farther than its slot a standing rallier may stand from a held spot and still keep it, in blocks. */
    static final double SPOT_SLACK = 1.0;
    /** A step farther than this in one tick is a teleport, not travel, in blocks. */
    static final double TELEPORT_STEP = 2.0;
    /** How far past a rallier's half width and a minion's the keep-clear radius reaches, in blocks. */
    static final double KEEP_CLEAR_MARGIN = 0.5;
    /** How far outside the keep-clear radius a walk round the rallier passes, in blocks. */
    static final double WAYPOINT_SLACK = 0.5;
    /** How near a point a minion stands to count as on it, so it only moves as its rallier does, in blocks. */
    static final double AIM_REACHED = 0.05;
    /**
     * How far ahead along its heading a minion's steering point stands, in
     * blocks: past a block, so its move control never reads the point as one
     * straight overhead to jump to.
     */
    static final double AIM_LEAD = 2.0;
    /** How far past the point it rounds the rallier a waypoint stands, so the minion walks on round, in blocks. */
    static final double WAYPOINT_LEAD = 0.5;
    /** The share a width or a spread takes to its half. */
    private static final double HALF = 0.5;

    /** A rallied mob's move this tick. */
    enum Move {
        /** Chase and strike the foe in reach. */
        FIGHT,
        /**
         * Walk faster than the rallier to the slot on the side it heads
         * toward, or headed toward before it stopped.
         */
        CATCH_UP,
        /** Move with the rallier at its speed, locked into the slot. */
        MARCH,
        /** Walk toward the standing rallier. */
        FOLLOW,
        /** Stand where it is, near the standing rallier. */
        HOLD
    }

    /**
     * One minion's place in its rallier's formation, measured from the
     * rallier against its travel.
     *
     * @param ahead how far along the travel the slot stands, in blocks
     * @param aside how far to the side of the travel line it stands, in blocks, negative on one side
     */
    public record Slot(double ahead, double aside) {

        /**
         * How far the slot stands from the rallier.
         *
         * @return the horizontal distance, in blocks
         */
        public double distance() {
            return Math.hypot(ahead, aside);
        }
    }

    private RallyFollow() {
    }

    /**
     * Chooses a rallied mob's move.
     *
     * @param foeInReach      whether a foe of the rallier stands in the mob's reach
     * @param mobPosition     the mob's position
     * @param rallierPosition the rallier's position
     * @param spot            the spot the mob walks to on the side its rallier headed toward, from {@link #spot}
     * @param lockedIn        whether the mob marched locked into its slot last tick
     * @return the move
     */
    static Move choose(boolean foeInReach, Vec3 mobPosition, Vec3 rallierPosition, Optional<Vec3> spot,
                       boolean lockedIn) {
        if (foeInReach) {
            return Move.FIGHT;
        }
        if (spot.isPresent()) {
            // each-rallied-mob-locks-into-a-fixed-slot: a locked-in minion holds the march out to the wider release
            double lockReach = lockedIn ? RELEASE_DISTANCE : ARRIVAL_DISTANCE;
            return mobPosition.subtract(spot.get()).horizontalDistance() > lockReach ? Move.CATCH_UP : Move.MARCH;
        }
        return mobPosition.distanceToSqr(rallierPosition) > NEAR_DISTANCE * NEAR_DISTANCE ? Move.FOLLOW : Move.HOLD;
    }

    /**
     * A rallier's minions in rally order: the earliest rally first, an equal
     * stamp broken by the minion's uuid, so every call answers one order.
     *
     * @param minions      the rallier's minions
     * @param rallyOrderOf each minion's rally stamp, from {@link Rallied#rallyOrder}
     * @param uuidOf       each minion's uuid
     * @param <T>          the minion type
     * @return the minions in rally order; a minion's index is its slot
     */
    static <T> List<T> inRallyOrder(Collection<T> minions, ToLongFunction<T> rallyOrderOf, Function<T, UUID> uuidOf) {
        List<T> ordered = new ArrayList<>(minions);
        ordered.sort(Comparator.comparingLong(rallyOrderOf).thenComparing(uuidOf));
        return ordered;
    }

    /**
     * The formation a rallier's minions take, one slot each in rally order.
     * Slots fill shells round the rallier from the inside out, each shell an
     * arc centred on the travel, so the formation's reach grows with its count
     * and its minions' widths. Neighbours along a shell stand their half
     * widths and the gap apart; one shell stands its widest minion's half
     * width, the gap and the next shell's widest half width inside the next.
     * The first shell's near edge stands clear of the rallier's half width by
     * the keep-clear margin and the gap, so every slot stands a gap outside
     * the keep-clear radius, however wide the rallier.
     * rallied-mobs-walk-around-their-rallier
     *
     * @param rallierWidth the rallier's width, in blocks
     * @param widths       the minions' widths in rally order, in blocks
     * @return the slots, one per width, in the same order
     */
    public static List<Slot> formation(double rallierWidth, List<Double> widths) {
        List<Slot> slots = new ArrayList<>(widths.size());
        double edge = Math.max(FIRST_SHELL_EDGE, rallierWidth * HALF + KEEP_CLEAR_MARGIN + SLOT_GAP);
        int first = 0;
        while (first < widths.size()) {
            int end = shellEnd(widths, first, edge);
            double radius = edge + widestHalf(widths, first, end);
            placeShell(widths, first, end, radius, slots);
            edge = radius + widestHalf(widths, first, end) + SLOT_GAP;
            first = end;
        }
        return slots;
    }

    /**
     * Where one shell's minions stop: it takes minions in order while their
     * spread fits the shell's arc, and always takes its first.
     *
     * @param widths the minions' widths in rally order, in blocks
     * @param first  the index of the shell's first minion
     * @param edge   how far from the rallier the shell's near edge stands, in blocks
     * @return the index past the shell's last minion
     */
    private static int shellEnd(List<Double> widths, int first, double edge) {
        int end = first + 1;
        while (end < widths.size()) {
            double radius = edge + widestHalf(widths, first, end + 1);
            if (spread(widths, first, end + 1, radius) > SHELL_ARC) {
                break;
            }
            end++;
        }
        return end;
    }

    private static void placeShell(List<Double> widths, int first, int end, double radius, List<Slot> slots) {
        double angle = -spread(widths, first, end, radius) * HALF;
        for (int index = first; index < end; index++) {
            if (index > first) {
                angle += step(widths.get(index - 1), widths.get(index), radius);
            }
            slots.add(new Slot(radius * Math.cos(angle), radius * Math.sin(angle)));
        }
    }

    private static double spread(List<Double> widths, int first, int end, double radius) {
        double spread = 0.0;
        for (int index = first + 1; index < end; index++) {
            spread += step(widths.get(index - 1), widths.get(index), radius);
        }
        return spread;
    }

    /**
     * The angle between two neighbours on a shell, whose chord spans their half widths and the gap.
     *
     * @param width          one neighbour's width, in blocks
     * @param neighbourWidth the other's width, in blocks
     * @param radius         the shell's radius, in blocks
     * @return the angle, in radians
     */
    private static double step(double width, double neighbourWidth, double radius) {
        double chord = (width + neighbourWidth) * HALF + SLOT_GAP;
        double halfAngle = Math.asin(Math.min(1.0, chord * HALF / radius));
        return halfAngle + halfAngle;
    }

    private static double widestHalf(List<Double> widths, int first, int end) {
        double widest = 0.0;
        for (int index = first; index < end; index++) {
            widest = Math.max(widest, widths.get(index));
        }
        return widest * HALF;
    }

    /**
     * The spot an unengaged minion walks to: a fresh one while its rallier
     * moves, and once the rallier stops the one it last took, so the minion
     * walks on to it and holds there rather than halting where it stood. A
     * held spot the rallier stands far from, as after a teleport, drops, and
     * the minion follows the rallier again.
     *
     * @param heldSpot        the spot the minion took last tick, empty for none
     * @param rallierPosition the rallier's position
     * @param rallierVelocity the rallier's smoothed velocity, in blocks a tick
     * @param slot            the minion's slot, from {@link #formation}
     * @return the spot, or empty for none
     */
    static Optional<Vec3> spot(Optional<Vec3> heldSpot, Vec3 rallierPosition, Vec3 rallierVelocity, Slot slot) {
        Optional<Vec3> fresh = destination(rallierPosition, rallierVelocity, slot);
        if (fresh.isPresent()) {
            return fresh;
        }
        return heldSpot.filter(held -> held.subtract(rallierPosition).horizontalDistance()
                <= slot.distance() + SPOT_SLACK);
    }

    /**
     * Where an unengaged minion walks while its rallier moves: its slot,
     * turned with the rallier's horizontal travel, so the formation stands on
     * the side the rallier heads toward and the minion keeps its place in it
     * as the rallier turns. Backing up puts it behind the rallier, stepping
     * left to the rallier's left.
     *
     * @param rallierPosition the rallier's position
     * @param rallierVelocity the rallier's smoothed velocity, in blocks a tick
     * @param slot            the minion's slot, from {@link #formation}
     * @return the spot, or empty while the rallier stands
     */
    public static Optional<Vec3> destination(Vec3 rallierPosition, Vec3 rallierVelocity, Slot slot) {
        if (!isMoving(rallierVelocity)) {
            return Optional.empty();
        }
        Vec3 travel = new Vec3(rallierVelocity.x, 0.0, rallierVelocity.z).normalize();
        Vec3 side = new Vec3(-travel.z, 0.0, travel.x);
        return Optional.of(rallierPosition.add(travel.scale(slot.ahead())).add(side.scale(slot.aside())));
    }

    /**
     * Folds one tick's step of the rallier into its smoothed velocity; a
     * teleport's step resets it to standing.
     *
     * @param smoothed the smoothed velocity so far, in blocks a tick
     * @param step     the rallier's position change over the last tick
     * @return the new smoothed velocity
     */
    static Vec3 smoothVelocity(Vec3 smoothed, Vec3 step) {
        if (step.horizontalDistance() > TELEPORT_STEP) {
            return Vec3.ZERO;
        }
        return smoothed.scale(1.0 - STEP_WEIGHT).add(step.scale(STEP_WEIGHT));
    }

    /**
     * The pace that walks a minion at a given ground speed: the modifier on
     * its movement speed, so a locked-in minion matches its rallier's speed
     * and one catching up outruns it, closing even on a standing rallier.
     * rallied-mobs-share-their-ralliers-speed
     *
     * @param rallierSpeed the rallier's smoothed horizontal speed, in blocks a tick
     * @param ownSpeed     the minion's movement speed attribute value, its rally buff included
     * @param lockedIn     whether the minion marches locked into its slot
     * @return the speed modifier its walk takes
     */
    static double pace(double rallierSpeed, double ownSpeed, boolean lockedIn) {
        if (ownSpeed <= 0.0) {
            return 0.0;
        }
        double ground = lockedIn ? rallierSpeed : Math.max(rallierSpeed * CATCH_UP_SHARE, CATCH_UP_FLOOR);
        return Math.sqrt(ground / WALK_GAIN) / ownSpeed;
    }

    /**
     * The ground speed a mob walks at under its move control: its speed
     * drives both its input and its friction-scaled push, so on a plain block
     * the walk settles at the walk gain times the speed's square.
     * rallied-mobs-share-their-ralliers-speed
     *
     * @param speed the mob's movement speed times its walk's modifier
     * @return the ground speed, in blocks a tick
     */
    static double groundSpeed(double speed) {
        return WALK_GAIN * speed * speed;
    }

    /**
     * How far a minion stands from its rallier, at the closest, before it
     * walks round: the rallier's half width, its own half width and this
     * margin, so it never shoves the rallier or crosses in front of its view.
     * rallied-mobs-walk-around-their-rallier
     *
     * @param rallierWidth the rallier's width, in blocks
     * @param minionWidth  the minion's width, in blocks
     * @return the keep-clear radius round the rallier's centre, in blocks
     */
    public static double keepClearRadius(double rallierWidth, double minionWidth) {
        return (rallierWidth + minionWidth) * HALF + KEEP_CLEAR_MARGIN;
    }

    /**
     * The next point a minion walks to on its way to a target, keeping clear
     * of its rallier: the target itself where the straight line there passes
     * outside the keep-clear radius; a waypoint beside the rallier on the
     * shorter way round where it crosses, along a line touching a circle a
     * little wider than the radius, so the minion circles the rallier; and
     * for a minion already inside, a point straight out of it first.
     * rallied-mobs-walk-around-their-rallier
     *
     * @param minion  the minion's position
     * @param target  where it walks, such as its slot's spot or a foe
     * @param rallier the rallier's position
     * @param radius  the keep-clear radius, from {@link #keepClearRadius}
     * @return the point to walk to now, the target where the way is clear
     */
    public static Vec3 walkPoint(Vec3 minion, Vec3 target, Vec3 rallier, double radius) {
        Vec3 out = flat(minion.subtract(rallier));
        double distance = out.length();
        if (distance < radius) {
            Vec3 away = distance > 0.0 ? out.scale(1.0 / distance) : awayToward(target, rallier);
            return rallier.add(away.scale(radius + WAYPOINT_SLACK + WAYPOINT_LEAD));
        }
        if (nearestPass(minion, target, rallier) >= radius) {
            return target;
        }
        // a minion already inside the wider circle walks round at its own distance, never back out
        double rounding = Math.min(radius + WAYPOINT_SLACK, distance);
        Vec3 toRallier = flat(rallier.subtract(minion)).scale(1.0 / distance);
        Vec3 toTarget = flat(target.subtract(minion));
        // the target's side of the line through the rallier is the shorter way round
        double turn = Math.copySign(Math.asin(rounding / distance),
                toRallier.x * toTarget.z - toRallier.z * toTarget.x);
        Vec3 along = new Vec3(toRallier.x * Math.cos(turn) - toRallier.z * Math.sin(turn), 0.0,
                toRallier.z * Math.cos(turn) + toRallier.x * Math.sin(turn));
        double tangent = Math.sqrt(distance * distance - rounding * rounding);
        Vec3 waypoint = minion.add(along.scale(tangent + WAYPOINT_LEAD));
        return new Vec3(waypoint.x, target.y, waypoint.z);
    }

    /**
     * Where a minion steers to walk to a point measured from its moving
     * rallier: its own motion is the rallier's plus a share toward the point,
     * sized so its ground speed holds, so its motion relative to the rallier
     * runs straight at the point and the walk round stays round. A minion at
     * the point moves as its rallier moves; one too slow to outrun the
     * rallier's drift steers straight at the point.
     * rallied-mobs-walk-around-their-rallier
     * each-rallied-mob-locks-into-a-fixed-slot
     *
     * @param minion          the minion's position
     * @param point           the point it walks to, from {@link #walkPoint}
     * @param rallierVelocity the rallier's smoothed velocity, in blocks a tick
     * @param groundSpeed     the minion's ground speed, in blocks a tick
     * @return a point along the minion's heading, at its height
     */
    public static Vec3 aim(Vec3 minion, Vec3 point, Vec3 rallierVelocity, double groundSpeed) {
        Vec3 drift = flat(rallierVelocity);
        Vec3 toPoint = flat(point.subtract(minion));
        double distance = toPoint.length();
        Vec3 heading = drift;
        if (distance > AIM_REACHED) {
            Vec3 toward = toPoint.scale(1.0 / distance);
            double driftAlong = drift.dot(toward);
            double room = driftAlong * driftAlong - drift.lengthSqr() + groundSpeed * groundSpeed;
            heading = room < 0.0 ? toward : drift.add(toward.scale(Math.sqrt(room) - driftAlong));
        }
        if (heading.lengthSqr() == 0.0) {
            return new Vec3(point.x, minion.y, point.z);
        }
        return minion.add(heading.normalize().scale(AIM_LEAD));
    }

    /**
     * The nearest the straight walk from one point to another passes the rallier, horizontally.
     *
     * @param from    where the walk starts
     * @param to      where it ends
     * @param rallier the rallier's position
     * @return the least horizontal distance from the rallier along the walk, in blocks
     */
    private static double nearestPass(Vec3 from, Vec3 to, Vec3 rallier) {
        Vec3 walk = flat(to.subtract(from));
        Vec3 toRallier = flat(rallier.subtract(from));
        double length = walk.lengthSqr();
        double along = length == 0.0 ? 0.0 : Math.clamp(toRallier.dot(walk) / length, 0.0, 1.0);
        return toRallier.subtract(walk.scale(along)).length();
    }

    /**
     * The way out for a minion standing on its rallier's centre: toward its target, or east for none.
     *
     * @param target  where the minion walks
     * @param rallier the rallier's position
     * @return a horizontal unit vector
     */
    private static Vec3 awayToward(Vec3 target, Vec3 rallier) {
        Vec3 toward = flat(target.subtract(rallier));
        return toward.lengthSqr() > 0.0 ? toward.normalize() : new Vec3(1.0, 0.0, 0.0);
    }

    private static Vec3 flat(Vec3 vector) {
        return new Vec3(vector.x, 0.0, vector.z);
    }

    private static boolean isMoving(Vec3 velocity) {
        return velocity.horizontalDistance() >= STANDING_SPEED;
    }
}
