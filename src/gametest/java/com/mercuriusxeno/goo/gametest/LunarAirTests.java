package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for lunar's airlessness: a player in goo:lunar loses one air a tick as underwater, takes drowning damage
 * when the air runs out, and keeps it under water breathing, while a player outside lunar refills as vanilla does; a
 * player an Updraft cell lifts in lunar gains air while one beside the column loses it.
 * decision astral-air-drains-as-underwater
 * decision updraft-refills-the-air-of-entities-it-propels
 */
@RegistersGameTests
public final class LunarAirTests {
    private static final String LUNAR_AIR_DRAINS_AS_UNDERWATER = "lunar_air_drains_as_underwater";
    private static final String UPDRAFT_REFILLS_AIR_IN_LUNAR = "updraft_refills_air_in_lunar";

    private static final ResourceKey<Level> LUNAR = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar"));
    /** Vanilla takes one air a tick from a breath held underwater, and refills four a tick in open air. */
    private static final int TICKS = 20;
    private static final int AIR_TAKEN_A_TICK = 1;
    private static final int AIR_REFILLED_A_TICK = 4;
    private static final int PART_FULL_AIR = 100;
    /** One tick before vanilla's drowning threshold of -20 air. */
    private static final int LAST_BREATH = -19;
    private static final int WATER_BREATHING_TICKS = 600;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String DRAINS = "A player in lunar should lose %d air over %d ticks, %d to %d";
    private static final String DROWNS = "A player out of air in lunar should take drowning damage, health %s to %s";
    private static final String HELD = "Water breathing should hold a player's air in lunar, %d to %d";
    private static final String REFILLS = "A player outside lunar should refill %d air a tick, %d to %d";

    /**
     * Where the test lays its own stone and Updraft cell in lunar, far from the drain test's ground and high over
     * any terrain, so the site stands as the test builds it.
     */
    private static final int UPDRAFT_SITE_X = 2056;
    private static final int UPDRAFT_SITE_Z = 8;
    private static final int UPDRAFT_SITE_UNDER_THE_TOP = 32;
    private static final int UPDRAFT_LAYERS = 6;
    /** How far east of the cell the player outside the column stands. */
    private static final int OUTSIDE_OFFSET = 5;
    private static final String COLUMN_REFILLS = "In lunar the player the Updraft cell lifts should gain air and the one"
            + " outside its column lose it, from %d: inside %d, outside %d";

