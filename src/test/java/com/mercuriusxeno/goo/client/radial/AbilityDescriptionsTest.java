package com.mercuriusxeno.goo.client.radial;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every ability's English description is one plain sentence of at most {@link #WORD_CAP} words, read from en_us.json
 * through the classpath.
 * decision ability-descriptions-are-tightened
 */
class AbilityDescriptionsTest {

    private static final String LANG_RESOURCE = "assets/goo/lang/en_us.json";
    /** An ability description's key: goo.ability, the type, the ability, then description. */
    private static final Pattern DESCRIPTION_KEY = Pattern.compile("goo\\.ability\\.[a-z_]+\\.[a-z_]+\\.description");
    /** The most words a description holds. */
    private static final int WORD_CAP = 18;
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]");

    private static JsonObject englishLang() throws IOException {
        try (InputStream stream = AbilityDescriptionsTest.class.getClassLoader().getResourceAsStream(LANG_RESOURCE)) {
            assertNotNull(stream, LANG_RESOURCE + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static List<String> descriptionKeys(JsonObject lang) {
        return lang.keySet().stream().filter(key -> DESCRIPTION_KEY.matcher(key).matches()).sorted().toList();
    }

    @Test
    void everyAbilityDescriptionIsOneSentenceWithinTheWordCap() throws IOException {
        JsonObject lang = englishLang();
        List<String> keys = descriptionKeys(lang);
        assertFalse(keys.isEmpty(), "no ability description in " + LANG_RESOURCE);
        assertAll(keys.stream().map(key -> (Executable) () -> {
            String text = lang.get(key).getAsString().strip();
            assertEquals(1, SENTENCE_END.matcher(text).results().count(), key + " sentences: " + text);
            assertTrue(SENTENCE_END.matcher(text.substring(text.length() - 1)).matches(), key + " ends: " + text);
            int words = text.split("\\s+").length;
            assertTrue(words <= WORD_CAP, key + " holds " + words + " words: " + text);
        }));
    }
}
