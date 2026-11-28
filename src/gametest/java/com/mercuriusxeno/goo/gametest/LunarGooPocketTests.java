package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.lunar.GooPocketFeature;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Gametests for lunar's world goo pockets: the pocket feature placed on ground a test lays high in goo:lunar lays
 * world goo cells holding no more layers together than the feature's volume, and 200 ticks after the site's chunk
 * ticks they still stand, holding the same layers, however they levelled out.
 * decision frost-astral-and-xeno-goo-spawn-periodically-on-lunar
 */
@RegistersGameTests
public final class LunarGooPocketTests {
    private static final String GOO_POCKET_STANDS_IN_LUNAR = "goo_pocket_stands_in_lunar";

    private static final ResourceKey<Feature> GOO_POCKET = ResourceKey.create(Registries.FEATURE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "goo_pocket"));

    /** Where the test lays its ground, apart from lunar's other sites, high over any terrain. */
    private static final int SITE_X = 2248;
    private static final int SITE_Z = 8;
    private static final int SITE_UNDER_THE_TOP = 32;
    /** The ground the test lays reaches this far each way of the pocket, this deep under its top. */
    private static final int GROUND_REACH = 6;
    private static final int GROUND_DEPTH = 3;
    /** The chunks each way of the site that stand loaded before its own chunk ticks. */
    private static final int TICKING_REACH = 2;
    /** Pocket goo is sought this far each way of the pocket and over its height. */
    private static final int CELL_REACH = 5;
    private static final int WATCH_TICKS = 200;
    private static final long PLACE_SEED = 1838L;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_FEATURE = "The feature registry should hold goo:goo_pocket as a GooPocketFeature";
    private static final String NOT_PLACED = "goo:goo_pocket should place on the ground laid at %s";
    private static final String NO_CELLS = "The pocket placed at %s should lay world goo cells, finds none";
    private static final String OVER_VOLUME = "The pocket's cells should hold at most %d layers together, hold %d: %s";
    private static final String CELLS_CHANGED = "The pocket's cells should still hold their layers %d ticks on: %d layers"
            + " in %s became %d in %s";

    private LunarGooPocketTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, GOO_POCKET_STANDS_IN_LUNAR, LunarGooPocketTests::gooPocketStandsInLunar);
    }

    /**
     * The pocket placed on laid ground lays world goo cells of lunar's pocket goo types, holding together no more
     * layers than the feature's volume, and from the tick the site's chunk ticks those cells still stand after the
     * watch's ticks, holding the same layers together.
     *
     * @param helper the gametest helper
     */
    public static void gooPocketStandsInLunar(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        BlockPos origin = layGround(lunar);
        Optional<Feature> feature = lunar.registryAccess().lookupOrThrow(Registries.FEATURE).getOptional(GOO_POCKET);
        helper.assertTrue(feature.isPresent() && feature.get() instanceof GooPocketFeature, NO_FEATURE);
        GooPocketFeature pocket = (GooPocketFeature) feature.orElseThrow();
        boolean placed = pocket.place(lunar, lunar.getChunkSource().getGenerator(), RandomSource.create(PLACE_SEED),
                origin);
        helper.assertTrue(placed, String.format(NOT_PLACED, origin));
        Map<BlockPos, Integer> laid = pocketCells(lunar, origin);
        helper.assertFalse(laid.isEmpty(), String.format(NO_CELLS, origin));
        int held = laid.values().stream().mapToInt(Integer::intValue).sum();
        helper.assertTrue(held <= pocket.volume(), String.format(OVER_VOLUME, pocket.volume(), held, laid));
        int[] watched = {-1};
        helper.onEachTick(() -> {
            if (watched[0] < 0) {
                if (lunar.isPositionEntityTicking(origin)) {
                    watched[0] = 0;
                }
                return;
            }
            if (++watched[0] < WATCH_TICKS) {
                return;
            }
            Map<BlockPos, Integer> after = pocketCells(lunar, origin);
            lunar.setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, false);
            int stillHeld = after.values().stream().mapToInt(Integer::intValue).sum();
            helper.assertTrue(stillHeld == held, String.format(CELLS_CHANGED, WATCH_TICKS, held, laid, stillHeld,
                    after));
            helper.succeed();
        });
    }

    /**
     * Forces the site's chunk and loads the chunks around it, and lays a flat slab of anorthosite there.
     *
     * @param lunar goo:lunar
     * @return the block over the slab's top at its middle
     */
    private static BlockPos layGround(ServerLevel lunar) {
        int chunkX = SITE_X >> 4;
        int chunkZ = SITE_Z >> 4;
        lunar.setChunkForced(chunkX, chunkZ, true);
        for (int dx = -TICKING_REACH; dx <= TICKING_REACH; dx++) {
            for (int dz = -TICKING_REACH; dz <= TICKING_REACH; dz++) {
                lunar.getChunk(chunkX + dx, chunkZ + dz);
            }
        }
        BlockPos origin = new BlockPos(SITE_X, lunar.getMaxY() - SITE_UNDER_THE_TOP, SITE_Z);
        BlockState ground = GooBlocks.ANORTHOSITE.get().defaultBlockState();
        BlockPos.betweenClosed(origin.offset(-GROUND_REACH, -GROUND_DEPTH, -GROUND_REACH),
                origin.offset(GROUND_REACH, -1, GROUND_REACH)).forEach(at -> lunar.setBlockAndUpdate(at.immutable(),
                ground));
        return origin;
    }

    /**
     * The volume cells within reach of the pocket, each with its layers.
     *
     * @param lunar  goo:lunar
     * @param origin where the pocket was placed
     * @return each cell's position and layers
     */
    private static Map<BlockPos, Integer> pocketCells(ServerLevel lunar, BlockPos origin) {
        Map<BlockPos, Integer> cells = new HashMap<>();
        for (BlockPos at : BlockPos.betweenClosed(origin.offset(-CELL_REACH, -1, -CELL_REACH),
                origin.offset(CELL_REACH, CELL_REACH, CELL_REACH))) {
            BlockState state = lunar.getBlockState(at);
            if (state.getBlock() instanceof VolumeCellBlock) {
                cells.put(at.immutable(), VolumeCellBlock.layersOf(state));
            }
        }
        return cells;
    }
}
