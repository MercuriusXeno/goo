package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a Sunbeam beam strikes, nine probes across its cross-section find
 * each surface it touches, and light motes scatter away from each one at the
 * points its own probes struck (decision sunbeam-motes-scatter-away-from-struck-surfaces).
 */
class SunbeamStrikeTest {

    private static final long SEED = 11L;
    private static final int TICKS = 400;
    private static final double RADIUS = 0.05;
    private static final double TOLERANCE = 1.0e-9;
    /** The mesh's turn is a float quaternion, so offsets taken through it agree to float precision. */
    private static final double FLOAT_TOLERANCE = 1.0e-6;
    private static final double END_ROD_SPEED = 0.12;
    private static final Vec3 SLANTED_AXIS = new Vec3(0.6, -0.5, 0.8).normalize();
    private static final Vec3 SOURCE = new Vec3(0.5, 66.0, -4.0);
    private static final Vec3 PRISM = new Vec3(17.5, 70.5, 11.5);
    private static final Vec3 REFRACTED_END = new Vec3(25.0, 64.9, 18.0);

    private static List<SunbeamMotes.Mote> scatterOver(SunbeamStrike.Surface surface) {
        RandomSource random = RandomSource.create(SEED);
        List<SunbeamMotes.Mote> motes = new ArrayList<>();
        for (int tick = 0; tick < TICKS; tick++) {
            motes.addAll(SunbeamStrike.scatterOff(surface, random));
        }
        return motes;
    }

    private static boolean spawnsAtOneOf(SunbeamMotes.Mote mote, List<Vec3> points) {
        return points.stream().anyMatch(point -> point.distanceTo(mote.at()) < TOLERANCE);
    }

    @Nested
    class Probing {

        private static final double[] GRID = {-RADIUS, 0, RADIUS};

        private static Vec3 drawnGridPoint(double x, double z) {
            Vector3f point = GlowBeamMesh.alongBeam(SLANTED_AXIS).transform(new Vector3f((float) x, 0f, (float) z));
            return new Vec3(point.x, point.y, point.z);
        }

        @Test
        void nineProbesLieOnTheDrawnSquaresGridItsMiddleEdgeMiddlesAndCorners() {
            List<Vec3> offsets = SunbeamStrike.probeOffsets(SLANTED_AXIS, RADIUS);
            assertEquals(9, offsets.size());
            int middles = 0;
            int edgeMiddles = 0;
            int corners = 0;
            for (Vec3 offset : offsets) {
                assertEquals(0, offset.dot(SLANTED_AXIS), FLOAT_TOLERANCE, "probe offset leaves the cross-section");
                double length = offset.length();
                if (length < FLOAT_TOLERANCE) {
                    middles++;
                } else if (Math.abs(length - RADIUS) < FLOAT_TOLERANCE) {
                    edgeMiddles++;
                } else if (Math.abs(length - RADIUS * Math.sqrt(2)) < FLOAT_TOLERANCE) {
                    corners++;
                }
            }
            assertEquals(1, middles, "one probe at the middle");
            assertEquals(4, edgeMiddles, "four probes at the edge middles");
            assertEquals(4, corners, "four probes at the corners");
            for (double x : GRID) {
                for (double z : GRID) {
                    Vec3 drawn = drawnGridPoint(x, z);
                    assertTrue(offsets.stream().anyMatch(offset -> offset.distanceTo(drawn) < FLOAT_TOLERANCE),
                            "no probe at the drawn square's grid point " + x + ", " + z);
                }
            }
        }
    }

    @Nested
    class SplittingACorner {

        private final List<Vec3> topPoints = List.of(new Vec3(3.98, 65.0, 7.5), new Vec3(3.95, 65.0, 7.48),
                new Vec3(3.95, 65.0, 7.52), new Vec3(3.99, 65.0, 7.46), new Vec3(3.99, 65.0, 7.54));
        private final List<Vec3> sidePoints = List.of(new Vec3(4.0, 64.97, 7.5), new Vec3(4.0, 64.96, 7.46),
                new Vec3(4.0, 64.96, 7.54), new Vec3(4.0, 64.99, 7.48));

