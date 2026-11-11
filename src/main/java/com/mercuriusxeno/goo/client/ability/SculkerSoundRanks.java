package com.mercuriusxeno.goo.client.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.Goo;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * The mod's own sub-ranks, on top of vanilla's vibration ranks, for the
 * sounds vanilla does not rank: mob noises at 9, Shriek's and Click's sounds
 * at 15. The table ships as data, {@code assets/goo/sculker_sound_ranks.json},
 * so the ranks are tuned there; each entry names a rank and the sound ids
 * it holds, where a star stands for any run of characters short of a
 * namespace's colon. The first entry naming a sound gives its rank.
 * decision sculker-echo-intensity-follows-vibration-rank
 */
public final class SculkerSoundRanks {

    private static final String SHIPPED = "/assets/goo/sculker_sound_ranks.json";
    private static final String ANY_RUN = "[^:]*";
    private static final String STAR = "*";
    private static final String RANKS = "ranks";
    private static final String RANK = "rank";
    private static final String SOUNDS = "sounds";
    private static final String LOG_UNREADABLE = "Sculker's sound ranks at {} could not be read, so no sound takes a sub-rank";

    private final List<Entry> entries;

    private SculkerSoundRanks(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    /**
     * One entry: the sounds it matches and the rank they take.
     *
     * @param sounds the pattern every sound id it names matches
     * @param rank   their rank
     */
    private record Entry(Pattern sounds, int rank) {
    }

    /** Holds the shipped table, read the first time it is asked for. */
    private static final class Shipped {
        private static final SculkerSoundRanks TABLE = readShipped();
    }

    /**
     * @return the table the mod ships, read once from the classpath
     */
    public static SculkerSoundRanks shipped() {
        return Shipped.TABLE;
    }

    private static SculkerSoundRanks readShipped() {
        try (InputStream in = SculkerSoundRanks.class.getResourceAsStream(SHIPPED)) {
            if (in == null) {
                Goo.LOGGER.warn(LOG_UNREADABLE, SHIPPED);
                return new SculkerSoundRanks(List.of());
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return parse(JsonParser.parseReader(reader).getAsJsonObject());
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Reads a table from its JSON: a {@code ranks} list, each entry a
     * {@code rank} and the {@code sounds} it holds.
     *
     * @param table the table's JSON
     * @return the table
     */
    static SculkerSoundRanks parse(JsonObject table) {
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : table.getAsJsonArray(RANKS)) {
            JsonObject entry = element.getAsJsonObject();
            int rank = entry.get(RANK).getAsInt();
            for (JsonElement sound : entry.getAsJsonArray(SOUNDS)) {
                entries.add(new Entry(patternOf(sound.getAsString()), rank));
            }
        }
        return new SculkerSoundRanks(entries);
    }

    private static Pattern patternOf(String sounds) {
        StringBuilder regex = new StringBuilder();
        int from = 0;
        for (int star = sounds.indexOf(STAR); star >= 0; star = sounds.indexOf(STAR, from)) {
            regex.append(Pattern.quote(sounds.substring(from, star))).append(ANY_RUN);
            from = star + 1;
        }
        return Pattern.compile(regex.append(Pattern.quote(sounds.substring(from))).toString());
    }

    /**
     * The sub-rank a sound takes.
     *
     * @param sound the sound's id, such as {@code minecraft:entity.zombie.ambient}
     * @return its rank, or none where the table names it nowhere
     */
    public OptionalInt rankOf(String sound) {
        return entries.stream().filter(entry -> entry.sounds().matcher(sound).matches())
                .mapToInt(Entry::rank).findFirst();
    }
}
