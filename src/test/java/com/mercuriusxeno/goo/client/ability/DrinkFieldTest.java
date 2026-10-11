package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A drink's field is the sum of soft bodies read per skeleton: a lone capsule's
 * surface sits at its radius, a box's at its faces and rounded corners, two
 * bodies that come near swell into one another, their surfaces meeting in the
 * gap between them like metaballs, and a body of no radius radiates nothing
 * (decision unmake-waves-dissolve-by-crucible-cost).
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
            1, 0, 0, 100);

    private static DrinkStream.Ring ring(Vec3 center) {
        return ring(center, RADIUS);
    }

    private static DrinkStream.Ring ring(Vec3 center, double radius) {
        return new DrinkStream.Ring(center, EAST, radius, 0, 0, DrinkStream.FLOW);
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

    private static boolean inside(List<DrinkField.Skeleton> skeletons, Vec3 point) {
        return DrinkField.valueAt(skeletons, point) >= DrinkField.ISO;
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
        List<DrinkField.Skeleton> alone = List.of(capsule(Vec3.ZERO, new Vec3(2, 0, 0)));

        assertEquals(DrinkField.ISO, DrinkField.valueAt(alone, new Vec3(1, RADIUS, 0)), DELTA);
        assertTrue(inside(alone, new Vec3(1, RADIUS / 2, 0)));
        assertFalse(inside(alone, new Vec3(1, 2 * RADIUS, 0)));
        assertEquals(DrinkField.ISO, DrinkField.valueAt(alone, new Vec3(-RADIUS, 0, 0)), DELTA);
    }

    @Test
    void aChainIsReadAtItsLeastDistanceSoItsJointsDoNotBulge() {
        DrinkField.Skeleton chain = new DrinkField.Skeleton(stream(),
                List.of(ring(Vec3.ZERO), ring(EAST), ring(EAST.scale(2))), null);

        assertEquals(DrinkField.ISO, DrinkField.valueAt(List.of(chain), new Vec3(1, RADIUS, 0)), DELTA,
                "at the joint of two capsules the surface sits at the radius, not swollen by both");
        assertEquals(2, chain.bodies());
    }

    @Test
    void aBoxsSurfaceSitsAtItsFacesAndItsRoundedCornersAreCut() {
        DrinkBody.Box box = new DrinkBody.Box(Vec3.ZERO, 0.5, 0);
        DrinkBody.Box sphere = new DrinkBody.Box(Vec3.ZERO, 0.5, 0.5);
        DrinkField.Skeleton boxed = new DrinkField.Skeleton(stream(), List.of(), box);

        assertEquals(0, box.signedDistance(new Vec3(0.5, 0.1, 0.2)), DELTA);
        assertEquals(-0.5, box.signedDistance(Vec3.ZERO), DELTA);
        assertEquals(0, box.signedDistance(new Vec3(0.5, 0.5, 0.5)), DELTA);
        assertEquals(0, sphere.signedDistance(UP.scale(0.5)), DELTA);
        assertTrue(sphere.signedDistance(new Vec3(0.5, 0.5, 0.5)) > 0, "a sphere's corner is outside it");
        assertEquals(DrinkField.ISO, DrinkField.valueAt(List.of(boxed), new Vec3(0.5, 0.1, 0.2)), DELTA);
        assertEquals(1, boxed.bodies());
    }

    @Test
    void twoBodiesWhoseSurfacesComeWithinTheReachSwellIntoOneAnother() {
        Vec3 near = new Vec3(0, 2 * RADIUS + NEAR_GAP, 0);
        Vec3 far = new Vec3(0, 2 * RADIUS + 2 * DrinkField.REACH, 0);
        List<DrinkField.Skeleton> touching = List.of(capsule(Vec3.ZERO, EAST), capsule(near, near.add(EAST)));
        List<DrinkField.Skeleton> apart = List.of(capsule(Vec3.ZERO, EAST), capsule(far, far.add(EAST)));

        assertTrue(inside(touching, new Vec3(0.5, RADIUS + NEAR_GAP / 2, 0)), "the gap between near bodies fills");
        assertFalse(inside(apart, new Vec3(0.5, RADIUS + DrinkField.REACH, 0)), "far bodies leave the gap empty");
    }

    @Test
    void aBodyOfNoRadiusRadiatesNothingAndAThinOneReachesInProportion() {
        Vec3 beside = UP.scale(DrinkField.REACH / 2);
        List<DrinkField.Skeleton> empty = List.of(
                new DrinkField.Skeleton(stream(), List.of(ring(Vec3.ZERO, 0), ring(EAST, 0)), null),
                new DrinkField.Skeleton(stream(), List.of(ring(beside, 0), ring(beside.add(EAST), 0)), null));
        double thin = DrinkField.FULL_RADIUS / 2;
        List<DrinkField.Skeleton> slender = List.of(
                new DrinkField.Skeleton(stream(), List.of(ring(Vec3.ZERO, thin), ring(EAST, thin)), null));

        assertEquals(0, DrinkField.valueAt(empty, new Vec3(0.5, 0.01, 0)), DELTA);
        assertFalse(inside(empty, beside.scale(0.5).add(0.5, 0, 0)),
                "two empty lines close together leave no blob between them");
        assertEquals(DrinkField.ISO, DrinkField.valueAt(slender, new Vec3(0.5, thin, 0)), DELTA);
        assertTrue(DrinkField.valueAt(slender, new Vec3(0.5, thin + DrinkField.REACH / 4, 0)) > 0);
        assertEquals(0, DrinkField.valueAt(slender, new Vec3(0.5, thin + DrinkField.REACH / 2 + 0.01, 0)), DELTA,
                "a body half a waist thick reaches half as far");
    }

    @Test
    void aBodysBoxBoundsItsOwnSurface() {
        DrinkField.Skeleton capsule = capsule(Vec3.ZERO, EAST);
        DrinkBody.Box box = new DrinkBody.Box(UP.scale(3), 0.5, 0);
        DrinkField.Skeleton boxed = new DrinkField.Skeleton(stream(), List.of(ring(Vec3.ZERO), ring(EAST)), box);

        assertEquals(new Vec3(-RADIUS, -RADIUS, -RADIUS), capsule.lowOf(0));
        assertEquals(new Vec3(1 + RADIUS, RADIUS, RADIUS), capsule.highOf(0));
        assertEquals(1, capsule.bodies());
        assertEquals(2, boxed.bodies());
        assertEquals(new Vec3(-0.5, 2.5, -0.5), boxed.lowOf(1));
        assertEquals(new Vec3(0.5, 3.5, 0.5), boxed.highOf(1));
        assertEquals(0, new DrinkField.Skeleton(stream(), List.of(), null).bodies());
    }
}
