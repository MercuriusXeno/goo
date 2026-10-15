package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The shape of Flames' fire, one body the server burns with and the client
 * draws: the jet's cone from its apex, and where the crosshair meets a block,
 * the damped bounce off that face, rolling along a ceiling, merged with the
 * jet by a smooth union and never reaching behind the face. The operator's
 * ruling: damage follows the drawn fire. The client's {@code fire_field.fsh}
 * mirrors {@link #body} and adds the flicker; the two change together.
 * flames-pixelated-fire-stream
 * <p>
 * The operator's rulings on the bounce: the fire keeps its push along the
 * face, throws back {@link #RESTITUTION} of its push into it, runs
 * {@link #KEEP} of the front's push past the wall, disperses at
 * {@link #SPLAY} times the jet's cone angle and climbs as it slows, sliding
 * along a face where up runs into it; under a ceiling it rolls along the
 * underside, its whole body hanging just under the face.
 */
public final class FlameShape {

    /** The share of its push into the face the bounce throws back off it. */
    public static final double RESTITUTION = 0.25;
    /** The share of the fire front's push past the wall the bounce runs. */
    public static final double KEEP = 0.35;
    /** How many times the jet's cone angle the bounce splays at. */
    public static final double SPLAY = 2;
    /** Blocks the bounce climbs per block squared it runs. */
    public static final double RISE = 0.25;
    /** Ticks the fire front takes to travel the stream's whole range. */
    public static final double TRAVEL_TICKS = 5;
    /** How far the fire's middle lifts per block it travels. */
    static final double LIFT_PER_BLOCK = 0.10;
    /** How much taller the fire burns above its middle than below. */
    static final double STRETCH_ABOVE = 0.65;
    static final double SQUASH_BELOW = 1.35;
    /** How wide the smooth union blends the jet into its bounce, in body units. */
    static final double BLEND = 0.45;
    /** How far past an open end the body runs before it stops outright, in blocks. */
    static final double FRAY_REACH = 0.4;
    /** The thinnest the jet runs, at its apex, in blocks. */
    static final double NOZZLE_BLOCKS = 0.06;
    /** Under a ceiling, below this share of the look along the face the roll spreads evenly; above the next it heads one way. */
    static final double CEILING_EVEN_BELOW = 0.15;
    static final double CEILING_AIMED_ABOVE = 0.5;
    /** The body a point must reach for the fire to touch it: inside the flame's radius, short of its licking edge. */
    static final double TOUCHES = -0.1;
    private static final double NO_BODY = -10;
    /** A length that reads as no wall and no bounce. */
    private static final double NONE = -1;
    /** The bits of a corner's index that pick its x, y and z. */
    private static final int X_BIT = 1;
    private static final int Y_BIT = 2;
    private static final int Z_BIT = 4;
    private static final double SMOOTHSTEP_RISE = 3;
    private static final double SMOOTHSTEP_EASE = 2;
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
    /** Below this squared length a direction has no way. */
    private static final double NO_WAY = 1e-6;
    private static final double HALF = 0.5;

    private final Vec3 apex;
    private final Vec3 axis;
    private final Vec3 side;
    private final double tan;
    private final double jetEnd;
    private final boolean frontFrays;
    private final @Nullable Vec3 hit;
    private final double wall;
    private final Vec3 normal;
    private final Vec3 bounce;
    private final double bounceLength;
    private final Vec3 climb;

    /**
     * The fire's shape with its front pushed a way down the look.
     *
     * @param apex   where the jet pours from
     * @param axis   the jet's unit vector
     * @param side   the unit direction across the jet to its side, level
     * @param tan    the tangent of half the cone's angle
     * @param front  blocks down the jet the fire front has pushed
     * @param hit    where the jet meets a block within the reach, or null
     * @param normal the unit normal of the face it meets, out of the block, or null
     */
    public FlameShape(Vec3 apex, Vec3 axis, Vec3 side, double tan, double front, @Nullable Vec3 hit,
                      @Nullable Vec3 normal) {
        this.apex = apex;
        this.axis = axis;
        this.side = side;
        this.tan = tan;
        double toWall = hit == null ? Double.POSITIVE_INFINITY : hit.distanceTo(apex);
        boolean cut = hit != null && normal != null && toWall <= front;
        this.hit = cut ? hit : null;
        this.wall = cut ? toWall : NONE;
        this.normal = cut ? normal : WORLD_UP;
        this.jetEnd = cut ? toWall : front;
        this.frontFrays = !cut;
        this.bounce = cut ? bounceAxis(axis, normal) : axis;
        this.bounceLength = cut ? (front - toWall) * KEEP : NONE;
        this.climb = cut ? climbAlong(normal) : WORLD_UP;
    }

    /**
     * How far down the look the fire front has pushed a given time into the
     * hold: the whole range in {@link #TRAVEL_TICKS}.
     *
     * @param ticksHeld ticks since the hold began
     * @param range     the stream's reach
     * @return blocks down the look
     */
    public static double frontAfter(double ticksHeld, double range) {
        return Math.min(range, range * ticksHeld / TRAVEL_TICKS);
    }

    /**
     * The way the fire bounces off a face: its push along the face kept
     * whole, its push into the face thrown back at {@link #RESTITUTION}.
     *
     * @param axis   the jet's unit vector
     * @param normal the face's unit normal, out of the block
     * @return the bounce's unit vector
     */
    public static Vec3 bounceAxis(Vec3 axis, Vec3 normal) {
        double into = axis.dot(normal);
        Vec3 along = axis.subtract(normal.scale(into));
        Vec3 bounced = along.add(normal.scale(Math.abs(into) * RESTITUTION));
        return bounced.lengthSqr() > NO_WAY ? bounced.normalize() : normal;
    }

    /**
     * The way the bounce climbs off a face as it slows: up, or where up runs
     * into the face, as under a ceiling, only up's share along the face.
     *
     * @param normal the face's unit normal, out of the block
     * @return the climb: up, or its share along the face
     */
    public static Vec3 climbAlong(Vec3 normal) {
        double into = WORLD_UP.dot(normal);
        return into < 0 ? WORLD_UP.subtract(normal.scale(into)) : WORLD_UP;
    }

    /** @return whether the jet meets a block, so the fire bounces */
    public boolean bounces() {
        return hit != null;
    }

    /** @return blocks down the jet it burns to: the wall, or the open front */
    public double jetEnd() {
        return jetEnd;
    }

    /** @return blocks the bounce runs from the hit, negative with no bounce */
    public double bounceLength() {
        return bounceLength;
    }

    /** @return the bounce's unit vector, the jet's own with no bounce */
    public Vec3 bounce() {
        return bounce;
    }

    /** @return the way the bounce climbs */
    public Vec3 climb() {
        return climb;
    }

    /** @return the hit face's unit normal, up with no bounce */
    public Vec3 normal() {
        return normal;
    }

    /** @return where the jet meets the block, or null */
    public @Nullable Vec3 hit() {
        return hit;
    }

    /** @return whether the jet's front is open and frays rather than meeting a block */
    public boolean frontFrays() {
        return frontFrays;
    }

    /**
     * How far inside the fire a point stands: positive inside the flame's
     * radius, zero on it, negative outside.
     *
     * @param point the point
     * @return the body there
     */
    public double body(Vec3 point) {
        if (hit != null && point.subtract(hit).dot(normal) < 0) {
            return NO_BODY;
        }
        double jet = jetBody(point);
        double bounced = bounceBody(point);
        double weight = Math.clamp(HALF + HALF * (jet - bounced) / BLEND, 0, 1);
        return bounced + (jet - bounced) * weight + BLEND * weight * (1 - weight) * HALF;
    }

    /**
     * Whether the fire touches a box: a point of it, its middle or a corner
     * drawn halfway in, stands inside the flame.
     *
     * @param box the box, a living thing's hitbox
     * @return whether the fire reaches it
     */
    public boolean touches(AABB box) {
        Vec3 middle = box.getCenter();
        if (body(middle) > TOUCHES) {
            return true;
        }
        for (int corner = 0; corner < CORNERS; corner++) {
            Vec3 at = new Vec3((corner & X_BIT) == 0 ? box.minX : box.maxX, (corner & Y_BIT) == 0 ? box.minY : box.maxY,
                    (corner & Z_BIT) == 0 ? box.minZ : box.maxZ);
            if (body(middle.add(at.subtract(middle).scale(HALF))) > TOUCHES) {
                return true;
            }
        }
        return false;
    }

    private static final int CORNERS = 8;

    private double jetBody(Vec3 point) {
        Vec3 fromApex = point.subtract(apex);
        double along = fromApex.dot(axis);
        if (along < 0 || (frontFrays && along > jetEnd + FRAY_REACH)) {
            return NO_BODY;
        }
        Vec3 off = fromApex.subtract(axis.scale(along));
        Vec3 up = side.cross(axis);
        return coneBody(off.dot(side), off.dot(up), along * tan, along, 0, LIFT_PER_BLOCK);
    }

    private double bounceBody(Vec3 point) {
        if (hit == null) {
            return NO_BODY;
        }
        Vec3 fromHit = point.subtract(hit);
        Vec3 heading = bounce;
        double lift = LIFT_PER_BLOCK;
        boolean ceiling = normal.y < -HALF;
        if (ceiling) {
            Vec3 inPlane = fromHit.subtract(normal.scale(fromHit.dot(normal)));
            double outwardLength = inPlane.length();
            Vec3 outward = outwardLength > NO_WAY ? inPlane.scale(1 / outwardLength) : bounce;
            Vec3 skim = axis.subtract(normal.scale(axis.dot(normal)));
            double evenness = 1 - smoothstep(CEILING_EVEN_BELOW, CEILING_AIMED_ABOVE, skim.length());
            heading = bounce.scale(1 - evenness).add(outward.scale(evenness)).normalize();
            lift = 0;
        }
        double alongLine = fromHit.dot(heading);
        if (alongLine > bounceLength + FRAY_REACH) {
            return NO_BODY;
        }
        double along = Math.clamp(alongLine, 0, bounceLength + FRAY_REACH);
        double behind = Math.min(alongLine, 0);
        double radius = wall * tan + along * tan * SPLAY;
        Vec3 center = hit.add(heading.scale(along)).add(climb.scale(RISE * along * along));
        if (ceiling) {
            center = center.add(normal.scale(radius / STRETCH_ABOVE));
        }
        Vec3 bounceSide = new Vec3(-heading.z, 0, heading.x);
        bounceSide = bounceSide.lengthSqr() > NO_WAY ? bounceSide.normalize() : side;
        Vec3 up = bounceSide.cross(heading);
        if (up.y < 0) {
            bounceSide = bounceSide.reverse();
            up = up.reverse();
        }
        Vec3 off = point.subtract(center);
        return coneBody(off.dot(bounceSide), off.dot(up), radius, wall + along, behind, lift);
    }

    /**
     * A cone's body at a point offset from its centerline, in its own frame,
     * and standing the given blocks behind the cone's start along its line.
     *
     * @param across   blocks to the cone's side
     * @param upward   blocks up the cone's frame
     * @param radius   the flame's radius there
     * @param traveled blocks the fire has traveled there
     * @param behind   blocks behind the cone's start along its line
     * @param lift     how far the middle lifts per block traveled
     * @return the body: positive inside the radius, negative outside
     */
    private static double coneBody(double across, double upward, double radius, double traveled, double behind,
                                   double lift) {
        double upFromMiddle = upward - traveled * lift;
        double shaped = upFromMiddle > 0 ? upFromMiddle * STRETCH_ABOVE : upFromMiddle * SQUASH_BELOW;
        return 1 - Math.sqrt(across * across + shaped * shaped + behind * behind) / Math.max(radius, NOZZLE_BLOCKS);
    }

    private static double smoothstep(double low, double high, double value) {
        double share = Math.clamp((value - low) / (high - low), 0, 1);
        return share * share * (SMOOTHSTEP_RISE - SMOOTHSTEP_EASE * share);
    }
}
