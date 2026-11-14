package com.mercuriusxeno.goo.ability.zoo;

import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * What a rallied mob does each tick: it fights a foe in reach; with none it
 * walks to the spot on the side its rallier heads toward and holds there once
 * the rallier stops, or with no spot walks to its rallier from beyond the near
 * distance and holds within it.
 * rallied-mobs-follow-the-player-who-rallied-them
 * unengaged-minions-move-where-you-move
 */
public final class RallyFollow {

    /** How close to its rallier a rallied mob comes before it holds, in blocks. */
    static final double NEAR_DISTANCE = 4.0;
    /** The horizontal speed below which a rallier counts as standing, in blocks a tick; a sneak runs above it. */
    static final double STANDING_SPEED = 0.03;
    /** How far along the rallier's travel its minions gather, in blocks. */
    static final double LEAD_DISTANCE = 3.0;
    /** The radius minions spread around the gathering spot, short of the lead so each stays on the moving side. */
    static final double SPREAD_RADIUS = 1.5;
    /** The share of each tick's step a smoothed velocity takes, so one stutter cannot swing the group. */
    static final double STEP_WEIGHT = 0.3;
    /** How near its spot a minion stands before it holds there, in blocks. */
    static final double ARRIVAL_DISTANCE = 0.5;
    /** How far from a standing rallier a held spot may lie and still be kept: the farthest spot, and a block over. */
    static final double SPOT_REACH = LEAD_DISTANCE + SPREAD_RADIUS + 1.0;
    /** A step farther than this in one tick is a teleport, not travel, in blocks. */
    static final double TELEPORT_STEP = 2.0;
    /** The golden angle, which spreads any run of seeds evenly round a circle. */
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    /** Seeds per sunflower of the spread: any run this long lands each minion more than a cow's width apart. */
    static final int SPREAD_ROUND = 8;
    /** The half step that sets each sunflower seed in the middle of its ring band, so seed zero leaves the centre. */
    private static final double SEED_BAND_MIDDLE = 0.5;

    /** A rallied mob's move this tick. */
    enum Move {
        /** Chase and strike the foe in reach. */
        FIGHT,
        /** Walk to the spot on the side the rallier heads toward, or headed toward before it stopped. */
        PRE_EMPT,
        /** Walk toward the standing rallier. */
        FOLLOW,
        /** Stand where it is: at its spot, or near the standing rallier. */
        HOLD
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
     * @return the move
     */
    static Move choose(boolean foeInReach, Vec3 mobPosition, Vec3 rallierPosition, Optional<Vec3> spot) {
        if (foeInReach) {
            return Move.FIGHT;
        }
        if (spot.isPresent()) {
            return mobPosition.subtract(spot.get()).horizontalDistance() > ARRIVAL_DISTANCE ? Move.PRE_EMPT : Move.HOLD;
        }
        return mobPosition.distanceToSqr(rallierPosition) > NEAR_DISTANCE * NEAR_DISTANCE ? Move.FOLLOW : Move.HOLD;
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
     * @param spreadSeed      a number telling one minion from another, such as its entity id
     * @return the spot, or empty for none
     */
    static Optional<Vec3> spot(Optional<Vec3> heldSpot, Vec3 rallierPosition, Vec3 rallierVelocity, int spreadSeed) {
        Optional<Vec3> fresh = destination(rallierPosition, rallierVelocity, spreadSeed);
        if (fresh.isPresent()) {
            return fresh;
        }
        return heldSpot.filter(held -> held.subtract(rallierPosition).horizontalDistance() <= SPOT_REACH);
    }

    /**
     * Where an unengaged minion walks while its rallier moves: the lead
     * distance along the rallier's horizontal travel, spread by the minion's
     * seed so a group does not stack on one block. Backing up puts it behind
     * the rallier, stepping left to the rallier's left.
     *
     * @param rallierPosition the rallier's position
     * @param rallierVelocity the rallier's smoothed velocity, in blocks a tick
     * @param spreadSeed      a number telling one minion from another, such as its entity id
     * @return the spot, or empty while the rallier stands
     */
    static Optional<Vec3> destination(Vec3 rallierPosition, Vec3 rallierVelocity, int spreadSeed) {
        if (!isMoving(rallierVelocity)) {
            return Optional.empty();
        }
        Vec3 travel = new Vec3(rallierVelocity.x, 0.0, rallierVelocity.z).normalize();
        // a sunflower spread: the golden angle turns each seed, the square root keeps the disc evenly filled
        double angle = spreadSeed * GOLDEN_ANGLE;
        double ring = SPREAD_RADIUS * Math.sqrt((Math.floorMod(spreadSeed, SPREAD_ROUND) + SEED_BAND_MIDDLE) / SPREAD_ROUND);
        return Optional.of(rallierPosition.add(travel.scale(LEAD_DISTANCE))
                .add(Math.cos(angle) * ring, 0.0, Math.sin(angle) * ring));
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

    private static boolean isMoving(Vec3 velocity) {
        return velocity.horizontalDistance() >= STANDING_SPEED;
    }
}
