package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.PortalTransform;
import com.mercuriusxeno.goo.network.GateCarriedPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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

    @Test
    void aCarriedBodyIsTurnedThroughThePairWhereItIsDrawnAndSlidesOnToTheLanding() {
        Entity body = mock(Entity.class);
        InterpolationHandler slide = mock(InterpolationHandler.class);
        when(body.getInterpolation()).thenReturn(slide);
        when(body.position()).thenReturn(DRAWN);
        body.xo = DRAWN_BEFORE.x();
        body.yo = DRAWN_BEFORE.y();
        body.zo = DRAWN_BEFORE.z();

        ClientGateCarry.snap(body, CARRY, CARRIED_AT);

        PortalTransform through = PortalTransform.between(ENTERED, EXIT);
        verify(slide).cancel();
        verify(body).setPos(through.point(DRAWN));
        Vec3 before = new Vec3(body.xo, body.yo, body.zo);
        assertTrue(before.distanceTo(through.point(DRAWN_BEFORE)) < 1.0E-6, "The body's last tick should turn "
                + "through the pair with it, so it is drawn moving on unbroken, stands at " + before);
        verify(slide).interpolateTo(CARRY.feet(), CARRY.yaw(), CARRY.pitch());
    }

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
        Entity body = mock(Entity.class);
        when(body.getInterpolation()).thenReturn(mock(InterpolationHandler.class));
        when(body.position()).thenReturn(DRAWN);
        when(body.getBbHeight()).thenReturn(HEIGHT);
        GateCarriedPayload upOut = new GateCarriedPayload(BODY, new Vec3(0.5, 65.0, 1.0), Vec3.ZERO, 0F, 0F,
                ENTERED, FLOOR);

        ClientGateCarry.snap(body, upOut, CARRIED_AT);

        Vec3 middle = PortalTransform.between(ENTERED, FLOOR).point(DRAWN.add(0, HEIGHT / 2, 0));
        verify(body).setPos(middle.subtract(0, HEIGHT / 2, 0));
    }

    @Test
    void theServersWordPlacingTheBodyBackAtTheLandingIsStale() {
        Entity body = mock(Entity.class);
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
