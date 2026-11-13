package com.mercuriusxeno.goo.throwing;

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
 * A spray drip flies a real arc under gravity with no range, ending on the
 * first block face its arc meets, however far below its launch that lies.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 */
class DripArcTest {

    private static final double TOLERANCE = 1e-9;
    /** Below this the fake world holds no airspace, as below a level's build height. */
    private static final double WORLD_FLOOR = -64;
    private static final DripArc.Airspace INSIDE_THE_WORLD = point -> point.y >= WORLD_FLOOR;
    /** The top of the ledge a falling drip meets on its way down. */
    private static final double LEDGE_TOP = 10;
    private static final double LEDGE_WEST = 8;
    private static final double LEDGE_EAST = 12;
    private static final BlockPos LEDGE = new BlockPos(10, 9, 0);
    /** The floor's top face at y 0. */
    private static final CoatRay.BlockClip FLOOR = (from, to) -> topCrossing(from, to, 0)
            .map(point -> new CoatRay.BlockStrike(point, BlockPos.containing(point.x, -1, point.z), Direction.UP));
    /** The floor, with a ledge standing over it east of the launch. */
    private static final CoatRay.BlockClip LEDGE_OVER_FLOOR = (from, to) -> topCrossing(from, to, LEDGE_TOP)
            .filter(point -> point.x >= LEDGE_WEST && point.x <= LEDGE_EAST)
            .map(point -> new CoatRay.BlockStrike(point, LEDGE, Direction.UP))
            .or(() -> FLOOR.firstHit(from, to));

    /**
     * Where a segment falling through a level top face crosses it.
     *
     * @param from the segment's start
     * @param to   the segment's end
     * @param top  the face's height
     * @return the crossing, empty where the segment does not fall through the face
     */
    private static Optional<Vec3> topCrossing(Vec3 from, Vec3 to, double top) {
        if (from.y < top || to.y >= top) {
            return Optional.empty();
        }
        return Optional.of(from.add(to.subtract(from).scale((from.y - top) / (from.y - to.y))));
    }

    @Test
    void aDripThrownDownFromTwoHundredBlocksUpStrikesTheFloor() {
        Vec3 launch = new Vec3(0.5, 200, 0.5);

        Optional<CoatRay.BlockStrike> strike = DripArc.flyToItsEnd(launch, new Vec3(0, -0.1, 0), FLOOR,
                INSIDE_THE_WORLD).strike();

        assertTrue(strike.isPresent(), "a drip thrown down from 200 blocks should strike the floor");
        assertEquals(0, strike.get().point().y, TOLERANCE, "the strike should stand on the floor's top");
        assertEquals(0.5, strike.get().point().x, TOLERANCE, "a drip thrown straight down should fall straight");
        assertEquals(Direction.UP, strike.get().face(), "the drip should strike the floor's top face");
    }

    @Test
    void aDripWhoseArcMeetsALedgeStrikesTheLedge() {
        Vec3 launch = new Vec3(0, 20, 0.5);

        Optional<CoatRay.BlockStrike> strike = DripArc.flyToItsEnd(launch, new Vec3(0.3, 0, 0), LEDGE_OVER_FLOOR,
                INSIDE_THE_WORLD).strike();

        assertTrue(strike.isPresent(), "the drip should strike something");
        assertEquals(LEDGE, strike.get().block(), "the drip's arc meets the ledge first, struck at "
                + strike.get().point());
        assertEquals(LEDGE_TOP, strike.get().point().y, TOLERANCE, "the strike should stand on the ledge's top");
    }

    @Test
    void aDripMeetingAMobBeforeTheFloorEndsOnTheMob() {
        AABB mob = new AABB(3, 0, 0, 4, 2, 1);
        DripArc.LivingBox zombie = new DripArc.LivingBox(17, mob);
        DripArc.Step step = DripArc.step(new Vec3(0, 1, 0.5), new Vec3(4.5, -1.2, 0), FLOOR,
                (from, to) -> List.of(zombie), INSIDE_THE_WORLD);

        assertEquals(Optional.of(zombie), step.living(), "the mob stands nearer than the floor along the path");
        assertTrue(step.strike().isEmpty(), "a drip ending on a mob strikes no block behind it");
        assertTrue(mob.inflate(TOLERANCE).contains(step.position()), "the strike should lie on the mob's box, lies at "
                + step.position());
    }

    @Test
    void aDripMeetingTheFloorBeforeAMobEndsOnTheFloor() {
        DripArc.LivingBox beyond = new DripArc.LivingBox(17, new AABB(6, -1, 0, 7, 2, 1));
        DripArc.Step step = DripArc.step(new Vec3(0, 1, 0.5), new Vec3(8, -2, 0), FLOOR,
                (from, to) -> List.of(beyond), INSIDE_THE_WORLD);

        assertTrue(step.living().isEmpty(), "the floor stands nearer than the mob along the path");
        assertTrue(step.strike().isPresent(), "the drip should strike the floor");
    }

    @Test
    void aDripLeavingTheWorldUnstruckEndsLost() {
        DripArc.Step step = DripArc.step(new Vec3(0, WORLD_FLOOR + 0.05, 0), new Vec3(0, -0.1, 0),
                (from, to) -> Optional.empty(), INSIDE_THE_WORLD);

        assertTrue(step.lost(), "a drip falling out of the world should end");
        assertTrue(step.strike().isEmpty(), "a drip meeting no block strikes nothing");
    }

    @Test
    void eachTickMovesTheDripByItsVelocityAndGravityBendsIt() {
        Vec3 velocity = new Vec3(0.4, 0.1, -0.2);

        DripArc.Step step = DripArc.step(Vec3.ZERO, velocity, (from, to) -> Optional.empty(), INSIDE_THE_WORLD);

        assertEquals(velocity, step.position(), "the drip should move by its velocity");
        assertEquals(velocity.y - DripArc.GRAVITY, step.velocity().y, TOLERANCE, "gravity should bend the velocity");
        assertEquals(velocity.x, step.velocity().x, TOLERANCE, "gravity should leave the heading's level part");
    }

    @Test
    void aDripLaunchesAlongItsShareOfTheConeAtTheThrowSpeedAlone() {
        Vec3 axis = new Vec3(0, -0.5, 1);

        Vec3 launch = DripArc.launch(axis, 16, new CoatRay.ConeShare(0.7, 0.3), 0.6);

        Vec3 expected = StreamCone.launchDirection(axis, 16, 0.7, 0.3).scale(0.6);
        assertEquals(expected.x, launch.x, TOLERANCE, "x");
        assertEquals(expected.y, launch.y, TOLERANCE, "y");
        assertEquals(expected.z, launch.z, TOLERANCE, "z");
    }
}