        private List<SunbeamStrike.Surface> surfaces() {
            List<SunbeamStrike.Probe> probes = new ArrayList<>();
            topPoints.forEach(point -> probes.add(SunbeamStrike.Probe.onBlock(point, Direction.UP)));
            sidePoints.forEach(point -> probes.add(SunbeamStrike.Probe.onBlock(point, Direction.EAST)));
            return SunbeamStrike.surfacesStruck(probes, SOURCE);
        }

        private SunbeamStrike.Surface facing(Direction face) {
            Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
            return surfaces().stream().filter(surface -> surface.normal().distanceTo(normal) < TOLERANCE)
                    .findFirst().orElseThrow();
        }

        @Test
        void aBeamSplittingACornerStrikesBothFaces() {
            assertEquals(2, surfaces().size());
        }

        @Test
        void eachFaceScattersFromThePointsItsOwnProbesStruck() {
            List<SunbeamMotes.Mote> offTheTop = scatterOver(facing(Direction.UP));
            List<SunbeamMotes.Mote> offTheSide = scatterOver(facing(Direction.EAST));
            assertFalse(offTheTop.isEmpty(), "the top face scatters motes");
            assertFalse(offTheSide.isEmpty(), "the side face scatters motes");
            offTheTop.forEach(mote -> assertTrue(spawnsAtOneOf(mote, topPoints), "top mote at " + mote.at()));
            offTheSide.forEach(mote -> assertTrue(spawnsAtOneOf(mote, sidePoints), "side mote at " + mote.at()));
        }
    }

    @Nested
    class Leaving {

        @Test
        void everyMoteLeavesTheSurfaceFasterThanItSlidesAcrossItAndSlowerThanTheEndRodSparks() {
            Vec3 normal = Vec3.atLowerCornerOf(Direction.NORTH.getUnitVec3i());
            List<SunbeamMotes.Mote> motes = scatterOver(
                    new SunbeamStrike.Surface(normal, List.of(new Vec3(2.5, 64.5, 9.0))));
            assertFalse(motes.isEmpty(), "a struck face scatters motes");
            for (SunbeamMotes.Mote mote : motes) {
                Vec3 velocity = mote.velocity();
                double outward = velocity.dot(normal);
                double across = velocity.subtract(normal.scale(outward)).length();
                assertTrue(outward > 0, "mote flies into the surface: " + velocity);
                assertTrue(outward > across, "mote slides more than it leaves: " + velocity);
                assertTrue(velocity.length() < END_ROD_SPEED, "mote flies as fast as a spark: " + velocity);
            }
        }
    }

    @Nested
    class OnAMob {

        @Test
        void aStruckMobsSurfaceFacesBackTowardTheBeamsSource() {
            Vec3 struck = new Vec3(9.2, 64.8, 5.1);
            List<SunbeamStrike.Surface> surfaces = SunbeamStrike.surfacesStruck(
                    List.of(SunbeamStrike.Probe.onMob(struck)), SOURCE);
            assertEquals(1, surfaces.size());
            Vec3 towardSource = SOURCE.subtract(struck).normalize();
            assertEquals(0, surfaces.getFirst().normal().distanceTo(towardSource), TOLERANCE);
        }

        @Test
        void aRefractedBeamScattersAtItsOwnEndBackTowardThePrism() {
            List<SunbeamStrike.Surface> surfaces = SunbeamStrike.surfacesStruck(
                    SunbeamStrike.refractedEndProbes(PRISM, REFRACTED_END, RADIUS), PRISM);
            assertEquals(1, surfaces.size());
            List<SunbeamMotes.Mote> motes = scatterOver(surfaces.getFirst());
            assertFalse(motes.isEmpty(), "the refracted beam's mob scatters motes");
            for (SunbeamMotes.Mote mote : motes) {
                assertTrue(mote.at().distanceTo(REFRACTED_END) <= RADIUS * Math.sqrt(2) + FLOAT_TOLERANCE,
                        "mote spawns away from the refracted end: " + mote.at());
                assertTrue(mote.velocity().dot(PRISM.subtract(REFRACTED_END)) > 0,
                        "mote scatters away from the prism: " + mote.velocity());
            }
        }
    }
}
