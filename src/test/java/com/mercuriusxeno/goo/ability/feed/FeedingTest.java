package com.mercuriusxeno.goo.ability.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.entity.monster.Slime;
import org.junit.jupiter.api.Test;

/**
 * The size a fed slime grows to: one vanilla step, 1 to 2 to 4, and no
 * further than the largest natural size.
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
}
