package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.ability.quantum.GateRay;
import com.mercuriusxeno.goo.throwing.GateArc;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A goo flight through a linked gate flies two legs over its travel ticks,
 * into the gate's face and out of the partner to the target, its far leg
 * drawn in the view through the gate it went into
 * (decision aim-and-shots-carry-through-linked-gates).
 */
class GooFlightThroughAGateTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    /** A 3x3 portal on a wall's north face, the plane z 0. */
    private static final Gate NEAR = new Gate(OVERWORLD, new AABB(new BlockPos(-1, 0, 0)).minmax(
            new AABB(new BlockPos(1, 2, 0))), Direction.NORTH, Direction.UP.getUnitVec3());
    /** Its partner on a wall's south face, the plane z 3. */
    private static final Gate FAR = new Gate(OVERWORLD, new AABB(new BlockPos(-1, 0, 2)).minmax(
            new AABB(new BlockPos(1, 2, 2))), Direction.SOUTH, Direction.UP.getUnitVec3());
    private static final Vec3 HAND = new Vec3(0.5, 1.5, -4.0);
    private static final Vec3 FACE = new Vec3(0.5, 1.5, 0.0);
    /** Four blocks past the partner, as far as the hand stands before the gate, so the legs split at half. */
    private static final Vec3 TARGET = new Vec3(0.5, 1.5, 7.0);
    private static final int TRAVEL_TICKS = 10;
    private static final int NEAR_LEG_ENDS = 5;
    private static final double CLOSE = 1.0E-6;

    private static GooFlightManager.GooFlight flight() {
        GateArc.Hop hop = GateRay.hopThrough(List.of(new GatePassage.Pair(NEAR, FAR)), NEAR, FACE).orElseThrow();
        return new GooFlightManager.GooFlight(HAND, TARGET, -1, BlockPos.containing(TARGET), GooTypes.CRYSTAL,
                Delivery.ARC, TRAVEL_TICKS, false, NEAR, hop);
    }

    private static Vec3 at(GooFlightManager.GooFlight flight, int tick) {
        flight.ticksElapsed = tick;
        return flight.getPosition(0F);
    }

    @Test
    void theNearLegEndsOnTheGatesFace() {
        Vec3 there = at(flight(), NEAR_LEG_ENDS);
        assertEquals(FACE.x(), there.x(), CLOSE, "The flight should meet the gate's face where its near leg ends");
        assertEquals(FACE.z(), there.z(), CLOSE, "The flight should meet the gate's face where its near leg ends");
    }

    @Test
    void theNextStepStandsPastThePartnersFace() {
        Vec3 there = at(flight(), NEAR_LEG_ENDS + 1);
        assertTrue(FAR.out(there) > 0, "A step after the near leg ends the flight should stand out past the "
                + "partner's face, stands " + FAR.out(there) + " out of it at " + there);
    }

    @Test
    void theLastTickIsTheTarget() {
        Vec3 there = at(flight(), TRAVEL_TICKS);
        assertTrue(there.distanceTo(TARGET) < CLOSE, "The flight should end at the target, ends at " + there);
    }

    @Test
    void theFarLegIsDrawnThroughTheGateItWentInto() {
        GooFlightManager.GooFlight flight = flight();
        flight.ticksElapsed = NEAR_LEG_ENDS - 2;
        assertEquals(Optional.empty(), flight.drawnThrough(0F), "The near leg should draw in the main frame");
        flight.ticksElapsed = NEAR_LEG_ENDS + 2;
        assertEquals(Optional.of(NEAR), flight.drawnThrough(0F), "The far leg should draw in the view through the "
                + "gate the flight went into");
    }
}
