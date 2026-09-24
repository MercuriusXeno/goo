package com.mercuriusxeno.goo.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every goo:block model a blockstate references ships under assets/goo/models/block
 * (decision anchor-exclude-commit-hidden-models), so a model git hides fails the gate
 * instead of drawing the missing model in a fresh worktree.
 */
class BlockstateModelsResolveTest {

    private static final String BLOCKSTATES = "assets/goo/blockstates";
    private static final String BLOCK_MODEL_PREFIX = "goo:block/";
    private static final String BLOCK_MODELS = "assets/goo/models/block/";

    @Test
    void everyReferencedBlockModelShips() throws Exception {
        Set<String> referenced = new TreeSet<>();
        for (Path blockstate : blockstateFiles()) {
            try (Reader reader = Files.newBufferedReader(blockstate, StandardCharsets.UTF_8)) {
                collectBlockModels(JsonParser.parseReader(reader), referenced);
            }
        }
        assertFalse(referenced.isEmpty(), "the blockstates should reference goo block models");
        Set<String> missing = new TreeSet<>();
        for (String model : referenced) {
            String path = BLOCK_MODELS + model.substring(BLOCK_MODEL_PREFIX.length()) + ".json";
            if (classLoader().getResource(path) == null) {
                missing.add(model);
            }
        }
        assertTrue(missing.isEmpty(), "Blockstates reference block models missing on the classpath: " + missing);
    }

    /** Every blockstate file under each classpath root, main and generated resources alike. */
    private static List<Path> blockstateFiles() throws Exception {
        List<Path> files = new ArrayList<>();
        for (URL root : Collections.list(classLoader().getResources(BLOCKSTATES))) {
            try (Stream<Path> walk = Files.walk(Path.of(root.toURI()))) {
                walk.filter(file -> file.toString().endsWith(".json")).forEach(files::add);
            }
        }
        assertFalse(files.isEmpty(), "no blockstate files found on the classpath under " + BLOCKSTATES);
        return files;
    }

    /** Adds each goo:block model a "model" key names anywhere in the tree, variants and multipart alike. */
    private static void collectBlockModels(JsonElement element, Set<String> models) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectBlockModels(child, models));
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (var entry : object.entrySet()) {
                JsonElement value = entry.getValue();
                if (entry.getKey().equals("model") && value.isJsonPrimitive()
                        && value.getAsString().startsWith(BLOCK_MODEL_PREFIX)) {
                    models.add(value.getAsString());
                } else {
                    collectBlockModels(value, models);
                }
            }
        }
    }

    private static ClassLoader classLoader() {
        return BlockstateModelsResolveTest.class.getClassLoader();
    }
}
