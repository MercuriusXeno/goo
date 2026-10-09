package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A drink's field is the sum of soft bodies: a lone capsule's surface sits
 * at its radius, a box's at its faces and rounded corners, and two bodies
 * that come near swell into one another, their surfaces meeting in the gap
 * between them like metaballs (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkFieldTest {

    private static final double DELTA = 1e-9;
    private static final double RADIUS = 0.2;
    /** A gap between two surfaces well within the reach, which the field fills. */
    private static final double NEAR_GAP = 0.15;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final DrinkTree.Block BLOCK = new DrinkTree.Block(new BlockPos(7, 2, 3), new Vec3(7.5, 2.5, 3.5),
            1, 0, 100);

    private static DrinkStream.Ring ring(Vec3 center) {
        return new DrinkStream.Ring(center, EAST, RADIUS, 0, 0, DrinkStream.FLOW, 1);
    }

    private static DrinkTree.Stream stream() {
        DrinkLayout layout = new DrinkLayout();
        layout.place(List.of(BLOCK.pos()), GLOVE);
        return DrinkTree.build(List.of(BLOCK), layout, GLOVE, EAST, 0).getFirst();
    }

    /**
     * @param from the capsule's start
     * @param to   its end
     * @return a skeleton of one capsule and no box
     */
    private static DrinkField.Skeleton capsule(Vec3 from, Vec3 to) {
        return new DrinkField.Skeleton(stream(), List.of(ring(from), ring(to)), null);
    }

    @Test
    void theFalloffIsWholeInsideTheIsoAtTheSurfaceAndNothingPastTheReach() {
        assertEquals(1, DrinkField.falloff(-DrinkField.DEPTH), DELTA);
        assertEquals(DrinkField.ISO, DrinkField.falloff(0), DELTA);
        assertEquals(0, DrinkField.falloff(DrinkField.REACH), DELTA);
        assertTrue(DrinkField.falloff(DrinkField.REACH / 2) < DrinkField.ISO);
        assertTrue(DrinkField.falloff(-DrinkField.DEPTH / 2) > DrinkField.ISO);
        assertTrue(DrinkField.ISO > 0.5, "a lone body's surface is where its own field is still high");
    }

    @Test
    void aLoneCapsulesSurfaceSitsAtItsRadius() {
        DrinkField.Skeleton capsule = capsule(Vec3.ZERO, new Vec3(2, 0, 0));
        List<DrinkField.Skeleton> alone = List.of(capsule);

        assertEquals(DrinkField.ISO, DrinkField.sample(alone, new Vec3(1, RADIUS, 0)).value(), DELTA);
        assertTrue(DrinkField.sample(alone, new Vec3(1, RADIUS / 2, 0)).inside());
        assertFalse(DrinkField.sample(alone, new Vec3(1, 2 * RADIUS, 0)).inside());
        assertEquals(DrinkField.ISO, DrinkField.sample(alone, new Vec3(-RADIUS, 0, 0)).value(), DELTA);
        assertSame(capsule, DrinkField.sample(alone, new Vec3(1, 0, 0)).skeleton());
    }

    @Test
    void aBoxsSurfaceSitsAtItsFacesAndItsRoundedCornersAreCut() {
        DrinkBody.Box box = new DrinkBody.Box(Vec3.ZERO, 0.5, 0);
        DrinkBody.Box sphere = new DrinkBody.Box(Vec3.ZERO, 0.5, 0.5);

        assertEquals(0, box.signedDistance(new Vec3(0.5, 0.1, 0.2)), DELTA);
        assertEquals(-0.5, box.signedDistance(Vec3.ZERO), DELTA);
        assertEquals(0, box.signedDistance(new Vec3(0.5, 0.5, 0.5)), DELTA);
        assertEquals(0, sphere.signedDistance(UP.scale(0.5)), DELTA);
        assertTrue(sphere.signedDistance(new Vec3(0.5, 0.5, 0.5)) > 0, "a sphere's corner is outside it");
    }

    @Test
    void twoBodiesWhoseSurfacesComeWithinTheReachSwellIntoOneAnother() {
        Vec3 near = new Vec3(0, 2 * RADIUS + NEAR_GAP, 0);
        Vec3 far = new Vec3(0, 2 * RADIUS + 2 * DrinkField.REACH, 0);
        List<DrinkField.Skeleton> touching = List.of(capsule(Vec3.ZERO, EAST), capsule(near, near.add(EAST)));
        List<DrinkField.Skeleton> apart = List.of(capsule(Vec3.ZERO, EAST), capsule(far, far.add(EAST)));

        assertTrue(DrinkField.sample(touching, new Vec3(0.5, RADIUS + NEAR_GAP / 2, 0)).inside(),
                "the gap between near bodies fills");
        assertFalse(DrinkField.sample(apart, new Vec3(0.5, RADIUS + DrinkField.REACH, 0)).inside(),
                "far bodies leave the gap empty");
    }

    @Test
    void readingOnlyTheBodiesThatReachACellGivesTheSameField() {
        List<DrinkField.Skeleton> both = List.of(capsule(Vec3.ZERO, EAST), capsule(UP.scale(3), UP.scale(3).add(EAST)));
        Vec3 point = new Vec3(0.5, RADIUS / 2, 0);

        DrinkField.Sample whole = DrinkField.sample(both, point);
        DrinkField.Sample some = DrinkField.sample(both, new int[]{DrinkField.candidate(0, 0)}, point);

        assertEquals(whole.value(), some.value(), DELTA);
        assertEquals(whole.value(), DrinkField.valueAt(both, new int[]{DrinkField.candidate(0, 0)}, point.x, point.y,
                point.z), DELTA);
        assertSame(both.getFirst(), some.skeleton());
    }

    @Test
    void aSampleKnowsWhenItIsNearerTheStandingBlockThanTheStream() {
        Vec3 boxCenter = UP.scale(3);
        DrinkBody.Box box = new DrinkBody.Box(boxCenter, 0.5, 0);
        DrinkField.Skeleton skeleton = new DrinkField.Skeleton(stream(), List.of(ring(Vec3.ZERO), ring(EAST)), box);
        List<DrinkField.Skeleton> alone = List.of(skeleton);

        assertTrue(DrinkField.sample(alone, boxCenter.add(0.5, 0.1, 0.2)).onBlock());
        assertFalse(DrinkField.sample(alone, new Vec3(0.5, RADIUS, 0)).onBlock());
        assertFalse(DrinkField.sample(List.of(capsule(Vec3.ZERO, EAST)), new Vec3(0.5, RADIUS, 0)).onBlock());
        assertEquals(2, skeleton.bodies());
    }

    @Test
    void aBodysBoxBoundsItsOwnSurface() {
        DrinkField.Skeleton capsule = capsule(Vec3.ZERO, EAST);

        assertEquals(new Vec3(-RADIUS, -RADIUS, -RADIUS), capsule.lowOf(0));
        assertEquals(new Vec3(1 + RADIUS, RADIUS, RADIUS), capsule.highOf(0));
        assertEquals(1, capsule.bodies());
    }

    @Test
    void theNearestRingIsTheOneClosestToThePoint() {
        List<DrinkStream.Ring> chain = List.of(ring(Vec3.ZERO), ring(EAST), ring(EAST.scale(2)));

        assertSame(chain.get(1), DrinkField.nearestRing(chain, new Vec3(1.2, 0.3, 0)));
        assertEquals(0, new DrinkField.Skeleton(stream(), List.of(), null).bodies());
    }
}
