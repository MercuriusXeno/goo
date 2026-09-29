package com.mercuriusxeno.goo.block.gasket;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * A source answers a partner asking one drip at a time with its types by turns, and a
 * type it holds none of yields its turn (decision vat-round-robins-gasket-send). The
 * source and partner are mocks, since a fluid stack needs data components a unit JVM
 * never binds.
 */
class GasketPusherTurnTest {

    private static final int ASKS = 4;
    private static final int PLENTY = 1_000;
    private static final FluidResource BLAZE = mock(FluidResource.class);
    private static final FluidResource UNSTABLE = mock(FluidResource.class);

    @Test
    void twoTypesAreSentByTurns() {
        assertEquals(List.of(BLAZE, UNSTABLE, BLAZE, UNSTABLE), sendsOverFourAsks(PLENTY, PLENTY));
    }

    @Test
    void onceOneTypeRunsOutEveryAskAnswersTheOther() {
        assertEquals(List.of(BLAZE, UNSTABLE, UNSTABLE, UNSTABLE), sendsOverFourAsks(1, PLENTY));
    }

    @Test
    void aSourceHoldingOneTypeAnswersItEveryAsk() {
        assertEquals(List.of(UNSTABLE, UNSTABLE, UNSTABLE, UNSTABLE), sendsOverFourAsks(0, PLENTY));
    }

    /**
     * Pushes four times from a two-slot source, blaze first and unstable second, to a
     * partner asking one drip per push, the way a tap's intake asks.
     *
     * @param blazeHeld    the mB of blaze the source holds
     * @param unstableHeld the mB of unstable the source holds
     * @return the type each push sent, in order
     */
    @SuppressWarnings("unchecked")
    private static List<FluidResource> sendsOverFourAsks(int blazeHeld, int unstableHeld) {
        FluidResource[] types = {BLAZE, UNSTABLE};
        int[] held = {blazeHeld, unstableHeld};
        ResourceHandler<FluidResource> source = mock(ResourceHandler.class);
        when(source.size()).thenReturn(types.length);
        when(source.getResource(anyInt())).thenAnswer(call -> {
            int slot = call.<Integer>getArgument(0);
            return held[slot] > 0 ? types[slot] : FluidResource.EMPTY;
        });
        when(source.getAmountAsLong(anyInt())).thenAnswer(call -> (long) held[call.<Integer>getArgument(0)]);
        when(source.extract(anyInt(), any(), anyInt(), any())).thenAnswer(call -> {
            int slot = call.<Integer>getArgument(0);
            int taken = Math.min(held[slot], call.<Integer>getArgument(2));
            held[slot] -= taken;
            return taken;
        });

        AtomicInteger room = new AtomicInteger();
        List<FluidResource> sent = new ArrayList<>();
        ResourceHandler<FluidResource> partner = mock(ResourceHandler.class,
                withSettings().extraInterfaces(GasketDemand.class));
        when(((GasketDemand) partner).statedDemand(any())).thenAnswer(call -> OptionalInt.of(room.get()));
        when(partner.insert(any(), anyInt(), any())).thenAnswer(call -> {
            sent.add(call.getArgument(0));
            room.set(0);
            return call.getArgument(1);
        });

        GasketPusher pusher = new GasketPusher(source, () -> null, () -> null, () -> null,
                () -> null, () -> { }, () -> null);
        for (int ask = 0; ask < ASKS; ask++) {
            room.set(1);
            pusher.pushViaHandler(partner);
        }
        return sent;
    }
}
