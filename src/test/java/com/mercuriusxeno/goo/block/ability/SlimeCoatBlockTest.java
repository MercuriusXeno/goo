package com.mercuriusxeno.goo.block.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.block.CompiledStateDefinition;
import com.mercuriusxeno.goo.data.GooFraction;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The slime coat as a layered cell: a plain block holding one to fifteen
 * layers, it stands its layers' fifteenths of a block high and draws a model
 * per layer count, a layer costs a fifteenth of Goop's cost, a broken cell
 * gives back the goo its layers cost, and neighbouring coats read as one
 * film, a coat hiding its face toward another coat as a slime block does
 * toward a slime block (decisions coat-is-a-green-slime-film-over-the-blocks-own-texture,
 * world-goo-fills-a-block-space-in-one-to-fifteen-layers, breaking-slime-drops-weird-goo-worth-its-layers).
 */
class SlimeCoatBlockTest {

    private static final String BLOCKSTATE_JSON = "/assets/goo/blockstates/slime_coat.json";
    /** The layers a whole block of slime holds, stated here so the test checks the block rather than echoing it. */
    private static final int FIFTEEN = 15;

    @Nested
    class Faces {

        @Test
        void aCoatHidesItsFaceTowardAnotherCoat() {
            BlockState coat = mock(BlockState.class);
            when(coat.getBlock()).thenReturn(mock(SlimeCoatBlock.class));

            assertTrue(SlimeCoatBlock.hidesFaceToward(coat));
        }

        @Test
        void aCoatDrawsItsFaceTowardAnythingElse() {
            BlockState stone = mock(BlockState.class);
            when(stone.getBlock()).thenReturn(mock(Block.class));

            assertFalse(SlimeCoatBlock.hidesFaceToward(stone));
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
                    CompiledStateDefinition.of(SlimeCoatBlock.class));
        }

        @Test
        void theCellHoldsNoBlockEntity() {
            assertFalse(EntityBlock.class.isAssignableFrom(SlimeCoatBlock.class));
        }
    }

    @Nested
    class Layers {

        @Test
        void oneLayerStandsAFifteenthHigh() {
            assertEquals(1.0 / FIFTEEN, SlimeCoatBlock.layerBox(1).max(Direction.Axis.Y), 1e-9);
        }

        @Test
        void eightLayersStandEightFifteenthsHigh() {
            assertEquals(8.0 / FIFTEEN, SlimeCoatBlock.layerBox(8).max(Direction.Axis.Y), 1e-9);
        }

        @Test
        void fifteenLayersStandAWholeBlockHigh() {
            assertEquals(1.0, SlimeCoatBlock.layerBox(FIFTEEN).max(Direction.Axis.Y), 1e-9);
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
            assertEquals(GooFraction.of(96).dividedBy(FIFTEEN), SlimeCoatBlock.layerPrice(A_COST));
        }

        @Test
        void aRetunedCostRetunesTheLayersPrice() {
            assertEquals(GooFraction.of(160).dividedBy(FIFTEEN), SlimeCoatBlock.layerPrice(RETUNED_COST));
        }

        @Test
        void aBrokenCellReturnsAFifteenthOfTheCostPerLayer() {
            assertEquals(LAYERS * 96 / FIFTEEN, SlimeCoatBlock.reclaimed(LAYERS, A_COST));
        }

        @Test
        void aRetunedCostRetunesTheRefund() {
            assertEquals(LAYERS * 160 / FIFTEEN, SlimeCoatBlock.reclaimed(LAYERS, RETUNED_COST));
        }

        /** goop-drains-per-cubic-meter-of-slime-placed: Goop's cost reads as a price per cubic meter of slime */
        @Test
        void goopsCostPricesACubicMeter() {
            assertEquals(Optional.of("%s/m³"), AbilityJson.decode("weird_goop").costFormula());
        }
    }

    private static JsonObject classpathJson(String path) throws IOException {
        try (InputStream in = SlimeCoatBlockTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, path + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
