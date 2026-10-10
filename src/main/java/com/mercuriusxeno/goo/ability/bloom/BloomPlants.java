package com.mercuriusxeno.goo.ability.bloom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The plant Bloom spawns in a cell, picked at random among the plants of its
 * surface's flora that can survive there: a lily pad on water, a vine on a
 * wall, moss, glow lichen or a spore blossom in a cave, and in a field one
 * of the biome's own plants, every plant its vegetation places a block at a
 * time, its bone-meal flowers and its sapling where it grows trees.
 * bloom-places-buds-by-biome-and-surface
 */
public final class BloomPlants {

    /** Draws from each block provider, so a weighted mix shows each of its plants. */
    private static final int PROVIDER_DRAWS = 4;
    /** Each biome's sapling, the first whose biomes match taken; the oak where none does. */
    private static final List<BiomeSapling> SAPLINGS = List.of(
            new BiomeSapling(biome -> biome.is(Biomes.CHERRY_GROVE), Blocks.CHERRY_SAPLING),
            new BiomeSapling(biome -> biome.is(Biomes.MANGROVE_SWAMP), Blocks.MANGROVE_PROPAGULE),
            new BiomeSapling(biome -> biome.is(Biomes.BIRCH_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST),
                    Blocks.BIRCH_SAPLING),
            new BiomeSapling(biome -> biome.is(BiomeTags.IS_JUNGLE), Blocks.JUNGLE_SAPLING),
            new BiomeSapling(biome -> biome.is(BiomeTags.IS_TAIGA), Blocks.SPRUCE_SAPLING),
            new BiomeSapling(biome -> biome.is(BiomeTags.IS_SAVANNA), Blocks.ACACIA_SAPLING));
    /** The green sparkles thrown over each plant, and how far round it they scatter. */
    private static final int SPARKLE_PARTICLES = 15;
    private static final double SPARKLE_SPREAD = 0.4;
    private static final double PLANT_MIDDLE = 0.5;

    private BloomPlants() {
    }

    /**
     * Spawns a random plant of the flora that survives in the cell, thrown
     * up with bone meal's green sparkles and the plant's own placing sound.
     *
     * @param level  the server level
     * @param pos    the cell
     * @param spot   the flora the cell's surface takes and the way out of what holds it
     * @param random the random source
     * @return true when a plant was spawned, false where none of the flora survives there
     */
    public static boolean spawn(ServerLevel level, BlockPos pos, BloomSurface.Spot spot, RandomSource random) {
        List<BlockState> surviving = plantsOf(level, pos, spot.flora(), spot.facing(), random).stream()
                .filter(state -> survives(level, pos, state)).toList();
        if (surviving.isEmpty()) {
            return false;
        }
        BlockState grown = surviving.get(random.nextInt(surviving.size()));
        if (grown.getBlock() instanceof DoublePlantBlock) {
            DoublePlantBlock.placeAt(level, grown, pos, Block.UPDATE_ALL);
        } else {
            level.setBlock(pos, grown, Block.UPDATE_ALL);
        }
        sparkle(level, pos, grown);
        return true;
    }

