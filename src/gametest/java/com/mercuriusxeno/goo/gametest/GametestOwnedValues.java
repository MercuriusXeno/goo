package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.data.GooValues;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Lays the goo values the gametests build with, stone and cobblestone in rock, oak planks lacking it, bedrock
 * denied and the rest owned_base_values.json names, over every pack the test server loads, so tuning the shipped
 * base_values breaks no gametest. A layer over the packs rather than a world datapack, since the world's packs sit
 * below the mod's own file and its groups would paint over the pins.
 * Decision no-test-asserts-a-shipped-base-value.
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GametestOwnedValues {
    private static final String OWNED_VALUES = "/goo_gametest/owned_base_values.json";

    private GametestOwnedValues() {
    }

    /**
     * Lays the owned values over the packs once the server, and its value registry, has started.
     *
     * @param event the server started event
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        apply(event.getServer());
    }

    /**
     * Loads the server's values from its packs with the owned values laid over them, and derives the recipes again.
     *
     * @param server the test server
     */
    public static void apply(MinecraftServer server) {
        GooValueRegistry registry = GooValues.registryOf(server);
        registry.loadBaseValuesOverPacks(server, ownedValues(), packId -> false);
        registry.deriveFromRecipes(server);
    }

    private static String ownedValues() {
        try (InputStream in = GametestOwnedValues.class.getResourceAsStream(OWNED_VALUES)) {
            return new String(Objects.requireNonNull(in, OWNED_VALUES).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
