package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.fluid.GooFluid;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.fml.loading.FMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * A receiver's demand on the gasket network (decision receivers-demand-and-links-relay):
 * the power-law default a receiver stating none asks, and the pusher sending the lesser
 * of the demand its partner states and what it holds. The vanilla bootstrap stands the
 * water fluid; goo is a mocked goo fluid, and the handlers are mocks, since a fluid
 * stack needs data components a unit JVM never binds.
 */
class GasketDemandTest {

    private static final int FAR_MORE_THAN_ASKED = 1_000_000;
    private static final int ASKED = 5_000;
    private static final int LESS_THAN_ASKED = 300;

    /**
     * The vanilla bootstrap asks FML whether it runs in production, which a test JVM
     * cannot answer, so a stubbed loader answers it while the bootstrap stands the fluids.
     */
    @BeforeAll
    static void standVanillaFluids() {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    private static FluidResource resourceOf(Fluid fluid) {
        FluidResource resource = mock(FluidResource.class);
        when(resource.getFluid()).thenReturn(fluid);
        return resource;
    }

    @Nested
    class DefaultDemand {

        @ParameterizedTest
        @ValueSource(ints = {1, 1_000, 65_536, FAR_MORE_THAN_ASKED})
        void gooAsksTheTaperRateAtTheGooExponent(int sourceHolds) {
            FluidResource goo = resourceOf(mock(GooFluid.Source.class));
            assertEquals(GasketPushMath.taperRate(sourceHolds, GasketPushMath.GOO_EXPONENT),
                    GasketDemand.defaultDemand(goo, sourceHolds));
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 1_000, 65_536, FAR_MORE_THAN_ASKED})
        void waterAsksTheTaperRateAtTheWaterExponent(int sourceHolds) {
            assertEquals(GasketPushMath.taperRate(sourceHolds, GasketPushMath.WATER_EXPONENT),
                    GasketDemand.defaultDemand(resourceOf(Fluids.WATER), sourceHolds));
        }

        @Test
        @SuppressWarnings("unchecked")
        void aReceiverStatingNoDemandAsksTheDefault() {
            FluidResource water = resourceOf(Fluids.WATER);
            ResourceHandler<FluidResource> plainContainer = mock(ResourceHandler.class);
            assertEquals(GasketDemand.defaultDemand(water, FAR_MORE_THAN_ASKED),
                    GasketDemand.demandOf(plainContainer, water, FAR_MORE_THAN_ASKED));
        }
    }

    @Nested
    class PusherSendsTheDemand {

        @Test
        void aSourceHoldingMoreThanAskedSendsTheDemand() {
            assertSendsFrom(FAR_MORE_THAN_ASKED, ASKED);
        }

        @Test
        void aSourceHoldingLessThanAskedSendsWhatItHolds() {
            assertSendsFrom(LESS_THAN_ASKED, LESS_THAN_ASKED);
        }

        /**
         * One send from a source holding water to a partner demanding {@link #ASKED} a tick.
         *
         * @param sourceHolds the mB the source holds
         * @param expected    the mB the partner should receive
         */
        @SuppressWarnings("unchecked")
        private void assertSendsFrom(int sourceHolds, int expected) {
            FluidResource water = resourceOf(Fluids.WATER);
            ResourceHandler<FluidResource> source = mock(ResourceHandler.class);
            when(source.size()).thenReturn(1);
            when(source.getResource(0)).thenReturn(water);
            when(source.getAmountAsLong(0)).thenReturn((long) sourceHolds);
            when(source.extract(eq(0), eq(water), anyInt(), any())).thenAnswer(call -> call.getArgument(2));
            ResourceHandler<FluidResource> partner = mock(ResourceHandler.class,
                    withSettings().extraInterfaces(GasketDemand.class));
            when(((GasketDemand) partner).statedDemand(water)).thenReturn(OptionalInt.of(ASKED));
            when(partner.insert(eq(water), anyInt(), any())).thenAnswer(call -> call.getArgument(1));
            GasketPusher pusher = new GasketPusher(source, () -> null, () -> null, () -> null,
                    () -> null, () -> { }, () -> null);
            pusher.pushViaHandler(partner);
            verify(partner).insert(eq(water), eq(expected), any());
        }
    }
}
