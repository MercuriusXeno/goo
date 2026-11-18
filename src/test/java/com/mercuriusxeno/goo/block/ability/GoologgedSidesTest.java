package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where goo grows its next goologged plant: on a coated side bearing none
 * yet, spending the layers a plant costs, and nowhere, its layers kept, once
 * every coated side bears one or the goo cannot pay.
 * vines-goo-on-walls-places-goologged-vines-and-depletes
 */
class GoologgedSidesTest {

    private static final int LAYERS = 4;
    private static final int PER_PLANT = 1;

    @Test
    void theOneUnplantedCoatedSideTakesAPlantForItsLayers() {
        assertEquals(new GoologgedSides.Planting(Optional.of(Direction.EAST), LAYERS - PER_PLANT),
                GoologgedSides.nextPlanting(List.of(Direction.EAST), Set.of(), LAYERS, PER_PLANT));
    }

    @Test
    void everyCoatedSidePlantedTakesNoneAndKeepsTheLayers() {
        assertEquals(new GoologgedSides.Planting(Optional.empty(), LAYERS),
                GoologgedSides.nextPlanting(List.of(Direction.EAST, Direction.NORTH),
                        Set.of(Direction.EAST, Direction.NORTH), LAYERS, PER_PLANT));
    }

    @Test
    void theNextCoatedSideTakesThePlantOnceTheFirstBearsOne() {
        assertEquals(Optional.of(Direction.NORTH), GoologgedSides.nextPlanting(
                List.of(Direction.EAST, Direction.NORTH), Set.of(Direction.EAST), LAYERS, PER_PLANT).side());
    }

    @Test
    void gooShortOfAPlantsCostTakesNone() {
        assertEquals(new GoologgedSides.Planting(Optional.empty(), 1),
                GoologgedSides.nextPlanting(List.of(Direction.EAST), Set.of(), 1, 2));
    }
}
