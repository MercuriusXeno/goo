package com.mercuriusxeno.goo.client.sound;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Sound recordings and the sounds.json entries that play them: vanilla's
 * from the asset cache, found through the asset index the build's
 * downloadAssets task names, and goo's from the classpath. Each take's
 * loudness reads as its mono RMS and peak in dBFS.
 * drain-lifts-its-chosen-sounds-past-the-volume-cap
 */
final class VanillaRecordings {

    /** The system property the build sets to the file naming the asset cache. */
    private static final String ASSETS_PROPERTY = "goo.minecraftAssets";
    private static final String SOUNDS_JSON = "minecraft/sounds.json";
    private static final String GOO_SOUNDS_JSON = "assets/goo/sounds.json";
    private static final String GOO_PREFIX = "goo:";
    private static final float FULL_SCALE = 32768f;
    private static final float DEFAULT_ENTRY_VOLUME = 1f;
    /** The attenuation distance a sounds.json entry naming none plays with. */
    private static final int DEFAULT_ATTENUATION_DISTANCE = 16;

    private final Path root;
    private final JsonObject objects;
    private final JsonObject sounds;

    /**
     * One take of a sound event.
     *
     * @param name                the recording's name, as sounds.json writes it
     * @param entryVolume         the volume its sounds.json entry plays it at
     * @param attenuationDistance the distance its entry falls off over
     */
    record Take(String name, float entryVolume, int attenuationDistance) {
    }

    /**
     * A recording's loudness.
     *
     * @param rmsDecibels  its mono RMS over full scale, in dB
     * @param peakDecibels its loudest mono sample over full scale, in dB
     */
    record Loudness(double rmsDecibels, double peakDecibels) {
    }

    private VanillaRecordings(Path root, JsonObject objects) throws IOException {
        this.root = root;
        this.objects = objects;
        try (Reader reader = Files.newBufferedReader(objectPath(SOUNDS_JSON), StandardCharsets.UTF_8)) {
            this.sounds = JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    /**
     * The asset cache the build named.
     *
     * @return the recordings
     * @throws IOException where the cache or its index cannot be read
     */
    static VanillaRecordings fromBuild() throws IOException {
        String named = System.getProperty(ASSETS_PROPERTY);
        if (named == null) {
            throw new IOException("the build sets no " + ASSETS_PROPERTY + "; run the unit suite through Gradle");
        }
        Properties assets = new Properties();
        try (InputStream in = Files.newInputStream(Path.of(named))) {
            assets.load(in);
        }
        Path root = Path.of(assets.getProperty("assets_root"));
        Path index = root.resolve("indexes").resolve(assets.getProperty("asset_index") + ".json");
        try (Reader reader = Files.newBufferedReader(index, StandardCharsets.UTF_8)) {
            return new VanillaRecordings(root, JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonObject("objects"));
        }
    }

    /**
     * The takes vanilla's sounds.json lists for a sound event.
     *
     * @param event the event's path, such as {@code block.fire.ambient}
     * @return its takes
     */
    List<Take> takesOf(String event) {
        return takesIn(sounds, event);
    }

    /**
     * A vanilla take's loudness.
     *
     * @param take the take
     * @return its loudness
     * @throws IOException where the recording cannot be read or decoded
     */
    Loudness loudnessOf(Take take) throws IOException {
        return loudnessOf(Files.readAllBytes(objectPath("minecraft/sounds/" + take.name() + ".ogg")), take.name());
    }

    /**
     * The takes goo's sounds.json lists for one of goo's sound events.
     *
     * @param event the event's path, such as {@code effects.earth_rumble}
     * @return its takes
     * @throws IOException where goo's sounds.json cannot be read
     */
    static List<Take> gooTakesOf(String event) throws IOException {
        try (Reader reader = new InputStreamReader(resource(GOO_SOUNDS_JSON), StandardCharsets.UTF_8)) {
            return takesIn(JsonParser.parseReader(reader).getAsJsonObject(), event);
        }
    }

    /**
     * One of goo's takes' loudness, its recording read from the classpath.
     *
     * @param take the take, its name namespaced {@code goo:}
     * @return its loudness
     * @throws IOException where the recording cannot be read or decoded
     */
    static Loudness gooLoudnessOf(Take take) throws IOException {
        String path = "assets/goo/sounds/" + take.name().substring(GOO_PREFIX.length()) + ".ogg";
        try (InputStream in = resource(path)) {
            return loudnessOf(in.readAllBytes(), take.name());
        }
    }

    private static List<Take> takesIn(JsonObject soundsJson, String event) {
        List<Take> takes = new ArrayList<>();
        for (JsonElement entry : soundsJson.getAsJsonObject(event).getAsJsonArray("sounds")) {
            if (entry.isJsonPrimitive()) {
                takes.add(new Take(entry.getAsString(), DEFAULT_ENTRY_VOLUME, DEFAULT_ATTENUATION_DISTANCE));
            } else {
                JsonObject take = entry.getAsJsonObject();
                takes.add(new Take(take.get("name").getAsString(),
                        take.has("volume") ? take.get("volume").getAsFloat() : DEFAULT_ENTRY_VOLUME,
                        take.has("attenuation_distance") ? take.get("attenuation_distance").getAsInt()
                                : DEFAULT_ATTENUATION_DISTANCE));
            }
        }
        return takes;
    }

    private static Loudness loudnessOf(byte[] ogg, String name) throws IOException {
        ByteBuffer encoded = MemoryUtil.memAlloc(ogg.length).put(ogg).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channels = stack.mallocInt(1);
            IntBuffer rate = stack.mallocInt(1);
            ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(encoded, channels, rate);
            if (pcm == null) {
                throw new IOException("stb_vorbis decodes no " + name);
            }
            try {
                return loudness(pcm, channels.get(0));
            } finally {
                // stb_vorbis allocates the decoded samples with libc's malloc, not LWJGL's allocator
                LibCStdlib.free(pcm);
            }
        } finally {
            MemoryUtil.memFree(encoded);
        }
    }

    private static Loudness loudness(ShortBuffer pcm, int channels) {
        int frames = pcm.remaining() / channels;
        double sumOfSquares = 0;
        double peak = 0;
        for (int frame = 0; frame < frames; frame++) {
            double mono = 0;
            for (int channel = 0; channel < channels; channel++) {
                mono += pcm.get(frame * channels + channel) / FULL_SCALE;
            }
            mono /= channels;
            sumOfSquares += mono * mono;
            peak = Math.max(peak, Math.abs(mono));
        }
        return new Loudness(10 * Math.log10(sumOfSquares / frames), 20 * Math.log10(peak));
    }

    private static InputStream resource(String path) throws IOException {
        InputStream in = VanillaRecordings.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IOException("the classpath holds no " + path);
        }
        return in;
    }

    private Path objectPath(String asset) throws IOException {
        JsonObject object = objects.getAsJsonObject(asset);
        if (object == null) {
            throw new IOException("the asset index holds no " + asset);
        }
        String hash = object.get("hash").getAsString();
        return root.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
    }
}
