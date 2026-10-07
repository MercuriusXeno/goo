package com.mercuriusxeno.goo.block.tap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each block counts its drips apart, per dimension, until its count starts
 * over (decision petrify-drip-calcifies-and-grows-dripstone).
 */
class TapDripCountsTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));
    private static final BlockPos GRAVEL = new BlockPos(3, 64, 2);

    @Test
    void dripsAccumulatePerBlockAndDimension() {
        TapDripCounts counts = new TapDripCounts();
        counts.countDrip(OVERWORLD, GRAVEL);
        assertEquals(2, counts.countDrip(OVERWORLD, GRAVEL));
        assertEquals(1, counts.countDrip(NETHER, GRAVEL));
        assertEquals(1, counts.countDrip(OVERWORLD, GRAVEL.above()));
    }

    @Test
    void aResetStartsTheCountOver() {
        TapDripCounts counts = new TapDripCounts();
        counts.countDrip(OVERWORLD, GRAVEL);
        counts.reset(OVERWORLD, GRAVEL);
        assertEquals(1, counts.countDrip(OVERWORLD, GRAVEL));
    }
}
