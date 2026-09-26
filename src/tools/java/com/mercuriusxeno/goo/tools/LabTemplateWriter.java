package com.mercuriusxeno.goo.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the Goo Lab level template tracked at {@code dev/lab/}: a superflat,
 * creative, cheats-on world named "Goo Lab" with daylight and weather locked
 * and natural spawning off (decision lab-built-from-code). Since 26.1 a save
 * keeps its world gen settings and game rules as saved data beside level.dat,
 * so the template is three small NBT files. Run by {@code ./gradlew generateLabTemplate}.
 */
public final class LabTemplateWriter {

    // --- level.dat ---
    private static final String LEVEL_NAME = "Goo Lab";
    private static final int CREATIVE = 1;
    private static final int ANVIL_STORAGE_VERSION = 19133;
    private static final String DIFFICULTY_NORMAL = "normal";

    // --- world gen settings ---
    private static final long SEED = 0L;
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String END = "minecraft:the_end";
    private static final String NOISE = "minecraft:noise";
    private static final String FLAT = "minecraft:flat";
    private static final String PLAINS = "minecraft:plains";
    private static final String BEDROCK = "minecraft:bedrock";
    private static final String STONE = "minecraft:stone";
    private static final int STONE_LAYERS = 3;

    // --- game rules held off ---
    private static final String[] RULES_OFF = {
        "minecraft:advance_time", "minecraft:advance_weather", "minecraft:spawn_mobs",
        "minecraft:spawn_monsters", "minecraft:spawn_phantoms", "minecraft:spawn_patrols",
        "minecraft:spawn_wandering_traders",
    };
    /**
     * Respawn radius zero, so a player spawns on the lab rather than scattered around it.
     */
    private static final String RULE_RESPAWN_RADIUS = "minecraft:respawn_radius";

    // --- NBT keys and paths ---
    private static final String KEY_VERSION = "Version";
    private static final String KEY_STORAGE_VERSION = "version";
    private static final String KEY_LEVEL_NAME = "LevelName";
    private static final String KEY_GAME_TYPE = "GameType";
    private static final String KEY_ALLOW_COMMANDS = "allowCommands";
    private static final String KEY_INITIALIZED = "initialized";
    private static final String KEY_WAS_MODDED = "WasModded";
    private static final String KEY_DIFFICULTY = "difficulty";
    private static final String KEY_HARDCORE = "hardcore";
    private static final String KEY_LOCKED = "locked";
    private static final String KEY_DIFFICULTY_SETTINGS = "difficulty_settings";
    private static final String KEY_NAME = "Name";
    private static final String KEY_ID = "Id";
    private static final String KEY_SNAPSHOT = "Snapshot";
    private static final String KEY_SERIES = "Series";
    private static final String KEY_SEED = "seed";
    private static final String KEY_GENERATE_STRUCTURES = "generate_structures";
    private static final String KEY_BONUS_CHEST = "bonus_chest";
    private static final String KEY_DIMENSIONS = "dimensions";
    private static final String KEY_LAYERS = "layers";
    private static final String KEY_BIOME = "biome";
    private static final String KEY_FEATURES = "features";
    private static final String KEY_LAKES = "lakes";
    private static final String KEY_STRUCTURE_OVERRIDES = "structure_overrides";
    private static final String KEY_DATA_ROOT = "Data";
    private static final String KEY_SAVED_DATA = "data";
    private static final String KEY_TYPE = "type";
    private static final String KEY_GENERATOR = "generator";
    private static final String KEY_SETTINGS = "settings";
    private static final String KEY_BIOME_SOURCE = "biome_source";
    private static final String KEY_PRESET = "preset";
    private static final String KEY_BLOCK = "block";
    private static final String KEY_HEIGHT = "height";
    private static final String MULTI_NOISE = "minecraft:multi_noise";
    private static final String NETHER_PRESET = "minecraft:nether";
    private static final String END_SETTINGS = "minecraft:end";
    private static final String LEVEL_DAT = "level.dat";
    private static final String DATA_DIR = "data";
    private static final String MINECRAFT_DIR = "minecraft";
    private static final String WORLD_GEN_FILE = "world_gen_settings.dat";
    private static final String GAME_RULES_FILE = "game_rules.dat";
    private static final String DEFAULT_TARGET = "dev/lab";

