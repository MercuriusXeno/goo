package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.FlameShape;
import com.mercuriusxeno.goo.client.ability.FireField.Jet;
import com.mercuriusxeno.goo.client.ability.FireField.Wall;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flames' fire field: the jet's cone and, where a block cuts the look off, its
 * damped bounce off the face, climbing as it slows and sliding along a
 * ceiling, both inside the box the shader marches
 * (decision flames-pixelated-fire-stream).
 */
class FireFieldTest {

    private static final double DELTA = 1e-9;
    private static final double TAN = 0.1763;
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    /** A wall facing back toward a jet heading south. */
    private static final Vec3 FACING_NORTH = new Vec3(0, 0, -1);
    private static final Jet GLANCING = new Jet(Vec3.ZERO, new Vec3(1, 0, 1).normalize(),
            new Vec3(-1, 0, 1).normalize(), TAN);
    private static final Wall WALL = new Wall(3, new Vec3(1, 0, 1).normalize().scale(3), Direction.NORTH);

    @Test
    void aGlancingHitKeepsItsPushAlongTheFaceAndPeelsOffItSoftly() {
        Vec3 bounce = FlameShape.bounceAxis(new Vec3(1, 0, 1).normalize(), FACING_NORTH);
        Vec3 expected = new Vec3(1, 0, -FlameShape.RESTITUTION).normalize();
        assertEquals(expected.x, bounce.x, DELTA);
        assertEquals(expected.z, bounce.z, DELTA);
    }

    @Test
    void aHeadOnHitBouncesStraightBack() {
        assertEquals(-1, FlameShape.bounceAxis(SOUTH, FACING_NORTH).z, DELTA);
    }

    @Test
    void aHitAlongTheFaceSkimsOnUnturned() {
        assertEquals(1, FlameShape.bounceAxis(new Vec3(1, 0, 0), FACING_NORTH).x, DELTA);
    }

    @Test
    void underACeilingTheBounceSlidesAlongTheUndersideRatherThanClimbingThroughIt() {
        assertEquals(0, FlameShape.climbAlong(new Vec3(0, -1, 0)).length(), DELTA);
        assertEquals(new Vec3(0, 1, 0), FlameShape.climbAlong(FACING_NORTH));
        assertEquals(new Vec3(0, 1, 0), FlameShape.climbAlong(new Vec3(0, 1, 0)));
    }

    @Test
    void theBounceClimbsMoreTheFartherItRuns() {
        FireField field = new FireField(GLANCING, new FireStream.JetSpan(0, 6), WALL);
        double near = field.bouncePoint(0.5).y;
        double far = field.bouncePoint(1).y;
        assertTrue(near > 0 && far > near * 3, near + " vs " + far);
    }

    @Test
    void theBoxHoldsTheGloveTheWallAndTheBounce() {
        FireField field = new FireField(GLANCING, new FireStream.JetSpan(0, 6), WALL);
        Vec3[] box = field.box();
        double bounceRuns = (6 - 3) * FlameShape.KEEP;
        for (Vec3 point : new Vec3[] {Vec3.ZERO, WALL.at(), field.bouncePoint(bounceRuns)}) {
            assertTrue(point.x > box[0].x && point.x < box[1].x, point + " outside on x");
            assertTrue(point.y > box[0].y && point.y < box[1].y, point + " outside on y");
            assertTrue(point.z > box[0].z && point.z < box[1].z, point + " outside on z");
        }
    }

    @Test
    void underACeilingTheBoxHoldsTheFireHangingBelowAndAroundTheHit() {
        Jet upward = new Jet(Vec3.ZERO, new Vec3(1, 1, 0).normalize(), new Vec3(0, 0, 1), TAN);
        Vec3 hit = new Vec3(1, 1, 0).normalize().scale(3);
        FireField field = new FireField(upward, new FireStream.JetSpan(0, 6),
                new Wall(3, hit, Direction.DOWN));
        Vec3[] box = field.box();
        double radius = (3 + (6 - 3) * FlameShape.KEEP * FlameShape.SPLAY) * TAN;
        assertTrue(box[0].y < hit.y - radius, "the hanging body below the ceiling falls outside the box");
        assertTrue(box[0].x < hit.x - radius && box[1].z > hit.z + radius, "the spread around the hit falls outside");
    }

    @Test
    void aJetBurnsUntilItsTailPassesItsFront() {
        assertTrue(new FireField(GLANCING, new FireStream.JetSpan(0, 2), null).burns());
        assertFalse(new FireField(GLANCING, new FireStream.JetSpan(2, 2), null).burns());
        assertTrue(new FireField(GLANCING, new FireStream.JetSpan(3.5, 6), WALL).burns());
    }

    @Test
    void theUniformBlockFillsItsTenRows() {
        ByteBuffer out = ByteBuffer.allocate(FireField.BYTES);
        new FireField(GLANCING, new FireStream.JetSpan(0, 6), WALL).pack(out, new Vec3(100.25, 64, -3.5), true);
        assertEquals(FireField.BYTES, out.position());
    }
}
