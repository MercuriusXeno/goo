package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.ability.quantum.Gate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Every aim target carries the exact point aimed at, which the throw flies to,
 * while a block's bullseye keeps its face's center (decisions
 * aim-point-follows-the-cursor, aim-line-snaps-with-the-tile-highlight).
 */
class TargetResultTest {

    private static final BlockPos POS = new BlockPos(2, 64, 2);
    /** A wall portal's gate, the face the plane z 0. */
    private static final Gate GATE = new Gate(
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld")),
            new AABB(new BlockPos(0, 0, 0)).minmax(new AABB(new BlockPos(0, 1, 0))), Direction.NORTH,
            Direction.UP.getUnitVec3());

    @Test
    void aFaceHitAimsWhereTheRayMetTheFaceNotItsCenter() {
        Vec3 met = new Vec3(2.15, 64.8, 2.0);
        TargetResult target = TargetResult.block(POS, Direction.NORTH, met);

        assertEquals(met, target.point());
        assertNotEquals(met, target.resolveEndpoint());
    }

    @Test
    void anEntityHitAimsWhereTheRayMetTheEntity() {
        Vec3 met = new Vec3(7.0, 65.3, 1.2);

        assertEquals(met, new TargetResult.EntityTarget(null, met, null).point());
    }

    @Test
    void aFreeMissAimsTheRaysEnd() {
        Vec3 reach = new Vec3(40, 90, -30);

        assertEquals(reach, TargetResult.pointInAir(reach).point());
    }

    @Test
    void aLobAimsItsTopFacesCenter() {
        assertEquals(new Vec3(2.5, 65.0, 2.5), TargetResult.grannyArc(POS).point());
    }

    @Test
    void aTargetReachedThroughAGateCarriesTheGateAndThePointOnItsFace() {
        TargetResult.Passage passage = new TargetResult.Passage(GATE, new Vec3(0.5, 1.5, 0.0));
        TargetResult target = TargetResult.block(POS, Direction.NORTH, new Vec3(2.5, 64.5, 2.0)).through(passage);

        assertEquals(passage, target.passage(), "A far-side target should carry the gate it was reached through");
        assertEquals(new Vec3(2.5, 64.5, 2.0), target.point(), "A far-side target should keep its own point");
    }

    @Test
    void nothingAimedNamesNoPoint() {
        assertNull(TargetResult.NONE.point());
    }
}
