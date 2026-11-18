package com.mercuriusxeno.goo.ability.nectar;

import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Nectar's guided throw: which mob the drips are guided to, the one whose
 * middle lies nearest the look ray within the guide radius of it and inside
 * the guide reach, whose feet the tick's cone of drips centers on. An
 * aim passing no mob that near guides nothing, and the drips spray free
 * along the look vector.
 * nectar-is-sprayed-goo-with-a-guided-throw-near-a-mob
 */
public final class NectarGuide {

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
