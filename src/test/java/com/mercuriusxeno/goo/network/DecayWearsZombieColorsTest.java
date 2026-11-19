package com.mercuriusxeno.goo.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.DegradeStep;
import com.mercuriusxeno.goo.client.ber.style.HivePrismStyle;
import com.mercuriusxeno.goo.client.particle.BlendPalette;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Decay wears zombie goo's colors, read from zombie.json through the classpath: its gnats blend from zombie's bright
 * to its edge, its decaying block and its charging prism wear zombie's bright, and its hive column wears zombie's
 * edge. The gnats are read at BlendPalette.ZOMBIE_ROT, the palette GnatParticle.Provider draws by default, since the
 * provider holds it privately.
 * decision zombie-goo-is-colored-after-zombie-flesh-and-decay-stops-being-maroon
 */
class DecayWearsZombieColorsTest {

    private static final String ZOMBIE_JSON = "/data/goo/goo/goo_type/zombie.json";
    private static final int RGB_MASK = 0xFFFFFF;
    private static final float CHANNEL = 255f;
    private static final float DELTA = 1e-6f;
    private static final int QUARTZ = 0xC8F0E8E0;

    private static int bright;
    private static int edge;

    @BeforeAll
    static void readZombiesColors() throws IOException {
        try (InputStream in = DecayWearsZombieColorsTest.class.getResourceAsStream(ZOMBIE_JSON)) {
            assertNotNull(in, ZOMBIE_JSON);
            JsonObject zombie = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            bright = Integer.parseInt(zombie.get("bright").getAsString(), 16);
            edge = Integer.parseInt(zombie.get("edge").getAsString(), 16);
        }
    }

    @Test
    void gnatsBlendFromZombiesBrightToItsEdge() {
        BlendPalette gnats = BlendPalette.ZOMBIE_ROT;
        assertEquals(red(bright), gnats.lightRed(), DELTA, "light red");
        assertEquals(green(bright), gnats.lightGreen(), DELTA, "light green");
        assertEquals(blue(bright), gnats.lightBlue(), DELTA, "light blue");
        assertEquals(red(edge), gnats.darkRed(), DELTA, "dark red");
        assertEquals(green(edge), gnats.darkGreen(), DELTA, "dark green");
        assertEquals(blue(edge), gnats.darkBlue(), DELTA, "dark blue");
    }

    @Test
    void decayingBlockWearsZombiesBright() {
        assertEquals(Integer.toHexString(bright), Integer.toHexString(DegradeStep.ZOMBIE_ROT));
    }

    @Test
    void chargingPrismWearsZombiesBright() {
        assertEquals(OptionalInt.of(bright), StreamPrismCharge.chargeTint(AbilityJson.decode("zombie_decay")));
    }

    @Test
    void hiveColumnWearsZombiesEdge() {
        assertEquals(Integer.toHexString(edge), Integer.toHexString(HivePrismStyle.columnColor(QUARTZ) & RGB_MASK));
    }

    private static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / CHANNEL;
    }

    private static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / CHANNEL;
    }

    private static float blue(int rgb) {
        return (rgb & 0xFF) / CHANNEL;
    }
}
