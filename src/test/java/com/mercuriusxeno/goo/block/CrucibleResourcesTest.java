package com.mercuriusxeno.goo.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The crucible's client resources resolve (decision diagnose-then-fix-crucible-texture):
 * every model its blockstate and item definitions reach ships under models, every texture
 * those models name ships under textures, and every block state the crucible exposes
 * matches a multipart case. A model file absent from the jar draws the missing-texture checker.
 */
class CrucibleResourcesTest {

    /** CrucibleBlock's source: its properties cannot be read in a unit test, since touching them needs the FML loader. */
    private static final Path CRUCIBLE_BLOCK_SOURCE =
            Path.of("src/main/java/com/mercuriusxeno/goo/block/crucible/CrucibleBlock.java");
    private static final Pattern STATE_DEFINITION = Pattern.compile("builder\\.add\\(([^)]*)\\)");
    private static final String BOOLEAN_DECLARATION = "BooleanProperty %s = BooleanProperty.create(\"";
    private static final String HORIZONTAL_FACING_DECLARATION = "%s = HorizontalDirectionalBlock.FACING;";
    private static final List<String> HORIZONTAL_FACINGS = List.of("north", "south", "west", "east");
    private static final List<String> BOOLEANS = List.of("true", "false");

    private static final String BLOCKSTATE = "/assets/goo/blockstates/crucible.json";
    private static final String ITEM_DEFINITION = "/assets/goo/items/crucible.json";
    private static final String ITEM_MODEL = "/assets/goo/models/item/crucible.json";
    private static final String GOO_NAMESPACE = "goo:";

    @Test
    void everyReferencedModelAndTextureShips() throws Exception {
        Set<String> models = new LinkedHashSet<>();
        for (JsonElement part : readJson(BLOCKSTATE).getAsJsonArray("multipart")) {
            models.add(part.getAsJsonObject().getAsJsonObject("apply").get("model").getAsString());
        }
        models.add(readJson(ITEM_DEFINITION).getAsJsonObject("model").get("model").getAsString());
        Set<String> textures = new LinkedHashSet<>();
        collectModel(readJson(ITEM_MODEL), textures, models);
        Set<String> walked = new LinkedHashSet<>();
        while (walked.size() < models.size()) {
            for (String model : new ArrayList<>(models)) {
                if (walked.add(model)) {
                    collectModel(readJson(modelPath(model)), textures, models);
                }
            }
        }
        assertFalse(textures.isEmpty(), "the crucible models should name textures");
        for (String texture : textures) {
            assertTrue(texture.startsWith(GOO_NAMESPACE), texture + " should be a goo texture");
            String path = "/assets/goo/textures/" + texture.substring(GOO_NAMESPACE.length()) + ".png";
            try (InputStream in = CrucibleResourcesTest.class.getResourceAsStream(path)) {
                assertNotNull(in, "Texture missing on classpath: " + path);
            }
        }
    }

    @Test
    void everyBlockStateMatchesAMultipartCase() throws Exception {
        List<JsonObject> conditions = new ArrayList<>();
        for (JsonElement part : readJson(BLOCKSTATE).getAsJsonArray("multipart")) {
            conditions.add(part.getAsJsonObject().getAsJsonObject("when"));
        }
        List<Map<String, String>> states = new ArrayList<>();
        states.add(new HashMap<>());
        for (Map.Entry<String, List<String>> property : stateDefinition().entrySet()) {
            List<Map<String, String>> next = new ArrayList<>();
            for (Map<String, String> state : states) {
                for (String value : property.getValue()) {
                    Map<String, String> extended = new HashMap<>(state);
                    extended.put(property.getKey(), value);
                    next.add(extended);
                }
            }
            states = next;
        }
        for (Map<String, String> state : states) {
            assertTrue(conditions.stream().anyMatch(when -> matches(when, state)),
                    "no multipart case applies to crucible state " + state);
        }
    }

    /** Each property createBlockStateDefinition adds, by its serialized name, with its value names. */
    private static Map<String, List<String>> stateDefinition() throws Exception {
        String source = Files.readString(CRUCIBLE_BLOCK_SOURCE);
        Matcher added = STATE_DEFINITION.matcher(source);
        assertTrue(added.find(), "CrucibleBlock should add its properties through builder.add");
        Map<String, List<String>> properties = new LinkedHashMap<>();
        for (String field : added.group(1).split(",")) {
            String name = field.trim();
            String booleanDeclaration = BOOLEAN_DECLARATION.formatted(name);
            int booleanAt = source.indexOf(booleanDeclaration);
            if (booleanAt >= 0) {
                int nameStart = booleanAt + booleanDeclaration.length();
                properties.put(source.substring(nameStart, source.indexOf('"', nameStart)), BOOLEANS);
            } else if (source.contains(HORIZONTAL_FACING_DECLARATION.formatted(name))) {
                properties.put("facing", HORIZONTAL_FACINGS);
            } else {
                fail("CrucibleBlock property " + name + " has a declaration this test does not enumerate");
            }
        }
        return properties;
    }

    private static boolean matches(JsonObject when, Map<String, String> state) {
        return when.entrySet().stream().allMatch(condition ->
                List.of(condition.getValue().getAsString().split("\\|")).contains(state.get(condition.getKey())));
    }

    /** Adds the model's textures to {@code textures} and its goo parent to {@code models}. */
    private static void collectModel(JsonObject model, Set<String> textures, Set<String> models) {
        if (model.has("parent")) {
            String parent = model.get("parent").getAsString();
            if (parent.startsWith(GOO_NAMESPACE)) {
                models.add(parent);
            }
        }
        if (model.has("textures")) {
            model.getAsJsonObject("textures").entrySet().forEach(texture -> {
                String name = texture.getValue().getAsString();
                if (!name.startsWith("#")) {
                    textures.add(name);
                }
            });
        }
    }

    private static String modelPath(String model) {
        assertTrue(model.startsWith(GOO_NAMESPACE), model + " should be a goo model");
        return "/assets/goo/models/" + model.substring(GOO_NAMESPACE.length()) + ".json";
    }

    private static JsonObject readJson(String path) throws Exception {
        try (InputStream in = CrucibleResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Resource missing on classpath: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
