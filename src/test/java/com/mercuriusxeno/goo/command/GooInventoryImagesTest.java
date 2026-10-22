package com.mercuriusxeno.goo.command;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the inventory's texture lookup over a fake asset listing: an item's own texture first, and a block
 * item without one taking its block model's top face through the model's parents.
 */
class GooInventoryImagesTest {

    private static final byte[] PNG = {1};

    private static Function<String, Optional<byte[]>> assets(Map<String, String> files) {
        return path -> Optional.ofNullable(files.get(path))
                .map(text -> text.isEmpty() ? PNG : text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * An item with its own texture draws with it.
     */
    @Test
    void itemTakesItsOwnTexture() {
        Map<String, String> files = Map.of("assets/minecraft/textures/item/iron_nugget.png", "");
        assertEquals(Optional.of("assets/minecraft/textures/item/iron_nugget.png"),
                GooInventoryImages.texturePathFor(Identifier.withDefaultNamespace("iron_nugget"), assets(files)));
    }

    /**
     * A block item without an item texture takes its block's top face, read through the model's parent.
     */
    @Test
    void blockItemTakesItsBlockTop() {
        Map<String, String> files = Map.of(
                "assets/minecraft/items/oak_log.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:block/oak_log\"}}",
                "assets/minecraft/models/block/oak_log.json",
                "{\"parent\":\"minecraft:block/cube_column\",\"textures\":{\"end\":\"minecraft:block/oak_log_top\","
                        + "\"side\":\"minecraft:block/oak_log\"}}",
                "assets/minecraft/models/block/cube_column.json",
                "{\"textures\":{\"particle\":\"#side\"}}",
                "assets/minecraft/textures/block/oak_log_top.png", "",
                "assets/minecraft/textures/block/oak_log.png", "");
        assertEquals(Optional.of("assets/minecraft/textures/block/oak_log_top.png"),
                GooInventoryImages.texturePathFor(Identifier.withDefaultNamespace("oak_log"), assets(files)));
    }

    /**
     * An item a special renderer draws takes a texture from the base model its definition names.
     */
    @Test
    void speciallyRenderedItemTakesItsBaseModel() {
        Map<String, String> files = Map.of(
                "assets/minecraft/items/black_bed.json",
                "{\"model\":{\"type\":\"minecraft:special\",\"base\":\"minecraft:item/black_bed\","
                        + "\"model\":{\"type\":\"minecraft:bed\",\"texture\":\"minecraft:black\"}}}",
                "assets/minecraft/models/item/black_bed.json",
                "{\"textures\":{\"particle\":\"minecraft:block/black_wool\"}}",
                "assets/minecraft/textures/block/black_wool.png", "");
        assertEquals(Optional.of("assets/minecraft/textures/block/black_wool.png"),
                GooInventoryImages.texturePathFor(Identifier.withDefaultNamespace("black_bed"), assets(files)));
    }
}
