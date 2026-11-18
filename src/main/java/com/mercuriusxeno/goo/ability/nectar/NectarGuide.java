package com.mercuriusxeno.goo.ability.nectar;

import com.mercuriusxeno.goo.throwing.DripArc;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Nectar's guided throw: which mob the drips are guided to, the one whose
 * middle lies nearest the look ray within the guide radius of it and inside
 * the guide reach, and the axis that throws a drip's arc onto that mob. An
 * aim passing no mob that near guides nothing, and the drips spray free
 * along the look vector.
 * nectar-is-sprayed-goo-with-a-guided-throw-near-a-mob
 */
public final class NectarGuide {

    /** The lowest pitch the throw tries, in degrees under level. */
    private static final int LOWEST_PITCH = -60;
    /** The highest pitch the throw tries, in degrees over level. */
    private static final int HIGHEST_PITCH = 75;
    /** The longest flight the throw follows, in ticks. */
    private static final int LONGEST_FLIGHT = 120;
    /** How near the arc may pass the mob and count as landing on it, in blocks. */
    private static final double ON_THE_MOB = 0.25;

    private NectarGuide() {
    }

    /**
     * A mob the guide may pick.
     *
     * @param entityId the mob's entity id
     * @param center   the middle of the mob's box
     */
    public record Candidate(int entityId, Vec3 center) {
    }

    /**
     * The mob the drips are guided to: of the mobs whose middle lies ahead of
     * the eye, inside the guide reach along the look ray and within the guide
     * radius off it, the one at the smallest angle off the look.
     *
     * @param eye         the caster's eye
     * @param look        the caster's look vector
     * @param mobs        the mobs around the aim
     * @param guideRadius the blocks off the look ray a mob may lie and still take the guide
     * @param guideReach  the blocks along the look ray out to which a mob takes the guide
     * @return the mob the drips are guided to, empty where they spray free
     */
    public static Optional<Integer> guidedTo(Vec3 eye, Vec3 look, List<Candidate> mobs, double guideRadius,
                                             double guideReach) {
        Vec3 ray = look.normalize();
        return mobs.stream()
                .map(mob -> new Offset(mob.entityId(), mob.center().subtract(eye), ray))
                .filter(offset -> offset.along > 0 && offset.along <= guideReach && offset.off <= guideRadius)
                .min(Comparator.comparingDouble(Offset::slope))
                .map(Offset::entityId);
    }

    /**
     * The axis that throws a drip's arc from the apex onto a point: the
     * lowest pitch, toward the point, whose arc under the gravity and the
     * drag a drip flies passes within a quarter block of it, or the pitch
     * passing nearest where none does.
     *
     * @param apex      where the drip leaves
     * @param target    the point it is thrown onto
     * @param exitSpeed the drip's exit speed, blocks per tick
     * @param gravity   the pull on the drip each tick
     * @return the unit axis to throw along
     */
    public static Vec3 throwOnto(Vec3 apex, Vec3 target, double exitSpeed, double gravity) {
        Vec3 line = target.subtract(apex);
        Vec3 level = new Vec3(line.x, 0, line.z);
        if (level.lengthSqr() == 0) {
            return line.normalize();
        }
        Vec3 heading = level.normalize();
        Vec3 nearest = line.normalize();
        double nearestMiss = Double.MAX_VALUE;
        for (int pitch = LOWEST_PITCH; pitch <= HIGHEST_PITCH; pitch++) {
            double radians = Math.toRadians(pitch);
            Vec3 axis = heading.scale(Math.cos(radians)).add(0, Math.sin(radians), 0);
            double miss = missOf(apex, axis.scale(exitSpeed), target, gravity);
            if (miss <= ON_THE_MOB) {
                return axis;
            }
            if (miss < nearestMiss) {
                nearestMiss = miss;
                nearest = axis;
            }
        }
        return nearest;
    }

    /**
     * How near a free arc passes a point, flown as {@link DripArc#step} flies a drip.
     *
     * @param apex     where the drip leaves
     * @param launched its launch velocity, blocks per tick
     * @param target   the point
     * @param gravity  the pull on the drip each tick
     * @return the nearest the arc passes the point, in blocks
     */
    private static double missOf(Vec3 apex, Vec3 launched, Vec3 target, double gravity) {
        Vec3 position = apex;
        Vec3 velocity = launched;
        double miss = position.distanceTo(target);
        for (int tick = 0; tick < LONGEST_FLIGHT; tick++) {
            velocity = velocity.add(0, -gravity, 0).scale(DripArc.DRAG);
            Vec3 next = position.add(velocity);
            miss = Math.min(miss, distanceToSegment(target, position, next));
            if (next.y < target.y && velocity.y < 0) {
                break;
            }
            position = next;
        }
        return miss;
    }

    private static double distanceToSegment(Vec3 point, Vec3 from, Vec3 to) {
        Vec3 segment = to.subtract(from);
        double lengthSquared = segment.lengthSqr();
        double share = lengthSquared == 0 ? 0
                : Math.clamp(point.subtract(from).dot(segment) / lengthSquared, 0.0, 1.0);
        return point.distanceTo(from.add(segment.scale(share)));
    }

    /**
     * Where a mob lies about the look ray.
     *
     * @param entityId the mob
     * @param along    the blocks along the ray to the mob's foot on it
     * @param off      the blocks off the ray the mob's middle lies
     */
    private record Offset(int entityId, double along, double off) {

        Offset(int entityId, Vec3 fromEye, Vec3 ray) {
            this(entityId, fromEye.dot(ray), fromEye.subtract(ray.scale(fromEye.dot(ray))).length());
        }

        double slope() {
            return off / along;
        }
    }
}
