package com.mercuriusxeno.goo.ability.quantum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A view's frustum is the cone from its eye through the gate's opening: what
 * stands beside the opening's line of sight is culled though the camera's
 * full frustum holds it, a nested gate off to the side of the opening shows
 * no view, and a view within a view keeps to its parent's cone too
 * (decision nested-views-skip-when-tiny-or-outside-the-parent-opening).
 */
class ViewConeTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    /** A two-tall wall gate on a north face, the face the plane z 0, its opening x 0 to 1 and y 0 to 2. */
    private static final Gate WALL = new Gate(OVERWORLD, new AABB(new BlockPos(0, 0, 0)).minmax(
            new AABB(new BlockPos(0, 1, 0))), Direction.NORTH, Direction.UP.getUnitVec3());
    /** An eye four blocks before the gate, square on its middle. */
    private static final Vec3 EYE = new Vec3(0.5, 1.0, -4.0);
    /** A section straight through the opening, eight blocks beyond it. */
    private static final AABB THROUGH = new AABB(0, 0, 8, 1, 2, 9);
    /** A section beside the line of sight beyond the opening: within a 70 degree view, outside the cone. */
    private static final AABB BESIDE = new AABB(4, 0, 4, 5, 2, 5);
    private static final double SEVENTY_DEGREES_HALF = Math.toRadians(35);

    @Test
    void aSectionStraightThroughTheOpeningIsHeldAndOneBesideItIsNot() {
        Vec3 besideMiddle = BESIDE.getCenter().subtract(EYE).normalize();
        assertTrue(Math.acos(besideMiddle.dot(new Vec3(0, 0, 1))) < SEVENTY_DEGREES_HALF, "The section beside should "
                + "stand within a 70 degree frustum, to test the cone rather than the frustum");
        ViewCone cone = ViewCone.through(EYE, WALL);
        assertTrue(cone.holds(THROUGH), "A section straight through the opening should be held");
        assertFalse(cone.holds(BESIDE), "A section beside the opening's line of sight should not be held");
    }

    /** A gate off to the side of the parent opening, beyond it: within the frustum, outside the cone. */
    private static final Gate OFF_TO_THE_SIDE = new Gate(OVERWORLD, new AABB(new BlockPos(4, 0, 6)).minmax(
            new AABB(new BlockPos(4, 1, 6))), Direction.NORTH, Direction.UP.getUnitVec3());
    /** A gate square through the parent opening, beyond it. */
    private static final Gate STRAIGHT_THROUGH = new Gate(OVERWORLD, new AABB(new BlockPos(0, 0, 10)).minmax(
            new AABB(new BlockPos(0, 1, 10))), Direction.NORTH, Direction.UP.getUnitVec3());

    @Test
    void aNestedGateOffToTheSideOfTheOpeningShowsNoViewAndOneThroughItDoes() {
        assertFalse(ViewCone.showsThrough(EYE, WALL, OFF_TO_THE_SIDE), "A gate off to the side of the parent opening "
                + "should show no view");
        assertTrue(ViewCone.showsThrough(EYE, WALL, STRAIGHT_THROUGH), "A gate through the parent opening should");
    }

    /** A wider gate further back, whose own cone from the eye holds more than the wall gate's. */
    private static final Gate WIDE = new Gate(OVERWORLD, new AABB(new BlockPos(-3, 0, -2)).minmax(
            new AABB(new BlockPos(3, 1, -2))), Direction.NORTH, Direction.UP.getUnitVec3());

    @Test
    void aViewWithinAViewKeepsToItsParentsConeToo() {
        ViewCone child = ViewCone.through(EYE, WIDE);
        ViewCone parent = ViewCone.through(EYE, WALL);
        assertTrue(child.holds(BESIDE), "The wider gate's own cone should hold the section beside, to test the "
                + "parent's part");
        assertFalse(child.and(parent).holds(BESIDE), "A box within the child cone but outside the parent cone should "
                + "not be held by the two together");
    }

    @Test
    void aConeCarriedThroughAPairHoldsWhatItsCarriedBoxIs() {
        Gate far = new Gate(OVERWORLD, new AABB(new BlockPos(20, 0, 20)).minmax(new AABB(new BlockPos(20, 1, 20))),
                Direction.SOUTH, Direction.UP.getUnitVec3());
        PortalTransform across = PortalTransform.between(WALL, far);
        ViewCone carried = ViewCone.through(EYE, WALL).through(across);
        assertTrue(carried.holds(boxThrough(across, THROUGH)), "The carried cone should hold the carried section");
        assertFalse(carried.holds(boxThrough(across, BESIDE)), "The carried cone should not hold the carried beside");
    }

    private static AABB boxThrough(PortalTransform across, AABB box) {
        Vec3 low = across.point(new Vec3(box.minX, box.minY, box.minZ));
        Vec3 high = across.point(new Vec3(box.maxX, box.maxY, box.maxZ));
        return new AABB(low, high);
    }
}
