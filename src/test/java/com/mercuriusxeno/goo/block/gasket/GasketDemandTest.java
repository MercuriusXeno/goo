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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * the power law of its capacity a container asks at rest, a link adding the demand
 * behind it, and the pusher sending the lesser of the demand its partner states and
 * what it holds. The vanilla bootstrap stands the
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
    class RestingDemand {

        @ParameterizedTest
        @ValueSource(ints = {1_000, 65_536, FAR_MORE_THAN_ASKED, 1 << 25})
        void gooRestsAtThePowerLawOfItsCapacity(int capacity) {
            FluidResource goo = resourceOf(mock(GooFluid.Source.class));
            assertEquals(GasketPushMath.taperRate(capacity, GasketPushMath.GOO_EXPONENT),
                    GasketDemand.restingDemand(goo, capacity, 0));
        }

        @ParameterizedTest
        @ValueSource(ints = {1_000, 65_536, FAR_MORE_THAN_ASKED, 1 << 25})
        void waterRestsAtThePowerLawOfItsCapacityAtTheWaterExponent(int capacity) {
            assertEquals(GasketPushMath.taperRate(capacity, GasketPushMath.WATER_EXPONENT),
                    GasketDemand.restingDemand(resourceOf(Fluids.WATER), capacity, 0));
        }

        @Test
        void aBiggerContainerPullsHarder() {
            FluidResource goo = resourceOf(mock(GooFluid.Source.class));
            assertTrue(GasketDemand.restingDemand(goo, 1 << 25, 0) > GasketDemand.restingDemand(goo, 1 << 20, 0));
        }

        @Test
        void aNearlyFullContainerAsksOnlyItsRoom() {
            FluidResource goo = resourceOf(mock(GooFluid.Source.class));
            assertEquals(LESS_THAN_ASKED, GasketDemand.restingDemand(goo, FAR_MORE_THAN_ASKED,
                    FAR_MORE_THAN_ASKED - LESS_THAN_ASKED));
        }

        @Test
        @SuppressWarnings("unchecked")
        void aReceiverStatingNoDemandRestsAtItsLargestTank() {
            FluidResource water = resourceOf(Fluids.WATER);
            ResourceHandler<FluidResource> plainContainer = mock(ResourceHandler.class);
            when(plainContainer.size()).thenReturn(1);
            when(plainContainer.isValid(0, water)).thenReturn(true);
            when(plainContainer.getCapacityAsLong(0, water)).thenReturn((long) FAR_MORE_THAN_ASKED);
            assertEquals(GasketDemand.restingDemand(water, FAR_MORE_THAN_ASKED, 0),
                    GasketDemand.demandOf(plainContainer, water));
        }
    }

    /**
     * A link asks its own resting demand plus its dependent's (decision relay-adds-dependent-ask-to-own).
     */
    @Nested
    class StackOnRest {

        @Test
        void aDemandPlacedBehindStacksOnTheLinksRest() {
            assertEquals(ASKED + LESS_THAN_ASKED, GasketDemand.stackOnRest(OptionalInt.of(ASKED), LESS_THAN_ASKED));
        }

        @Test
        void nothingBehindRests() {
            assertEquals(LESS_THAN_ASKED, GasketDemand.stackOnRest(OptionalInt.empty(), LESS_THAN_ASKED));
        }

        @Test
        void aConsumerAskingNothingLeavesTheLinkAtRest() {
            assertEquals(LESS_THAN_ASKED, GasketDemand.stackOnRest(OptionalInt.of(0), LESS_THAN_ASKED));
        }

        @Test
        void aFullLinkPassesItsDependentsDemandAlone() {
            assertEquals(ASKED, GasketDemand.stackOnRest(OptionalInt.of(ASKED), 0));
        }

        @Test
        void aStackPastTheIntRangeHoldsAtItsMost() {
            assertEquals(Integer.MAX_VALUE, GasketDemand.stackOnRest(OptionalInt.of(Integer.MAX_VALUE), ASKED));
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
