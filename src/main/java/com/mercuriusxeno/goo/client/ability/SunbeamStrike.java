package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a Sunbeam beam strikes and how its light motes scatter off it. Nine
 * probes run parallel to the beam across its cross-section, the middle and
 * eight around the rim, so a beam splitting a block's corner strikes each
 * face it touches; each struck surface scatters a few slow motes from the
 * points its own probes struck, mostly along its outward normal.
 * decision sunbeam-motes-scatter-away-from-struck-surfaces
 */
final class SunbeamStrike {

    /** Motes each struck surface scatters per client tick, on average. */
    static final double MOTES_PER_SURFACE = 0.6;
    /** The slowest and the fastest a mote leaves its surface, in blocks per tick. */
    private static final double SLOWEST_LEAVE = 0.015;
    private static final double LEAVE_VARIANCE = 0.02;
    /** The most a mote slides across its surface, as a share of how fast it leaves. */
    private static final double MOST_SLIDE_OF_LEAVE = 0.6;
    /** The rim's probes: the grid's four edge middles and four corners, laid on the beam's round edge. */
    private static final int RIM_PROBES = 8;
    private static final double SHORTEST_NORMAL = 1.0e-6;
    private static final double TWO_PI = 2 * Math.PI;

    private SunbeamStrike() {
    }

    /**
     * Where each of the nine probes runs, offset from the beam's axis: the
     * middle, then the eight rim probes at the beam's radius.
     *
     * @param axis   the beam's direction
     * @param radius the beam's drawn radius, in blocks
     * @return each probe's offset, square to the axis
     */
    static List<Vec3> probeOffsets(Vec3 axis, double radius) {
        Vec3 unit = axis.normalize();
        Vec3 across = SunbeamMotes.across(unit);
        Vec3 acrossToo = unit.cross(across);
        List<Vec3> offsets = new ArrayList<>(RIM_PROBES + 1);
        offsets.add(Vec3.ZERO);
        for (int probe = 0; probe < RIM_PROBES; probe++) {
            double angle = TWO_PI * probe / RIM_PROBES;
            offsets.add(across.scale(Math.cos(angle) * radius).add(acrossToo.scale(Math.sin(angle) * radius)));
        }
        return offsets;
    }

    /**
     * The probes a refracted beam strikes its mob with: the server ends each
     * refracted beam on its mob's body, so the nine probes land on the mob
     * around that end.
     *
     * @param prism  the prism core the refracted beam leaves
     * @param end    the refracted beam's end on its mob
     * @param radius the beam's drawn radius, in blocks
     * @return the nine probes, each on the mob
     */
    static List<Probe> refractedEndProbes(Vec3 prism, Vec3 end, double radius) {
        return probeOffsets(end.subtract(prism), radius).stream().map(offset -> Probe.onMob(end.add(offset)))
                .toList();
    }

    /**
     * The distinct surfaces a beam's probes struck: one per block face they
     * met, and one for a mob, which faces back toward the beam's source.
     *
     * @param probes each probe that struck something
     * @param source where the beam leaves from
     * @return each struck surface with the points its probes landed on
     */
    static List<Surface> surfacesStruck(List<Probe> probes, Vec3 source) {
        Map<Direction, List<Vec3>> faces = new LinkedHashMap<>();
        List<Vec3> onMob = new ArrayList<>();
        for (Probe probe : probes) {
            Direction face = probe.blockFace();
            if (face == null) {
                onMob.add(probe.point());
            } else {
                faces.computeIfAbsent(face, struck -> new ArrayList<>()).add(probe.point());
            }
        }
        List<Surface> surfaces = new ArrayList<>();
        faces.forEach((face, points) -> surfaces.add(new Surface(Vec3.atLowerCornerOf(face.getUnitVec3i()), points)));
        if (!onMob.isEmpty()) {
            Vec3 back = source.subtract(middleOf(onMob));
            if (back.length() > SHORTEST_NORMAL) {
                surfaces.add(new Surface(back.normalize(), onMob));
            }
        }
        return surfaces;
    }

    /**
     * The motes one client tick scatters off a struck surface: each from one
     * of the points its probes struck, leaving along the outward normal with
     * a smaller slide across the surface.
     *
     * @param surface the struck surface
     * @param random  the random source
     * @return each mote's spawn point and velocity
     */
    static List<SunbeamMotes.Mote> scatterOff(Surface surface, RandomSource random) {
        int count = SunbeamMotes.countFor(MOTES_PER_SURFACE, random);
        List<SunbeamMotes.Mote> motes = new ArrayList<>(count);
        Vec3 normal = surface.normal();
        Vec3 across = SunbeamMotes.across(normal);
        Vec3 acrossToo = normal.cross(across);
        for (int mote = 0; mote < count; mote++) {
            Vec3 at = surface.points().get(random.nextInt(surface.points().size()));
            double leave = SLOWEST_LEAVE + random.nextDouble() * LEAVE_VARIANCE;
            double slide = leave * MOST_SLIDE_OF_LEAVE * random.nextDouble();
            double heading = random.nextDouble() * TWO_PI;
            Vec3 velocity = normal.scale(leave).add(across.scale(Math.cos(heading) * slide))
                    .add(acrossToo.scale(Math.sin(heading) * slide));
            motes.add(new SunbeamMotes.Mote(at, velocity));
        }
        return motes;
    }

    private static Vec3 middleOf(List<Vec3> points) {
        Vec3 sum = Vec3.ZERO;
        for (Vec3 point : points) {
            sum = sum.add(point);
        }
        return sum.scale(1.0 / points.size());
    }

    /**
     * What one probe struck.
     *
     * @param point     where it struck, world space
     * @param blockFace the block face it struck, null on a mob
     */
    record Probe(Vec3 point, @Nullable Direction blockFace) {

        /**
         * A probe that struck a block's face.
         *
         * @param point where it struck
         * @param face  the face
         * @return the probe
         */
        static Probe onBlock(Vec3 point, Direction face) {
            return new Probe(point, face);
        }

        /**
         * A probe that struck a mob.
         *
         * @param point where it struck
         * @return the probe
         */
        static Probe onMob(Vec3 point) {
            return new Probe(point, null);
        }
    }

    /**
     * One surface a beam struck.
     *
     * @param normal its outward unit normal
     * @param points where the beam's probes struck it
     */
    record Surface(Vec3 normal, List<Vec3> points) {
    }
}
