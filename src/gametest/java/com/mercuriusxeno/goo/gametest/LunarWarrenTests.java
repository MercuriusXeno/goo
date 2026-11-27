package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarDepths;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for the warrens' mechanical biome, scanning goo:lunar band by band over a square of chunks for what the
 * warrens generate and the bands over them do not.
 * decision warrens-hold-xeno-rich-alien-blocks
 * decision each-lunar-zone-holds-its-own-deposit-richer-by-depth
 */
@RegistersGameTests
public final class LunarWarrenTests {
    private static final String LUNAR_WARRENS_STAND_MONOLITH_MARKERS_ALONE = "lunar_warrens_stand_monolith_markers_alone";
    /** How many chunks from the origin a scan reaches each way. */
    private static final int CHUNK_REACH = 4;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_MARKER_IN_WARRENS = "The warrens over %d chunks should stand monolith markers, "
            + "stand none";
    private static final String MARKER_ABOVE_WARRENS = "The bands over the warrens across %d chunks should stand no "
            + "monolith marker, stand %d, the first at %s";
    private static final String MARKER_OFF_THE_FLOOR = "The monolith marker at %s should stand on a solid block under "
            + "air, stands on %s under %s";
    private static final String MARKER_BESIDE_MARKER = "The monolith marker at %s should stand alone, touches another "
            + "at %s";

    private LunarWarrenTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_WARRENS_STAND_MONOLITH_MARKERS_ALONE,
                LunarWarrenTests::lunarWarrensStandMonolithMarkersAlone);
    }

    /**
     * Scans a square of chunks in goo:lunar from its floor to its top for goo:monolith_marker: the warrens' band stands
     * some, each on a solid floor under air with no other marker touching it, and the bands over the warrens stand
     * none.
     *
     * @param helper the gametest helper
     */
    public static void lunarWarrensStandMonolithMarkersAlone(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        List<BlockPos> inWarrens = new ArrayList<>();
        List<BlockPos> above = new ArrayList<>();
        for (BlockPos marker : scanFor(lunar, GooBlocks.MONOLITH_MARKER.get())) {
            if (inWarrens(marker.getY())) {
                inWarrens.add(marker);
            } else if (marker.getY() >= LunarDepths.WARREN_ZONE_TOP_Y) {
                above.add(marker);
            }
        }
        int chunks = 4 * CHUNK_REACH * CHUNK_REACH;
        helper.assertTrue(!inWarrens.isEmpty(), String.format(NO_MARKER_IN_WARRENS, chunks));
        helper.assertTrue(above.isEmpty(), String.format(MARKER_ABOVE_WARRENS, chunks, above.size(),
                above.isEmpty() ? null : above.getFirst()));
        for (BlockPos marker : inWarrens) {
            assertStandsAloneOnAFloor(helper, lunar, marker);
        }
        helper.succeed();
    }

    private static void assertStandsAloneOnAFloor(GameTestHelper helper, ServerLevel lunar, BlockPos marker) {
        BlockPos floor = marker.below();
        BlockState under = lunar.getBlockState(floor);
        BlockState over = lunar.getBlockState(marker.above());
        helper.assertTrue(under.isFaceSturdy(lunar, floor, Direction.UP) && over.isAir(),
                String.format(MARKER_OFF_THE_FLOOR, marker, under, over));
        for (BlockPos near : BlockPos.betweenClosed(marker.offset(-1, -1, -1), marker.offset(1, 1, 1))) {
            helper.assertTrue(near.equals(marker) || !lunar.getBlockState(near).is(GooBlocks.MONOLITH_MARKER.get()),
                    String.format(MARKER_BESIDE_MARKER, marker, near.immutable()));
        }
    }

    /** Whether a height lies in the warrens' band. */
    static boolean inWarrens(int y) {
        return y >= LunarDepths.SEA_ZONE_TOP_Y && y < LunarDepths.WARREN_ZONE_TOP_Y;
    }

    /**
     * Every block of a kind over the square of chunks around the origin, from lunar's floor to its top.
     *
     * @param lunar the lunar level
     * @param block the block to find
     * @return each position holding it
     */
    static List<BlockPos> scanFor(ServerLevel lunar, Block block) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int top = LunarDepths.WORLD_FLOOR_Y + LunarDepths.WORLD_HEIGHT;
        for (int chunkX = -CHUNK_REACH; chunkX < CHUNK_REACH; chunkX++) {
            for (int chunkZ = -CHUNK_REACH; chunkZ < CHUNK_REACH; chunkZ++) {
                lunar.getChunk(chunkX, chunkZ);
                for (int x = chunkX * 16; x < chunkX * 16 + 16; x++) {
                    for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++) {
                        for (int y = LunarDepths.WORLD_FLOOR_Y; y < top; y++) {
                            if (lunar.getBlockState(at.set(x, y, z)).is(block)) {
                                found.add(at.immutable());
                            }
                        }
                    }
                }
            }
        }
        return found;
    }
}
