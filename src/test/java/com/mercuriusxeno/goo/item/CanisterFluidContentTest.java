package com.mercuriusxeno.goo.item;

import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * A canister's content holds several goo types on one shared capacity, keeps a
 * vanilla fluid to itself, and reads the single-fluid shape older canisters saved, over a stub fluid codec
 * (decision canisters-hold-more-than-one-goo-type). Goo resources are mocks whose
 * type GooFluids answers through a static mock, since the goo fluid is unregistered
 * in the unit suite; the water resource is a mock GooFluids answers no type for.
 */
class CanisterFluidContentTest {

    private static final int CAPACITY = 1000;
    private static final int FIRST_VOLUME = 300;
    private static final int OVERFLOW = 5000;
    private static final int SAVED_VOLUME = 250;
    private static final String BLAZE_NAME = "goo:blaze";

    private final FluidResource blaze = mock(FluidResource.class);
    private final FluidResource rock = mock(FluidResource.class);
    private final FluidResource water = mock(FluidResource.class);
    private MockedStatic<GooFluids> gooFluids;

    @BeforeAll
    static void standVanillaFluids() {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    @BeforeEach
    void stampGooTypes() {
        Map<FluidResource, ResourceKey<GooTypeDefinition>> types = Map.of(blaze, GooTypes.BLAZE, rock, GooTypes.ROCK);
        gooFluids = mockStatic(GooFluids.class);
        gooFluids.when(() -> GooFluids.keyOf(any())).thenAnswer(call -> types.get(call.<FluidResource>getArgument(0)));
    }

    @AfterEach
    void releaseGooFluids() {
        gooFluids.close();
    }

    @Nested
    class SharedCapacity {

        @Test
        void aSecondGooTypeTakesTheCapacityLessTheFirstTypesVolume() {
            CanisterFluidContent first = CanisterFluidContent.EMPTY.withCappedAdd(blaze, FIRST_VOLUME, CAPACITY);
            int accepted = first.cappedAddAmount(rock, OVERFLOW, CAPACITY);
            CanisterFluidContent both = first.withCappedAdd(rock, OVERFLOW, CAPACITY);

            assertEquals(CAPACITY - FIRST_VOLUME, accepted);
            assertEquals(List.of(new CanisterFluidContent.Portion(blaze, FIRST_VOLUME),
                    new CanisterFluidContent.Portion(rock, CAPACITY - FIRST_VOLUME)), both.portions());
        }

        @Test
        void aFullCanisterTakesNoFurtherType() {
            CanisterFluidContent full = CanisterFluidContent.EMPTY.withCappedAdd(blaze, CAPACITY, CAPACITY);
            assertEquals(0, full.cappedAddAmount(rock, 1, CAPACITY));
        }

        @Test
        void removingOneTypeLeavesTheOtherStanding() {
            CanisterFluidContent both = CanisterFluidContent.of(blaze, FIRST_VOLUME).withCappedAdd(rock, 1, CAPACITY);
            CanisterFluidContent left = both.withRemoved(rock, OVERFLOW);
            assertEquals(List.of(new CanisterFluidContent.Portion(blaze, FIRST_VOLUME)), left.portions());
        }
    }

    @Nested
    class VanillaFluidsStayAlone {

        @Test
        void gooRefusesToJoinWater() {
            assertFalse(CanisterFluidContent.of(water, FIRST_VOLUME).canAccept(blaze));
        }

        @Test
        void waterRefusesToJoinGoo() {
            assertFalse(CanisterFluidContent.of(blaze, FIRST_VOLUME).canAccept(water));
        }

        @Test
        void waterTakesMoreWater() {
            assertTrue(CanisterFluidContent.of(water, FIRST_VOLUME).canAccept(water));
        }
    }

    @Nested
    class SavedShape {

        @Test
        void theSingleFluidShapeLoadsWithItsOneTypeAndVolume() {
            Codec<FluidResource> byName = Codec.STRING.xmap(name -> blaze, resource -> BLAZE_NAME);
            JsonObject saved = new JsonObject();
            saved.addProperty("fluid", BLAZE_NAME);
            saved.addProperty("amount", SAVED_VOLUME);

            CanisterFluidContent loaded = CanisterFluidContent.codecOver(byName).parse(JsonOps.INSTANCE, saved)
                    .getOrThrow();

            assertEquals(List.of(new CanisterFluidContent.Portion(blaze, SAVED_VOLUME)), loaded.portions());
        }
    }
}
