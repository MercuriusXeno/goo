package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.throwing.ConeShape;
import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The fire the server burns with for held Flames: each burn casts a spray of rays at random angles inside the cone,
 * each the range long and ending at the first block it meets, and burns
 * every living thing whose hitbox a ray's line crosses. A ray passes through
 * the mobs on its line; nothing burns past the block.
 * flames-burns-by-a-spray-of-rays
 */
public final class FlameRays {

    /** Rays one burn casts: enough that a mob standing in the cone is all but sure to be crossed. */
    static final int RAYS_PER_BURN = 12;
    private static final double TWO_PI = Math.PI * 2;

    private FlameRays() {
    }

    /** Where a line from one point toward another ends: at the first block it meets, or at its far end. */
    @FunctionalInterface
    public interface BlockClip {

        /**
         * Ends a line at the first block it meets.
         *
         * @param from where the line starts
         * @param to   where it would end in open air
         * @return the point it ends at
         */
        Vec3 stop(Vec3 from, Vec3 to);
    }

    /**
     * One ray of the spray.
     *
     * @param from where it starts, the spout
     * @param to   where it ends, at the range or the first block it met
     */
    public record Ray(Vec3 from, Vec3 to) {

        /**
         * Whether the ray's line crosses a hitbox.
         *
         * @param box the hitbox
         * @return whether the line passes through it, or starts inside it
         */
        boolean crosses(AABB box) {
            return box.contains(from) || box.clip(from, to).isPresent();
        }
    }

    /**
     * Ends each line at the first block whose collision shape it meets in a level.
     *
     * @param level the level
     * @return the clip
     */
    public static BlockClip clipIn(Level level) {
        return (from, to) -> {
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, CollisionContext.empty()));
            return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : to;
        };
    }

    /**
     * One burn's spray: {@link #RAYS_PER_BURN} rays from the apex at random
     * angles inside the cone, spread evenly over its cap, each the range long
     * and stopped at the first block it meets.
     *
     * decision cone-style-abilities-are-reviewed-for-consistency: each ray's rim is the shape's at its turn
     *
     * @param apex   where the fire pours from
     * @param axis   the unit vector it pours along
     * @param shape  the cone's widths across and up and down
     * @param range  each ray's length in open air
     * @param random the random source the angles come from: a tilt off the axis, then a turn about it
     * @param clip   where a line meets a block
     * @return the rays
     */
    public static List<Ray> spray(Vec3 apex, Vec3 axis, ConeShape shape, double range, RandomSource random,
                                  BlockClip clip) {
        Vec3 across = StreamCone.side(axis);
        Vec3 up = across.cross(axis).normalize();
        List<Ray> rays = new ArrayList<>(RAYS_PER_BURN);
        for (int index = 0; index < RAYS_PER_BURN; index++) {
            double tilt = random.nextDouble();
            double turnShare = random.nextDouble();
            double rimCosine = StreamCone.rimDirection(axis, shape, turnShare).dot(axis.normalize());
            double cosine = 1 - tilt * (1 - rimCosine);
            double sine = Math.sqrt(Math.max(0, 1 - cosine * cosine));
            double turn = turnShare * TWO_PI;
            Vec3 way = axis.scale(cosine).add(across.scale(sine * Math.cos(turn))).add(up.scale(sine * Math.sin(turn)));
            rays.add(new Ray(apex, clip.stop(apex, apex.add(way.scale(range)))));
        }
        return rays;
    }

    /**
     * Every candidate whose hitbox any ray's line crosses, in the candidates' order.
     *
     * @param rays       the spray
     * @param candidates the living things that may stand in it
     * @param boxOf      each candidate's hitbox
     * @param <T>        the candidates' type
     * @return the candidates the fire burns
     */
    public static <T> List<T> crossed(List<Ray> rays, List<T> candidates, Function<T, AABB> boxOf) {
        List<T> burned = new ArrayList<>();
        for (T candidate : candidates) {
            AABB box = boxOf.apply(candidate);
            if (rays.stream().anyMatch(ray -> ray.crosses(box))) {
                burned.add(candidate);
            }
        }
        return burned;
    }
}