    private LunarAirTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_AIR_DRAINS_AS_UNDERWATER, LunarAirTests::lunarAirDrainsAsUnderwater);
        GooTestFunctions.registerTest(registrar, UPDRAFT_REFILLS_AIR_IN_LUNAR, LunarAirTests::updraftRefillsAirInLunar);
    }

    /**
     * In goo:lunar a survival player's air drains one a tick, drowning damage lands when it runs out, water
     * breathing holds it, and a player in the overworld refills four a tick.
     *
     * @param helper the gametest helper
     */
    public static void lunarAirDrainsAsUnderwater(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel lunar = overworld.getServer().getLevel(LUNAR);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        lunar.getChunk(0, 0);
        Vec3 openGround = new Vec3(0.5, lunar.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0) + 1, 0.5);
        travel(player, new TeleportTransition(lunar, openGround, Vec3.ZERO, 0F, 0F, TeleportTransition.DO_NOTHING));

        int full = player.getMaxAirSupply();
        player.setAirSupply(full);
        tick(player, TICKS);
        helper.assertTrue(player.getAirSupply() == full - TICKS * AIR_TAKEN_A_TICK,
                String.format(DRAINS, TICKS * AIR_TAKEN_A_TICK, TICKS, full, player.getAirSupply()));

        player.setAirSupply(LAST_BREATH);
        float before = player.getHealth();
        tick(player, 1);
        helper.assertTrue(player.getHealth() < before, String.format(DROWNS, before, player.getHealth()));

        player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, WATER_BREATHING_TICKS));
        player.setAirSupply(PART_FULL_AIR);
        tick(player, TICKS);
        helper.assertTrue(player.getAirSupply() >= PART_FULL_AIR,
                String.format(HELD, PART_FULL_AIR, player.getAirSupply()));
        overworld.getServer().getPlayerList().remove(player);

        ServerPlayer outside = SurvivalPlayers.placeIn(helper);
        outside.setAirSupply(PART_FULL_AIR);
        tick(outside, 1);
        helper.assertTrue(outside.getAirSupply() == PART_FULL_AIR + AIR_REFILLED_A_TICK,
                String.format(REFILLS, AIR_REFILLED_A_TICK, PART_FULL_AIR, outside.getAirSupply()));
        overworld.getServer().getPlayerList().remove(outside);
        helper.succeed();
    }

    /**
     * The test lays its own stone and a six-layer Updraft cell high in lunar, reading no terrain height, and stands
     * one survival player on the cell and one beside the column: over the same ticks the first gains air and the
     * second loses it. The site's chunk is forced so the cell ticks, and the players breathe through their own base
     * tick, as the drain test runs them. The air is read only from the tick the site's entities tick: under a full
     * suite a freshly forced lunar chunk loads its entities late, and until then the cell's scan sees no player.
     * decision updraft-refills-the-air-of-entities-it-propels
     *
     * @param helper the gametest helper
     */
    public static void updraftRefillsAirInLunar(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(LUNAR);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        int chunkX = UPDRAFT_SITE_X >> 4;
        int chunkZ = UPDRAFT_SITE_Z >> 4;
        lunar.setChunkForced(chunkX, chunkZ, true);
        BlockPos cell = new BlockPos(UPDRAFT_SITE_X, lunar.getMaxY() - UPDRAFT_SITE_UNDER_THE_TOP, UPDRAFT_SITE_Z);
        layWalledCell(lunar, cell);
        Vec3 onTheGoo = Vec3.atBottomCenterOf(cell).add(0, (double) UPDRAFT_LAYERS / VolumeCellBlock.MAX_LAYERS, 0);
        ServerPlayer inside = placeInLunar(helper, lunar, onTheGoo);
        ServerPlayer outside = placeInLunar(helper, lunar, onTheGoo.add(OUTSIDE_OFFSET, 0, 0));
        boolean[] read = {false};
        boolean[] started = {false};
        helper.onEachTick(() -> {
            if (read[0]) {
                return;
            }
            if (!started[0]) {
                if (!lunar.isPositionEntityTicking(cell)) {
                    return;
                }
                started[0] = true;
                inside.setAirSupply(PART_FULL_AIR);
                outside.setAirSupply(PART_FULL_AIR);
            }
            inside.baseTick();
            outside.baseTick();
            if (inside.getAirSupply() <= PART_FULL_AIR) {
                return;
            }
            read[0] = true;
            int insideAir = inside.getAirSupply();
            int outsideAir = outside.getAirSupply();
            lunar.getServer().getPlayerList().remove(inside);
            lunar.getServer().getPlayerList().remove(outside);
            lunar.setChunkForced(chunkX, chunkZ, false);
            helper.assertTrue(outsideAir < PART_FULL_AIR,
                    String.format(COLUMN_REFILLS, PART_FULL_AIR, insideAir, outsideAir));
            helper.succeed();
        });
    }

    /**
     * Lays a stone floor under an Updraft cell of the test's layers and stone on its four sides, so the cell stands
     * walled and pours none of its layers away.
     *
     * @param lunar goo:lunar
     * @param cell  where the cell stands
     */
    private static void layWalledCell(ServerLevel lunar, BlockPos cell) {
        BlockPos.betweenClosed(cell.offset(-1, -1, -1), cell.offset(1, -1, 1))
                .forEach(floor -> lunar.setBlockAndUpdate(floor.immutable(), Blocks.STONE.defaultBlockState()));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            lunar.setBlockAndUpdate(cell.relative(side), Blocks.STONE.defaultBlockState());
        }
        lunar.setBlockAndUpdate(cell, GooBlocks.TYPHOON_UPDRAFT.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, UPDRAFT_LAYERS));
    }

    /**
     * Places a survival player in lunar with its feet at a point, its air part full.
     *
     * @param helper the gametest helper
     * @param lunar  goo:lunar
     * @param feet   where the player's feet stand
     * @return the player
     */
    private static ServerPlayer placeInLunar(GameTestHelper helper, ServerLevel lunar, Vec3 feet) {
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        travel(player, new TeleportTransition(lunar, feet, Vec3.ZERO, 0F, 0F, TeleportTransition.DO_NOTHING));
        player.setAirSupply(PART_FULL_AIR);
        return player;
    }

    /**
     * Carries the player to another level as a client would arrive: a real client acknowledges the dimension change
     * and reports itself loaded, and until both the server holds the player invulnerable.
     *
     * @param player     the player
     * @param transition where the player goes
     */
    private static void travel(ServerPlayer player, TeleportTransition transition) {
        player.teleport(transition);
        player.hasChangedDimension();
        player.connection.markClientLoaded();
    }

    private static void tick(ServerPlayer player, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            player.baseTick();
        }
    }
}
