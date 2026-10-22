package com.mercuriusxeno.goo.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Finds the texture an item draws with, from the client assets the classpath carries, so the values
 * workbench shows an image for every entry (decision inventory-dump-from-a-gametest-and-a-command). The
 * item's own texture comes first; a block item without one takes its model's top face.
 */
final class GooInventoryImages {
    private static final String ASSETS = "assets/";
    private static final String TEXTURES = "/textures/";
    private static final String PNG = ".png";
    private static final String JSON = ".json";
    private static final String ITEM_TEXTURE = "item/";
    private static final String BLOCK_TEXTURE = "block/";
    private static final String TOP_SUFFIX = "_top";
    private static final String ITEM_DEFINITIONS = "/items/";
    private static final String MODELS = "/models/";
    private static final String MODEL = "model";
    // A special renderer's item definition names its base model here rather than under model.
    private static final String BASE = "base";
    private static final String PARENT = "parent";
    private static final String TEXTURES_KEY = "textures";
    private static final String REFERENCE = "#";
    // The top face leads, so a block item reads as its block seen from above.
    private static final List<String> FACE_PRIORITY = List.of("layer0", "top", "end", "all", "side", "texture",
            "particle");
    private static final int MAX_MODEL_DEPTH = 8;

    private GooInventoryImages() {
    }

    /**
     * @param item   the item's id
     * @param assets reads a classpath asset by its path, answering empty where none stands
     * @return the asset path of the PNG the item draws with, or empty where none was found
     */
    static Optional<String> texturePathFor(Identifier item, Function<String, Optional<byte[]>> assets) {
        String own = texturePath(item.getNamespace(), ITEM_TEXTURE + item.getPath());
        if (assets.apply(own).isPresent()) {
            return Optional.of(own);
        }
        Optional<String> modelled = itemModel(item, assets).flatMap(model -> modelTexture(model, assets))
                .filter(path -> assets.apply(path).isPresent());
        if (modelled.isPresent()) {
            return modelled;
        }
        for (String fallback : List.of(BLOCK_TEXTURE + item.getPath() + TOP_SUFFIX, BLOCK_TEXTURE + item.getPath())) {
            String path = texturePath(item.getNamespace(), fallback);
            if (assets.apply(path).isPresent()) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    private static String texturePath(String namespace, String texture) {
        return ASSETS + namespace + TEXTURES + texture + PNG;
    }

    private static Optional<Identifier> itemModel(Identifier item, Function<String, Optional<byte[]>> assets) {
        return readJson(ASSETS + item.getNamespace() + ITEM_DEFINITIONS + item.getPath() + JSON, assets)
                .flatMap(GooInventoryImages::firstModelReference)
                .map(Identifier::tryParse);
    }

    private static Optional<String> firstModelReference(JsonElement element) {
        if (element.isJsonArray()) {
            return firstAmong(element.getAsJsonArray().asList());
        }
        if (!element.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject object = element.getAsJsonObject();
        return Stream.of(MODEL, BASE).map(object::get)
                .filter(model -> model != null && model.isJsonPrimitive())
                .map(JsonElement::getAsString)
                .findFirst()
                .or(() -> firstAmong(object.asMap().values()));
    }

    private static Optional<String> firstAmong(Collection<JsonElement> children) {
        return children.stream().map(GooInventoryImages::firstModelReference)
                .flatMap(Optional::stream).findFirst();
    }

    private static Optional<String> modelTexture(Identifier model, Function<String, Optional<byte[]>> assets) {
        Map<String, String> faces = new HashMap<>();
        Optional<Identifier> current = Optional.of(model);
        for (int depth = 0; current.isPresent() && depth < MAX_MODEL_DEPTH; depth++) {
            current = readModel(current.get(), assets).flatMap(object -> collectFaces(object, faces));
        }
        return FACE_PRIORITY.stream()
                .flatMap(face -> resolveFace(faces, face).map(Identifier::tryParse).stream())
                .findFirst()
                .map(texture -> texturePath(texture.getNamespace(), texture.getPath()));
    }

    private static Optional<JsonObject> readModel(Identifier model, Function<String, Optional<byte[]>> assets) {
        return readJson(ASSETS + model.getNamespace() + MODELS + model.getPath() + JSON, assets)
                .filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject);
    }

    /**
     * Adds a model's faces under the ones a child model already named, so the child's win.
     *
     * @param model the model's JSON
     * @param faces the faces collected so far
     * @return the model's parent, or empty at the top of the chain
     */
    private static Optional<Identifier> collectFaces(JsonObject model, Map<String, String> faces) {
        JsonElement textures = model.get(TEXTURES_KEY);
        if (textures != null && textures.isJsonObject()) {
            textures.getAsJsonObject().entrySet().stream()
                    .filter(face -> face.getValue().isJsonPrimitive())
                    .forEach(face -> faces.putIfAbsent(face.getKey(), face.getValue().getAsString()));
        }
        JsonElement parent = model.get(PARENT);
        return parent == null ? Optional.empty() : Optional.ofNullable(Identifier.tryParse(parent.getAsString()));
    }

    private static Optional<String> resolveFace(Map<String, String> faces, String face) {
        String value = faces.get(face);
        for (int hops = 0; value != null && value.startsWith(REFERENCE) && hops < MAX_MODEL_DEPTH; hops++) {
            value = faces.get(value.substring(REFERENCE.length()));
        }
        return value == null || value.startsWith(REFERENCE) ? Optional.empty() : Optional.of(value);
    }

    private static Optional<JsonElement> readJson(String path, Function<String, Optional<byte[]>> assets) {
        try {
            return assets.apply(path).map(bytes -> JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)));
        } catch (JsonParseException e) {
            return Optional.empty();
        }
    }
}
