package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.LavaTubes;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarDepths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Gametests for lunar's lava tubes: from a tube's opening on the surface, an unbroken path of air runs down through
 * every zone to the sea cavern.
 * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
 */
@RegistersGameTests
public final class LunarLavaTubeTests {
    private static final String LUNAR_LAVA_TUBES_REACH_THE_SEA = "lunar_lava_tubes_reach_the_sea";

    /** How many tube cells from the origin a search for a tube reaches each way. */
    private static final int CELL_REACH = 4;
    /** The fill stays within this many blocks of the tube's axis, so it follows the tube and nothing past it. */
    private static final int FILL_REACH = 48;
    /** The fill reaches the sea cavern once it stands under the lowest the cavern's roof falls. */
    private static final int CAVERN_Y = LunarDepths.SEA_ZONE_TOP_Y - 16;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_TUBE = "No lava tube within %d cells of the origin opens on the surface";
    private static final String REACHES_THE_SEA = "Air filled from the tube's opening at %s should reach the sea cavern "
            + "under y %d, reaches down to y %d";

    private LunarLavaTubeTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_LAVA_TUBES_REACH_THE_SEA, LunarLavaTubeTests::lunarLavaTubesReachTheSea);
    }

    /**
     * Finds a lava tube in goo:lunar through the seeded tube field, walks its axis down from the sky to the first
     * point under the ground, its opening, and fills through connected air from there, through the tunnel and the air
     * under the ground near the tube's axis; the fill reaches the sea cavern.
     *
     * @param helper the gametest helper
     */
    public static void lunarLavaTubesReachTheSea(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        LavaTubes tubes = LavaTubes.seeded(lunar.getChunkSource().randomState().getOrCreateNoise(LavaTubes.NOISE));
        LavaTubes.Tube tube = null;
        BlockPos opening = null;
        for (int cellX = -CELL_REACH; cellX <= CELL_REACH && opening == null; cellX++) {
            for (int cellZ = -CELL_REACH; cellZ <= CELL_REACH && opening == null; cellZ++) {
                Optional<LavaTubes.Tube> found = tubes.tubeInCell(cellX, cellZ);
                if (found.isPresent()) {
                    tube = found.get();
                    opening = openingOf(lunar, tube);
                }
            }
        }
        helper.assertTrue(opening != null, String.format(NO_TUBE, CELL_REACH));
        int lowest = lowestFilled(lunar, tube, opening);
        helper.assertTrue(lowest < CAVERN_Y, String.format(REACHES_THE_SEA, opening, CAVERN_Y, lowest));
        helper.succeed();
    }

    /**
     * The first point down the tube's axis that stands under its column's surface, where the tube enters the ground,
     * if the tube is open air there; null where the ground there is solid.
     */
    private static BlockPos openingOf(ServerLevel lunar, LavaTubes.Tube tube) {
        for (int y = LavaTubes.TOP_Y; y > LavaTubes.BOTTOM_Y; y--) {
            BlockPos point = tube.axisAt(y);
            lunar.getChunk(point.getX() >> 4, point.getZ() >> 4);
            if (y < lunar.getHeight(Heightmap.Types.WORLD_SURFACE, point.getX(), point.getZ())) {
                return lunar.getBlockState(point).isAir() ? point : null;
            }
        }
        return null;
    }

    /** Whether a block lies within the tube's tunnel, where the tube runs open to the sky along a slope. */
    private static boolean insideTube(LavaTubes.Tube tube, BlockPos block) {
        return tube.distance(block.getX(), block.getY(), block.getZ()) <= LavaTubes.TUBE_RADIUS + 1.0;
    }

    /**
     * Fills connected air from the opening, through the tube's tunnel and the air under the ground, near the axis;
     * answers the lowest y it reaches.
     */
    private static int lowestFilled(ServerLevel lunar, LavaTubes.Tube tube, BlockPos opening) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(opening);
        seen.add(opening);
        int lowest = opening.getY();
        while (!frontier.isEmpty() && lowest >= CAVERN_Y) {
            BlockPos at = frontier.poll();
            lowest = Math.min(lowest, at.getY());
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (seen.contains(next) || Math.abs(next.getX() - tube.centerX()) > FILL_REACH
                        || Math.abs(next.getZ() - tube.centerZ()) > FILL_REACH) {
                    continue;
                }
                lunar.getChunk(next.getX() >> 4, next.getZ() >> 4);
                boolean underground = next.getY() < lunar.getHeight(Heightmap.Types.WORLD_SURFACE, next.getX(),
                        next.getZ());
                if (lunar.getBlockState(next).isAir() && (underground || insideTube(tube, next))) {
                    seen.add(next);
                    frontier.add(next);
                }
            }
        }
        return lowest;
    }
}
