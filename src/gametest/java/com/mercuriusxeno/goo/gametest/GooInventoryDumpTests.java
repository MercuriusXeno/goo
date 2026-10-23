package com.mercuriusxeno.goo.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.command.GooInventoryJson;
import com.mercuriusxeno.goo.command.GooInventoryReport;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Writes the inventory of everything the loadout registers to config/goo_inventory.json on a headless server,
 * reads it back, and finds the image folder untouched (decision inventory-dump-from-a-gametest-and-a-command).
 */
@RegistersGameTests
public final class GooInventoryDumpTests {
    private static final String INVENTORY_DUMP = "inventory_dump";
    private static final List<String> SECTIONS = List.of(GooInventoryJson.ITEMS, GooInventoryJson.BLOCKS,
            GooInventoryJson.ITEM_TAGS, GooInventoryJson.BLOCK_TAGS, GooInventoryJson.GOO_TYPES,
            GooInventoryJson.RECIPES);
    private static final String ID = "id";
    private static final String UNREADABLE = "The inventory dump could not be read back: ";
    private static final String SECTION_MISSING = "The inventory dump holds no section ";
    private static final String ENTRY_MISSING = "The inventory dump lists no ";
    private static final String UNDER = " under ";
    private static final String MEMBER_MISSING = "minecraft:logs lists no minecraft:oak_log";
    private static final String GRID_MISSING = "minecraft:iron_chain carries no pattern, key or output";
    // Images are slot captures a client takes; the report writes the json alone
    // (decision images-are-slot-captures-taken-on-detection).
    private static final String IMAGE_WRITTEN = "The inventory report wrote or removed a PNG under goo_inventory/";
    private static final String PNG = ".png";

    private GooInventoryDumpTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, INVENTORY_DUMP, GooInventoryDumpTests::writesTheInventoryDump);
    }

    /**
     * Writes the inventory and reads every section back, with a known entry in each and no image written.
     *
     * @param helper the gametest helper
     */
    public static void writesTheInventoryDump(GameTestHelper helper) {
        Path config = FMLPaths.CONFIGDIR.get();
        Path images = config.resolve(GooInventoryReport.DIR_IMAGES);
        Map<Path, FileTime> pngsBefore = pngsUnder(images);
        GooInventoryReport.writeInventory(helper.getLevel().getServer());
        JsonObject root;
        try {
            root = JsonParser.parseString(Files.readString(config.resolve(GooInventoryReport.FILE_INVENTORY),
                    StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            helper.fail(UNREADABLE + e.getMessage());
            return;
        }
        for (String section : SECTIONS) {
            helper.assertTrue(root.has(section), SECTION_MISSING + section);
        }
        assertListed(helper, root, GooInventoryJson.ITEMS, "minecraft:iron_nugget");
        assertListed(helper, root, GooInventoryJson.BLOCKS, "minecraft:stone");
        GooTypes.BUNDLED.forEach(type -> assertListed(helper, root, GooInventoryJson.GOO_TYPES, GooTypes.id(type)));
        JsonObject logs = entry(root, GooInventoryJson.ITEM_TAGS, "minecraft:logs").orElse(new JsonObject());
        helper.assertTrue(logs.has("members") && strings(logs.getAsJsonArray("members")).contains("minecraft:oak_log"),
                MEMBER_MISSING);
        JsonObject chain = entry(root, GooInventoryJson.RECIPES, "minecraft:iron_chain").orElse(new JsonObject());
        helper.assertTrue(chain.has("pattern") && chain.has("key")
                && "minecraft:iron_chain".equals(chain.has("output") ? chain.get("output").getAsString() : null),
                GRID_MISSING);
        helper.assertTrue(pngsBefore.equals(pngsUnder(images)), IMAGE_WRITTEN);
        helper.succeed();
    }

    /**
     * Every PNG under the image folder with when it was last written, so a report that writes or deletes any
     * image changes the answer.
     *
     * @param images the image folder
     * @return each PNG's path and last-modified time, empty where the folder stands absent
     */
    private static Map<Path, FileTime> pngsUnder(Path images) {
        if (!Files.isDirectory(images)) {
            return Map.of();
        }
        Map<Path, FileTime> pngs = new HashMap<>();
        try (Stream<Path> paths = Files.walk(images)) {
            for (Path png : paths.filter(path -> path.toString().endsWith(PNG)).toList()) {
                pngs.put(png, Files.getLastModifiedTime(png));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return pngs;
    }

    private static void assertListed(GameTestHelper helper, JsonObject root, String section, String id) {
        helper.assertTrue(entry(root, section, id).isPresent(), ENTRY_MISSING + id + UNDER + section);
    }

    private static Optional<JsonObject> entry(JsonObject root, String section, String id) {
        if (!root.has(section)) {
            return Optional.empty();
        }
        return root.getAsJsonArray(section).asList().stream().map(JsonElement::getAsJsonObject)
                .filter(entry -> id.equals(entry.get(ID).getAsString())).findFirst();
    }

    private static List<String> strings(JsonArray array) {
        return array.asList().stream().map(JsonElement::getAsString).toList();
    }
}
