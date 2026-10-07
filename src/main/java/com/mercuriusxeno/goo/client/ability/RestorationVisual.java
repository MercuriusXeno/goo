package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.LeafStep;
import com.mercuriusxeno.goo.ability.program.LeafSteps;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The restoration waves a held healing stream draws: soft rings of restore
 * motes that roll out along the cone from the glove hand and widen with
 * it, each mote drifting up as it fades. A first pass the choreography
 * idea refines.
 * vitality-waves-regenerate-and-court
 */
public final class RestorationVisual {

    /** Blocks a wave travels along the cone each tick. */
    static final double WAVE_SPEED = 0.35;
    /** Waves rolling along the cone at once, evenly spaced over its range. */
    static final int WAVES_IN_FLIGHT = 3;
    /** Motes around each ring. */
    static final int MOTES_PER_RING = 10;
    /** Outward drift of each ring mote, in blocks per tick. */
    private static final double OUTWARD_DRIFT = 0.015;
    /** A wave this close to the apex has no ring wide enough to read. */
    private static final double MIN_RING_DISTANCE = 0.5;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double HALF = 0.5;

    private RestorationVisual() {
    }

    /**
     * Draws this tick's rings for the local player's held stream when its
     * ability heals.
     *
     * @param player    the streaming player
     * @param abilityId the streamed ability
     * @param delivery  the stream delivery, whose range and cone the rings fill
     * @param apex      the glove hand the stream leaves from
     */
    public static void drawWaves(Player player, String abilityId, Delivery delivery, Vec3 apex) {
        if (!heals(abilityId)) {
            return;
        }
        Level level = player.level();
        Vec3 axis = player.getLookAngle();
        for (int wave = 0; wave < WAVES_IN_FLIGHT; wave++) {
            double distance = waveDistance(level.getGameTime(), wave, delivery.range());
            if (distance < MIN_RING_DISTANCE) {
                continue;
            }
            for (Vec3 at : ringPoints(apex, axis, distance, delivery.coneDegrees(), MOTES_PER_RING)) {
                Vec3 drift = at.subtract(apex.add(axis.scale(distance))).normalize().scale(OUTWARD_DRIFT);
                level.addParticle(GooParticles.RESTORE_MOTE.get(), at.x, at.y, at.z, drift.x, drift.y, drift.z);
            }
        }
    }

    /**
     * Whether the synced ability's program holds a heal step anywhere.
     *
     * @param abilityId the ability id
     * @return true for a healing ability
     */
    static boolean heals(String abilityId) {
        AbilitySyncHandler.ClientAbility ability = AbilitySyncHandler.findAbility(abilityId);
        return ability != null && ability.behaviors().stream().flatMap(RestorationVisual::withDescendants)
                .anyMatch(step -> step instanceof LeafStep<?> leaf && leaf.leaf() == LeafSteps.HEAL);
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(RestorationVisual::withDescendants));
    }

    /**
     * How far along the cone a wave stands at a game tick: each wave rolls
     * out at {@link #WAVE_SPEED} and wraps to the apex at the range, the
     * waves spaced evenly over it.
     *
     * @param gameTime the level's game time
     * @param wave     the wave's index among {@link #WAVES_IN_FLIGHT}
     * @param range    the stream's reach
     * @return the wave's distance from the apex, in [0, range)
     */
    static double waveDistance(long gameTime, int wave, double range) {
        if (range <= 0) {
            return 0;
        }
        double travelled = gameTime * WAVE_SPEED + range * wave / WAVES_IN_FLIGHT;
        return travelled % range;
    }

    /**
     * The points of a ring around the cone's axis at a distance from its
     * apex, its radius the cone's half-width there.
     *
     * @param apex        the cone's apex
     * @param axis        the cone's unit axis
     * @param distance    the ring's distance along the axis
     * @param coneDegrees the cone's apex angle, edge to edge
     * @param count       the points around the ring
     * @return the ring's points, evenly spaced
     */
    static List<Vec3> ringPoints(Vec3 apex, Vec3 axis, double distance, double coneDegrees, int count) {
        float[] basis = ConeGeometry.computeBasis((float) axis.x, (float) axis.y, (float) axis.z);
        Vec3 perp = new Vec3(basis[ConeGeometry.PERP_X], basis[ConeGeometry.PERP_Y], basis[ConeGeometry.PERP_Z]);
        Vec3 cross = new Vec3(basis[ConeGeometry.CROSS_X], basis[ConeGeometry.CROSS_Y],
                basis[ConeGeometry.CROSS_Z]);
        Vec3 center = apex.add(axis.scale(distance));
        double radius = distance * Math.tan(Math.toRadians(coneDegrees * HALF));
        List<Vec3> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = TWO_PI * i / count;
            points.add(center.add(perp.scale(Math.cos(angle) * radius)).add(cross.scale(Math.sin(angle) * radius)));
        }
        return points;
    }
}
