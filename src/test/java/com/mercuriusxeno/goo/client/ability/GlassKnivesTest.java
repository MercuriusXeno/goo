package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.crystal.ThrownKnife;
import com.mercuriusxeno.goo.network.GlassKnifePayload;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.DripArc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A glass knife on the client ends its flight in a block it meets, on a mob
 * it meets, or spent where it leaves the world or its flight runs out, and
 * lies point first along its path, its kunai laid square to its heading
 * (decisions knives-sling-then-morph-to-flechettes, knives-and-dart-function-as-projectiles).
 */
class GlassKnivesTest {

    private static final double TOLERANCE = 1e-9;
    private static final Vec3 AHEAD = new Vec3(0, 0, 1.5);
    private static final Optional<CoatRay.BlockStrike> NO_BLOCK = Optional.empty();
    private static final Optional<DripArc.LivingBox> NO_MOB = Optional.empty();

    private static DripArc.Step flownTo(Optional<CoatRay.BlockStrike> block, Optional<DripArc.LivingBox> mob,
                                        boolean lost) {
        return new DripArc.Step(AHEAD, AHEAD, block, mob, lost);
    }

    @Test
    void aTickMeetingAMobStrikesIt() {
        DripArc.LivingBox mob = new DripArc.LivingBox(7, new AABB(-0.3, 0, 1, 0.3, 1.8, 1.6));
        assertEquals(Optional.of(GlassKnifePayload.Ending.STRUCK), GlassKnives.endingOf(
                flownTo(NO_BLOCK, Optional.of(mob), false), 3));
    }

    @Test
    void aTickMeetingABlockSticks() {
        CoatRay.BlockStrike block = new CoatRay.BlockStrike(AHEAD, BlockPos.ZERO, Direction.NORTH);
        assertEquals(Optional.of(GlassKnifePayload.Ending.STUCK), GlassKnives.endingOf(
                flownTo(Optional.of(block), NO_MOB, false), 1));
    }

    @Test
    void aKnifeFliesOnUntilItLeavesTheWorldOrItsFlightRunsOut() {
        assertEquals(Optional.empty(), GlassKnives.endingOf(flownTo(NO_BLOCK, NO_MOB, false), 1));
        assertEquals(Optional.of(GlassKnifePayload.Ending.SPENT), GlassKnives.endingOf(
                flownTo(NO_BLOCK, NO_MOB, true), 1));
        assertEquals(Optional.of(GlassKnifePayload.Ending.SPENT), GlassKnives.endingOf(
                flownTo(NO_BLOCK, NO_MOB, false), ThrownKnife.MOST_FLIGHT_TICKS));
    }
    @Test
    void aKnifeLiesBetweenItsTickPointsHeadingAlongItsPath() {
        List<Vec3> path = List.of(Vec3.ZERO, new Vec3(0, 0, 1.5), new Vec3(0.3, 0, 3));

        GlassKnives.Placement half = GlassKnives.placementAt(path, 1.5);
        assertEquals(0, path.get(1).lerp(path.get(2), 0.5).distanceTo(half.point()), TOLERANCE);
        assertEquals(0, path.get(2).subtract(path.get(1)).normalize().distanceTo(half.heading()), TOLERANCE);
        assertEquals(path.getLast(), GlassKnives.placementAt(path, 99).point());
    }

    @Test
    void aKunaisFrameLiesSquareToItsHeading() {
        Vec3 heading = new Vec3(0.3, -0.4, 0.8).normalize();
        Vec3[] frame = GlassKunai.frame(heading, 0.7f);

        assertEquals(0, frame[0].dot(heading), TOLERANCE);
        assertEquals(0, frame[1].dot(heading), TOLERANCE);
        assertEquals(0, frame[0].dot(frame[1]), TOLERANCE);
        assertEquals(1, frame[0].length(), TOLERANCE);
        assertEquals(0, GlassKunai.frame(new Vec3(0, 0, 1), 0f)[0].y, TOLERANCE);
    }

    @org.junit.jupiter.api.AfterEach
    void clearTheFlights() {
        GlassKnives.clear();
    }

    private static java.util.List<com.mercuriusxeno.goo.ability.program.SoundCue> thrownAs(String ability,
                                                                                         String javelinType) {
        java.util.Optional<com.mercuriusxeno.goo.ability.program.SoundCue> cue =
                com.mercuriusxeno.goo.ability.AbilityJson.decode(ability).delivery().formSounds().thrown();
        java.util.List<com.mercuriusxeno.goo.ability.program.SoundCue> played = new java.util.ArrayList<>();
        GlassKnives.launched(new GlassKnifePayload(7, Vec3.ZERO, AHEAD, javelinType, cue), 0, played::add);
        return played;
    }

    // knives-and-javelins-sound-as-each-forms-and-is-thrown
    @Test
    void eachKnifeThrownPlaysTheArrowsShotPitchedUpOnce() {
        java.util.List<com.mercuriusxeno.goo.ability.program.SoundCue> played =
                thrownAs("crystal_knives", GlassKnifePayload.GLASS);
        assertEquals(1, played.size(), "a knife leaving should sound once, " + played);
        assertEquals("minecraft:entity.arrow.shoot", played.getFirst().sound().toString());
        assertTrue(played.getFirst().pitch() > 1f, "the shot should be pitched up, " + played.getFirst().pitch());
    }

    // knives-and-javelins-sound-as-each-forms-and-is-thrown
    @Test
    void eachJavelinThrownPlaysTheTridentsThrowOnce() {
        java.util.List<com.mercuriusxeno.goo.ability.program.SoundCue> played = thrownAs("metal_dart", "goo:metal");
        assertEquals(1, played.size(), "a javelin leaving should sound once, " + played);
        assertEquals("minecraft:item.trident.throw", played.getFirst().sound().toString());
    }

    @Test
    void aThrowNamingNoSoundPlaysNone() {
        java.util.List<com.mercuriusxeno.goo.ability.program.SoundCue> played = new java.util.ArrayList<>();
        GlassKnives.launched(new GlassKnifePayload(7, Vec3.ZERO, AHEAD, GlassKnifePayload.GLASS,
                java.util.Optional.empty()), 0, played::add);
        assertTrue(played.isEmpty());
    }
}
