package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mod's en_us.json as a key to text map, read once off the classpath, for tests translating as the game would.
 */
final class EnglishLang {
    private static final String LANG = "assets/goo/lang/en_us.json";
    /** Every key en_us.json holds, with its text. */
    static final Map<String, String> ENTRIES = read();

    private EnglishLang() {
    }

    private static Map<String, String> read() {
        try (InputStream in = EnglishLang.class.getClassLoader().getResourceAsStream(LANG)) {
            if (in == null) {
                throw new IllegalStateException("the classpath holds no " + LANG);
            }
            Map<String, String> entries = new LinkedHashMap<>();
            JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet()
                    .forEach(entry -> entries.put(entry.getKey(), entry.getValue().getAsString()));
            return Map.copyOf(entries);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
