package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.UpdraftTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for lunar's airlessness: a player in goo:lunar loses one air a tick as underwater, takes drowning damage
 * when the air runs out, and keeps it under water breathing, while a player outside lunar refills as vanilla does; a
 * player an updraft column carries in lunar gains air while one beside the column loses it.
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

    /** Where the updraft lands in lunar, far from the open ground the drain test stands on. */
    private static final int UPDRAFT_SITE_X = 2056;
    private static final int UPDRAFT_SITE_Z = 8;
    /** Beside the blob inside the column's width, and well outside it. */
    private static final int INSIDE_OFFSET = 1;
    private static final int OUTSIDE_OFFSET = 5;
    private static final String COLUMN_REFILLS = "In lunar the player in the column should gain air and the one outside"
            + " lose it, from %d: inside %d, outside %d";

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
     * An updraft lands on lunar ground between two survival players: over the same ticks, the player the column
     * carries gains air and the player beside the column loses it. The column's chunk is forced so its blob ticks,
     * and the players breathe through their own base tick, as the drain test runs them.
     *
     * @param helper the gametest helper
     */
    public static void updraftRefillsAirInLunar(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(LUNAR);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        int chunkX = UPDRAFT_SITE_X >> 4;
        int chunkZ = UPDRAFT_SITE_Z >> 4;
        lunar.setChunkForced(chunkX, chunkZ, true);
        lunar.getChunk(chunkX, chunkZ);
        BlockPos floor = new BlockPos(UPDRAFT_SITE_X,
                lunar.getHeight(Heightmap.Types.MOTION_BLOCKING, UPDRAFT_SITE_X, UPDRAFT_SITE_Z) - 1, UPDRAFT_SITE_Z);
        ServerPlayer inside = placeInLunar(helper, lunar, floor.above().east(INSIDE_OFFSET));
        ServerPlayer outside = placeInLunar(helper, lunar, floor.above().east(OUTSIDE_OFFSET));
        UpdraftTests.landUpdraft(lunar, floor);
        boolean[] read = {false};
        helper.onEachTick(() -> {
            if (read[0]) {
                return;
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
     * Places a survival player in lunar standing on a cell, its air part full.
     *
     * @param helper the gametest helper
     * @param lunar  goo:lunar
     * @param cell   the cell the player stands in
     * @return the player
     */
    private static ServerPlayer placeInLunar(GameTestHelper helper, ServerLevel lunar, BlockPos cell) {
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        travel(player, new TeleportTransition(lunar, Vec3.atBottomCenterOf(cell), Vec3.ZERO, 0F, 0F,
                TeleportTransition.DO_NOTHING));
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
