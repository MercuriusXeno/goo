package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A vine strand's spine: it runs from where it leaves to where it latches,
 * a rooted mob's strands leave its body and end on the knot at the root,
 * bending to the ground when slack and straight when strained, and a
 * strand reaches only as far as it has grown
 * (decision vines-unpack-root-and-thorn).
 */
class VineRibbonTest {

    private static final Vec3 FROM = new Vec3(0, 1, 0);
    private static final Vec3 BEND = new Vec3(1, -1, 0);
    private static final Vec3 TO = new Vec3(2, 0, 0);
    private static final Vec3 ROOT = new Vec3(5, 64, 5);
    private static final double BODY_RADIUS = 0.1;
    private static final double BODY_HEIGHT = 0.6;
    private static final double EPSILON = 1e-9;

    private static VineTendrils.Stance standingAt(Vec3 feet) {
        return new VineTendrils.Stance(feet, ROOT, BODY_RADIUS, BODY_HEIGHT);
    }

    @Test
    void aGrownStrandRunsFromItsStartToWhereItLatches() {
        List<Vec3> spine = VineRibbon.curve(FROM, BEND, TO, 1f);
        assertEquals(VineRibbon.SEGMENTS + 1, spine.size());
        assertEquals(0, spine.get(0).distanceTo(FROM), EPSILON);
        assertEquals(0, spine.get(VineRibbon.SEGMENTS).distanceTo(TO), EPSILON);
    }

    @Test
    void aStrandStillGrowingStopsShortOfItsLatch() {
        Vec3 middle = FROM.add(TO).scale(0.5);
        List<Vec3> half = VineRibbon.curve(FROM, middle, TO, 0.5f);
        assertEquals(0, half.get(VineRibbon.SEGMENTS).distanceTo(middle), EPSILON);
    }

    @Test
    void everyStrandLeavesTheBodyAndEndsOnTheKnotAtTheRoot() {
        Vec3 feet = ROOT.add(0.5, 0, 0);
        for (int i = 0; i < VineTendrils.TENDRILS; i++) {
            List<Vec3> spine = VineTendrils.strandSpine(standingAt(feet), i, 0.5, 1f);
            Vec3 end = spine.get(spine.size() - 1);
            assertEquals(VineTendrils.KNOT_RIM, Math.hypot(end.x - ROOT.x, end.z - ROOT.z), EPSILON);
            assertEquals(ROOT.y, end.y, EPSILON);
            assertEquals(feet.y + BODY_HEIGHT, spine.get(0).y, EPSILON);
        }
    }

    @Test
    void aStrandStrainedToTheLeashRunsStraightFromBodyToKnot() {
        List<Vec3> spine = VineTendrils.strandSpine(standingAt(ROOT.add(1, 0, 0)), 0, 1, 1f);
        Vec3 from = spine.get(0);
        Vec3 to = spine.get(spine.size() - 1);
        assertEquals(0, spine.get(VineRibbon.SEGMENTS / 2).distanceTo(from.add(to).scale(0.5)), EPSILON);
    }

    @Test
    void aSlackStrandBendsDownToTheGroundBesideTheFeet() {
        List<Vec3> spine = VineTendrils.strandSpine(standingAt(ROOT.add(0.5, 0, 0)), 0, 0, 1f);
        Vec3 from = spine.get(0);
        Vec3 to = spine.get(spine.size() - 1);
        assertTrue(spine.get(VineRibbon.SEGMENTS / 2).y < (from.y + to.y) / 2);
    }

    @Test
    void aMobOnItsRootStrainsNothingAndOneAtTheLeashStrainsWhole() {
        assertEquals(0, VineTendrils.strainOf(ROOT, ROOT), EPSILON);
        assertEquals(1, VineTendrils.strainOf(ROOT, ROOT.add(0, 0, 2)), EPSILON);
    }

    @Test
    void aTrapsTendrilsHookOverTheEdgeOnlyOnceUnpacked() {
        VineTrapVisual.Basis floor = VineTrapVisual.Basis.of(Direction.UP);
        Vec3 surface = new Vec3(0.5, 0, 0.5);
        List<Vec3> reaching = VineTrapVisual.tendrilSpine(surface, floor, 0, 0.5f, 0f);
        List<Vec3> latched = VineTrapVisual.tendrilSpine(surface, floor, 0, 1f, 0f);
        assertEquals(VineRibbon.SEGMENTS - 1, reaching.size());
        assertEquals(VineRibbon.SEGMENTS + 1, latched.size());
        assertEquals(-0.2, latched.get(latched.size() - 1).y, 1e-6);
    }
}
