package com.mercuriusxeno.goo.block.hub;

import com.mercuriusxeno.goo.ISidedProxy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The hub's pick-block target read through a mocked {@link ISidedProxy}, the fake that keeps
 * the proxy an interface (decision delete-dead-fold-mirrors). Only the paths that end before
 * the hub is read run here, so no block entity is built.
 */
class HubBlockHandlersTest {

    private static final BlockPos HUB_POS = new BlockPos(4, 64, 4);

    private final ISidedProxy proxy = mock(ISidedProxy.class);

    @BeforeEach
    void installProxy() {
        ISidedProxy.INSTANCE[0] = proxy;
    }

    @AfterEach
    void restoreServerProxy() {
        ISidedProxy.INSTANCE[0] = ISidedProxy.SERVER;
    }

    @Test
    void noCrosshairHitTargetsNoCanister() {
        when(proxy.getCrosshairHit()).thenReturn(null);

        assertTrue(HubBlockHandlers.resolveTargetedCanister(null, HUB_POS).isEmpty());
        verify(proxy).getCrosshairHit();
    }

    @Test
    void entityUnderCrosshairTargetsNoCanister() {
        when(proxy.getCrosshairHit()).thenReturn(mock(EntityHitResult.class));

        assertTrue(HubBlockHandlers.resolveTargetedCanister(null, HUB_POS).isEmpty());
    }

    @Test
    void crosshairOnAnotherBlockTargetsNoCanister() {
        BlockHitResult elsewhere = mock(BlockHitResult.class);
        when(elsewhere.getBlockPos()).thenReturn(HUB_POS.above());
        when(proxy.getCrosshairHit()).thenReturn(elsewhere);

        assertTrue(HubBlockHandlers.resolveTargetedCanister(null, HUB_POS).isEmpty());
    }
}
