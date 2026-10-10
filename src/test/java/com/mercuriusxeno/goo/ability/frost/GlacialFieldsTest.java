package com.mercuriusxeno.goo.ability.frost;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A glacial field holds the points within its radius of its prism while
 * the prism keeps renewing it, in its own level only, and lapses once the
 * prism stops.
 */
class GlacialFieldsTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));
    private static final BlockPos PRISM = new BlockPos(10, 64, 10);
    private static final double RADIUS = 5;
    private static final long NOW = 500L;
    private static final Vec3 INSIDE = new Vec3(13.5, 64.5, 10.5);
    private static final Vec3 OUTSIDE = new Vec3(17.5, 64.5, 10.5);

    private static GlacialFields renewed() {
        GlacialFields fields = new GlacialFields();
        fields.renew(OVERWORLD, PRISM, RADIUS, NOW);
        return fields;
    }

    @Test
    void aRenewedFieldHoldsAPointWithinItsRadius() {
        assertTrue(renewed().holds(OVERWORLD, INSIDE, NOW));
    }

    @Test
    void aFieldLeavesAPointPastItsRadius() {
        assertFalse(renewed().holds(OVERWORLD, OUTSIDE, NOW));
    }

    @Test
    void aFieldHoldsOnlyInItsOwnLevel() {
        assertFalse(renewed().holds(NETHER, INSIDE, NOW));
    }

    @Test
    void aFieldLapsesOnceItsPrismStopsRenewingIt() {
        GlacialFields fields = renewed();
        assertTrue(fields.holds(OVERWORLD, INSIDE, NOW + GlacialFields.LAPSE_TICKS));
        assertFalse(fields.holds(OVERWORLD, INSIDE, NOW + GlacialFields.LAPSE_TICKS + 1));
    }
}
