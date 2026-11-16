package com.mercuriusxeno.goo.block.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.block.CompiledStateDefinition;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mercuriusxeno.goo.throwing.DripFall;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
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
        void gooBlocksRegistersSlimeSlickMoltenAndShatterAsTheOneVolumeCell() throws ReflectiveOperationException {
            assertEquals(List.of(VolumeCellBlock.class, VolumeCellBlock.class, VolumeCellBlock.class,
                            VolumeCellBlock.class),
                    List.of(registeredBlockClass("SLIME_COAT"), registeredBlockClass("KINETIC_SLICK"),
                            registeredBlockClass("BLAZE_MOLTEN"), registeredBlockClass("CRYSTAL_SHATTER")));
        }

        @Test
        void slimeIsBoundToWeirdSlickToKineticAndMoltenToBlaze() {
            assertEquals(List.of(GooTypes.WEIRD, GooTypes.KINETIC, GooTypes.BLAZE),
                    List.of(CellBinding.SLIME.gooType(), CellBinding.SLICK.gooType(), CellBinding.MOLTEN.gooType()));
        }

        @Test
        void slimeBouncesSlickRampsAndMoltenPools() {
            assertEquals(List.of(CellSurface.BOUNCE, CellSurface.RAMP, CellSurface.POOL),
                    List.of(CellBinding.SLIME.surface(), CellBinding.SLICK.surface(), CellBinding.MOLTEN.surface()));
        }

        /** molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava: Molten alone pools on lava */
        @Test
        void moltenAlonePoolsOnLava() {
            assertEquals(List.of(false, false, true), List.of(CellBinding.SLIME.liesOnLava(),
                    CellBinding.SLICK.liesOnLava(), CellBinding.MOLTEN.liesOnLava()));
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

        /**
         * The state holds a fill of 0 to 15 layers and each of four sides bare,
         * one layer or runny (decision goo-cell-holds-a-fill-and-a-state-per-side).
         */
        @Test
        void theStateHoldsAFillAndAStatePerSide() {
            List<String> side = List.of("bare", "film", "runny");
            List<String> fills = IntStream.rangeClosed(0, FIFTEEN).mapToObj(Integer::toString).toList();

            assertEquals(Map.of("north", side, "east", side, "south", side, "west", side, "layers", fills),
                    CompiledStateDefinition.of(VolumeCellBlock.class));
        }

        @Test
        void theCellHoldsNoBlockEntity() {
            assertFalse(EntityBlock.class.isAssignableFrom(VolumeCellBlock.class));
        }
    }

    /**
     * A cell holding fill stands on a top; a cell of fill 0 stands on its
     * films alone, each clinging to a wall with a face to hold it
     * (decision goo-cell-holds-a-fill-and-a-state-per-side).
     */
    @Nested
    class Standing {

        @Test
        void aFilledCellStandsOnATopAlone() {
            assertTrue(VolumeCellBlock.stands(LAYERS_ON_TOP, true, List.of(), false));
            assertFalse(VolumeCellBlock.stands(LAYERS_ON_TOP, false, List.of(true), false));
        }

        @Test
        void aCellOfFillZeroStandsOnAFilmClingingToItsWall() {
            assertTrue(VolumeCellBlock.stands(0, false, List.of(true), false));
        }

        @Test
        void aCellOfFillZeroStandsWhileARunnyLayerPassesThroughIt() {
            assertTrue(VolumeCellBlock.stands(0, false, List.of(), true));
        }

        @Test
        void aCellOfFillZeroFallsWhenAFilmsWallIsGone() {
            assertFalse(VolumeCellBlock.stands(0, false, List.of(true, false), false));
            assertFalse(VolumeCellBlock.stands(0, true, List.of(), false));
        }

        private static final int LAYERS_ON_TOP = 3;
    }

    /**
     * A layer starting a fall hands the visuals one launch: from the middle
     * of the cell it falls from at the height it leaves, to the surface of
     * the fill it lands on, over the ticks a drip takes to fall that far
     * (decisions goo-moving-downward-falls-as-drips, falling-layer-lands-when-its-drip-arrives).
     */
    @Nested
    class FallStart {

        private static final BlockPos FALLS_FROM = new BlockPos(6, 70, -4);
        private static final BlockPos LANDS_IN = new BlockPos(6, 64, -4);
        private static final double LEAVES_AT = 70.4;
        private static final int LANDING_FILL = 3;

        private final VolumeCellBlock.FallStart start = VolumeCellBlock.fallStart(FALLS_FROM, LEAVES_AT, LANDS_IN,
                LANDING_FILL);

        @Test
        void theLaunchLeavesTheMiddleOfTheCellAtTheHeightTheLayerLeaves() {
            assertEquals(new Vec3(6.5, LEAVES_AT, -3.5), start.from());
        }

        @Test
        void theLaunchEndsOnTheSurfaceOfTheFillItLandsOn() {
            assertEquals(64 + (double) LANDING_FILL / FIFTEEN, start.landingY(), 1e-9);
        }

        @Test
        void theLaunchTakesTheTicksADripTakesToFallThatFar() {
            assertEquals(DripFall.ticksToFall(LEAVES_AT - start.landingY()), start.ticks());
        }
    }

    /**
     * Each step a runny layer takes down its wall hands the visuals one drip
     * launch, on the wall face of the cell it leaves, set off the wall as the
     * streak is, ending on that face in the cell it enters or on the surface
     * of the fill it joins; a step into its own cell's fill draws none
     * (decision goo-moving-downward-falls-as-drips).
     */
    @Nested
    class WallDrips {

        private static final BlockPos HERE = new BlockPos(6, 70, -4);
        private static final int BELOW_FILL = 3;
        private final Vec3 onTheEastWall = new Vec3(7 - VolumeCellBlock.WALL_DRIP_OFF, 71, -3.5);

        @Test
        void aStepToTheCellBelowDrawsADripDownTheWallOneCell() {
            Optional<VolumeCellBlock.WallDrip> drip = VolumeCellBlock.wallDripOf(VolumeCellBlock.RunStep.RUN_DOWN,
                    HERE, Direction.EAST, BELOW_FILL);

            assertEquals(Optional.of(new VolumeCellBlock.WallDrip(onTheEastWall, 70)), drip);
        }

        @Test
        void aStepIntoTheFillBelowDrawsADripDownToItsSurface() {
            Optional<VolumeCellBlock.WallDrip> drip = VolumeCellBlock.wallDripOf(VolumeCellBlock.RunStep.JOIN_BELOW,
                    HERE, Direction.EAST, BELOW_FILL);

            assertEquals(Optional.of(new VolumeCellBlock.WallDrip(onTheEastWall,
                    69 + (double) BELOW_FILL / FIFTEEN)), drip);
        }

        @Test
        void aStepIntoItsOwnCellsFillDrawsNone() {
            assertEquals(Optional.empty(), VolumeCellBlock.wallDripOf(VolumeCellBlock.RunStep.JOIN_HERE, HERE,
                    Direction.EAST, BELOW_FILL));
        }
    }

    /**
     * A runny layer moves down its wall a cell each step and ends as fill on
     * the first top below, so every layer paid for stands as fill or film
     * (decision runny-goo-runs-down-the-wall-to-the-first-top-below).
     */
    @Nested
    class RunnyStep {

        @Test
        void aRunnyLayerInACellOnATopJoinsThatCellsFill() {
            assertEquals(VolumeCellBlock.RunStep.JOIN_HERE,
                    VolumeCellBlock.runStep(true, false, VolumeCellBlock.Below.BLOCKED));
        }

        @Test
        void aRunnyLayerWaitsOverAFullCellOnATop() {
            assertEquals(VolumeCellBlock.RunStep.WAIT,
                    VolumeCellBlock.runStep(true, true, VolumeCellBlock.Below.BLOCKED));
        }

        @Test
        void aRunnyLayerJoinsTheFillOfTheCellBelow() {
            assertEquals(VolumeCellBlock.RunStep.JOIN_BELOW,
                    VolumeCellBlock.runStep(false, false, VolumeCellBlock.Below.FILL_ROOM));
        }

        @Test
        void aRunnyLayerWaitsOverAFullCellBelow() {
            assertEquals(VolumeCellBlock.RunStep.WAIT,
                    VolumeCellBlock.runStep(false, false, VolumeCellBlock.Below.FILL_FULL));
        }

        @Test
        void aRunnyLayerMovesDownWhileTheWallGoesOn() {
            assertEquals(VolumeCellBlock.RunStep.RUN_DOWN,
                    VolumeCellBlock.runStep(false, false, VolumeCellBlock.Below.WALL_GOES_ON));
        }

        @Test
        void aRunnyLayerFallsWhereTheWallEnds() {
            assertEquals(VolumeCellBlock.RunStep.FALL,
                    VolumeCellBlock.runStep(false, false, VolumeCellBlock.Below.WALL_ENDS));
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

            assertEquals(IntStream.rangeClosed(0, FIFTEEN).boxed().collect(Collectors.toSet()), drawn);
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

        /**
         * A broken cell gives back every layer it holds, its fill and its films together
         * (decision goo-cell-holds-a-fill-and-a-state-per-side).
         */
        @Test
        void aBrokenCellReturnsItsFillAndItsFilms() {
            int held = VolumeCellBlock.layersHeld(LAYERS,
                    List.of(SideCoat.FILM, SideCoat.BARE, SideCoat.FILM, SideCoat.BARE));

            assertEquals((LAYERS + 2) * 96 / FIFTEEN, VolumeCellBlock.reclaimed(held, A_COST));
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

    /**
     * A broken Molten cell gives back blaze goo, each layer a fifteenth of
     * Molten's cost as blaze_molten.json ships it
     * (decision molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava).
     */
    @Nested
    class MoltenReclaim {

        private static final int LAYERS = 4;

        @Test
        void moltenIsLaidByBlazesMolten() {
            assertEquals(AbilityJson.idOf("blaze_molten.json"), CellBinding.MOLTEN.layingAbility());
        }

        @Test
        void aBrokenMoltenCellDropsBlazeGooAtFifteenthsOfMoltensCost() {
            GooFraction cost = AbilityJson.decode("blaze_molten").cost().exactIn(GooValueTable.EMPTY);

            assertEquals(Optional.of(new VolumeCellBlock.Reclaim(GooTypes.BLAZE,
                            cost.times(LAYERS).dividedBy(FIFTEEN).floor())),
                    VolumeCellBlock.reclaimedBy(CellBinding.MOLTEN, LAYERS, cost));
        }
    }

    /**
     * A broken Shatter cell gives back crystal goo, each layer a fifteenth of
     * Shatter's cost as crystal_shatter.json ships it
     * (decision shatter-is-splattered-goo-and-shards-form-over-the-splatters).
     */
    @Nested
    class ShatterReclaim {

        private static final int LAYERS = 6;

        @Test
        void shatterIsLaidByCrystalsShatter() {
            assertEquals(AbilityJson.idOf("crystal_shatter.json"), CellBinding.SHATTER.layingAbility());
        }

        @Test
        void aBrokenShatterCellDropsCrystalGooAtFifteenthsOfShattersCost() {
            GooFraction cost = AbilityJson.decode("crystal_shatter").cost().exactIn(GooValueTable.EMPTY);

            assertEquals(Optional.of(new VolumeCellBlock.Reclaim(GooTypes.CRYSTAL,
                            cost.times(LAYERS).dividedBy(FIFTEEN).floor())),
                    VolumeCellBlock.reclaimedBy(CellBinding.SHATTER, LAYERS, cost));
        }
    }

    /**
     * A broken Host cell gives back deep goo, each layer a fifteenth of
     * Host's cost as deep_host.json ships it
     * (decision host-is-a-sculky-slime-that-sculk-spreads-eagerly-through).
     */
    @Nested
    class HostReclaim {

        private static final int LAYERS = 7;

        @Test
        void hostIsDeepGooLaidByDeepsHost() {
            assertEquals(List.of(GooTypes.DEEP, AbilityJson.idOf("deep_host.json")),
                    List.of(CellBinding.HOST.gooType(), CellBinding.HOST.layingAbility()));
        }

        @Test
        void aBrokenHostCellDropsDeepGooAtFifteenthsOfHostsCost() {
            GooFraction cost = AbilityJson.decode("deep_host").cost().exactIn(GooValueTable.EMPTY);

            assertEquals(Optional.of(new VolumeCellBlock.Reclaim(GooTypes.DEEP,
                            cost.times(LAYERS).dividedBy(FIFTEEN).floor())),
                    VolumeCellBlock.reclaimedBy(CellBinding.HOST, LAYERS, cost));
        }
    }

    private static JsonObject classpathJson(String path) throws IOException {
        try (InputStream in = VolumeCellBlockTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, path + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
