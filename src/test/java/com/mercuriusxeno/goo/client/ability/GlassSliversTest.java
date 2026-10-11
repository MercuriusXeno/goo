package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A splinter of a shattered knife flies out, falls, tumbles, and shrinks and
 * fades to nothing over its life (decision shards-sling-then-morph-to-flechettes).
 */
class GlassSliversTest {

    private static final double TOLERANCE = 1e-9;
    private static final GlassSlivers.Sliver SLIVER = new GlassSlivers.Sliver(new Vec3(0, 5, 0),
            new Vec3(0.05, 0.08, 0), new Vec3(0, 0, 1), 100);

    @Test
    void aSplinterFliesOutAndFallsUnderGravity() {
        assertEquals(new Vec3(0, 5, 0), SLIVER.positionAt(0));
        Vec3 later = SLIVER.positionAt(10);
        assertEquals(0.5, later.x, TOLERANCE);
        assertEquals(5 + 0.8 - 0.5 * GlassSlivers.GRAVITY * 100, later.y, TOLERANCE);
        assertTrue(SLIVER.positionAt(GlassSlivers.LIFE_TICKS).y < 5, "the splinter ends below where it broke");
    }

    @Test
    void aSplinterShrinksAndFadesToNothingOverItsLife() {
        assertEquals(1f, SLIVER.leftAt(0), 1e-6f);
        assertEquals(0.5f, SLIVER.leftAt(GlassSlivers.LIFE_TICKS / 2), 1e-6f);
        assertEquals(0f, SLIVER.leftAt(GlassSlivers.LIFE_TICKS), 1e-6f);
    }

    @Test
    void aSplinterTumblesAboutItsAxis() {
        Vec3 first = SLIVER.headingAt(0);
        Vec3 later = SLIVER.headingAt(3);
        assertEquals(1, later.length(), TOLERANCE);
        assertEquals(0, later.dot(new Vec3(0, 0, 1)), TOLERANCE);
        assertTrue(first.distanceTo(later) > 0.5, "the splinter turns as it falls");
    }
}
