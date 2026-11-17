package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.PlanetFace;
import com.mercuriusxeno.goo.network.PlanetFacePayload;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for the blue planet's face: the server holds one face map for its overworld, sampled from the world's own
 * biome source around world spawn with no chunk generated for it, and sends a player that map.
 * decision planet-face-is-procgen-from-the-overworlds-biomes
 */
@RegistersGameTests
public final class PlanetFaceTests {
    private static final String PLANET_FACE_IS_SAMPLED_FROM_THE_OVERWORLDS_BIOMES =
            "planet_face_is_sampled_from_the_overworlds_biomes";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long NANOS_PER_MICRO = 1000L;

    private static final String SIZED = "The face map should hold %d pixels, holds %d";
    private static final String HELD_ONCE = "The server should hold one face map, answered two";
    private static final String HELD_IS_BUILT = "The held face map should read as one built from the biome source";
    private static final String OVER_SPAWN = "The pixel over world spawn %s should read %s, the color of its biome,"
            + " reads %s";
    private static final String NO_CHUNK = "Building the face should generate no chunk, yet chunk %d, %d stands";
    private static final String SENT = "A player should be sent the server's face map, was sent %s";

    private PlanetFaceTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PLANET_FACE_IS_SAMPLED_FROM_THE_OVERWORLDS_BIOMES,
                PlanetFaceTests::planetFaceIsSampledFromTheOverworldsBiomes);
    }

    /**
     * The face built from the overworld's biome source holds the face's size, its far corner's chunk still stands
     * ungenerated, the server answers one held map reading the same, its pixel over world spawn holds the color of
     * the biome the level answers there, and a mock player is sent it.
     *
     * @param helper the gametest helper
     */
    public static void planetFaceIsSampledFromTheOverworldsBiomes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getRespawnData().globalPos().pos();
        long startedAt = System.nanoTime();
        int[] built = PlanetFace.build(overworld);
        LOGGER.info("The blue planet's face took {} microseconds to build", (System.nanoTime() - startedAt)
                / NANOS_PER_MICRO);
        helper.assertTrue(built.length == PlanetFace.SIZE * PlanetFace.SIZE,
                String.format(SIZED, PlanetFace.SIZE * PlanetFace.SIZE, built.length));
        int cornerX = SectionPos.blockToSectionCoord(spawn.getX() - PlanetFace.SIZE / 2 * PlanetFace.BLOCKS_PER_PIXEL);
        int cornerZ = SectionPos.blockToSectionCoord(spawn.getZ() - PlanetFace.SIZE / 2 * PlanetFace.BLOCKS_PER_PIXEL);
        helper.assertFalse(overworld.getChunkSource().hasChunk(cornerX, cornerZ),
                String.format(NO_CHUNK, cornerX, cornerZ));
        int[] held = PlanetFace.held(server);
        helper.assertTrue(held == PlanetFace.held(server), HELD_ONCE);
        helper.assertTrue(Arrays.equals(held, built), HELD_IS_BUILT);
        int overSpawn = held[PlanetFace.SIZE / 2 * PlanetFace.SIZE + PlanetFace.SIZE / 2];
        int spawnColor = PlanetFace.colorOf(overworld.getUncachedNoiseBiome(QuartPos.fromBlock(spawn.getX()),
                QuartPos.fromBlock(PlanetFace.SAMPLE_Y), QuartPos.fromBlock(spawn.getZ()))::is);
        helper.assertTrue(overSpawn == spawnColor, String.format(OVER_SPAWN, spawn,
                Integer.toHexString(spawnColor), Integer.toHexString(overSpawn)));
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        List<CustomPacketPayload> sent = new ArrayList<>();
        PlanetFace.syncTo(player, sent::add);
        boolean sentTheMap = sent.size() == 1 && sent.getFirst() instanceof PlanetFacePayload face
                && Arrays.equals(face.colors(), held);
        helper.assertTrue(sentTheMap, String.format(SENT, sent));
        server.getPlayerList().remove(player);
        helper.succeed();
    }
}
