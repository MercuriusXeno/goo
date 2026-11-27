package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for void crystal, the moon's source of void, which grows in clusters on the dark side's surface.
 * decision void-crystals-spawn-on-the-moon
 */
@RegistersGameTests
public final class LunarVoidCrystalTests {
    private static final String VOID_CRYSTAL_CLUSTER_GROWS_ON_THE_REGOLITH =
            "void_crystal_cluster_grows_on_the_regolith";

    private static final ResourceKey<PlacedFeature> PLACED_CLUSTER = ResourceKey.create(Registries.PLACED_FEATURE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "void_crystal_cluster"));
    private static final ResourceKey<Feature> CLUSTER = ResourceKey.create(Registries.FEATURE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "void_crystal_cluster"));
    private static final int FLOOR_Y = 1;
    private static final int FLOOR_EDGE = 8;
    private static final int CLUSTER_REACH = 3;
    private static final BlockPos CLUSTER_SPOT = new BlockPos(4, FLOOR_Y + 1, 4);

    private static final String BIOMES_NAME_THE_CLUSTER = "Every lunar surface biome's features should name "
            + "goo:void_crystal_cluster";
    private static final String CLUSTER_GREW = "The void crystal cluster placed at %s should grow void crystal over "
            + "the regolith, grew none";
    private static final String CRYSTAL_ON_ROCK = "The void crystal at %s should grow out from sturdy rock, stands on %s";

    private LunarVoidCrystalTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, VOID_CRYSTAL_CLUSTER_GROWS_ON_THE_REGOLITH,
                LunarVoidCrystalTests::voidCrystalClusterGrowsOnTheRegolith);
    }

    /**
     * Every lunar surface biome names the void crystal cluster among its features, and the cluster feature placed on
     * a flat regolith floor leaves void crystal standing, each crystal facing out from the rock it grows on.
     *
     * @param helper the gametest helper
     */
    public static void voidCrystalClusterGrowsOnTheRegolith(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean everyBiomeNamesIt = LunarSurfaceBiomes.ALL.stream().map(key -> level.registryAccess()
                .lookupOrThrow(Registries.BIOME).getOrThrow(key)).allMatch(biome -> biome.value()
                .getGenerationSettings().features().stream()
                .anyMatch(step -> step.stream().anyMatch(feature -> feature.is(PLACED_CLUSTER))));
        helper.assertTrue(everyBiomeNamesIt, BIOMES_NAME_THE_CLUSTER);
        for (int x = 0; x <= FLOOR_EDGE; x++) {
            for (int z = 0; z <= FLOOR_EDGE; z++) {
                helper.setBlock(new BlockPos(x, FLOOR_Y, z), GooBlocks.REGOLITH.get());
            }
        }
        Feature cluster = level.registryAccess().lookupOrThrow(Registries.FEATURE).getOrThrow(CLUSTER).value();
        cluster.place(level, level.getChunkSource().getGenerator(), level.getRandom(), helper.absolutePos(CLUSTER_SPOT));
        int grown = 0;
        for (BlockPos spot : BlockPos.betweenClosed(CLUSTER_SPOT.offset(-CLUSTER_REACH, -CLUSTER_REACH, -CLUSTER_REACH),
                CLUSTER_SPOT.offset(CLUSTER_REACH, CLUSTER_REACH, CLUSTER_REACH))) {
            BlockState state = helper.getBlockState(spot);
            if (state.is(GooBlocks.VOID_CRYSTAL.get())) {
                assertGrowsFromRock(helper, spot.immutable(), state.getValue(AmethystClusterBlock.FACING));
                grown++;
            }
        }
        helper.assertTrue(grown > 0, String.format(CLUSTER_GREW, CLUSTER_SPOT));
        helper.succeed();
    }

    private static void assertGrowsFromRock(GameTestHelper helper, BlockPos crystal, Direction facing) {
        BlockPos root = crystal.relative(facing.getOpposite());
        BlockState rock = helper.getBlockState(root);
        helper.assertTrue(rock.isFaceSturdy(helper.getLevel(), helper.absolutePos(root), facing)
                && !rock.is(GooBlocks.VOID_CRYSTAL.get()), String.format(CRYSTAL_ON_ROCK, crystal, rock));
    }
}
