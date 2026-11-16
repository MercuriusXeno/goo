package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.CraterField;
import com.mercuriusxeno.goo.lunar.GiantCraterField;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Gametests for the details breaking up lunar's regolith: boulders of anorthosite, basalt or breccia stand on its
 * surface, and ejecta rays expose anorthosite through the regolith out from the large craters.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 */
@RegistersGameTests
public final class LunarBoulderAndRayTests {
    private static final String BOULDER_STANDS_ON_THE_REGOLITH = "boulder_stands_on_the_regolith";
    private static final String LUNAR_RAYS_EXPOSE_ANORTHOSITE = "lunar_rays_expose_anorthosite";

    private static final ResourceKey<Biome> LUNAR_BIOME = ResourceKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar"));
    private static final ResourceKey<PlacedFeature> BOULDER_FIELD = ResourceKey.create(Registries.PLACED_FEATURE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "boulder_field"));
    private static final ResourceKey<ConfiguredFeature<?, ?>> BOULDER = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, Identifier.fromNamespaceAndPath(Goo.MODID, "boulder"));
    private static final int FLOOR_Y = 1;
    private static final int FLOOR_EDGE = 8;
    private static final BlockPos BOULDER_SPOT = new BlockPos(4, FLOOR_Y + 1, 4);
    /** How many giant crater cells from the origin a search reaches each way. */
    private static final int CELL_REACH = 8;
    /** The two columns stand this many radii out from the crater's centre, past its rim's fall. */
    private static final double COLUMN_REACH = 1.6;

    private static final String BIOME_NAMES_BOULDERS = "goo:lunar's features should name goo:boulder_field";
    private static final String BOULDER_STANDS = "The boulder placed at %s should stand anorthosite, basalt or breccia "
            + "over the regolith, reads %s";
    private static final String NO_RAYED_CRATER = "No giant crater within %d cells of the origin has a ray column and a "
            + "between column clear of every other crater";
    private static final String RAY_READS_ANORTHOSITE = "The ray column %s should read anorthosite at its surface y %d, "
            + "reads %s";
    private static final String BETWEEN_READS_REGOLITH = "The column %s between two rays should read regolith at its "
            + "surface y %d, reads %s";

    private LunarBoulderAndRayTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, BOULDER_STANDS_ON_THE_REGOLITH,
                LunarBoulderAndRayTests::boulderStandsOnTheRegolith);
        GooTestFunctions.registerTest(registrar, LUNAR_RAYS_EXPOSE_ANORTHOSITE,
                LunarBoulderAndRayTests::lunarRaysExposeAnorthosite);
    }

    /**
     * goo:lunar's biome names the boulder field among its features, and the boulder feature placed on a flat
     * regolith floor leaves anorthosite, basalt or breccia standing over it.
     *
     * @param helper the gametest helper
     */
    public static void boulderStandsOnTheRegolith(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<Biome> lunar = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(LUNAR_BIOME);
        boolean namesBoulders = lunar.value().getGenerationSettings().features().stream()
                .anyMatch(step -> step.stream().anyMatch(feature -> feature.is(BOULDER_FIELD)));
        helper.assertTrue(namesBoulders, BIOME_NAMES_BOULDERS);
        for (int x = 0; x <= FLOOR_EDGE; x++) {
            for (int z = 0; z <= FLOOR_EDGE; z++) {
                helper.setBlock(new BlockPos(x, FLOOR_Y, z), GooBlocks.REGOLITH.get());
            }
        }
        ConfiguredFeature<?, ?> boulder = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE)
                .getOrThrow(BOULDER).value();
        boulder.place(level, level.getChunkSource().getGenerator(), level.getRandom(), helper.absolutePos(BOULDER_SPOT));
        BlockState standing = helper.getBlockState(BOULDER_SPOT);
        helper.assertTrue(standing.is(GooBlocks.ANORTHOSITE.get()) || standing.is(Blocks.BASALT)
                || standing.is(GooBlocks.BRECCIA.get()), String.format(BOULDER_STANDS, BOULDER_SPOT, standing));
        helper.succeed();
    }

    /**
     * Finds a giant crater in goo:lunar through the seeded giant crater field, with a column on one of its rays and a
     * column midway between two, both past its rim and clear of every other crater, every other ray and every thrown
     * boulder; the ray
     * column reads anorthosite at its surface and the between column reads regolith.
     *
     * @param helper the gametest helper
     */
    public static void lunarRaysExposeAnorthosite(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        RandomState randomState = lunar.getChunkSource().randomState();
        CraterField craters = CraterField.seeded(randomState.getOrCreateNoise(CraterField.NOISE));
        GiantCraterField giants = GiantCraterField.seeded(randomState.getOrCreateNoise(CraterField.NOISE));
        BlockPos[] columns = findRayColumns(craters, giants, ClearOfBoulders.in(lunar));
        helper.assertTrue(columns != null, String.format(NO_RAYED_CRATER, CELL_REACH));
        BlockPos ray = surface(lunar, columns[0]);
        BlockState rayState = lunar.getBlockState(ray);
        helper.assertTrue(rayState.is(GooBlocks.ANORTHOSITE.get()),
                String.format(RAY_READS_ANORTHOSITE, columns[0], ray.getY(), rayState));
        BlockPos between = surface(lunar, columns[1]);
        BlockState betweenState = lunar.getBlockState(between);
        helper.assertTrue(betweenState.is(GooBlocks.REGOLITH.get()),
                String.format(BETWEEN_READS_REGOLITH, columns[1], between.getY(), betweenState));
        helper.succeed();
    }

    private static BlockPos surface(ServerLevel lunar, BlockPos column) {
        lunar.getChunk(column.getX() >> 4, column.getZ() >> 4);
        return column.atY(lunar.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1);
    }

    /** A column on a ray and a column between two rays of one giant crater, or null where the search finds none. */
    private static BlockPos[] findRayColumns(CraterField craters, GiantCraterField giants,
            ClearOfBoulders clearOfBoulders) {
        for (int cellX = -CELL_REACH; cellX <= CELL_REACH; cellX++) {
            for (int cellZ = -CELL_REACH; cellZ <= CELL_REACH; cellZ++) {
                Optional<GiantCraterField.Crater> crater = giants.craterInCell(cellX, cellZ);
                if (crater.isEmpty()) {
                    continue;
                }
                double spacing = 2.0 * Math.PI / crater.get().rays();
                BlockPos onRay = columnAt(crater.get(), crater.get().phase());
                BlockPos between = columnAt(crater.get(), crater.get().phase() + spacing / 2.0);
                if (clearOfOtherCraters(craters, giants, onRay) && clearOfOtherCraters(craters, giants, between)
                        && clearOfBoulders.test(onRay) && clearOfBoulders.test(between)
                        && !craters.isOnRayAt(between.getX(), between.getZ())
                        && !giants.isOnRayAt(between.getX(), between.getZ())) {
                    return new BlockPos[] {onRay, between};
                }
            }
        }
        return null;
    }

    private static BlockPos columnAt(GiantCraterField.Crater crater, double angle) {
        double distance = crater.radius() * COLUMN_REACH;
        return BlockPos.containing(crater.centerX() + Math.cos(angle) * distance, 0.0,
                crater.centerZ() + Math.sin(angle) * distance);
    }

    private static boolean clearOfOtherCraters(CraterField craters, GiantCraterField giants, BlockPos column) {
        return craters.isOpenGroundAt(column.getX(), column.getZ())
                && !giants.reachesBandAt(column.getX(), column.getZ(), 0.0, 1.0 + GiantCraterField.RIM_REACH, 0.0);
    }
}
