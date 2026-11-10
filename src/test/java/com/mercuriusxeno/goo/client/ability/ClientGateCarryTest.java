package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.PortalTransform;
import com.mercuriusxeno.goo.network.GateCarriedPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A body the server carried through a linked gate is turned through the same
 * pair on the client where the client draws it, its last tick's place with
 * it, and slides on from there to where the server set it, so it is seen to
 * go into the one gate and come out of the other unbroken rather than
 * flashing to the landing; the server's own stale word placing it back at
 * the landing is dropped meanwhile
 * (decision aim-and-shots-carry-through-linked-gates).
 */
class ClientGateCarryTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    /** A 3x3 portal on a wall's north face, the face the plane z 0. */
    private static final Gate ENTERED = new Gate(OVERWORLD, new AABB(new BlockPos(-1, 0, 0)).minmax(
            new AABB(new BlockPos(1, 2, 0))), Direction.NORTH, Direction.UP.getUnitVec3());
    /** Its partner on the wall's far face, the plane z 3, facing south. */
    private static final Gate EXIT = new Gate(OVERWORLD, new AABB(new BlockPos(-1, 0, 2)).minmax(
            new AABB(new BlockPos(1, 2, 2))), Direction.SOUTH, Direction.UP.getUnitVec3());
    private static final int BODY = 7;
    private static final long CARRIED_AT = 61402;
    private static final GateCarriedPayload CARRY = new GateCarriedPayload(BODY, new Vec3(0.5, 0.0, 3.2),
            Vec3.ZERO, 0F, 10F, ENTERED, EXIT);
    /** Where the client draws the body as the carry arrives: a few tenths short of the gate it goes into. */
    private static final Vec3 DRAWN = new Vec3(0.5, 0.0, -0.4);
    /** Where the client drew it the tick before, a step further back. */
    private static final Vec3 DRAWN_BEFORE = new Vec3(0.5, 0.0, -0.7);

    @AfterEach
    void forgetTheCarries() {
        ClientGateCarry.forgetAll();
    }

    private static Entity mockBody() {
        Entity body = mock(Entity.class);
        when(body.getDeltaMovement()).thenReturn(Vec3.ZERO);
        return body;
    }

    /**
     * A body the client still draws short of the gate it went into is drawn
     * on into that gate, sliding toward the landing as seen from the entry
     * side, rather than turned behind the partner's wall, where the probe saw
     * a thrown slime hidden for 8 to 14 frames after each carry.
     */
    @Test
    void aCarriedBodyDrawnShortOfTheGateSlidesOnIntoItTowardTheLandingSeenFromTheEntrySide() {
        Entity body = mockBody();
        InterpolationHandler slide = mock(InterpolationHandler.class);
        when(body.getId()).thenReturn(BODY);
        when(body.getInterpolation()).thenReturn(slide);
        when(body.position()).thenReturn(DRAWN);

        ClientGateCarry.snap(body, CARRY, CARRIED_AT);

        verify(body, never()).setPos(any(Vec3.class));
        Vec3 landingSeenFromTheEntry = PortalTransform.between(EXIT, ENTERED).point(CARRY.feet());
        verify(slide).interpolateTo(landingSeenFromTheEntry, CARRY.yaw(), CARRY.pitch());
    }

    private static final float PARTIAL = 0.5F;

    /**
     * On the frame the drawn body's middle reaches the entered gate's face it
     * is turned through the pair, its last tick's place with it, so it is
     * seen to come on out of the partner unbroken, and it slides on toward
     * the landing itself from there.
     */
    @Test
    void aCarriedBodyIsTurnedThroughThePairOnTheFrameItsDrawnMiddleReachesTheGate() {
        Entity body = mockBody();
        InterpolationHandler slide = mock(InterpolationHandler.class);
        when(body.getId()).thenReturn(BODY);
        when(body.getInterpolation()).thenReturn(slide);
        when(body.position()).thenReturn(DRAWN);
        ClientGateCarry.snap(body, CARRY, CARRIED_AT);
        Vec3 pastTheFace = new Vec3(0.5, 0.0, 0.1);
        when(body.position()).thenReturn(pastTheFace);
        when(body.getPosition(PARTIAL)).thenReturn(new Vec3(0.5, 0.0, 0.02));
        when(slide.hasActiveInterpolation()).thenReturn(true);
        when(slide.position()).thenReturn(PortalTransform.between(EXIT, ENTERED).point(CARRY.feet()));
        body.xo = DRAWN_BEFORE.x();
        body.yo = DRAWN_BEFORE.y();
        body.zo = DRAWN_BEFORE.z();

        ClientGateCarry.crossDrawn(body, PARTIAL, CARRIED_AT + 1);

        PortalTransform through = PortalTransform.between(ENTERED, EXIT);
        verify(body).setPos(through.point(pastTheFace));
        Vec3 before = new Vec3(body.xo, body.yo, body.zo);
        assertTrue(before.distanceTo(through.point(DRAWN_BEFORE)) < 1.0E-6, "The body's last tick should turn "
                + "through the pair with it, so it is drawn moving on unbroken, stands at " + before);
        verify(slide).interpolateTo(argThat(target -> target.distanceTo(CARRY.feet()) < 1.0E-6), anyFloat(),
                anyFloat());
    }

    /** A body drawn still short of the gate it went into stays where it is drawn. */
    @Test
    void aCarriedBodyDrawnShortOfTheGateIsNotYetTurned() {
        Entity body = mockBody();
        when(body.getId()).thenReturn(BODY);
        when(body.getInterpolation()).thenReturn(mock(InterpolationHandler.class));
        when(body.position()).thenReturn(DRAWN);
        when(body.getPosition(PARTIAL)).thenReturn(DRAWN);
        ClientGateCarry.snap(body, CARRY, CARRIED_AT);

        ClientGateCarry.crossDrawn(body, PARTIAL, CARRIED_AT + 1);

        verify(body, never()).setPos(any(Vec3.class));
    }

    /**
     * A move the server sends for the body before the client draws it into
     * the gate is drawn as seen from the entry side; once drawn through, the
     * server's moves stand as sent.
     */
    @Test
    void aServerMoveBeforeTheDrawnBodyReachesTheGateIsSeenFromTheEntrySide() {
        Entity body = mockBody();
        when(body.getId()).thenReturn(BODY);
        when(body.getInterpolation()).thenReturn(mock(InterpolationHandler.class));
        when(body.position()).thenReturn(DRAWN);
        ClientGateCarry.snap(body, CARRY, CARRIED_AT);
        Vec3 movedOn = CARRY.feet().add(0, 0, 0.5);

        assertTrue(ClientGateCarry.drawnSideOf(BODY, movedOn).distanceTo(
                PortalTransform.between(EXIT, ENTERED).point(movedOn)) < 1.0E-6,
                "The move should be drawn as seen from the entry side");
        when(body.position()).thenReturn(new Vec3(0.5, 0.0, 0.1));
        when(body.getPosition(PARTIAL)).thenReturn(new Vec3(0.5, 0.0, 0.1));
        ClientGateCarry.crossDrawn(body, PARTIAL, CARRIED_AT + 1);
        assertEquals(movedOn, ClientGateCarry.drawnSideOf(BODY, movedOn), "Once drawn through, the move stands");
    }

    /**
     * A body the client never draws into the gate, the server having moved
     * it on some other way, is turned through anyway a short while after the
     * carry, so it never stays drawn on the wrong side.
     */
    @Test
    void aCarriedBodyNeverDrawnIntoTheGateIsTurnedThroughAShortWhileLater() {
        Entity body = mockBody();
        when(body.getId()).thenReturn(BODY);
        when(body.getInterpolation()).thenReturn(mock(InterpolationHandler.class));
        when(body.position()).thenReturn(DRAWN);
        when(body.getPosition(PARTIAL)).thenReturn(DRAWN);
        ClientGateCarry.snap(body, CARRY, CARRIED_AT);
        ClientGateCarry.crossDrawn(body, PARTIAL, CARRIED_AT + GIVE_UP_TICKS - 1);
        verify(body, never()).setPos(any(Vec3.class));

        ClientGateCarry.crossDrawn(body, PARTIAL, CARRIED_AT + GIVE_UP_TICKS);

        verify(body).setPos(PortalTransform.between(ENTERED, EXIT).point(DRAWN));
    }

    private static final int GIVE_UP_TICKS = 10;

    /** A floor portal on the top of a block at y 64, its face the plane y 65. */
    private static final Gate FLOOR = new Gate(OVERWORLD, new AABB(new BlockPos(0, 64, 0)).minmax(
            new AABB(new BlockPos(0, 64, 1))), Direction.UP, Direction.NORTH.getUnitVec3());
    private static final float HEIGHT = 1.0F;

    /**
     * A body carried from a wall portal out of a floor portal is turned
     * through the pair by its middle and stands upright under it, as the
     * server sets it, rather than by its feet, which the turn lays half its
     * height off to the side, so it never jumps that far as it slides on.
     */
    @Test
    void aBodyCarriedOutOfAFloorPortalStandsUprightUnderItsTurnedMiddle() {
        Entity body = mockBody();
        when(body.getId()).thenReturn(BODY);
        when(body.getInterpolation()).thenReturn(mock(InterpolationHandler.class));
        when(body.position()).thenReturn(DRAWN);
        when(body.getBbHeight()).thenReturn(HEIGHT);
        GateCarriedPayload upOut = new GateCarriedPayload(BODY, new Vec3(0.5, 65.0, 1.0), Vec3.ZERO, 0F, 0F,
                ENTERED, FLOOR);
        ClientGateCarry.snap(body, upOut, CARRIED_AT);
        Vec3 pastTheFace = new Vec3(0.5, 0.0, 0.1);
        when(body.position()).thenReturn(pastTheFace);
        when(body.getPosition(PARTIAL)).thenReturn(pastTheFace);

        ClientGateCarry.crossDrawn(body, PARTIAL, CARRIED_AT + 1);

        Vec3 middle = PortalTransform.between(ENTERED, FLOOR).point(pastTheFace.add(0, HEIGHT / 2, 0));
        verify(body).setPos(middle.subtract(0, HEIGHT / 2, 0));
    }

    /** A body dropping into the floor portal and coming out of the north-facing wall one. */
    private static final int DROPPED = 11;
    /** About half a second. */
    private static final int EASE_TICKS = 10;

    /**
     * A body the server carried from a floor portal out of a wall portal
     * holds a quarter-turn tilt on the client from the frame it is drawn
     * through, and the tilt leaves once it has eased to nothing
     * (decision bodies-emerge-along-their-travel-and-ease-upright).
     */
    @Test
    void aBodyCarriedFromAFloorPortalOutOfAWallPortalHoldsItsTiltUntilItEases() {
        Entity body = mockBody();
        when(body.getInterpolation()).thenReturn(mock(InterpolationHandler.class));
        Vec3 inTheFloorGate = new Vec3(0.5, 64.9, 1.0);
        when(body.position()).thenReturn(inTheFloorGate);
        when(body.getPosition(PARTIAL)).thenReturn(inTheFloorGate);
        when(body.getId()).thenReturn(DROPPED);
        GateCarriedPayload outOfTheWall = new GateCarriedPayload(DROPPED, new Vec3(0.5, 0.0, -0.4), Vec3.ZERO, 0F,
                0F, FLOOR, ENTERED);
        ClientGateCarry.snap(body, outOfTheWall, CARRIED_AT);

        ClientGateCarry.crossDrawn(body, PARTIAL, CARRIED_AT + 1);

        assertEquals(QUARTER_TURN, ClientExitTilts.tiltOf(DROPPED, CARRIED_AT + 1).map(tilt -> tilt.degrees())
                .orElse(0.0), 1.0E-6, "The body should hold a quarter-turn tilt from the frame it is drawn through");
        assertTrue(ClientExitTilts.tiltOf(DROPPED, CARRIED_AT + 1 + EASE_TICKS).isEmpty(),
                "The tilt should leave once it has eased to nothing");
    }

    /**
     * The local player its own client carried from a floor portal out of a
     * wall portal holds the same tilt on its client.
     */
    @Test
    void theLocalPlayerCarriedFromAFloorPortalOutOfAWallPortalHoldsItsTilt() {
        ClientGatePassage.recordTilt(DROPPED + 1, new com.mercuriusxeno.goo.ability.quantum.GatePassage.Pair(FLOOR,
                ENTERED), CARRIED_AT);

        assertEquals(QUARTER_TURN, ClientExitTilts.tiltOf(DROPPED + 1, CARRIED_AT).map(tilt -> tilt.degrees())
                .orElse(0.0), 1.0E-6, "The player should hold a quarter-turn tilt from the tick it emerged");
    }

    private static final double QUARTER_TURN = 90.0;

    /**
     * Dropping the server's word placing a carried body at its landing still
     * takes the landing as the base the server's later small moves are read
     * from, so each later move places the body where the server has it, not
     * the carry's whole distance back toward the gate it went into.
     */
    @Test
    void aDroppedLandingSyncStillMovesTheBaseLaterMovesAreReadFrom() {
        Entity body = mockBody();
        VecDeltaCodec moves = new VecDeltaCodec();
        moves.setBase(DRAWN);
        when(body.getId()).thenReturn(BODY);
        when(body.position()).thenReturn(DRAWN);
        when(body.getPositionCodec()).thenReturn(moves);
        ClientGateCarry.snap(body, CARRY, CARRIED_AT);

        assertTrue(ClientGateCarry.dropStale(body, CARRY.feet(), CARRIED_AT + 1), "The landing's sync should drop");

        Vec3 stepOn = CARRY.feet().add(0, 0, 0.25);
        Vec3 read = moves.decode(new VecDeltaCodec() { { setBase(CARRY.feet()); } }.encodeX(stepOn),
                new VecDeltaCodec() { { setBase(CARRY.feet()); } }.encodeY(stepOn),
                new VecDeltaCodec() { { setBase(CARRY.feet()); } }.encodeZ(stepOn));
        assertTrue(read.distanceTo(stepOn) < 0.01, "A later move from the landing should read where the server has "
                + "the body, " + stepOn + ", reads " + read);
    }

    @Test
    void theServersWordPlacingTheBodyBackAtTheLandingIsStale() {
        Entity body = mockBody();
        when(body.position()).thenReturn(DRAWN);
        ClientGateCarry.snap(body, CARRY, CARRIED_AT);

        assertTrue(ClientGateCarry.staleSync(BODY, CARRY.feet().add(0.0001, 0, 0), CARRIED_AT + 2),
                "A move placing the body back at its landing just after the carry should be dropped");
        assertFalse(ClientGateCarry.staleSync(BODY, CARRY.feet().add(-0.7, 0, 0), CARRIED_AT + 2),
                "A move the body made since the carry should stand");
        assertFalse(ClientGateCarry.staleSync(BODY, CARRY.feet(), CARRIED_AT + 40),
                "A move long after the carry should stand, wherever it places the body");
    }
}
