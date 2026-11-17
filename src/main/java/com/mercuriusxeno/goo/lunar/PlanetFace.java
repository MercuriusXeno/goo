package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.PlanetFacePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The blue planet's face, the overworld's own: the server samples the overworld's biomes on a grid around world spawn
 * through the world's biome source, the call /locate biome makes, so no chunk is generated for it; each biome gives
 * one color by its tags, ocean blue, land green, desert tan and snow white. The face is built once a server and sent
 * to each player as they join, so each world's planet shows its own continents and seas.
 * decision planet-face-is-procgen-from-the-overworlds-biomes
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class PlanetFace {

    /** The face is a square of pixels this many wide, west to east and north to south. */
    public static final int SIZE = 32;
    /**
     * How far apart the grid samples the overworld, in blocks: the face spans eight thousand blocks, wide enough that
     * continents and seas show on the disc.
     */
    public static final int BLOCKS_PER_PIXEL = 256;
    /** The column and the row of the pixel standing on the grid's center. */
    public static final int CENTER = SIZE / 2;
    /** The height the grid samples at, the overworld's sea level, where the surface biomes stand. */
    public static final int SAMPLE_Y = 63;

    /** Ocean and river. */
    public static final int SEA = 0xFF3A6EE0;
    /** Desert, badlands and beach. */
    public static final int TAN = 0xFFD9C27C;
    /** Snowy and frozen ground and water. */
    public static final int SNOW = 0xFFEEF3F8;
    /** Any other land. */
    public static final int LAND = 0xFF4E9A3C;

    private static final List<TagKey<Biome>> SNOWY_OR_FROZEN = List.of(Tags.Biomes.IS_SNOWY, Tags.Biomes.IS_ICY,
            Tags.Biomes.IS_AQUATIC_ICY);
    private static final List<TagKey<Biome>> WATER = List.of(BiomeTags.IS_OCEAN, BiomeTags.IS_RIVER);
    private static final List<TagKey<Biome>> SANDY = List.of(Tags.Biomes.IS_DESERT, BiomeTags.IS_BADLANDS,
            BiomeTags.IS_BEACH);

    /** The face the running server holds, read and built on the server thread alone. */
    private static @Nullable Held held;

    /**
     * The tags of the biome at a block, as the overworld's biome source answers it.
     */
    @FunctionalInterface
    public interface BiomeTagsAt {
        /**
         * The tags the biome at a block carries.
         *
         * @param blockX the block's x
         * @param blockY the block's y
         * @param blockZ the block's z
         * @return whether the biome there carries a tag
         */
        Predicate<TagKey<Biome>> at(int blockX, int blockY, int blockZ);
    }

    /**
     * The face a server holds.
     *
     * @param server the server it was built for
     * @param colors its colors
     */
    private record Held(MinecraftServer server, int[] colors) {
    }

    private PlanetFace() {
    }

    /**
     * The color a biome gives the face by its tags: snow and frozen water white first, so frozen seas read as ice,
     * then ocean and river blue, desert, badlands and beach tan, and any other land green.
     *
     * @param biomeIs whether the biome carries a tag
     * @return the face color, ARGB
     */
    public static int colorOf(Predicate<TagKey<Biome>> biomeIs) {
        if (SNOWY_OR_FROZEN.stream().anyMatch(biomeIs)) {
            return SNOW;
        }
        if (WATER.stream().anyMatch(biomeIs)) {
            return SEA;
        }
        return SANDY.stream().anyMatch(biomeIs) ? TAN : LAND;
    }

    /**
     * Walks the grid around a center, one sample a pixel: columns run west to east and rows north to south, and the
     * pixel half the face's size in on each stands on the center itself.
     *
     * @param biomes  the biome tags at a block
     * @param centerX the center's x, world spawn's
     * @param centerZ the center's z, world spawn's
     * @return the face's colors, ARGB, row by row from the north
     */
    public static int[] sample(BiomeTagsAt biomes, int centerX, int centerZ) {
        int[] colors = new int[SIZE * SIZE];
        for (int pixel = 0; pixel < colors.length; pixel++) {
            int x = centerX + (pixel % SIZE - CENTER) * BLOCKS_PER_PIXEL;
            int z = centerZ + (pixel / SIZE - CENTER) * BLOCKS_PER_PIXEL;
            colors[pixel] = colorOf(biomes.at(x, SAMPLE_Y, z));
        }
        return colors;
    }

    /**
     * Builds the face from an overworld's biome source around its world spawn, generating no chunk.
     *
     * @param overworld the overworld
     * @return the face's colors, ARGB, row by row from the north
     */
    public static int[] build(ServerLevel overworld) {
        BlockPos spawn = overworld.getRespawnData().globalPos().pos();
        return sample((x, y, z) -> overworld.getUncachedNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
                QuartPos.fromBlock(z))::is, spawn.getX(), spawn.getZ());
    }

    /**
     * The face a server holds, built the first time it is asked for and kept.
     *
     * @param server the server
     * @return the face's colors, ARGB, row by row from the north
     */
    public static int[] held(MinecraftServer server) {
        Held kept = held;
        if (kept == null || kept.server() != server) {
            kept = new Held(server, build(server.overworld()));
            held = kept;
        }
        return kept.colors();
    }

    /**
     * Sends a player the face their server holds.
     *
     * @param player the player
     * @param send   sends a payload to the player
     */
    public static void syncTo(ServerPlayer player, Consumer<CustomPacketPayload> send) {
        send.accept(new PlanetFacePayload(held(player.level().getServer())));
    }

    /**
     * Sends a joining player the planet's face.
     *
     * @param event the login event
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player, payload -> {
                // A listener that never negotiated the mod's channels, a gametest's mock player, gets no face.
                if (player.connection.hasChannel(payload)) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            });
        }
    }
}
