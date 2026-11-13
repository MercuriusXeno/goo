package com.mercuriusxeno.goo.block.ability;

import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A prism a combo lit emits the full block light, 15, and an unlit one emits none.
 */
class PrismBlockTest {

    private static BlockState prism(boolean lit) {
        BlockState state = mock(BlockState.class);
        when(state.getValue(PrismBlock.LIT)).thenReturn(lit);
        return state;
    }

    @Test
    void aLitPrismEmitsFullLightAndAnUnlitOneNone() {
        assertEquals(List.of(15, 0), List.of(PrismBlock.lightLevel(prism(true)), PrismBlock.lightLevel(prism(false))));
    }
}
