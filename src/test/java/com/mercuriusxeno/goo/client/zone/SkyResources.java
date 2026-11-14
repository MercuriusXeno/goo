package com.mercuriusxeno.goo.client.zone;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.dimension.DimensionType;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Reads the sky a shipped dimension type names and the shader text the
 * stars draw from, through the classpath, as the game reads them.
 */
public final class SkyResources {

    /** The dimension type the Zone's rooms stand in. */
    public static final String POCKET = "data/goo/dimension_type/pocket.json";
    /** Vanilla's overworld dimension type, which the Minecraft jar carries. */
    public static final String OVERWORLD = "data/minecraft/dimension_type/overworld.json";
    /** The attribute NeoForge picks a dimension's custom sky renderer by. */
    private static final String CUSTOM_SKYBOX = "neoforge:custom_skybox";
    /** The custom sky a dimension naming none answers, which draws vanilla's own sky. */
    private static final Identifier DEFAULT_SKYBOX = Identifier.withDefaultNamespace("default");

    private SkyResources() {
    }

    /**
     * The custom sky a dimension type's attributes name.
     *
     * @param resource the dimension type's classpath resource
     * @return the custom skybox id, vanilla's default where it names none
     */
    public static Identifier customSkyboxOf(String resource) {
        JsonObject type = JsonParser.parseString(text(resource)).getAsJsonObject();
        JsonObject attributes = type.has("attributes") ? type.getAsJsonObject("attributes") : new JsonObject();
        return attributes.has(CUSTOM_SKYBOX) ? Identifier.parse(attributes.get(CUSTOM_SKYBOX).getAsString())
                : DEFAULT_SKYBOX;
    }

    /**
     * The skybox a dimension type draws.
     *
     * @param resource the dimension type's classpath resource
     * @return its skybox
     */
    public static DimensionType.Skybox skyboxOf(String resource) {
        JsonObject type = JsonParser.parseString(text(resource)).getAsJsonObject();
        return DimensionType.Skybox.valueOf(type.get("skybox").getAsString().toUpperCase(Locale.ROOT));
    }

    /**
     * The text of a classpath resource.
     *
     * @param resource the resource's path
     * @return its text
     */
    public static String text(String resource) {
        try (InputStream stream = SkyResources.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Classpath holds no " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
