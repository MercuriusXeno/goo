package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * An unmaking tap's drips count on per block while the block stands
 * unchanged, and start over on a changed or unmade block
 * (decision unmake-drip-dissolves-the-block-below).
 */
class TapUnmakeDripsTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));
    private static final BlockPos BELOW = new BlockPos(1, 2, 3);
    private final BlockState cobblestone = mock(BlockState.class);
    private final BlockState stone = mock(BlockState.class);

    @Test
    void dripsOnAnUnchangedBlockCountOn() {
        TapUnmakeDrips drips = new TapUnmakeDrips();
        drips.count(OVERWORLD, BELOW, cobblestone);
        drips.count(OVERWORLD, BELOW, cobblestone);

        assertEquals(3, drips.count(OVERWORLD, BELOW, cobblestone));
    }

    @Test
    void aChangedBlockStartsOver() {
        TapUnmakeDrips drips = new TapUnmakeDrips();
        drips.count(OVERWORLD, BELOW, cobblestone);
        drips.count(OVERWORLD, BELOW, cobblestone);

        assertEquals(1, drips.count(OVERWORLD, BELOW, stone));
    }

    @Test
    void anUnmadeBlockStartsOver() {
        TapUnmakeDrips drips = new TapUnmakeDrips();
        drips.count(OVERWORLD, BELOW, cobblestone);
        drips.forget(OVERWORLD, BELOW);

        assertEquals(1, drips.count(OVERWORLD, BELOW, cobblestone));
    }

    @Test
    void eachLevelCountsItsOwn() {
        TapUnmakeDrips drips = new TapUnmakeDrips();
        drips.count(OVERWORLD, BELOW, cobblestone);

        assertEquals(1, drips.count(NETHER, BELOW, cobblestone));
    }
}
