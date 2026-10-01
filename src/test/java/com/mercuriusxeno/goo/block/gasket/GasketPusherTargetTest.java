package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.registry.GooTickets;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A pusher aims its capability cache at the registry's location for its target gasket,
 * not at the position the tuner stored on its partner, and follows that location when
 * it moves (decision diagnose-then-fix-capability-lifetimes). The level and ticket
 * controller are mocks and BlockCapabilityCache.create is stubbed, so only the pusher's
 * targeting runs; the vanilla bootstrap stands the registries the gasket types read.
 */
class GasketPusherTargetTest {

    private static final BlockPos LINKED_AT = new BlockPos(10, 64, 10);
    private static final BlockPos MOVED_TO = new BlockPos(90, 64, 90);
    private static final int SLOT = 4;

    private final UUID transmitter = UUID.randomUUID();
    private final UUID receiver = UUID.randomUUID();
    private final ResourceKey<Level> here = mockDimension();
    private GasketRegistry registry;
    private ServerLevel level;
    private TicketController savedTickets;

    @BeforeAll
    static void standRegistries() {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<Level> mockDimension() {
        return mock(ResourceKey.class);
    }

    @BeforeEach
    void linkTransmitterToReceiver() {
        savedTickets = GooTickets.gasketChunks;
        GooTickets.gasketChunks = mock(TicketController.class);
        registry = new GasketRegistry();
        registry.link(transmitter, receiver);
        level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(here);
    }

    @AfterEach
    void restoreTickets() {
        GooTickets.gasketChunks = savedTickets;
    }

    @SuppressWarnings("unchecked")
    private GasketPusher pusherLinkedAt(BlockPos storedPartnerPos) {
        return new GasketPusher(mock(ResourceHandler.class), () -> transmitter,
                () -> new GasketPartner(storedPartnerPos, SLOT), () -> level, () -> BlockPos.ZERO,
                () -> { }, () -> registry);
    }

    private void receiverStandsAt(BlockPos pos) {
        registry.updateLocation(receiver, new GasketLocation(here, pos, true, SLOT));
    }

    @Nested
    class RebuildCache {

        @Test
        void aimsAtTheRegistryLocationOverTheStoredPartnerPosition() {
            receiverStandsAt(MOVED_TO);
            try (MockedStatic<BlockCapabilityCache> caches = mockStatic(BlockCapabilityCache.class)) {
                pusherLinkedAt(LINKED_AT).rebuildCache();

                caches.verify(() -> BlockCapabilityCache.create(any(), eq(level), eq(MOVED_TO), eq(receiver)));
            }
        }

        @Test
        void aimsAtTheStoredPartnerPositionWhenTheRegistryHoldsNoLocation() {
            try (MockedStatic<BlockCapabilityCache> caches = mockStatic(BlockCapabilityCache.class)) {
                pusherLinkedAt(LINKED_AT).rebuildCache();

                caches.verify(() -> BlockCapabilityCache.create(any(), eq(level), eq(LINKED_AT), eq(receiver)));
            }
        }
    }

    @Nested
    class Tick {

        @Test
        void rebuildsOnTheNewLocationOnceTheReceiverMoves() {
            receiverStandsAt(LINKED_AT);
            try (MockedStatic<BlockCapabilityCache> caches = mockStatic(BlockCapabilityCache.class)) {
                GasketPusher pusher = pusherLinkedAt(LINKED_AT);
                pusher.rebuildCache();

                receiverStandsAt(MOVED_TO);
                pusher.tick();

                caches.verify(() -> BlockCapabilityCache.create(any(), eq(level), eq(MOVED_TO), eq(receiver)));
            }
        }
    }

    @Nested
    class CurrentTargetPos {

        @Test
        void answersNoneForAReceiverInAnotherDimension() {
            registry.updateLocation(receiver, new GasketLocation(mockDimension(), MOVED_TO, true, SLOT));

            assertNull(pusherLinkedAt(LINKED_AT).currentTargetPos());
        }
    }
}