    /**
     * Throws bone meal's green sparkles over a spawned plant, with the sound
     * the plant makes as it is placed. Sent directly, since the bone-meal
     * level event draws its sparkles only over a block bone meal grows, which
     * a lily pad, a vine or a flower is not.
     *
     * @param level the server level
     * @param pos   the plant's cell
     * @param plant the plant spawned
     */
    private static void sparkle(ServerLevel level, BlockPos pos, BlockState plant) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + PLANT_MIDDLE, pos.getY() + PLANT_MIDDLE,
                pos.getZ() + PLANT_MIDDLE, SPARKLE_PARTICLES, SPARKLE_SPREAD, SPARKLE_SPREAD, SPARKLE_SPREAD, 0);
        SoundType sound = plant.getSoundType(level, pos, null);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, sound.getVolume(), sound.getPitch());
    }

    private static boolean survives(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoublePlantBlock && !level.isEmptyBlock(pos.above())) {
            return false;
        }
        return state.canSurvive(level, pos);
    }

    /**
     * Every plant a flora offers a cell, before survival.
     *
     * @param level  the server level
     * @param pos    the cell
     * @param flora  the flora the cell's surface takes
     * @param facing the way out of what holds the plant
     * @param random the random source
     * @return the plants
     */
    static List<BlockState> plantsOf(ServerLevel level, BlockPos pos, BloomFlora flora, Direction facing,
                                     RandomSource random) {
        Direction support = facing.getOpposite();
        return switch (flora) {
            case WATER -> List.of(Blocks.LILY_PAD.defaultBlockState());
            case WALL -> List.of(Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(support), true));
            case CAVE -> caveFlora(facing);
            case FIELD -> biomePlants(level, pos, random);
        };
    }

    /**
     * A cave's plants by what holds them: moss or glow lichen on the
     * floor, a spore blossom or glow lichen on the ceiling, glow lichen on a wall.
     *
     * @param facing the way out of what holds the plant
     * @return the plants
     */
    static List<BlockState> caveFlora(Direction facing) {
        BlockState lichen = Blocks.GLOW_LICHEN.defaultBlockState()
                .setValue(MultifaceBlock.getFaceProperty(facing.getOpposite()), true);
        return switch (facing) {
            case UP -> List.of(Blocks.MOSS_CARPET.defaultBlockState(), lichen);
            case DOWN -> List.of(Blocks.SPORE_BLOSSOM.defaultBlockState(), lichen);
            default -> List.of(lichen);
        };
    }

    /**
     * The biome's own plants: every plant its vegetation and bone meal place
     * a block at a time, and its sapling where it grows trees.
     *
     * @param level  the server level
     * @param pos    the cell
     * @param random the random source
     * @return the plants
     */
    static List<BlockState> biomePlants(ServerLevel level, BlockPos pos, RandomSource random) {
        Holder<Biome> biome = level.getBiome(pos);
        Set<BlockState> plants = new LinkedHashSet<>();
        boolean growsTrees = false;
        for (ConfiguredFeature<?, ?> feature : vegetationOf(biome.value().getGenerationSettings()).toList()) {
            if (feature.config() instanceof TreeConfiguration) {
                growsTrees = true;
            } else if (feature.config() instanceof SimpleBlockConfiguration simple) {
                for (int draw = 0; draw < PROVIDER_DRAWS; draw++) {
                    plants.add(simple.toPlace().getState(level, random, pos));
                }
            }
        }
        if (growsTrees) {
            plants.add(saplingFor(biome).defaultBlockState());
        }
        return new ArrayList<>(plants);
    }

    /**
     * Every feature a biome's vegetation and bone meal place, the features
     * nested in each among them.
     *
     * @param settings the biome's generation settings
     * @return the features
     */
    private static Stream<ConfiguredFeature<?, ?>> vegetationOf(BiomeGenerationSettings settings) {
        int vegetal = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
        List<HolderSet<PlacedFeature>> steps = settings.features();
        Stream<ConfiguredFeature<?, ?>> placed = steps.size() <= vegetal ? Stream.empty()
                : steps.get(vegetal).stream().flatMap(feature -> feature.value().getFeatures()).map(Holder::value);
        Stream<ConfiguredFeature<?, ?>> boneMeal = settings.getBoneMealFeatures().stream()
                .flatMap(BloomPlants::withNested);
        return Stream.concat(placed, boneMeal);
    }

    private static Stream<ConfiguredFeature<?, ?>> withNested(ConfiguredFeature<?, ?> feature) {
        return Stream.concat(Stream.of(feature),
                feature.config().getSubFeatures().map(Holder::value).flatMap(BloomPlants::withNested));
    }

    /**
     * The sapling a biome's trees grow from, among those that grow alone;
     * oak where no other fits.
     *
     * @param biome the biome
     * @return the sapling
     */
    static Block saplingFor(Holder<Biome> biome) {
        return SAPLINGS.stream().filter(sapling -> sapling.grows().test(biome)).map(BiomeSapling::sapling)
                .findFirst().orElse(Blocks.OAK_SAPLING);
    }

    /**
     * A sapling and the biomes whose trees grow from it.
     *
     * @param grows   whether a biome's trees grow from it
     * @param sapling the sapling
     */
    private record BiomeSapling(Predicate<Holder<Biome>> grows, Block sapling) {
    }
}
