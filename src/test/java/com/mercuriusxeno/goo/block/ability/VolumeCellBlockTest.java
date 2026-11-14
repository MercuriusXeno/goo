package com.mercuriusxeno.goo.block.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.block.CompiledStateDefinition;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The volume cell: one plain block implementation holding one to fifteen
 * layers, bound to slime and to slick, it stands its layers' fifteenths of a
 * block high and draws a model per layer count, a layer costs a fifteenth of
 * its laying ability's cost, a broken cell gives back the goo its layers
 * cost, and neighbouring cells of one goo read as one film
 * (decisions coat-is-a-green-slime-film-over-the-blocks-own-texture,
 * world-goo-fills-a-block-space-in-one-to-fifteen-layers, slime-and-slick-are-one-volume-cell-tech,
 * breaking-slime-drops-weird-goo-worth-its-layers).
 */
class VolumeCellBlockTest {

    private static final String BLOCKSTATE_JSON = "/assets/goo/blockstates/slime_coat.json";
    /** The layers a whole block of slime holds, stated here so the test checks the block rather than echoing it. */
    private static final int FIFTEEN = 15;

    /**
     * A cell's film continues into a cell of its own goo alone, so slime and
     * slick standing side by side keep their own faces and drips
     * (decision diagnose-and-correct-the-slick-film-seams).
     */
    @Nested
    class Faces {

        private final VolumeCellBlock slime = mock(VolumeCellBlock.class, CALLS_REAL_METHODS);

        @Test
        void aCellContinuesIntoACellOfItsGoo() {
            BlockState sameGoo = mock(BlockState.class);
            when(sameGoo.is(slime)).thenReturn(true);

            assertTrue(slime.continuesInto(sameGoo));
        }

        @Test
        void aCellStopsAtACellOfAnotherGoo() {
            BlockState otherGoo = mock(BlockState.class);
            when(otherGoo.getBlock()).thenReturn(mock(VolumeCellBlock.class));

            assertFalse(slime.continuesInto(otherGoo));
        }
    }

    /**
     * Slime and slick are one volume cell implementation, each registered in
     * GooBlocks bound to its goo type, with no block entity
     * (decisions slime-and-slick-are-one-volume-cell-tech, each-goo-type-ships-a-world-goo-on-the-volume-cell).
     */
    @Nested
    class OneImplementation {

        @Test
        void gooBlocksRegistersSlimeAndSlickAsTheOneVolumeCell() throws ReflectiveOperationException {
            assertEquals(List.of(VolumeCellBlock.class, VolumeCellBlock.class),
                    List.of(registeredBlockClass("SLIME_COAT"), registeredBlockClass("KINETIC_SLICK")));
        }

        @Test
        void slimeIsBoundToWeirdAndSlickToKinetic() {
            assertEquals(List.of(GooTypes.WEIRD, GooTypes.KINETIC),
                    List.of(CellBinding.SLIME.gooType(), CellBinding.SLICK.gooType()));
        }

        @Test
        void slimeBouncesAndSlickRamps() {
            assertEquals(List.of(CellSurface.BOUNCE, CellSurface.RAMP),
                    List.of(CellBinding.SLIME.surface(), CellBinding.SLICK.surface()));
        }

        /**
         * The block class a GooBlocks field registers, read from the field's
         * declared type without initializing GooBlocks, whose registrations
         * need the game's registries.
         */
        private static Class<?> registeredBlockClass(String field) throws ReflectiveOperationException {
            Class<?> gooBlocks = Class.forName("com.mercuriusxeno.goo.registry.GooBlocks", false,
                    VolumeCellBlockTest.class.getClassLoader());
            ParameterizedType deferred = (ParameterizedType) gooBlocks.getDeclaredField(field).getGenericType();
            return (Class<?>) deferred.getActualTypeArguments()[0];
        }
    }

    /**
     * The cell is a plain block whose whole state is its block state: one to
     * fifteen layers and a drip flag per side, with no block entity
     * (decision world-goo-fills-a-block-space-in-one-to-fifteen-layers).
     */
    @Nested
    class PlainBlockState {

