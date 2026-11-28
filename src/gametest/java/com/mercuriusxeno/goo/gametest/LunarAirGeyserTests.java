package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Gametests for lunar's air geysers: the geyser feature placed on ground a test lays high in goo:lunar digs a hole
 * holding typhoon's Updraft cells, which keep their layers, refill the air of a player out of it standing over them,
 * and leave trace Updraft cells on the ground around the hole.
 * decision air-geysers-of-updraft-goo-dot-lunar
 */
@RegistersGameTests
public final class LunarAirGeyserTests {
    private static final String AIR_GEYSER_KEEPS_ITS_UPDRAFT = "air_geyser_keeps_its_updraft";
    private static final String AIR_GEYSER_REFILLS_AIR_IN_LUNAR = "air_geyser_refills_air_in_lunar";

    private static final ResourceKey<Feature> AIR_GEYSER = ResourceKey.create(Registries.FEATURE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "air_geyser"));

    /** Where each test lays its ground, apart from each other and from lunar's other sites, high over any terrain. */
    private static final int KEEPS_SITE_X = 2120;
    private static final int REFILLS_SITE_X = 2184;
    private static final int SITE_Z = 8;
    private static final int SITE_UNDER_THE_TOP = 32;
    /** The ground the test lays reaches this far each way of the geyser, this deep under its top. */
    private static final int GROUND_REACH = 10;
    private static final int GROUND_DEPTH = 6;
    /** The chunks each way of the site that stand loaded before its own chunk ticks. */
    private static final int TICKING_REACH = 2;
    /** Trace goo is sought this far each way of the geyser, on the ground's top. */
    private static final int TRACE_REACH = 8;
    /** How long the hole's cells are watched for a change in their layers. */
    private static final int WATCH_TICKS = 200;
    private static final long PLACE_SEED = 1478L;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_FEATURE = "The feature registry should hold goo:air_geyser";
    private static final String NOT_PLACED = "goo:air_geyser should place on the ground laid at %s";
    private static final String NO_HOLE = "The geyser placed at %s should hold Updraft cells in a hole under the ground's"
            + " top, finds none";
    private static final String LAYERS_CHANGED = "The geyser's Updraft cells should hold their layers %d ticks on: %s"
            + " became %s";
    private static final String NO_TRACE = "Trace Updraft cells should stand on the ground within %d blocks of the hole"
            + " at %s, finds none";

    private LunarAirGeyserTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, AIR_GEYSER_KEEPS_ITS_UPDRAFT, LunarAirGeyserTests::airGeyserKeepsItsUpdraft);
        GooTestFunctions.registerTest(registrar, AIR_GEYSER_REFILLS_AIR_IN_LUNAR,
                LunarAirGeyserTests::airGeyserRefillsAirInLunar);
    }

    /**
     * The geyser placed on laid ground digs a hole under the ground's top holding Updraft cells, and from the tick
     * the site's chunk ticks, those cells hold the same layers for the watch's ticks: Updraft never depletes.
     * decision updraft-goo-is-semi-permanent-and-does-not-deplete
     *
     * @param helper the gametest helper
     */
    public static void airGeyserKeepsItsUpdraft(GameTestHelper helper) {
        ServerLevel lunar = lunarOf(helper);
        BlockPos origin = placeGeyser(helper, lunar, KEEPS_SITE_X);
        Map<BlockPos, Integer> hole = updraftCells(lunar, origin, -GROUND_DEPTH, -1);
        helper.assertFalse(hole.isEmpty(), String.format(NO_HOLE, origin));
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
            Map<BlockPos, Integer> after = updraftCells(lunar, origin, -GROUND_DEPTH, -1);
            release(lunar, origin);
            helper.assertTrue(after.equals(hole), String.format(LAYERS_CHANGED, WATCH_TICKS, hole, after));
            helper.succeed();
        });
    }

    /**
     * A survival player with no air standing on the geyser's goo in goo:lunar regains air, breathing through its own
     * base tick from the tick the site's chunk ticks its entities; and trace Updraft cells stand on the ground's top
     * around the hole.
     * decision updraft-refills-the-air-of-entities-it-propels
     *
     * @param helper the gametest helper
     */
    public static void airGeyserRefillsAirInLunar(GameTestHelper helper) {
        ServerLevel lunar = lunarOf(helper);
        BlockPos origin = placeGeyser(helper, lunar, REFILLS_SITE_X);
        Map<BlockPos, Integer> hole = updraftCells(lunar, origin, -GROUND_DEPTH, -1);
        helper.assertFalse(hole.isEmpty(), String.format(NO_HOLE, origin));
        helper.assertFalse(updraftCells(lunar, origin, 0, 0).isEmpty(), String.format(NO_TRACE, TRACE_REACH, origin));
        Map.Entry<BlockPos, Integer> cell = hole.entrySet().iterator().next();
        Vec3 onTheGoo = Vec3.atBottomCenterOf(cell.getKey())
                .add(0, (double) cell.getValue() / VolumeCellBlock.MAX_LAYERS, 0);
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        player.teleport(new TeleportTransition(lunar, onTheGoo, Vec3.ZERO, 0F, 0F, TeleportTransition.DO_NOTHING));
        player.hasChangedDimension();
        player.connection.markClientLoaded();
        boolean[] started = {false};
        helper.onEachTick(() -> {
            if (!started[0]) {
                if (!lunar.isPositionEntityTicking(cell.getKey())) {
                    return;
                }
                started[0] = true;
                player.setAirSupply(0);
            }
            player.baseTick();
            if (player.getAirSupply() <= 0) {
                return;
            }
            lunar.getServer().getPlayerList().remove(player);
            release(lunar, origin);
            helper.succeed();
        });
    }

    private static ServerLevel lunarOf(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        return lunar;
    }

    /**
     * Forces the site's chunk and loads the chunks around it, lays a flat slab of anorthosite there, and places the
     * geyser feature on the slab's top.
     *
     * @param helper the gametest helper
     * @param lunar  goo:lunar
     * @param siteX  the site's x
     * @return where the geyser was placed, the block over the slab's top
     */
    private static BlockPos placeGeyser(GameTestHelper helper, ServerLevel lunar, int siteX) {
        int chunkX = siteX >> 4;
        int chunkZ = SITE_Z >> 4;
        lunar.setChunkForced(chunkX, chunkZ, true);
        for (int dx = -TICKING_REACH; dx <= TICKING_REACH; dx++) {
            for (int dz = -TICKING_REACH; dz <= TICKING_REACH; dz++) {
                lunar.getChunk(chunkX + dx, chunkZ + dz);
            }
        }
        BlockPos origin = new BlockPos(siteX, lunar.getMaxY() - SITE_UNDER_THE_TOP, SITE_Z);
        BlockState ground = GooBlocks.ANORTHOSITE.get().defaultBlockState();
        BlockPos.betweenClosed(origin.offset(-GROUND_REACH, -GROUND_DEPTH, -GROUND_REACH),
                origin.offset(GROUND_REACH, -1, GROUND_REACH)).forEach(at -> lunar.setBlockAndUpdate(at.immutable(),
                ground));
        Optional<Feature> geyser = lunar.registryAccess().lookupOrThrow(Registries.FEATURE).getOptional(AIR_GEYSER);
        helper.assertTrue(geyser.isPresent(), NO_FEATURE);
        boolean placed = geyser.orElseThrow().place(lunar, lunar.getChunkSource().getGenerator(),
                RandomSource.create(PLACE_SEED), origin);
        helper.assertTrue(placed, String.format(NOT_PLACED, origin));
        return origin;
    }

    /**
     * The Updraft cells within the trace reach of the geyser, between two heights relative to it, each with its
     * layers.
     *
     * @param lunar  goo:lunar
     * @param origin where the geyser was placed
     * @param fromDy the lowest height read, relative to the origin
     * @param toDy   the highest height read, relative to the origin
     * @return each cell's position and layers
     */
    private static Map<BlockPos, Integer> updraftCells(ServerLevel lunar, BlockPos origin, int fromDy, int toDy) {
        Map<BlockPos, Integer> cells = new HashMap<>();
        for (BlockPos at : BlockPos.betweenClosed(origin.offset(-TRACE_REACH, fromDy, -TRACE_REACH),
                origin.offset(TRACE_REACH, toDy, TRACE_REACH))) {
            BlockState state = lunar.getBlockState(at);
            if (state.is(GooBlocks.TYPHOON_UPDRAFT.get())) {
                cells.put(at.immutable(), VolumeCellBlock.layersOf(state));
            }
        }
        return cells;
    }

    private static void release(ServerLevel lunar, BlockPos origin) {
        lunar.setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, false);
    }
}
