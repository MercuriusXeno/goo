package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.program.EntityFilter;
import com.mercuriusxeno.goo.ability.program.FieldEffectStep;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * zombie_decay.json and its icon, read from the classpath as the loader reads them: the hive its hold makes of a
 * prism strikes living mobs alone (decision hive-prism-pillar-eats-the-living), and the icon wears zombie goo's
 * colors (decision zombie-goo-is-colored-after-zombie-flesh-and-decay-stops-being-maroon).
 */
class ZombieDecayJsonTest {

    private static final String ICON = "/assets/goo/textures/goo/ability/zombie_decay.png";
    private static final String FLUID_TYPES = "/data/goo/goo_fluid_types.json";
    private static final int OPAQUE_ALPHA = 255;
    private static final int RGB_MASK = 0xFFFFFF;

    private final AbilityDefinition definition = AbilityJson.decode("zombie_decay");

    /**
     * The hive's field keeps living mobs still alive, the operator's ruling of mobs only.
     */
    @Test
    void theHiveStrikesLivingMobsAlone() {
        FieldEffectStep hive = (FieldEffectStep) definition.onPrism().getFirst();
        assertEquals(List.of(EntityFilter.LIVING, EntityFilter.ALIVE, EntityFilter.MOB), hive.where());
    }

    /**
     * Every pixel the Decay icon draws is a stop of zombie goo's palette, green and meat brown
     * (operator pick 2026-11-18: "Decay 2", green gnat bodies in meat-brown rims).
     */
    @Test
    void theIconWearsZombiesPalette() throws IOException {
        Set<String> zombie = zombiePaletteStops();
        Set<String> drawn = new TreeSet<>();
        BufferedImage icon = read(ICON);
        for (int y = 0; y < icon.getHeight(); y++) {
            for (int x = 0; x < icon.getWidth(); x++) {
                int argb = icon.getRGB(x, y);
                if (argb >>> 24 == OPAQUE_ALPHA) {
                    drawn.add(String.format(Locale.ROOT, "%06X", argb & RGB_MASK));
                }
            }
        }
        drawn.removeAll(zombie);
        assertEquals(Set.of(), drawn, "colors outside zombie's palette");
    }

    private static Set<String> zombiePaletteStops() throws IOException {
        Set<String> stops = new HashSet<>();
        try (InputStream in = ZombieDecayJsonTest.class.getResourceAsStream(FLUID_TYPES)) {
            assertNotNull(in, FLUID_TYPES);
            JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray().forEach(entry -> {
                if ("zombie".equals(entry.getAsJsonObject().get("id").getAsString())) {
                    entry.getAsJsonObject().getAsJsonArray("palette").forEach(stop ->
                            stops.add(stop.getAsJsonObject().get("color").getAsString().substring(1)
                                    .toUpperCase(Locale.ROOT)));
                }
            });
        }
        return stops;
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream in = ZombieDecayJsonTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return ImageIO.read(in);
        }
    }
}
