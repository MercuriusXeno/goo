package com.mercuriusxeno.goo.client.radial;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the glove menu key's press and release decisions and its lang entries
 * (decisions g-opens-radial-while-glove-held, radial-selects-on-g-release).
 * Reads en_us.json through the classpath.
 */
class GloveRadialKeyGateTest {

    /** Records what a release resolved to, in order. */
    private static final class RecordingRelease implements GloveRadialKeyGate.ReleaseActions {
        private final List<String> calls = new ArrayList<>();
        private RadialWheel.Outcome selected;

        @Override
        public void selectHovered(RadialWheel.Outcome hovered) {
            calls.add("selectHovered");
            selected = hovered;
        }

        @Override
        public void close() {
            calls.add("close");
        }
    }

    @Nested
    class Press {

        @Test
        void pressWithGloveHeldAndNoScreenOpensRadial() {
            assertTrue(GloveRadialKeyGate.pressOpensRadial(true, false));
        }

        @Test
        void pressWithNoGloveHeldOpensNothing() {
            assertFalse(GloveRadialKeyGate.pressOpensRadial(false, false));
        }

        @Test
        void pressWithScreenOpenOpensNothing() {
            assertFalse(GloveRadialKeyGate.pressOpensRadial(true, true));
        }
    }

    @Nested
    class Release {

        @Test
        void releaseOverHoveredAbilitySelectsItThenCloses() {
            RadialWheel.Outcome hovered = new RadialWheel.Outcome(2, 1);
            RecordingRelease release = new RecordingRelease();

            GloveRadialKeyGate.release(hovered, release);

            assertEquals(List.of("selectHovered", "close"), release.calls);
            assertEquals(hovered, release.selected);
        }

        @Test
        void releaseOverNothingClosesUnchanged() {
            RecordingRelease release = new RecordingRelease();

            GloveRadialKeyGate.release(RadialWheel.Outcome.CANCEL, release);

            assertEquals(List.of("close"), release.calls);
        }
    }

    @Nested
    class Translations {

        @Test
        void mappingNameAndCategoryHaveEnglishEntries() throws Exception {
            JsonObject lang = readLang();
            String categoryKey = Identifier.fromNamespaceAndPath(Goo.MODID, GloveRadialKeyGate.CATEGORY_PATH)
                    .toLanguageKey("key.category");

            assertTrue(lang.has(GloveRadialKeyGate.NAME_KEY), GloveRadialKeyGate.NAME_KEY + " should have a translation");
            assertTrue(lang.has(categoryKey), categoryKey + " should have a translation");
        }

        private JsonObject readLang() throws Exception {
            try (InputStream in = GloveRadialKeyGateTest.class.getResourceAsStream("/assets/goo/lang/en_us.json")) {
                assertNotNull(in, "en_us.json missing on the classpath");
                return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
    }
}
