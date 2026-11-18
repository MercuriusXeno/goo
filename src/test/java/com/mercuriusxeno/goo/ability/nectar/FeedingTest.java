package com.mercuriusxeno.goo.ability.nectar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Slime;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;

/**
 * The size a fed slime grows to: one vanilla step, 1 to 2 to 4, and no
 * further than the largest natural size; and what an animal eating Nectar
 * goo eats, a layer for each feeding.
 */
class FeedingTest {

    @Test
    void smallestSlimeGrowsToSizeTwo() {
        assertEquals(2, Feeding.grownSlimeSize(1));
    }

    @Test
    void sizeTwoSlimeGrowsToSizeFour() {
        assertEquals(4, Feeding.grownSlimeSize(2));
    }

    @Test
    void largestNaturalSlimeGrowsNoFurther() {
        assertEquals(Slime.MAX_NATURAL_SIZE, Feeding.grownSlimeSize(Slime.MAX_NATURAL_SIZE));
    }

    @Test
    void aFedBabyGrowsToAnAdult() {
        Animal calf = mock(Animal.class);
        when(calf.isBaby()).thenReturn(true);

        Feeding.feed(calf, null);

        verify(calf).setAge(Feeding.ADULT_AGE);
    }

    /**
     * What an animal reaching Nectar goo eats: a baby grows to an adult and
     * an adult ready to breed falls in love, each eating one layer, and an
     * animal neither feeds eats nothing.
     * each-feeding-eats-a-layer-of-nectar
     * nectar-goo-keeps-feeds-effects-as-a-universal-food
     */
    @Nested
    class NectarMeals {

        private static final int LAYERS = 5;
        private static final int BABY_AGE = -24000;
        /** An adult's age after breeding, waiting out its cooldown. */
        private static final int COOLING_DOWN_AGE = 6000;

        @Test
        void aBabyGrowsToAnAdultForALayer() {
            assertEquals(Optional.of(new Feeding.Meal(LAYERS - 1, Feeding.ADULT_AGE, false)),
                    Feeding.mealOf(LAYERS, BABY_AGE, false));
        }

        @Test
        void anAdultReadyToBreedFallsInLoveForALayer() {
            assertEquals(Optional.of(new Feeding.Meal(LAYERS - 1, Feeding.ADULT_AGE, true)),
                    Feeding.mealOf(LAYERS, Feeding.ADULT_AGE, true));
        }

        @Test
        void anAdultThatCannotBreedEatsNothing() {
            assertEquals(Optional.empty(), Feeding.mealOf(LAYERS, COOLING_DOWN_AGE, false));
        }
    }
}