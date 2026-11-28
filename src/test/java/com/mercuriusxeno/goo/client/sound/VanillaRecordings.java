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
 * Vanilla's sound recordings as the asset cache holds them, found through
 * the asset index the build's downloadAssets task names, each take's
 * loudness read as its mono RMS in dBFS.
 * drain-plays-its-chosen-sounds-as-loud-as-blaze-fire
 */
final class VanillaRecordings {

    /** The system property the build sets to the file naming the asset cache. */
    private static final String ASSETS_PROPERTY = "goo.minecraftAssets";
    private static final String SOUNDS_JSON = "minecraft/sounds.json";
    private static final float FULL_SCALE = 32768f;
    private static final float DEFAULT_ENTRY_VOLUME = 1f;

    private final Path root;
    private final JsonObject objects;
    private final JsonObject sounds;

    /** One take of a sound event: its recording's path and the volume its sounds.json entry plays it at. */
    record Take(String name, float entryVolume) {
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
        List<Take> takes = new ArrayList<>();
        for (JsonElement entry : sounds.getAsJsonObject(event).getAsJsonArray("sounds")) {
            if (entry.isJsonPrimitive()) {
                takes.add(new Take(entry.getAsString(), DEFAULT_ENTRY_VOLUME));
            } else {
                JsonObject take = entry.getAsJsonObject();
                takes.add(new Take(take.get("name").getAsString(),
                        take.has("volume") ? take.get("volume").getAsFloat() : DEFAULT_ENTRY_VOLUME));
            }
        }
        return takes;
    }

    /**
     * A take's loudness: its channels averaged to mono, then its RMS over
     * full scale, in decibels.
     *
     * @param take the take
     * @return its RMS in dBFS
     * @throws IOException where the recording cannot be read or decoded
     */
    double rmsDecibelsOf(Take take) throws IOException {
        byte[] ogg = Files.readAllBytes(objectPath("minecraft/sounds/" + take.name() + ".ogg"));
        ByteBuffer encoded = MemoryUtil.memAlloc(ogg.length).put(ogg).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channels = stack.mallocInt(1);
            IntBuffer rate = stack.mallocInt(1);
            ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(encoded, channels, rate);
            if (pcm == null) {
                throw new IOException("stb_vorbis decodes no " + take.name());
            }
            try {
                return rmsDecibels(pcm, channels.get(0));
            } finally {
                // stb_vorbis allocates the decoded samples with libc's malloc, not LWJGL's allocator
                LibCStdlib.free(pcm);
            }
        } finally {
            MemoryUtil.memFree(encoded);
        }
    }

    private static double rmsDecibels(ShortBuffer pcm, int channels) {
        int frames = pcm.remaining() / channels;
        double sumOfSquares = 0;
        for (int frame = 0; frame < frames; frame++) {
            double mono = 0;
            for (int channel = 0; channel < channels; channel++) {
                mono += pcm.get(frame * channels + channel) / FULL_SCALE;
            }
            mono /= channels;
            sumOfSquares += mono * mono;
        }
        return 10 * Math.log10(sumOfSquares / frames);
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
