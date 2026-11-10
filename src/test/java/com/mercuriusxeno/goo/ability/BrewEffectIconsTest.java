package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every goo type a shipped brew ability belongs to ships its brew effect's
 * icon, so the effect list never draws the missing-texture tile for a brew
 * held from the glove or drunk (decisions brew-runs-the-crawl-prepaid-on-a-shown-clock
 * and dive-is-renamed-sculker).
 */
class BrewEffectIconsTest {

    private static final String BREW_BADGE = "durative";
    private static final String ICON_PATH = "/assets/goo/textures/mob_effect/%s_held.png";

    @Test
    void everyBrewTypeShipsItsEffectIcon() {
        List<String> missing = new ArrayList<>();
        for (Path file : AbilityJson.files()) {
            JsonObject ability = parse(file);
            if (ability.has("badge") && BREW_BADGE.equals(ability.get("badge").getAsString())) {
                String icon = String.format(ICON_PATH, ability.get("gooType").getAsString());
                if (BrewEffectIconsTest.class.getResource(icon) == null) {
                    missing.add(file.getFileName() + " -> " + icon);
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    private static JsonObject parse(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
