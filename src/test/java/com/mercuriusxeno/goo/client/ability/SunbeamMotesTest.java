package com.mercuriusxeno.goo.client.ability;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The motes a Sunbeam ray sheds each client tick spawn inside the drawn beam
 * and rise gently out of it, off the main ray and every refracted beam alike
 * (decision sunbeam-sheds-motes-rising-from-the-beam).
 */
class SunbeamMotesTest {

    private static final long SEED = 7L;
    private static final int TICKS = 400;
    private static final double RADIUS = 0.05;
    private static final double TOLERANCE = 1.0e-9;
    private static final double END_ROD_SPEED = 0.12;
    private static final Vec3 HAND = new Vec3(0.5, 64.2, -3.0);
    private static final Vec3 PRISM = new Vec3(17.0, 70.0, 11.0);
    private static final Vec3 FIRST_MOB = new Vec3(30.0, 64.0, 20.0);
    private static final Vec3 SECOND_MOB = new Vec3(5.0, 66.0, 25.0);

    private static List<SunbeamMotes.Mote> shedOver(List<SunbeamMotes.Segment> segments) {
        RandomSource random = RandomSource.create(SEED);
        List<SunbeamMotes.Mote> motes = new ArrayList<>();
        for (int tick = 0; tick < TICKS; tick++) {
            motes.addAll(SunbeamMotes.shedAlong(segments, RADIUS, random));
        }
        return motes;
    }

    private static boolean liesInside(SunbeamMotes.Segment segment, Vec3 point) {
        Vec3 along = segment.to().subtract(segment.from());
        double length = along.length();
        Vec3 axis = along.scale(1.0 / length);
        Vec3 offset = point.subtract(segment.from());
        double projection = offset.dot(axis);
        double fromAxis = offset.subtract(axis.scale(projection)).length();
        return projection >= -TOLERANCE && projection <= length + TOLERANCE && fromAxis <= RADIUS + TOLERANCE;
    }

    @Nested
    class Spawning {

        @Test
        void everyMoteSpawnsBetweenTheEndsWithinTheBeamRadius() {
            SunbeamMotes.Segment segment = new SunbeamMotes.Segment(HAND, PRISM);
            List<SunbeamMotes.Mote> motes = shedOver(List.of(segment));
            assertFalse(motes.isEmpty(), "a long segment sheds motes");
            for (SunbeamMotes.Mote mote : motes) {
                assertTrue(liesInside(segment, mote.at()), "mote at " + mote.at() + " left the beam");
            }
        }

        @Test
        void aRefractedRayShedsFromTheMainRayAndEachRefractedBeam() {
            List<SunbeamMotes.Segment> segments = List.of(new SunbeamMotes.Segment(HAND, PRISM),
                    new SunbeamMotes.Segment(PRISM, FIRST_MOB), new SunbeamMotes.Segment(PRISM, SECOND_MOB));
            List<SunbeamMotes.Mote> motes = shedOver(segments);
            for (SunbeamMotes.Segment segment : segments) {
                assertTrue(motes.stream().anyMatch(mote -> liesInside(segment, mote.at())
                                && mote.at().distanceTo(PRISM) > RADIUS * 2),
                        "no mote shed along " + segment);
            }
        }
    }

    @Nested
    class Rising {

        @Test
        void everyMoteRisesWithLessDriftThanRiseAndSlowerThanTheEndRodSparks() {
            List<SunbeamMotes.Mote> motes = shedOver(List.of(new SunbeamMotes.Segment(HAND, FIRST_MOB)));
            assertFalse(motes.isEmpty(), "a long segment sheds motes");
            for (SunbeamMotes.Mote mote : motes) {
                Vec3 velocity = mote.velocity();
                double drift = Math.hypot(velocity.x, velocity.z);
                assertTrue(velocity.y > 0, "mote sinks: " + velocity);
                assertTrue(drift < velocity.y, "mote drifts more than it rises: " + velocity);
                assertTrue(velocity.length() < END_ROD_SPEED, "mote flies as fast as a spark: " + velocity);
            }
        }
    }
}
