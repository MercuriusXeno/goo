package com.mercuriusxeno.goo.block.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValueTable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The slime coat as a layered cell: it stands its layers' eighths of a block
 * high and draws a model per layer count, a broken cell gives back the goo
 * its layers cost, and neighbouring coats read as one film, a coat hiding
 * its face toward another coat as a slime block does toward a slime block
 * (decisions coat-is-a-green-slime-film-over-the-blocks-own-texture,
 * slime-is-a-layered-cell-that-levels-out, breaking-slime-drops-weird-goo-worth-its-layers).
 */
class SlimeCoatBlockTest {

    private static final String BLOCKSTATE_JSON = "/assets/goo/blockstates/slime_coat.json";

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

    @Nested
    class Layers {

        @Test
        void oneLayerStandsAnEighthHigh() {
            assertEquals(1.0 / 8, SlimeCoatBlock.layerBox(1).max(Direction.Axis.Y), 1e-9);
        }

        @Test
        void fourLayersStandHalfABlockHigh() {
            assertEquals(0.5, SlimeCoatBlock.layerBox(4).max(Direction.Axis.Y), 1e-9);
        }

        @Test
        void eightLayersStandAWholeBlockHigh() {
            assertEquals(1.0, SlimeCoatBlock.layerBox(8).max(Direction.Axis.Y), 1e-9);
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

            assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8), drawn);
        }

        private static JsonObject blockstateJson() throws IOException {
            return classpathJson(BLOCKSTATE_JSON);
        }
    }

    @Nested
    class Reclaim {

        @Test
        void oneLayerReclaimsTheGooOneLayerCosts() {
            assertEquals(SlimeCoatBlock.MB_PER_LAYER, SlimeCoatBlock.reclaimed(1));
        }

        @Test
        void eightLayersReclaimEightLayersOfGoo() {
            assertEquals(8 * SlimeCoatBlock.MB_PER_LAYER, SlimeCoatBlock.reclaimed(8));
        }

        @Test
        void aLayerCostsBetweenSixAndTwentyFourMb() {
            assertTrue(SlimeCoatBlock.MB_PER_LAYER >= 6 && SlimeCoatBlock.MB_PER_LAYER <= 24,
                    "a coat layer costs " + SlimeCoatBlock.MB_PER_LAYER + " mb");
        }

        /** goop-drains-per-cubic-meter-of-slime-placed */
        @Test
        void goopsCostPricesACubicMeterAndALayerReclaimsItsEighth() {
            AbilityDefinition goop = AbilityJson.decode("weird_goop");

            assertEquals(Optional.of("%s/m³"), goop.costFormula());
            GooFraction layerPrice = goop.cost().exactIn(GooValueTable.EMPTY).dividedBy(SlimeCoatBlock.MAX_LAYERS);
            assertEquals(0, GooFraction.of(SlimeCoatBlock.reclaimed(1)).compareTo(layerPrice),
                    "a layer costs " + layerPrice + " and reclaims " + SlimeCoatBlock.reclaimed(1));
        }
    }

    private static JsonObject classpathJson(String path) throws IOException {
        try (InputStream in = SlimeCoatBlockTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, path + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
