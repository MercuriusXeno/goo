package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;

/**
 * The motion of Petrify's dust: each puff leaves the glove where the glove
 * stood, along the aim it was poured on, spread inside the cone, and travels
 * out to the reach over its life, rolling about its path as it goes. A puff
 * keeps the glove and aim it left with, so when the look turns the dust
 * already poured trails behind the new aim rather than turning with the hand.
 * decision petrify-fog-is-billowing-roiling-dust
 * decision petrify-stone-encasement-and-calcify-map
 */
final class PetrifyPuffs {

    /** How long a puff lives, in ticks, from the glove to the reach. */
    static final double LIFE_TICKS = 24;
    /** The share of the cone's half angle a puff's path leans out to; the roll fills the rest. */
    static final double SPREAD_SHARE = 0.85;
    /** The share of the cone's half angle the roll swings a puff out by, at most. */
    static final double ROLL_SHARE = 1 - SPREAD_SHARE;
    /** How fast a puff rolls about its path, in radians a tick. */
    private static final double ROLL_RATE = 0.35;
    /** A puff's radius at the glove, in blocks. */
    private static final double SMALLEST_RADIUS = 0.15;
    /** How much of the cone's width at its distance a puff covers. */
    private static final double COVERS = 0.9;
    private static final double TWO_PI = 2 * Math.PI;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final double NEARLY_UPRIGHT = 0.99;

    private PetrifyPuffs() {
    }

    /**
     * One puff of dust.
     *
     * @param glove     where the glove stood when the puff left it, in world space
     * @param path      the puff's unit direction, inside the cone about the aim it was poured on
     * @param bornTicks the game time the puff left the glove, in ticks
     * @param seed      the puff's own value, 0 to 1, setting its roll and its churn
     */
    record Puff(Vec3 glove, Vec3 path, double bornTicks, float seed) {
    }

    /**
     * Pours a puff from the glove along the aim, leaning out inside the cone.
     *
     * @param glove     the glove, in world space
     * @param aim       the aim's unit vector
     * @param halfAngle the cone's half angle in radians
     * @param bornTicks the game time it leaves the glove, in ticks
     * @param lean      how far out it leans, 0 on the aim to 1 at the spread's edge
     * @param around    where around the aim it leans, 0 to 1
     * @param seed      the puff's own value, 0 to 1
     * @return the puff
     */
    static Puff pour(Vec3 glove, Vec3 aim, double halfAngle, double bornTicks, double lean, double around,
                     float seed) {
        double tilt = halfAngle * SPREAD_SHARE * Math.sqrt(lean);
        Vec3 path = aim.scale(Math.cos(tilt)).add(across(aim, around * TWO_PI).scale(Math.sin(tilt)));
        return new Puff(glove, path.normalize(), bornTicks, seed);
    }

    /**
     * A puff's share of its life.
     *
     * @param puff     the puff
     * @param nowTicks the game time, in ticks
     * @return 0 as it leaves the glove to 1 at the reach
     */
    static double ageShare(Puff puff, double nowTicks) {
        return Math.clamp((nowTicks - puff.bornTicks()) / LIFE_TICKS, 0, 1);
    }

    /**
     * How far down its path a puff has travelled: quickly as it pours from
     * the glove, slowing as it billows out to the reach.
     *
     * @param ageShare the puff's share of its life
     * @param range    the delivery's reach
     * @return the distance from the glove, in blocks
     */
    static double distanceAt(double ageShare, double range) {
        double left = 1 - ageShare;
        return range * (1 - left * left);
    }

    /**
     * Where a puff stands: down its path from the glove it left, swung about
     * the path by its roll, never out past the cone.
     *
     * @param puff      the puff
     * @param nowTicks  the game time, in ticks
     * @param range     the delivery's reach
     * @param halfAngle the cone's half angle in radians
     * @return the puff's center, in world space
     */
    static Vec3 positionAt(Puff puff, double nowTicks, double range, double halfAngle) {
        double distance = distanceAt(ageShare(puff, nowTicks), range);
        double age = nowTicks - puff.bornTicks();
        double rollAngle = age * ROLL_RATE + puff.seed() * TWO_PI;
        double swing = distance * Math.tan(halfAngle) * ROLL_SHARE * Math.sin(age * ROLL_RATE + puff.seed());
        return puff.glove().add(puff.path().scale(distance)).add(across(puff.path(), rollAngle).scale(swing));
    }

    /**
     * A puff's radius at a distance from the glove: small as it pours,
     * spreading to cover the cone's width out at the reach.
     *
     * @param distance  the puff's distance from the glove
     * @param halfAngle the cone's half angle in radians
     * @return the radius in blocks
     */
    static double radiusAt(double distance, double halfAngle) {
        return SMALLEST_RADIUS + distance * Math.tan(halfAngle) * COVERS;
    }

    /**
     * A unit direction square to an axis, an angle around it.
     *
     * @param axis  the unit axis
     * @param angle the angle around it in radians
     * @return the direction
     */
    private static Vec3 across(Vec3 axis, double angle) {
        Vec3 reference = Math.abs(axis.dot(UP)) > NEARLY_UPRIGHT ? EAST : UP;
        Vec3 side = axis.cross(reference).normalize();
        Vec3 up = side.cross(axis);
        return side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
    }
}