    // --- game version, read from the version.json the Minecraft jar carries ---
    private static final String VERSION_RESOURCE = "/version.json";
    private static final String JSON_WORLD_VERSION = "world_version";
    private static final String JSON_NAME = "name";
    private static final String JSON_SERIES = "series_id";
    private static final String JSON_STABLE = "stable";
    private static final String KEY_DATA_VERSION = "DataVersion";

    private LabTemplateWriter() {
    }

    /**
     * Writes the template into the folder the first argument names, {@code dev/lab} when none.
     *
     * @param args the target folder, optional
     * @throws IOException when a file cannot be written
     */
    public static void main(String[] args) throws IOException {
        JsonObject gameVersion = readGameVersion();
        Path target = Path.of(args.length > 0 ? args[0] : DEFAULT_TARGET);
        Path savedData = target.resolve(DATA_DIR).resolve(MINECRAFT_DIR);
        Files.createDirectories(savedData);
        CompoundTag levelRoot = new CompoundTag();
        levelRoot.put(KEY_DATA_ROOT, levelData(gameVersion));
        NbtIo.writeCompressed(levelRoot, target.resolve(LEVEL_DAT));
        int dataVersion = gameVersion.get(JSON_WORLD_VERSION).getAsInt();
        NbtIo.writeCompressed(savedDataFile(worldGenSettings(), dataVersion), savedData.resolve(WORLD_GEN_FILE));
        NbtIo.writeCompressed(savedDataFile(gameRules(), dataVersion), savedData.resolve(GAME_RULES_FILE));
    }

