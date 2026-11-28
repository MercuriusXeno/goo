package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.ability.program.MortarPaving;
import com.mercuriusxeno.goo.network.MortarCarryPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Covers the client's store of the Mortar Level holds carry: each pile
 * holding layers answers the Mortar state of its layer count at its cell over
 * the disc, and an empty pile or an empty carry answers nothing to draw.
 * carried-mortar-stops-being-a-block-on-the-level-plane
 */
class CarriedMortarTest {

    private static final int HOLDER = 7;
    private static final ChannelAim.FacePlane PLANE = new ChannelAim.FacePlane(new BlockPos(0, 64, 0), Direction.UP);
    private static final BlockPos MIDDLE = new BlockPos(0, 64, 0);
    private static final int TWELVE = 12;
    private static final int THREE = 3;
    private static final int PILES = 9;
    private static final long NOW = 100;
    private static final BlockState[] MORTAR_OF = new BlockState[16];
    private static final IntFunction<BlockState> MORTAR_STATE = layers -> MORTAR_OF[layers];

    static {
        for (int layers = 0; layers < MORTAR_OF.length; layers++) {
            MORTAR_OF[layers] = mock(BlockState.class);
        }
    }

    @Test
    void eachPileHoldingLayersDrawsTheMortarOfItsCountAtItsCell() {
        CarriedMortar store = new CarriedMortar();
        List<Integer> piles = new ArrayList<>(Collections.nCopies(PILES, 0));
        piles.set(0, TWELVE);
        piles.set(1, THREE);
        store.accept(new MortarCarryPayload(HOLDER, PLANE, MIDDLE, piles), NOW);
        List<BlockPos> cells = MortarPaving.pileCells(PLANE, MIDDLE);
        assertEquals(Map.of(cells.get(0), MORTAR_OF[TWELVE], cells.get(1), MORTAR_OF[THREE]),
                store.drawn(MORTAR_STATE, NOW));
    }

    @Test
    void anEmptyCarryDrawsNothing() {
        CarriedMortar store = new CarriedMortar();
        List<Integer> full = Collections.nCopies(PILES, TWELVE);
        store.accept(new MortarCarryPayload(HOLDER, PLANE, MIDDLE, full), NOW);
        store.accept(new MortarCarryPayload(HOLDER, PLANE, MIDDLE, Collections.nCopies(PILES, 0)), NOW);
        assertEquals(Map.of(), store.drawn(MORTAR_STATE, NOW));
    }
}
