package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarDepths;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for lunar's astral goo beside its cave deposits: across a wide square of generated chunks, some of the
 * deposits facing a cave, native iron, astral crystal and ancient debris, carry astral goo's Dowsing cells beside
 * them and some carry none.
 * decision frost-astral-and-xeno-goo-spawn-periodically-on-lunar
 */
@RegistersGameTests
public final class LunarCavePocketTests {
    private static final String ASTRAL_GOO_SITS_BESIDE_SOME_CAVE_DEPOSITS = "astral_goo_sits_beside_some_cave_deposits";

    /** The square's middle chunk, apart from lunar's other test sites. */
    private static final int MIDDLE_CHUNK_X = 256;
    private static final int MIDDLE_CHUNK_Z = -256;
    /** The square of chunks generated reaches this many chunks each way of its middle. */
    private static final int CHUNK_REACH = 5;
    /** Astral goo within this many blocks of a deposit, every way, lies beside it. */
    private static final int BESIDE = 5;
    private static final int CHUNK_SIDE = 16;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String SHARE_BETWEEN = "Of the %d cave deposits in the %d chunks around chunk %d, %d, some"
            + " should carry astral goo beside them and some none; %d carry it, among %d Dowsing cells in the bands";

    private LunarCavePocketTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, ASTRAL_GOO_SITS_BESIDE_SOME_CAVE_DEPOSITS,
                LunarCavePocketTests::astralGooSitsBesideSomeCaveDeposits);
    }

    /**
     * Generates a wide square of chunks and reads every deposit facing a cave in lunar's cave bands; the share with a
     * Dowsing cell beside it sits strictly between none and all.
     *
     * @param helper the gametest helper
     */
    public static void astralGooSitsBesideSomeCaveDeposits(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        List<BlockPos> deposits = new ArrayList<>();
        List<BlockPos> dowsing = new ArrayList<>();
        for (int cx = MIDDLE_CHUNK_X - CHUNK_REACH; cx <= MIDDLE_CHUNK_X + CHUNK_REACH; cx++) {
            for (int cz = MIDDLE_CHUNK_Z - CHUNK_REACH; cz <= MIDDLE_CHUNK_Z + CHUNK_REACH; cz++) {
                readCaveBands(lunar, lunar.getChunk(cx, cz), deposits, dowsing);
            }
        }
        long carried = deposits.stream().filter(deposit -> dowsing.stream().anyMatch(cell -> besides(cell, deposit)))
                .count();
        int side = 2 * CHUNK_REACH + 1;
        helper.assertTrue(carried > 0 && carried < deposits.size(), String.format(SHARE_BETWEEN, deposits.size(),
                side * side, MIDDLE_CHUNK_X, MIDDLE_CHUNK_Z, carried, dowsing.size()));
        helper.succeed();
    }

    /**
     * Gathers a chunk's deposits facing a cave and its Dowsing cells, between the bottom of the warrens and the top of
     * the shallow zone.
     */
    private static void readCaveBands(ServerLevel lunar, LevelChunk chunk, List<BlockPos> deposits,
                                      List<BlockPos> dowsing) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int x = minX; x < minX + CHUNK_SIDE; x++) {
            for (int z = minZ; z < minZ + CHUNK_SIDE; z++) {
                for (int y = LunarDepths.SEA_ZONE_TOP_Y; y < LunarDepths.SHALLOW_ZONE_TOP_Y; y++) {
                    BlockPos at = new BlockPos(x, y, z);
                    BlockState state = chunk.getBlockState(at);
                    if (state.is(GooBlocks.ASTRAL_DOWSING.get())) {
                        dowsing.add(at);
                    } else if (isDeposit(state) && facesCave(lunar, at)) {
                        deposits.add(at);
                    }
                }
            }
        }
    }

    private static boolean isDeposit(BlockState state) {
        return state.is(GooBlocks.NATIVE_IRON.get()) || state.is(GooBlocks.ASTRAL_CRYSTAL.get())
                || state.is(Blocks.ANCIENT_DEBRIS);
    }

    private static boolean facesCave(ServerLevel lunar, BlockPos at) {
        for (Direction side : Direction.values()) {
            if (lunar.getBlockState(at.relative(side)).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static boolean besides(BlockPos cell, BlockPos deposit) {
        return Math.abs(cell.getX() - deposit.getX()) <= BESIDE && Math.abs(cell.getY() - deposit.getY()) <= BESIDE
                && Math.abs(cell.getZ() - deposit.getZ()) <= BESIDE;
    }
}
