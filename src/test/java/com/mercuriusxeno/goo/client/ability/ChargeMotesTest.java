package com.mercuriusxeno.goo.client.ability;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * While Blast charges, green motes fade in from nothing around the rooted
 * spheroid and around the palm blob at one steady rate and pull in to the
 * shell and to the blob, and the spheroid beats on the palm blob's own beat
 * (decision blast-charge-pulls-green-motes-into-the-shell-and-the-blob).
 */
class ChargeMotesTest {

    private static final Vec3 ROOTED = new Vec3(10.5, 64, -3.5);
    private static final Vec3 BLOB = new Vec3(0.4, 65.3, 0.2);
    private static final long BEGAN = 5_000L;
    private static final int FULL_CHARGE = 100;
    private static final int SECOND = 20;
    private static final double DELTA = 1e-6;

    /** The shell's radius at a tick of the hold, as the eased spheroid draws it. */
    private static double radiusAt(int tick) {
        return 2 + tick / 20.0;
    }

    private static List<ChargeMotes.Mote> spawnedOver(int fromTick, int toTick) {
        RandomSource random = RandomSource.create(7L);
        List<ChargeMotes.Mote> spawned = new ArrayList<>();
        for (int tick = fromTick; tick < toTick; tick++) {
            spawned.addAll(ChargeMotes.spawn(BEGAN + tick, ROOTED, radiusAt(tick), BLOB, random));
        }
        return spawned;
    }

    @Test
    void motesSpawnAtOneSteadyRate() {
        int first = spawnedOver(0, SECOND).size();
        int last = spawnedOver(FULL_CHARGE - SECOND, FULL_CHARGE).size();
        assertTrue(first > 0, "the charge should spawn motes");
        assertEquals(first, last);
    }

    @Test
    void eachMoteFadesInFromNothingInUnstablesGreen() {
        for (ChargeMotes.Mote mote : spawnedOver(0, SECOND)) {
            assertEquals(0f, mote.alphaAt(0), DELTA);
            assertEquals(ChargeMotes.UNSTABLE_GREEN, mote.rgb());
        }
    }

    @Test
    void shellMotesEndAtTheShellAndBlobMotesAtTheBlob() {
        List<ChargeMotes.Mote> motes = ChargeMotes.spawn(BEGAN, ROOTED, radiusAt(0), BLOB, RandomSource.create(3L));
        long atTheShell = motes.stream().filter(mote -> Math.abs(mote.to().distanceTo(ROOTED) - radiusAt(0)) < DELTA)
                .count();
        long atTheBlob = motes.stream().filter(mote -> mote.to().distanceTo(BLOB) < DELTA).count();
        assertTrue(atTheShell > 0, "some motes should end at the shell");
        assertTrue(atTheBlob > 0, "some motes should end at the blob");
        assertEquals(motes.size(), atTheShell + atTheBlob, "every mote should end at the shell or the blob");
    }

    /**
     * A mote still on its way in when the charge releases is pushed back out, away from where it was headed, and
     * fades to nothing over the brief repel, rather than vanishing (the operator's ruling of 2026-11-15).
     */
    @Test
    void aMoteMidFlightAtTheReleaseIsPushedOutAndFadesToNothing() {
        ChargeMotes.Mote headingIn = ChargeMotes.spawn(BEGAN, ROOTED, radiusAt(0), BLOB, RandomSource.create(5L))
                .getFirst();
        double midway = headingIn.lifeTicks() / 2.0;
        long released = BEGAN + (long) midway;
        ChargeMotes.Mote repelled = headingIn.repelledAt(released);
        Vec3 atRelease = headingIn.placeAt(midway);

        double halfRepel = midway + ChargeMotes.REPEL_TICKS / 2.0;
        double fullRepel = midway + ChargeMotes.REPEL_TICKS;
        assertTrue(repelled.placeAt(halfRepel).distanceTo(headingIn.to()) > atRelease.distanceTo(headingIn.to()),
                "the mote should move away from where it was headed");
        assertTrue(repelled.alphaAt(halfRepel) < headingIn.alphaAt(midway), "the mote should fade as it is pushed");
        assertEquals(0f, repelled.alphaAt(fullRepel), DELTA);
        assertTrue(repelled.absorbedBy(BEGAN + fullRepel), "the mote should be gone once the repel ends");
    }

    @Test
    void theBlobDrawsAMoteEveryOtherTick() {
        long atTheBlob = spawnedOver(0, SECOND).stream().filter(ChargeMotes.Mote::atTheBlob).count();

        assertEquals(SECOND / 2, atTheBlob);
    }

    /** The blob's wisps fade in over more of their life than the shell's, so they ease in rather than pop. */
    @Test
    void aBlobMoteFadesInOverMoreOfItsLifeThanAShellMote() {
        List<ChargeMotes.Mote> motes = ChargeMotes.spawn(BEGAN, ROOTED, radiusAt(0), BLOB, RandomSource.create(9L));
        ChargeMotes.Mote shell = motes.stream().filter(mote -> !mote.atTheBlob()).findFirst().orElseThrow();
        ChargeMotes.Mote blob = motes.stream().filter(ChargeMotes.Mote::atTheBlob).findFirst().orElseThrow();
        double halfLife = blob.lifeTicks() / 2.0;

        assertEquals(1f, shell.alphaAt(halfLife), DELTA);
        assertTrue(blob.alphaAt(halfLife) < 1f, "a blob mote should still be fading in halfway through its life");
    }

    @Test
    void theSpheroidBeatsOnThePalmBlobsBeat() {
        double[] times = {BEGAN, BEGAN + 3.25, BEGAN + 7.5};
        for (double time : times) {
            assertEquals(LiftedPalmBlob.beatPhase(time), ChargeMotes.pulsePhase(time), DELTA);
        }
        assertNotEquals(ChargeMotes.pulsePhase(times[0]), ChargeMotes.pulsePhase(times[1]));
        assertNotEquals(ChargeMotes.pulsePhase(times[1]), ChargeMotes.pulsePhase(times[2]));
    }
}
