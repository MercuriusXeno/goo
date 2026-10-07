package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Petrify's tap step waits for its drip count, then grows dripstone under a
 * dripstone-capable block and starts the count over
 * (decision petrify-drip-calcifies-and-grows-dripstone).
 */
class PetrifyDripStepTest {

    private static final TagKey<Block> GROWS =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("goo", "grows_dripstone"));
    private static final int DRIPS = 8;
    private static final BlockPos LANDING = new BlockPos(2, 64, 2);

    private static DripHost landingAfter(int drips, boolean growsDripstone) {
        DripHost host = mock(DripHost.class);
        when(host.kind()).thenReturn(HostKind.TAP);
        when(host.position()).thenReturn(LANDING);
        when(host.countDrip()).thenReturn(drips);
        when(host.blockIn(eq(LANDING), any())).thenReturn(growsDripstone);
        return host;
    }

    private static void land(DripHost host) {
        ProgramBehavior.forHost(List.of(new PetrifyDripStep(Identifier.fromNamespaceAndPath("goo", "calcify"),
                DRIPS, GROWS)), HostKind.TAP).tick(host);
    }

    @Test
    void dripsShortOfTheCountDoNothing() {
        DripHost host = landingAfter(DRIPS - 1, true);

        land(host);

        verify(host, never()).growStalactite();
        verify(host, never()).resetDrips();
    }

    @Test
    void theCountOnDripstoneGrowsATipAndStartsOver() {
        DripHost host = landingAfter(DRIPS, true);

        land(host);

        verify(host).growStalactite();
        verify(host).resetDrips();
        verify(host, never()).transformBlock(any(), any());
    }
}