        @Test
        void theStateHoldsFifteenLayerCountsAndASideFlagEach() {
            List<String> flag = List.of("true", "false");
            List<String> layerCounts = IntStream.rangeClosed(1, FIFTEEN).mapToObj(Integer::toString).toList();

            assertEquals(Map.of("north", flag, "east", flag, "south", flag, "west", flag, "layers", layerCounts),
                    CompiledStateDefinition.of(VolumeCellBlock.class));
        }

        @Test
        void theCellHoldsNoBlockEntity() {
            assertFalse(EntityBlock.class.isAssignableFrom(VolumeCellBlock.class));
        }
    }

    @Nested
    class Layers {

        @Test
        void oneLayerStandsAFifteenthHigh() {
            assertEquals(1.0 / FIFTEEN, VolumeCellBlock.layerBox(1).max(Direction.Axis.Y), 1e-9);
        }

        @Test
        void eightLayersStandEightFifteenthsHigh() {
            assertEquals(8.0 / FIFTEEN, VolumeCellBlock.layerBox(8).max(Direction.Axis.Y), 1e-9);
        }

        @Test
        void fifteenLayersStandAWholeBlockHigh() {
            assertEquals(1.0, VolumeCellBlock.layerBox(FIFTEEN).max(Direction.Axis.Y), 1e-9);
        }

        @Test
        void theBlockstateDrawsAModelForEachLayerCount() throws IOException {
            Set<Integer> drawn = new TreeSet<>();
            for (JsonElement part : blockstateJson().getAsJsonArray("multipart")) {
                JsonObject when = part.getAsJsonObject().getAsJsonObject("when");
                if (when != null && when.size() == 1 && when.has("layers")) {
                    drawn.add(when.get("layers").getAsInt());
                }
            }

            assertEquals(IntStream.rangeClosed(1, FIFTEEN).boxed().collect(Collectors.toSet()), drawn);
        }

        private static JsonObject blockstateJson() throws IOException {
            return classpathJson(BLOCKSTATE_JSON);
        }
    }

    /**
     * A layer costs a fifteenth of Goop's cost whatever the workbench sets
     * that cost to, and a broken cell gives back that price per layer
     * (decisions world-goo-fills-a-block-space-in-one-to-fifteen-layers,
     * breaking-slime-drops-weird-goo-worth-its-layers).
     */
    @Nested
    class Reclaim {

        private static final int LAYERS = 3;
        private static final GooFraction A_COST = GooFraction.of(96);
        private static final GooFraction RETUNED_COST = GooFraction.of(160);

        @Test
        void aLayerCostsAFifteenthOfTheCost() {
            assertEquals(GooFraction.of(96).dividedBy(FIFTEEN), VolumeCellBlock.layerPrice(A_COST));
        }

        @Test
        void aRetunedCostRetunesTheLayersPrice() {
            assertEquals(GooFraction.of(160).dividedBy(FIFTEEN), VolumeCellBlock.layerPrice(RETUNED_COST));
        }

        @Test
        void aBrokenCellReturnsAFifteenthOfTheCostPerLayer() {
            assertEquals(LAYERS * 96 / FIFTEEN, VolumeCellBlock.reclaimed(LAYERS, A_COST));
        }

        @Test
        void aRetunedCostRetunesTheRefund() {
            assertEquals(LAYERS * 160 / FIFTEEN, VolumeCellBlock.reclaimed(LAYERS, RETUNED_COST));
        }

        /** goop-drains-per-cubic-meter-of-slime-placed: Goop's cost reads as a price per cubic meter of slime */
        @Test
        void goopsCostPricesACubicMeter() {
            assertEquals(Optional.of("%s/m³"), AbilityJson.decode("weird_goop").costFormula());
        }

        /** slick-drains-per-film-it-lays: Slick's cost reads as a price per cubic meter of slick */
        @Test
        void slicksCostPricesACubicMeter() {
            assertEquals(Optional.of("%s/m³"), AbilityJson.decode("kinetic_slick").costFormula());
        }
    }

    private static JsonObject classpathJson(String path) throws IOException {
        try (InputStream in = VolumeCellBlockTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, path + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
