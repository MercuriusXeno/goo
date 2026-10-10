package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Gametests for Leaf Bloom: blobs landing in a dirt field beside a pond and
 * a wall spawn plants straight away, a lily pad on the water, a vine on the
 * wall and a plant on the dirt, and every plant stands on the surface its
 * kind belongs to.
 * bloom-places-buds-by-biome-and-surface
 */
public final class BloomTests {

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
    /**
     * Blobs thrown until a plant stands on each surface; each spawns up to
     * eight, by chance, so enough are thrown that the pond's few cells come up.
     */
    private static final int MAX_THROWS = 30;
    /** The cells scanned for plants: the field, the pond and the wall, and a block past them. */
    private static final int SCAN_MAX = FLOOR_MAX + 1;
    private static final int SCAN_TOP = WALL_TOP + 2;

    private static final String SHOULD_COVER_EVERY_SURFACE =
            "Plants should spawn on water, wall and ground: lily %b, vine %b, field plant %b";
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
        boolean everySurface() {
            return lily && vine && field;
        }
    }

    /**
     * Blobs struck into a dirt field beside a pond and a wall spawn a lily
     * pad on the water, a vine on the wall and a plant on the dirt, each
     * standing on the surface its kind belongs to.
     *
     * @param helper the gametest helper
     */
    public static void bloomPlantsWaterWallAndGround(GameTestHelper helper) {
        layField(helper);
        AbilityDefinition bloom = AbilityRegistry.of(helper.getLevel()).getAbility(BLOOM);
        helper.assertTrue(bloom != null, ABILITY_REQUIRED);
        Spawned spawned = scan(helper);
        for (int throwCount = 0; throwCount < MAX_THROWS && !spawned.everySurface(); throwCount++) {
            AbilityImpact.land(helper.getLevel(), helper.absolutePos(STRUCK), bloom.gooType(), Direction.UP, bloom);
            spawned = scan(helper);
        }
        helper.assertTrue(spawned.everySurface(),
                String.format(SHOULD_COVER_EVERY_SURFACE, spawned.lily(), spawned.vine(), spawned.field()));
        helper.succeed();
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
