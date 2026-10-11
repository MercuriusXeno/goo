package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Gametest for Deep's dive: a player standing on the roof of a sealed stone
 * cave casts dive and lands inside the cave below, on its floor, clear of
 * every block (decision dive-drops-you-to-a-cave-below). The scene stands in
 * a chunk of its own far from every other test, so no other test's enclosed
 * air is within the dive's reach.
 */
public final class DiveTests {

    private static final Identifier DEEP_DIVE = Identifier.parse("goo:deep_dive");
    /** How far off the bay on each horizontal axis the scene's chunk lies, past the convoke tests' chunks. */
    private static final int FAR_OFF = 16384;
    private static final int CHUNK_MIDDLE = 8;
    /** The cave's interior reaches this far from its middle column on each horizontal axis. */
    private static final int CAVE_HALF_WIDTH = 1;
    private static final int CAVE_HEIGHT = 3;
    private static final String ABILITY_REQUIRED = "goo:deep_dive must be loaded";
    private static final String SHOULD_DIVE = "The dive should land inside the cave %s..%s, landed at %s";
    private static final String SHOULD_DROP = "The dive should land below where it started at y=%d, landed at y=%d";
    private static final String SHOULD_STAND = "The landing %s should be air over a sturdy floor";
    private static final String SHOULD_FIT = "The diver should stand clear of every block at %s";

    private DiveTests() {
    }

    /**
     * A player on the roof of a sealed cave dives and lands inside it: the
     * landing cell is air over a sturdy floor, lower than the start, and the
     * player's box is clear of every block.
     *
     * @param helper the gametest helper
     */
    public static void diveLandsInTheCaveBelow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AbilityDefinition dive = AbilityRegistry.of(level).getAbility(DEEP_DIVE);
        helper.assertTrue(dive != null, ABILITY_REQUIRED);
        BlockPos caveFloor = buildCave(helper);
        BlockPos roofTop = caveFloor.above(CAVE_HEIGHT + 1);
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.DEEP);
        KnownRecipes.teachRequires(player, dive);
        player.teleportTo(roofTop.getX() + 0.5, roofTop.getY(), roofTop.getZ() + 0.5);

        SelfDeliveryTests.invoke(player, GooTypes.DEEP, DEEP_DIVE);

        BlockPos landed = player.blockPosition();
        boolean fits = level.noCollision(player, player.getBoundingBox());
        level.getServer().getPlayerList().remove(player);
        BlockPos low = caveFloor.offset(-CAVE_HALF_WIDTH, 0, -CAVE_HALF_WIDTH);
        BlockPos high = caveFloor.offset(CAVE_HALF_WIDTH, CAVE_HEIGHT - 1, CAVE_HALF_WIDTH);
        boolean inCave = landed.getX() >= low.getX() && landed.getX() <= high.getX()
                && landed.getY() >= low.getY() && landed.getY() <= high.getY()
                && landed.getZ() >= low.getZ() && landed.getZ() <= high.getZ();
        BlockState floor = level.getBlockState(landed.below());
        boolean stands = level.getBlockState(landed).isAir()
                && floor.isFaceSturdy(level, landed.below(), Direction.UP);
        release(helper, caveFloor);
        helper.assertTrue(inCave, String.format(SHOULD_DIVE, low, high, landed));
        helper.assertTrue(landed.getY() < roofTop.getY(), String.format(SHOULD_DROP, roofTop.getY(), landed.getY()));
        helper.assertTrue(stands, String.format(SHOULD_STAND, landed));
        helper.assertTrue(fits, String.format(SHOULD_FIT, landed));
        helper.succeed();
    }

    /**
     * Builds a sealed stone cave on the ground of a far chunk held loaded:
     * a stone shell around a three-wide, three-tall air pocket.
     *
     * @param helper the gametest helper
     * @return the middle cell of the cave's lowest air layer
     */
    private static BlockPos buildCave(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bay = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = ChunkPos.containing(bay.offset(FAR_OFF, 0, FAR_OFF));
        level.setChunkForced(chunk.x(), chunk.z(), true);
        int x = chunk.getMinBlockX() + CHUNK_MIDDLE;
        int z = chunk.getMinBlockZ() + CHUNK_MIDDLE;
        BlockPos caveFloor = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        int shell = CAVE_HALF_WIDTH + 1;
        for (BlockPos cell : BlockPos.betweenClosed(caveFloor.offset(-shell, -1, -shell),
                caveFloor.offset(shell, CAVE_HEIGHT, shell))) {
            level.setBlockAndUpdate(cell, Blocks.STONE.defaultBlockState());
        }
        for (BlockPos cell : BlockPos.betweenClosed(caveFloor.offset(-CAVE_HALF_WIDTH, 0, -CAVE_HALF_WIDTH),
                caveFloor.offset(CAVE_HALF_WIDTH, CAVE_HEIGHT - 1, CAVE_HALF_WIDTH))) {
            level.setBlockAndUpdate(cell, Blocks.AIR.defaultBlockState());
        }
        return caveFloor;
    }

    /**
     * Clears the cave's shell above the ground and lets the chunk unload.
     *
     * @param helper    the gametest helper
     * @param caveFloor the middle cell of the cave's lowest air layer
     */
    private static void release(GameTestHelper helper, BlockPos caveFloor) {
        ServerLevel level = helper.getLevel();
        int shell = CAVE_HALF_WIDTH + 1;
        for (BlockPos cell : BlockPos.betweenClosed(caveFloor.offset(-shell, 0, -shell),
                caveFloor.offset(shell, CAVE_HEIGHT, shell))) {
            level.removeBlock(cell, false);
        }
        ChunkPos chunk = ChunkPos.containing(caveFloor);
        level.setChunkForced(chunk.x(), chunk.z(), false);
    }
}