    /**
     * Reads the game's version.json from the classpath. SharedConstants needs a
     * running FML loader, which a plain JavaExec does not stand.
     *
     * @return the parsed version object
     * @throws IOException when the resource is missing or unreadable
     */
    private static JsonObject readGameVersion() throws IOException {
        try (InputStream in = LabTemplateWriter.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (in == null) {
                throw new IOException(VERSION_RESOURCE);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /**
     * Builds the {@code Data} compound of level.dat. It stays uninitialized so
     * the server picks the spawn on first load.
     *
     * @param gameVersion the game's version.json
     * @return the level data compound
     */
    private static CompoundTag levelData(JsonObject gameVersion) {
        CompoundTag data = new CompoundTag();
        data.putInt(KEY_DATA_VERSION, gameVersion.get(JSON_WORLD_VERSION).getAsInt());
        data.put(KEY_VERSION, versionTag(gameVersion));
        data.putInt(KEY_STORAGE_VERSION, ANVIL_STORAGE_VERSION);
        data.putString(KEY_LEVEL_NAME, LEVEL_NAME);
        data.putInt(KEY_GAME_TYPE, CREATIVE);
        data.putBoolean(KEY_ALLOW_COMMANDS, true);
        data.putBoolean(KEY_INITIALIZED, false);
        data.putBoolean(KEY_WAS_MODDED, true);
        CompoundTag difficulty = new CompoundTag();
        difficulty.putString(KEY_DIFFICULTY, DIFFICULTY_NORMAL);
        difficulty.putBoolean(KEY_HARDCORE, false);
        difficulty.putBoolean(KEY_LOCKED, false);
        data.put(KEY_DIFFICULTY_SETTINGS, difficulty);
        return data;
    }

    /**
     * Builds the {@code Version} compound the world list reads.
     *
     * @param gameVersion the game's version.json
     * @return the version compound
     */
    private static CompoundTag versionTag(JsonObject gameVersion) {
        CompoundTag version = new CompoundTag();
        version.putString(KEY_NAME, gameVersion.get(JSON_NAME).getAsString());
        version.putInt(KEY_ID, gameVersion.get(JSON_WORLD_VERSION).getAsInt());
        version.putBoolean(KEY_SNAPSHOT, !gameVersion.get(JSON_STABLE).getAsBoolean());
        version.putString(KEY_SERIES, gameVersion.get(JSON_SERIES).getAsString());
        return version;
    }

    /**
     * Wraps a saved data payload the way the game writes it.
     *
     * @param payload     the saved data's encoded value
     * @param dataVersion the game's world data version
     * @return the file's root compound
     */
    private static CompoundTag savedDataFile(CompoundTag payload, int dataVersion) {
        CompoundTag root = new CompoundTag();
        root.put(KEY_SAVED_DATA, payload);
        root.putInt(KEY_DATA_VERSION, dataVersion);
        return root;
    }

    /**
     * Builds the world gen settings: a flat overworld and the vanilla nether and end,
     * so every built-in dimension reads stable and the world opens without a warning.
     *
     * @return the world gen settings payload
     */
    private static CompoundTag worldGenSettings() {
        CompoundTag settings = new CompoundTag();
        settings.putLong(KEY_SEED, SEED);
        settings.putBoolean(KEY_GENERATE_STRUCTURES, false);
        settings.putBoolean(KEY_BONUS_CHEST, false);
        CompoundTag dimensions = new CompoundTag();
        dimensions.put(OVERWORLD, levelStem(OVERWORLD, flatGenerator()));
        dimensions.put(NETHER, levelStem(NETHER, noiseGenerator(NETHER_PRESET, netherBiomes())));
        dimensions.put(END, levelStem(END, noiseGenerator(END_SETTINGS, endBiomes())));
        settings.put(KEY_DIMENSIONS, dimensions);
        return settings;
    }

    /**
     * Builds one dimension entry.
     *
     * @param dimensionType the dimension type id
     * @param generator     the chunk generator compound
     * @return the level stem compound
     */
    private static CompoundTag levelStem(String dimensionType, CompoundTag generator) {
        CompoundTag stem = new CompoundTag();
        stem.putString(KEY_TYPE, dimensionType);
        stem.put(KEY_GENERATOR, generator);
        return stem;
    }

    /**
     * Builds the superflat generator: bedrock under a stone floor, plains, no features or lakes.
     *
     * @return the flat generator compound
     */
    private static CompoundTag flatGenerator() {
        ListTag layers = new ListTag();
        layers.add(layer(BEDROCK, 1));
        layers.add(layer(STONE, STONE_LAYERS));
        CompoundTag flat = new CompoundTag();
        flat.put(KEY_LAYERS, layers);
        flat.putString(KEY_BIOME, PLAINS);
        flat.putBoolean(KEY_FEATURES, false);
        flat.putBoolean(KEY_LAKES, false);
        flat.put(KEY_STRUCTURE_OVERRIDES, new ListTag());
        CompoundTag generator = new CompoundTag();
        generator.putString(KEY_TYPE, FLAT);
        generator.put(KEY_SETTINGS, flat);
        return generator;
    }

    /**
     * Builds one superflat layer.
     *
     * @param block  the block id
     * @param height the layer's thickness
     * @return the layer compound
     */
    private static CompoundTag layer(String block, int height) {
        CompoundTag layer = new CompoundTag();
        layer.putString(KEY_BLOCK, block);
        layer.putInt(KEY_HEIGHT, height);
        return layer;
    }

    /**
     * Builds a vanilla noise generator.
     *
     * @param noiseSettings the noise settings id
     * @param biomeSource   the biome source compound
     * @return the noise generator compound
     */
    private static CompoundTag noiseGenerator(String noiseSettings, CompoundTag biomeSource) {
        CompoundTag generator = new CompoundTag();
        generator.putString(KEY_TYPE, NOISE);
        generator.putString(KEY_SETTINGS, noiseSettings);
        generator.put(KEY_BIOME_SOURCE, biomeSource);
        return generator;
    }

    /**
     * Builds the vanilla nether's biome source.
     *
     * @return the multi noise biome source on the nether preset
     */
    private static CompoundTag netherBiomes() {
        CompoundTag source = new CompoundTag();
        source.putString(KEY_TYPE, MULTI_NOISE);
        source.putString(KEY_PRESET, NETHER_PRESET);
        return source;
    }

    /**
     * Builds the vanilla end's biome source.
     *
     * @return the end biome source
     */
    private static CompoundTag endBiomes() {
        CompoundTag source = new CompoundTag();
        source.putString(KEY_TYPE, END);
        return source;
    }

    /**
     * Builds the game rules payload: every rule in {@link #RULES_OFF} set false, respawn radius zero.
     *
     * @return the game rules payload
     */
    private static CompoundTag gameRules() {
        CompoundTag rules = new CompoundTag();
        for (String rule : RULES_OFF) {
            rules.put(rule, ByteTag.valueOf(false));
        }
        rules.putInt(RULE_RESPAWN_RADIUS, 0);
        return rules;
    }
}
