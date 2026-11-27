package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.LavaTubes;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Gametests for regolith as dust: a regolith block with air beneath it falls as sand does and rests on the first solid
 * block below, and lunar's generation leaves no regolith over air, around a lava tube's mouth and over the caves under
 * the surface alike.
 * decision regolith-falls-like-sand-and-generates-on-solid-ground
 */
@RegistersGameTests
public final class RegolithTests {
    private static final String REGOLITH_FALLS_TO_SOLID_GROUND = "regolith_falls_to_solid_ground";
    private static final String LUNAR_LEAVES_NO_REGOLITH_OVER_AIR = "lunar_leaves_no_regolith_over_air";

    private static final BlockPos GAP_FLOOR = new BlockPos(5, 1, 5);
    private static final BlockPos GAP_REGOLITH = new BlockPos(5, 4, 5);
    private static final BlockPos PROPPED_FLOOR = new BlockPos(3, 1, 3);
    private static final BlockPos PROP = new BlockPos(3, 3, 3);
    private static final BlockPos PROPPED_REGOLITH = PROP.above();

    /** How many tube cells from the origin a search for a tube's mouth reaches each way. */
    private static final int CELL_REACH = 4;
    /** The sampled area reaches this many blocks each way from the tube's mouth. */
    private static final int SAMPLE_REACH = 32;
    /** A cave stands under the surface where air lies within this many blocks under a column's top block. */
    private static final int CAVE_DEPTH = 24;
    /**
     * The spots a ruin and a nest are each generated at as the place command generates them, since the gametest world
     * generates no structure of its own; spread so some fall on slopes the ground rises over.
     */
    private static final List<BlockPos> LANDMARK_SPOTS = List.of(new BlockPos(-1600, 0, 400),
            new BlockPos(-800, 0, -1600), new BlockPos(400, 0, -800), new BlockPos(1600, 0, 1600),
            new BlockPos(-400, 0, 2000), new BlockPos(2000, 0, -400), new BlockPos(-2000, 0, -2000),
            new BlockPos(800, 0, 2400));
    /** A landmark's surroundings are scanned this far out from its box. */
    private static final int LANDMARK_MARGIN = 3;
    private static final ResourceKey<Structure> RUIN = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_ruin"));
    private static final ResourceKey<Structure> NEST = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "eldritch_nest"));
    private static final String NO_LANDMARK = "%s should generate at %s";

    private static final String SHOULD_REST = "Regolith %s should rest at %s on the solid block below, reads %s";
    private static final String SHOULD_LEAVE = "Regolith should have fallen from %s, which reads %s";
    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_MOUTH = "No lava tube within %d cells of the origin opens on the surface";
    private static final String NO_CAVE = "The area sampled around the tube's mouth at %s should hold a cave under the "
            + "surface";
    private static final String OVER_AIR = "Lunar should leave no regolith over air; %d regolith blocks around the "
            + "tube's mouth at %s stand over air, first %s";

    private RegolithTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, REGOLITH_FALLS_TO_SOLID_GROUND, RegolithTests::regolithFallsToSolidGround);
        GooTestFunctions.registerTest(registrar, LUNAR_LEAVES_NO_REGOLITH_OVER_AIR,
                RegolithTests::lunarLeavesNoRegolithOverAir);
    }

    /**
     * Regolith set over a two block gap falls onto the stone below it, and a second regolith resting on a prop falls
     * onto the stone below once a survival player breaks the prop.
     *
     * @param helper the gametest helper
     */
    public static void regolithFallsToSolidGround(GameTestHelper helper) {
        helper.setBlock(GAP_FLOOR, Blocks.STONE);
        helper.setBlock(GAP_REGOLITH, GooBlocks.REGOLITH.get());
        helper.setBlock(PROPPED_FLOOR, Blocks.STONE);
        helper.setBlock(PROP, GooBlocks.ANORTHOSITE.get());
        helper.setBlock(PROPPED_REGOLITH, GooBlocks.REGOLITH.get());
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        player.gameMode.destroyBlock(helper.absolutePos(PROP));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeedWhen(() -> {
            assertRestsOn(helper, GAP_REGOLITH, GAP_FLOOR);
            assertRestsOn(helper, PROPPED_REGOLITH, PROPPED_FLOOR);
        });
    }

    private static void assertRestsOn(GameTestHelper helper, BlockPos from, BlockPos floor) {
        BlockPos rest = floor.above();
        helper.assertTrue(helper.getBlockState(rest).is(GooBlocks.REGOLITH.get()),
                String.format(SHOULD_REST, from, rest, helper.getBlockState(rest)));
        helper.assertTrue(helper.getBlockState(from).isAir(),
                String.format(SHOULD_LEAVE, from, helper.getBlockState(from)));
    }

    /**
     * Finds a lava tube's mouth in goo:lunar through the seeded tube field and scans every block of every column
     * within {@link #SAMPLE_REACH} of it, an area holding the mouth and a cave under the surface, then the same area
     * around ruins and nests generated at spread spots; no regolith block stands over air in any of them.
     *
     * @param helper the gametest helper
     */
    public static void lunarLeavesNoRegolithOverAir(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        BlockPos mouth = findMouth(lunar);
        helper.assertTrue(mouth != null, String.format(NO_MOUTH, CELL_REACH));
        Sample sample = sampleAround(lunar, mouth);
        helper.assertTrue(sample.holdsCave(), String.format(NO_CAVE, mouth));
        assertNoneOverAir(helper, sample, mouth);
        for (ResourceKey<Structure> landmark : List.of(RUIN, NEST)) {
            for (BlockPos spot : LANDMARK_SPOTS) {
                StructureStart start = LunarLandmarkTests.generate(lunar, landmark, spot);
                helper.assertTrue(start.isValid(), String.format(NO_LANDMARK, landmark.identifier(), spot));
                BoundingBox box = start.getBoundingBox().inflatedBy(LANDMARK_MARGIN);
                assertNoneOverAir(helper, sample(lunar, box.minX(), box.minZ(), box.maxX(), box.maxZ()),
                        box.getCenter());
            }
        }
        helper.succeed();
    }

    private static void assertNoneOverAir(GameTestHelper helper, Sample sample, BlockPos center) {
        List<BlockPos> overAir = sample.regolithOverAir();
        helper.assertTrue(overAir.isEmpty(), String.format(OVER_AIR, overAir.size(), center,
                overAir.isEmpty() ? null : overAir.get(0)));
    }

    /**
     * Scans every block of every column within {@link #SAMPLE_REACH} of a centre for regolith over air, and whether a
     * cave stands under the surface there.
     */
    private static Sample sampleAround(ServerLevel lunar, BlockPos center) {
        return sample(lunar, center.getX() - SAMPLE_REACH, center.getZ() - SAMPLE_REACH, center.getX() + SAMPLE_REACH,
                center.getZ() + SAMPLE_REACH);
    }

    /** Scans every block of every column in an area for regolith over air, and whether a cave stands under it. */
    private static Sample sample(ServerLevel lunar, int minX, int minZ, int maxX, int maxZ) {
        List<BlockPos> overAir = new ArrayList<>();
        boolean holdsCave = false;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                lunar.getChunk(x >> 4, z >> 4);
                int top = lunar.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                for (int y = top; y > lunar.getMinY(); y--) {
                    boolean airBelow = lunar.getBlockState(at.set(x, y - 1, z)).isAir();
                    holdsCave |= airBelow && y > top - CAVE_DEPTH && y < top;
                    if (airBelow && lunar.getBlockState(at.set(x, y, z)).is(GooBlocks.REGOLITH.get())) {
                        overAir.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return new Sample(overAir, holdsCave);
    }

    /** What a scan of an area found: each regolith block over air, and whether a cave stands under its surface. */
    private record Sample(List<BlockPos> regolithOverAir, boolean holdsCave) {
    }

    /** The first point down a tube's axis standing under its column's surface and open, or null where none is. */
    private static BlockPos findMouth(ServerLevel lunar) {
        LavaTubes tubes = LavaTubes.seeded(lunar.getChunkSource().randomState().getOrCreateNoise(LavaTubes.NOISE));
        for (int cellX = -CELL_REACH; cellX <= CELL_REACH; cellX++) {
            for (int cellZ = -CELL_REACH; cellZ <= CELL_REACH; cellZ++) {
                Optional<LavaTubes.Tube> tube = tubes.tubeInCell(cellX, cellZ);
                BlockPos mouth = tube.map(found -> mouthOf(lunar, found)).orElse(null);
                if (mouth != null) {
                    return mouth;
                }
            }
        }
        return null;
    }

    private static BlockPos mouthOf(ServerLevel lunar, LavaTubes.Tube tube) {
        for (int y = LavaTubes.TOP_Y; y > LavaTubes.BOTTOM_Y; y--) {
            BlockPos point = tube.axisAt(y);
            lunar.getChunk(point.getX() >> 4, point.getZ() >> 4);
            if (y < lunar.getHeight(Heightmap.Types.WORLD_SURFACE, point.getX(), point.getZ())) {
                return lunar.getBlockState(point).isAir() ? point : null;
            }
        }
        return null;
    }
}
