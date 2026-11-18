package com.mercuriusxeno.goo.block.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.block.CompiledStateDefinition;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mercuriusxeno.goo.throwing.DripFall;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
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
        private static final BlockPos AT = new BlockPos(0, 64, 0);

        /**
         * The sloped top is drawing only: a block of 12 collides as the box
         * twelve fifteenths of a block tall across the whole block, with a
         * block of 6 beside it and alone
         * (decision goo-block-top-slopes-to-the-average-with-the-adjoining-block).
         */
        @Test
        void aBlocksCollisionStaysTheFlatBoxAtItsVolume() {
            BlockState twelve = mock(BlockState.class);
            when(twelve.getBlock()).thenReturn(slime);
            when(twelve.getValue(VolumeCellBlock.LAYERS)).thenReturn(12);
            BlockState six = mock(BlockState.class);
            when(six.is(slime)).thenReturn(true);
            when(six.getValue(VolumeCellBlock.LAYERS)).thenReturn(6);
            BlockGetter beside = mock(BlockGetter.class);
            when(beside.getBlockState(any())).thenReturn(mock(BlockState.class));
            when(beside.getBlockState(AT.east())).thenReturn(six);
            BlockGetter alone = mock(BlockGetter.class);
            when(alone.getBlockState(any())).thenReturn(mock(BlockState.class));

            doReturn(true).when(slime).onAFloor(any(), any());

            for (BlockGetter level : List.of(beside, alone)) {
                AABB box = slime.getCollisionShape(twelve, level, AT, CollisionContext.empty()).bounds();
                assertEquals(new AABB(0, 0, 0, 1, 12 / 15.0, 1), box);
            }
        }

        /**
         * Goo is no wall: a full block of goo has a sturdy face, yet goo
         * clings to it no more than to air, so no goo hangs beside other goo
         * over a void; a stone block's face holds it.
         * drip-striking-a-side-sticks-or-runs-by-adhesion
         */
        @Test
        void gooClingsToNoGooButToAStoneFace() {
            BlockState fullGoo = mock(BlockState.class);
            when(fullGoo.getBlock()).thenReturn(mock(VolumeCellBlock.class));
            when(fullGoo.isFaceSturdy(any(), any(), any())).thenReturn(true);
            BlockState stone = mock(BlockState.class);
            when(stone.isFaceSturdy(any(), any(), any())).thenReturn(true);
            BlockGetter besideGoo = mock(BlockGetter.class);
            when(besideGoo.getBlockState(AT.east())).thenReturn(fullGoo);
            BlockGetter besideStone = mock(BlockGetter.class);
            when(besideStone.getBlockState(AT.east())).thenReturn(stone);

            assertFalse(VolumeCellBlock.clingsTo(besideGoo, AT, Direction.EAST), "goo clings to a full block of goo");
            assertTrue(VolumeCellBlock.clingsTo(besideStone, AT, Direction.EAST), "goo lets go of a stone face");
        }

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

        /** molten-burns-mobs-as-hard-as-lava-and-a-hit-spends-a-layer: Molten's cell is the volume cell, burning */
        @Test
        void gooBlocksRegistersSlimeSlickMoltenAndShatterAsTheOneVolumeCell() throws ReflectiveOperationException {
            assertEquals(List.of(VolumeCellBlock.class, VolumeCellBlock.class, MoltenCellBlock.class,
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
            assertEquals(List.of(FluidFooting.NONE, FluidFooting.NONE, FluidFooting.LAVA),
                    List.of(CellBinding.SLIME.fluidFooting(), CellBinding.SLICK.fluidFooting(),
                            CellBinding.MOLTEN.fluidFooting()));
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
         * The state holds a volume of 1 to 15 layers and each of four sides bare,
         * attached or runny; no state holds a volume of 0
         * (decision goo-cell-holds-a-fill-and-a-state-per-side).
         */
        @Test
        void theStateHoldsAVolumeAndAStatePerSide() {
            List<String> side = List.of("bare", "attached", "runny");
            List<String> fills = IntStream.rangeClosed(1, FIFTEEN).mapToObj(Integer::toString).toList();

            assertEquals(Map.of("north", side, "east", side, "south", side, "west", side, "ceiling",
                    List.of("true", "false"), "layers", fills), CompiledStateDefinition.of(VolumeCellBlock.class));
        }

        @Test
        void theCellHoldsNoBlockEntity() {
            assertFalse(EntityBlock.class.isAssignableFrom(VolumeCellBlock.class));
        }
    }

    /**
     * A block on a floor stands; a block with no floor stands on the walls it
     * is attached to, each with a face to hold it, and while a runny layer
     * passes through it (decision goo-cell-holds-a-fill-and-a-state-per-side).
     */
    @Nested
    class Standing {

        @Test
        void aBlockOnAFloorStands() {
            assertTrue(VolumeCellBlock.stands(true, List.of(), false));
            assertTrue(VolumeCellBlock.stands(true, List.of(false), false));
        }

        @Test
        void aBlockWithNoFloorStandsOnAWallItIsAttachedTo() {
            assertTrue(VolumeCellBlock.stands(false, List.of(true), false));
        }

        @Test
        void aBlockWithNoFloorStandsWhileARunnyLayerPassesThroughIt() {
            assertTrue(VolumeCellBlock.stands(false, List.of(), true));
        }

        @Test
        void aBlockWithNoFloorFallsWhenAWallItIsAttachedToIsGone() {
            assertFalse(VolumeCellBlock.stands(false, List.of(true, false), false));
            assertFalse(VolumeCellBlock.stands(false, List.of(), false));
        }
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

        /**
         * The block's volume is all the goo it holds, whichever sides are
         * attached or runny: no side keeps layers of its own
         * (decision goo-cell-holds-a-fill-and-a-state-per-side).
         */
        @ParameterizedTest
        @ValueSource(ints = {0, 1, 4})
        void theVolumeIsAllTheGooWhicheverSidesAreAttached(int attachedSides) {
            BlockState block = mock(BlockState.class);
            when(block.getValue(VolumeCellBlock.LAYERS)).thenReturn(HELD_VOLUME);
            List<Direction> sides = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
            for (int side = 0; side < sides.size(); side++) {
                when(block.getValue(VolumeCellBlock.coatProperty(sides.get(side))))
                        .thenReturn(side < attachedSides ? SideCoat.ATTACHED : SideCoat.RUNNY);
            }

            assertEquals(HELD_VOLUME, VolumeCellBlock.layersHeld(block));
        }

        private static final int HELD_VOLUME = 6;

        /**
         * A block attached to a ceiling holds its volume, the ceiling keeping no
         * layer of its own (decision goo-cell-holds-a-fill-and-a-state-per-side).
         */
        @Test
        void aBlockAttachedToACeilingHoldsItsVolume() {
            BlockState block = mock(BlockState.class);
            when(block.getValue(VolumeCellBlock.LAYERS)).thenReturn(CEILING_VOLUME);
            when(block.getValue(VolumeCellBlock.CEILING)).thenReturn(true);

            assertEquals(CEILING_VOLUME, VolumeCellBlock.layersHeld(block));
        }

        private static final int CEILING_VOLUME = 3;

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

    /**
     * A broken Rime cell gives back frost goo, each layer a fifteenth of
     * Rime's cost as frost_rime.json ships it
     * (decision iceborn-is-dropped-for-rime-a-frost-goo-that-keeps-what-is-around-it-frozen).
     */
    @Nested
    class RimeReclaim {

        private static final int LAYERS = 7;

        @Test
        void rimeIsFrostGooLaidByFrostsRime() {
            assertEquals(List.of(GooTypes.FROST, AbilityJson.idOf("frost_rime.json")),
                    List.of(CellBinding.RIME.gooType(), CellBinding.RIME.layingAbility()));
        }

        @Test
        void aBrokenRimeCellDropsFrostGooAtFifteenthsOfRimesCost() {
            GooFraction cost = AbilityJson.decode("frost_rime").cost().exactIn(GooValueTable.EMPTY);

            assertEquals(Optional.of(new VolumeCellBlock.Reclaim(GooTypes.FROST,
                            cost.times(LAYERS).dividedBy(FIFTEEN).floor())),
                    VolumeCellBlock.reclaimedBy(CellBinding.RIME, LAYERS, cost));
        }
    }

    /**
     * A broken Updraft cell gives back typhoon goo, each layer a fifteenth of
     * Updraft's cost as typhoon_updraft.json ships it.
     * updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it
     */
    @Nested
    class UpdraftReclaim {

        private static final int LAYERS = 7;

        @Test
        void updraftIsTyphoonGooLaidByTyphoonsUpdraft() {
            assertEquals(List.of(GooTypes.TYPHOON, AbilityJson.idOf("typhoon_updraft.json")),
                    List.of(CellBinding.UPDRAFT.gooType(), CellBinding.UPDRAFT.layingAbility()));
        }

        @Test
        void aBrokenUpdraftCellDropsTyphoonGooAtFifteenthsOfUpdraftsCost() {
            GooFraction cost = AbilityJson.decode("typhoon_updraft").cost().exactIn(GooValueTable.EMPTY);

            assertEquals(Optional.of(new VolumeCellBlock.Reclaim(GooTypes.TYPHOON,
                            cost.times(LAYERS).dividedBy(FIFTEEN).floor())),
                    VolumeCellBlock.reclaimedBy(CellBinding.UPDRAFT, LAYERS, cost));
        }
    }

    /**
     * A broken Infest cell gives back nether goo, each layer a fifteenth of
     * Infest's cost as nether_infest.json ships it.
     * infest-is-nethers-goo-the-decay-effect-localized-around-the-goo
     */
    @Nested
    class InfestReclaim {

        private static final int LAYERS = 8;

        @Test
        void infestIsNetherGooLaidByNethersInfest() {
            assertEquals(List.of(GooTypes.NETHER, AbilityJson.idOf("nether_infest.json")),
                    List.of(CellBinding.INFEST.gooType(), CellBinding.INFEST.layingAbility()));
        }

        @Test
        void aBrokenInfestCellDropsNetherGooAtFifteenthsOfInfestsCost() {
            GooFraction cost = AbilityJson.decode("nether_infest").cost().exactIn(GooValueTable.EMPTY);

            assertEquals(Optional.of(new VolumeCellBlock.Reclaim(GooTypes.NETHER,
                            cost.times(LAYERS).dividedBy(FIFTEEN).floor())),
                    VolumeCellBlock.reclaimedBy(CellBinding.INFEST, LAYERS, cost));
        }
    }

    /**
     * A broken Vines cell gives back leaf goo, each layer a fifteenth of
     * Vines' cost as leaf_vines.json ships it.
     * vines-is-sprayed-slime-that-entangles-what-tries-to-cross
     */
    @Nested
    class VinesReclaim {

        private static final int LAYERS = 7;

        @Test
        void vinesIsLeafGooLaidByLeafsVines() {
            assertEquals(List.of(GooTypes.LEAF, AbilityJson.idOf("leaf_vines.json")),
                    List.of(CellBinding.VINES.gooType(), CellBinding.VINES.layingAbility()));
        }

        @Test
        void aBrokenVinesCellDropsLeafGooAtFifteenthsOfVinesCost() {
            GooFraction cost = AbilityJson.decode("leaf_vines").cost().exactIn(GooValueTable.EMPTY);

            assertEquals(Optional.of(new VolumeCellBlock.Reclaim(GooTypes.LEAF,
                            cost.times(LAYERS).dividedBy(FIFTEEN).floor())),
                    VolumeCellBlock.reclaimedBy(CellBinding.VINES, LAYERS, cost));
        }
    }

    /**
     * A broken Mortar cell gives back rock goo, each layer a fifteenth of
     * Mortar's cost as rock_mortar.json ships it, read here from a fixture's
     * $mundane_block of 7 rock (decision no-test-asserts-a-shipped-base-value).
     * mortar-is-rocks-residual-a-petrification-and-hardening-goo
     * layer-of-rock-goo-is-a-stone-block
     */
    @Nested
    class MortarReclaim {

        private static final int LAYERS = 6;

        @Test
        void mortarIsRockGooLaidByRocksMortar() {
            assertEquals(List.of(GooTypes.ROCK, AbilityJson.idOf("rock_mortar.json")),
                    List.of(CellBinding.MORTAR.gooType(), CellBinding.MORTAR.layingAbility()));
        }

        @Test
        void aBrokenMortarCellDropsRockGooAtFifteenthsOfMortarsCost() {
            GooFraction cost = AbilityJson.decode("rock_mortar").cost().exactIn(GooBaseValuesPreview.registry("""
                    {"_constants": {"mundane_block": {"rock": 7}}}
                    """).table());

            assertEquals(Optional.of(new VolumeCellBlock.Reclaim(GooTypes.ROCK,
                            cost.times(LAYERS).dividedBy(FIFTEEN).floor())),
                    VolumeCellBlock.reclaimedBy(CellBinding.MORTAR, LAYERS, cost));
        }
    }

    /**
     * A broken Wisps cell gives back glow goo, each layer a fifteenth of
     * Wisps' cost as glow_wisps.json ships it
     * (decision glow-world-goo-is-named-wisps-and-takes-the-holds-place).
     */
    @Nested
    class WispsReclaim {

        private static final int LAYERS = 9;

        @Test
        void wispsIsGlowGooLaidByGlowsWisps() {
            assertEquals(List.of(GooTypes.GLOW, AbilityJson.idOf("glow_wisps.json")),
                    List.of(CellBinding.WISPS.gooType(), CellBinding.WISPS.layingAbility()));
        }

        @Test
        void aBrokenWispsCellDropsGlowGooAtFifteenthsOfWispsCost() {
            GooFraction cost = AbilityJson.decode("glow_wisps").cost().exactIn(GooValueTable.EMPTY);

            assertEquals(Optional.of(new VolumeCellBlock.Reclaim(GooTypes.GLOW,
                            cost.times(LAYERS).dividedBy(FIFTEEN).floor())),
                    VolumeCellBlock.reclaimedBy(CellBinding.WISPS, LAYERS, cost));
        }
    }

    /**
     * A broken Leech cell gives back hex goo, each layer a fifteenth of
     * Leech's cost as hex_leech.json ships it.
     * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
     */
    @Nested
    class LeechReclaim {

        private static final int LAYERS = 7;

        @Test
        void leechIsHexGooLaidByHexsLeech() {
            assertEquals(List.of(GooTypes.HEX, AbilityJson.idOf("hex_leech.json")),
                    List.of(CellBinding.LEECH.gooType(), CellBinding.LEECH.layingAbility()));
        }

        @Test
        void aBrokenLeechCellDropsHexGooAtFifteenthsOfLeechsCost() {
            GooFraction cost = AbilityJson.decode("hex_leech").cost().exactIn(GooValueTable.EMPTY);

            assertEquals(Optional.of(new VolumeCellBlock.Reclaim(GooTypes.HEX,
                            cost.times(LAYERS).dividedBy(FIFTEEN).floor())),
                    VolumeCellBlock.reclaimedBy(CellBinding.LEECH, LAYERS, cost));
        }
    }

    /**
     * How a block running down a wall divides its volume: goo running down
     * a wall with nothing under it draws on that wall, never as a floor in
     * midair; the same block over a floor keeps its fill on the floor.
     * runny-goo-runs-down-the-wall-to-the-first-top-below
     * attached-goo-draws-per-attachment-and-divides-its-volume
     */
    @Nested
    class RunnyShares {

        private static final int LAYERS = 3;
        private static final int NO_COHESION = 0;

        private BlockState runningDownTheWestWall() {
            BlockState state = mock(BlockState.class);
            when(state.getBlock()).thenReturn(mock(VolumeCellBlock.class));
            when(state.getValue(VolumeCellBlock.LAYERS)).thenReturn(LAYERS);
            when(state.getValue(VolumeCellBlock.NORTH)).thenReturn(SideCoat.BARE);
            when(state.getValue(VolumeCellBlock.EAST)).thenReturn(SideCoat.BARE);
            when(state.getValue(VolumeCellBlock.SOUTH)).thenReturn(SideCoat.BARE);
            when(state.getValue(VolumeCellBlock.WEST)).thenReturn(SideCoat.RUNNY);
            when(state.getValue(VolumeCellBlock.CEILING)).thenReturn(false);
            return state;
        }

        @Test
        void gooRunningDownAWallInMidairDrawsOnTheWallAndNoFloor() {
            GooShares shares = VolumeCellBlock.sharesOf(runningDownTheWestWall(), false, NO_COHESION);

            assertEquals(new GooShares(0, Map.of(Direction.WEST, (double) LAYERS)), shares);
        }

        /**
         * operator UAT 2026-11-17: the client crashed drawing a goo block Vacuum broke, the block already air
         */
        @Test
        void aBlockNoLongerGooSharesNothing() {
            BlockState air = mock(BlockState.class);
            when(air.getBlock()).thenReturn(mock(net.minecraft.world.level.block.Block.class));
            when(air.getValue(any())).thenThrow(new IllegalArgumentException("air holds no layers"));

            assertEquals(new GooShares(0, Map.of()), VolumeCellBlock.sharesOf(air, false, NO_COHESION));
        }

        @Test
        void gooRunningDownAWallOntoAFloorKeepsItsFillOnTheFloor() {
            GooShares shares = VolumeCellBlock.sharesOf(runningDownTheWestWall(), true, NO_COHESION);

            assertEquals(new GooShares(LAYERS, Map.of()), shares);
        }
    }

    /**
     * How much a block drains into goo of its type below: a block no wall
     * holds drains whole, as far as the room below goes; one a wall holds
     * keeps its hold there and drains only what slips past it.
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     */
    @Nested
    class DrainDown {

        private static final int LAYERS = 5;
        private static final int NOTHING_HELD = 0;
        /** Jelly's hold on one wall: a layer plus one for its cohesion of 1. */
        private static final int ONE_WALL_HOLD = 2;
        private static final int ROOMY = 12;
        private static final int TIGHT = 1;

        @Test
        void aBlockNoWallHoldsDrainsWhole() {
            assertEquals(LAYERS, VolumeCellBlock.layersDrained(LAYERS, NOTHING_HELD, ROOMY));
        }

        @Test
        void aBlockAWallHoldsKeepsItsHoldAndDrainsTheRest() {
            assertEquals(LAYERS - ONE_WALL_HOLD, VolumeCellBlock.layersDrained(LAYERS, ONE_WALL_HOLD, ROOMY));
        }

        @Test
        void aBlockWithinItsHoldDrainsNothing() {
            assertEquals(0, VolumeCellBlock.layersDrained(ONE_WALL_HOLD, ONE_WALL_HOLD, ROOMY));
        }

        @Test
        void theDrainStopsAtTheRoomBelow() {
            assertEquals(TIGHT, VolumeCellBlock.layersDrained(LAYERS, NOTHING_HELD, TIGHT));
        }
    }

    /**
     * A broken Nectar cell gives back jelly goo, each layer a fifteenth of
     * Nectar's cost as jelly_nectar.json ships it.
     * nectar-is-sprayed-goo-with-a-guided-throw-near-a-mob
     */
    @Nested
    class NectarReclaim {

        private static final int LAYERS = 5;

        @Test
        void nectarIsJellyGooLaidByJellysNectar() {
            assertEquals(List.of(GooTypes.JELLY, AbilityJson.idOf("jelly_nectar.json")),
                    List.of(CellBinding.NECTAR.gooType(), CellBinding.NECTAR.layingAbility()));
        }

        @Test
        void aBrokenNectarCellDropsJellyGooAtFifteenthsOfNectarsCost() {
            GooFraction cost = AbilityJson.decode("jelly_nectar").cost().exactIn(GooValueTable.EMPTY);

            assertEquals(Optional.of(new VolumeCellBlock.Reclaim(GooTypes.JELLY,
                            cost.times(LAYERS).dividedBy(FIFTEEN).floor())),
                    VolumeCellBlock.reclaimedBy(CellBinding.NECTAR, LAYERS, cost));
        }
    }

    /**
     * A broken Phase cell gives back quantum goo, each layer a fifteenth of
     * Phase's cost as quantum_phase.json ships it.
     * phase-goo-puts-what-stands-in-it-out-of-phase
     */
    @Nested
    class PhaseReclaim {

        private static final int LAYERS = 11;

        @Test
        void phaseIsQuantumGooLaidByQuantumsPhase() {
            assertEquals(List.of(GooTypes.QUANTUM, AbilityJson.idOf("quantum_phase.json")),
                    List.of(CellBinding.PHASE.gooType(), CellBinding.PHASE.layingAbility()));
        }

        @Test
        void aBrokenPhaseCellDropsQuantumGooAtFifteenthsOfPhasesCost() {
            GooFraction cost = AbilityJson.decode("quantum_phase").cost().exactIn(GooValueTable.EMPTY);

            assertEquals(Optional.of(new VolumeCellBlock.Reclaim(GooTypes.QUANTUM,
                            cost.times(LAYERS).dividedBy(FIFTEEN).floor())),
                    VolumeCellBlock.reclaimedBy(CellBinding.PHASE, LAYERS, cost));
        }
    }

    /**
     * A broken Signal cell gives back pulse goo, each layer a fifteenth of
     * Signal's cost as pulse_signal.json ships it.
     * extend-is-dropped-for-signal-an-in-world-pulse-goo
     */
    @Nested
    class SignalReclaim {

        private static final int LAYERS = 11;

        @Test
        void signalIsPulseGooLaidByPulsesSignal() {
            assertEquals(List.of(GooTypes.PULSE, AbilityJson.idOf("pulse_signal.json")),
                    List.of(CellBinding.SIGNAL.gooType(), CellBinding.SIGNAL.layingAbility()));
        }

        @Test
        void aBrokenSignalCellDropsPulseGooAtFifteenthsOfSignalsCost() {
            GooFraction cost = AbilityJson.decode("pulse_signal").cost().exactIn(GooValueTable.EMPTY);

            assertEquals(Optional.of(new VolumeCellBlock.Reclaim(GooTypes.PULSE,
                            cost.times(LAYERS).dividedBy(FIFTEEN).floor())),
                    VolumeCellBlock.reclaimedBy(CellBinding.SIGNAL, LAYERS, cost));
        }
    }

    /**
     * A broken Spikes cell gives back metal goo, each layer a fifteenth of
     * Spikes' cost as metal_spikes.json ships it.
     * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
     */
    @Nested
    class SpikesReclaim {

        private static final int LAYERS = 11;

        @Test
        void spikesIsMetalGooLaidByMetalsSpikes() {
            assertEquals(List.of(GooTypes.METAL, AbilityJson.idOf("metal_spikes.json")),
                    List.of(CellBinding.SPIKES.gooType(), CellBinding.SPIKES.layingAbility()));
        }

        @Test
        void aBrokenSpikesCellDropsMetalGooAtFifteenthsOfSpikesCost() {
            GooFraction cost = AbilityJson.decode("metal_spikes").cost().exactIn(GooValueTable.EMPTY);

            assertEquals(Optional.of(new VolumeCellBlock.Reclaim(GooTypes.METAL,
                            cost.times(LAYERS).dividedBy(FIFTEEN).floor())),
                    VolumeCellBlock.reclaimedBy(CellBinding.SPIKES, LAYERS, cost));
        }
    }

    private static JsonObject classpathJson(String path) throws IOException {
        try (InputStream in = VolumeCellBlockTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, path + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
