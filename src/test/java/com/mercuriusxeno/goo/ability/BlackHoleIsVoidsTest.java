package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The black hole loads as Void's ability named Black Hole, last on Void's wheel, and Nether ships
 * Decay and Undead alone; reads the shipped ability files and en_us.json through the classpath.
 * decision anti-moves-from-nether-to-void
 * decision anti-is-renamed-black-hole
 */
class BlackHoleIsVoidsTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String BLACK_HOLE = "void_black_hole";
    private static final String NAME_KEY = "goo.ability.void.black_hole";
    private static final String OLD_NAME_KEY = "goo.ability.nether.black_hole";
    /** After Erase 0, Hole 10, Outsider 20 and the Vacuum another thread adds at 30. */
    private static final int WHEEL_ORDER = 40;
    private static final String ICON = "/assets/goo/textures/goo/ability/void_black_hole.png";

    private static JsonObject lang;

    @BeforeAll
    static void readLang() throws IOException {
        try (InputStream in = BlackHoleIsVoidsTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US);
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void blackHoleLoadsAsAVoidAbilityLastOnItsWheel() {
        AbilityDefinition hole = AbilityJson.decode(BLACK_HOLE);

        assertEquals(GooTypes.VOID, hole.gooType());
        assertEquals(NAME_KEY, hole.displayName());
        assertEquals(WHEEL_ORDER, hole.order());
        assertNotNull(BlackHoleIsVoidsTest.class.getResource(ICON), ICON);
    }

    @Test
    void blackHoleReadsBlackHoleUnderVoidsKey() {
        assertEquals("Black Hole", lang.get(NAME_KEY).getAsString());
        assertNotNull(lang.get(NAME_KEY + ".description"), NAME_KEY + ".description");
        List<String> nether = lang.keySet().stream().filter(key -> key.startsWith(OLD_NAME_KEY)).toList();
        assertEquals(List.of(), nether, "lang keys still naming the black hole under Nether");
    }

    @Test
    void netherShipsDecayAndUndead() {
        Set<String> nether = AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(ability -> ability.gooType() == GooTypes.NETHER)
                .map(ability -> ability.id().getPath())
                .collect(Collectors.toSet());

        assertEquals(Set.of("nether_decay", "nether_undead"), nether);
    }
}
