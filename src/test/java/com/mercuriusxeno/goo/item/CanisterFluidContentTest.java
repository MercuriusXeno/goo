package com.mercuriusxeno.goo.item;

import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A canister's content holds several goo types on one shared capacity, keeps a
 * vanilla fluid to itself, and reads the single-fluid shape older canisters saved, over a stub fluid codec
 * (decision canisters-hold-more-than-one-goo-type). StampedGoo stands the goo and
 * water resources, since the goo fluid is unregistered in the unit suite.
 */
class CanisterFluidContentTest {

    private static final int CAPACITY = 1000;
    private static final int FIRST_VOLUME = 300;
    private static final int OVERFLOW = 5000;
    private static final int SAVED_VOLUME = 250;
    private static final String BLAZE_NAME = "goo:blaze";

    private StampedGoo goo;
    private FluidResource blaze;
    private FluidResource rock;
    private FluidResource water;

    @BeforeEach
    void stampGooTypes() {
        goo = new StampedGoo();
        blaze = goo.resource(GooTypes.BLAZE);
        rock = goo.resource(GooTypes.ROCK);
        water = goo.vanilla();
    }

    @AfterEach
    void releaseGooFluids() {
        goo.close();
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
