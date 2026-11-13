package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.bloom.BloomFlora;
import com.mercuriusxeno.goo.ability.bloom.BloomPlants;
import com.mercuriusxeno.goo.ability.bloom.BloomSurface;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Gametests for Leaf Bloom: blobs landing in a dirt field beside a pond and
 * a wall spawn plants straight away, a lily pad on the water, a vine on the
 * wall and a plant on the dirt, and every plant stands on the surface its
 * kind belongs to.
 * bloom-places-buds-by-biome-and-surface
 */
@RegistersGameTests
public final class BloomTests {
    private static final String BLOOM_PLANTS = "bloom_plants_water_wall_and_ground";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, BLOOM_PLANTS, BloomTests::bloomPlantsWaterWallAndGround);
    }


    private static final Identifier BLOOM = Identifier.fromNamespaceAndPath(Goo.MODID, "leaf_bloom");
    private static final String ABILITY_REQUIRED = "Ability registry must hold leaf_bloom";
    /** The bay's floor: stone under a layer of dirt, the pond and the wall standing in it. */
    private static final int FLOOR_MIN = 1;
    private static final int FLOOR_MAX = 6;
    private static final int GROUND_Y = 1;
    /** The pond: a three by three of still water in the dirt. */
    private static final int POND_MIN = 1;
    private static final int POND_MAX = 3;
    /** The wall along the field's east edge, three blocks tall. */
    private static final int WALL_X = 6;
    private static final int WALL_TOP = 4;
    /** Where the blobs strike: the dirt in the field's middle. */
    private static final BlockPos STRUCK = new BlockPos(4, GROUND_Y, 3);
    /** The cells scanned for plants: the field, the pond and the wall, and a block past them. */
    private static final int SCAN_MAX = FLOOR_MAX + 1;
    private static final int SCAN_TOP = WALL_TOP + 2;

    private static final String LANDING_SPAWNED_NOTHING = "One blob struck into the dirt should spawn a plant on it";
    private static final String SPOT_READ = "Bloom should read the spot at %s";
    private static final String SPOT_TOOK_NO_PLANT = "The spot at %s should take its plant";
    /** A cell over the pond's middle, one over the dirt and one beside the wall, clear of the ground. */
    private static final BlockPos OVER_THE_POND = new BlockPos(2, GROUND_Y + 1, 2);
    private static final BlockPos OVER_THE_DIRT = new BlockPos(4, GROUND_Y + 1, 5);
    private static final BlockPos BESIDE_THE_WALL = new BlockPos(WALL_X - 1, WALL_TOP - 1, 3);
    private static final String LILY_ON_WATER = "A lily pad at %s should float on still water";
    private static final String VINE_ON_WALL = "A vine at %s should hang on a sturdy wall";

    private BloomTests() {
    }

    /**
     * What the field holds after the throws.
     *
     * @param lily  a lily pad stands on the pond
     * @param vine  a vine hangs on a wall
     * @param field a plant stands on the dirt
     */
    private record Spawned(boolean lily, boolean vine, boolean field) {
    }

    /**
     * One blob struck into a dirt field beside a pond and a wall spawns plants on the dirt, and each surface Bloom
     * reads takes its own plant: a lily pad on still water, a vine on the wall and a plant on the dirt.
     *
     * @param helper the gametest helper
     */
    public static void bloomPlantsWaterWallAndGround(GameTestHelper helper) {
        layField(helper);
        AbilityDefinition bloom = AbilityRegistry.of(helper.getLevel()).getAbility(BLOOM);
        helper.assertTrue(bloom != null, ABILITY_REQUIRED);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(STRUCK), bloom.gooType(), Direction.UP, bloom);
        helper.assertTrue(scan(helper).field(), LANDING_SPAWNED_NOTHING);
        assertSpotTakes(helper, OVER_THE_POND, new BloomSurface.Spot(BloomFlora.WATER, Direction.UP), Blocks.LILY_PAD);
        assertSpotTakes(helper, BESIDE_THE_WALL, new BloomSurface.Spot(BloomFlora.WALL, Direction.WEST), Blocks.VINE);
        BlockPos field = helper.absolutePos(OVER_THE_DIRT);
        helper.getLevel().setBlockAndUpdate(field, Blocks.AIR.defaultBlockState());
        BloomSurface.Spot fieldSpot = new BloomSurface.Spot(BloomFlora.FIELD, Direction.UP);
        helper.assertValueEqual(Optional.of(fieldSpot), BloomSurface.classify(BloomSurface.of(helper.getLevel(), field)),
                String.format(SPOT_READ, OVER_THE_DIRT));
        helper.assertTrue(BloomPlants.spawn(helper.getLevel(), field, fieldSpot, helper.getLevel().getRandom())
                && !helper.getBlockState(OVER_THE_DIRT).isAir(), String.format(SPOT_TOOK_NO_PLANT, OVER_THE_DIRT));
        helper.succeed();
    }

    /**
     * Empties the cell, reads the spot Bloom finds there and spawns the plant it takes.
     *
     * @param helper the gametest helper
     * @param cell   the test-relative cell
     * @param spot   the spot the cell's surface offers
     * @param plant  the plant the spot takes
     */
    private static void assertSpotTakes(GameTestHelper helper, BlockPos cell, BloomSurface.Spot spot, Block plant) {
        BlockPos at = helper.absolutePos(cell);
        helper.getLevel().setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
        helper.assertValueEqual(Optional.of(spot), BloomSurface.classify(BloomSurface.of(helper.getLevel(), at)),
                String.format(SPOT_READ, cell));
        helper.assertTrue(BloomPlants.spawn(helper.getLevel(), at, spot, helper.getLevel().getRandom()),
                String.format(SPOT_TOOK_NO_PLANT, cell));
        helper.assertTrue(helper.getBlockState(cell).is(plant), String.format(SPOT_TOOK_NO_PLANT, cell));
    }
    private static void layField(GameTestHelper helper) {
        for (int x = FLOOR_MIN; x <= FLOOR_MAX; x++) {
            for (int z = FLOOR_MIN; z <= FLOOR_MAX; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                boolean pond = x >= POND_MIN && x <= POND_MAX && z >= POND_MIN && z <= POND_MAX;
                helper.setBlock(new BlockPos(x, GROUND_Y, z), pond ? Blocks.WATER : Blocks.DIRT);
            }
            for (int y = GROUND_Y + 1; y <= WALL_TOP; y++) {
                helper.setBlock(new BlockPos(WALL_X, y, x), Blocks.STONE);
            }
        }
    }

    /**
     * Reads what the field holds, asserting every lily pad floats on water
     * and every vine hangs on a sturdy wall.
     *
     * @param helper the gametest helper
     * @return what has spawned on each surface
     */
    private static Spawned scan(GameTestHelper helper) {
        boolean lily = false;
        boolean vine = false;
        boolean field = false;
        for (int x = 0; x <= SCAN_MAX; x++) {
            for (int y = GROUND_Y + 1; y <= SCAN_TOP; y++) {
                for (int z = 0; z <= SCAN_MAX; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = helper.getBlockState(pos);
                    if (state.is(Blocks.LILY_PAD)) {
                        helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(pos.below()))
                                .is(Fluids.WATER), String.format(LILY_ON_WATER, pos));
                        lily = true;
                    } else if (state.getBlock() instanceof VineBlock) {
                        helper.assertTrue(hangsOnAWall(helper, pos, state), String.format(VINE_ON_WALL, pos));
                        vine = true;
                    } else if (!state.isAir() && helper.getBlockState(pos.below()).is(BlockTags.DIRT)) {
                        field = true;
                    }
                }
            }
        }
        return new Spawned(lily, vine, field);
    }

    /**
     * Whether a vine hangs on a block offering it a sturdy face: the built
     * wall, or the structure's barrier shell the field runs up to.
     *
     * @param helper the gametest helper
     * @param pos    the vine's cell
     * @param vine   the vine
     * @return true when a face it hangs on is held by a sturdy block
     */
    private static boolean hangsOnAWall(GameTestHelper helper, BlockPos pos, BlockState vine) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos holder = helper.absolutePos(pos.relative(side));
            if (vine.getValue(VineBlock.getPropertyForFace(side))
                    && helper.getLevel().getBlockState(holder).isFaceSturdy(helper.getLevel(), holder,
                    side.getOpposite())) {
                return true;
            }
        }
        return false;
    }
}
