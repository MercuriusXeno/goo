package com.mercuriusxeno.goo.ability.xeno;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.program.GloomStep;
import com.mercuriusxeno.goo.ability.program.OozeStep;
import com.mercuriusxeno.goo.block.ability.CellBinding;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Xeno's Ooze decodes from xeno_ooze.json through the ability codec as a
 * residual stream carrying Gloom's numbers and the slime turn, its cell bound
 * to xeno, and nether_gloom.json decodes with the turn gone from Gloom.
 * ooze-is-xenos-world-goo-that-turns-slimes-into-random-hostiles
 * gloom-turns-slimes-into-random-hostiles-in-place-of-spawn
 */
class OozeAbilityTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String OOZE_KEY = "goo.ability.xeno.ooze";
    /** The layers a block of Ooze holds, over which its cost per block is spread. */
    private static final int LAYERS_PER_BLOCK = 15;
    /** The zoo goo Spawn's cast cost to turn one slime hostile, before the turn left it. */
    private static final double SPAWN_CAST_COST = 2000;

    @Test
    void oozeIsAXenoResidualStreamAtNinetySixAMetreCubed() {
        AbilityDefinition ooze = AbilityJson.decode("xeno_ooze");

        assertEquals(List.of(GooTypes.XENO, AbilityBadge.RESIDUAL, DeliveryKind.STREAM, 96.0),
                List.of(ooze.gooType(), ooze.badge(), ooze.delivery().kind(),
                        (double) ooze.cost().in(GooValueTable.EMPTY)));
    }

    @Test
    void oozeTurnsASlimeAtEightLayersWithATwentyTickMorph() {
        OozeStep ooze = OozeStep.in(AbilityJson.decode("xeno_ooze").behaviors()).orElseThrow();

        assertEquals(List.of(8, 20), List.of(ooze.slimeTurnLayers(), ooze.slimeTurn().morphTicks()));
    }

    @Test
    void oozesCellIsBoundToXeno() {
        assertEquals(GooTypes.XENO, CellBinding.OOZE.gooType());
    }

    @Test
    void gloomsBehaviorDecodesAsTheBareGloomStep() {
        assertEquals(new GloomStep(), GloomStep.in(AbilityJson.decode("nether_gloom").behaviors()).orElseThrow());
    }

    // gloom-turns-slimes-into-random-hostiles-in-place-of-spawn
    @Test
    void aSlimesTurnOnOozeCostsLessThanSpawnsCast() {
        AbilityDefinition ooze = AbilityJson.decode("xeno_ooze");
        int turnLayers = OozeStep.in(ooze.behaviors()).orElseThrow().slimeTurnLayers();
        double turnCost = turnLayers * (double) ooze.cost().in(GooValueTable.EMPTY) / LAYERS_PER_BLOCK;

        assertTrue(turnCost < SPAWN_CAST_COST, "a turn costs " + turnCost + " xeno goo");
    }

    @Test
    void oozeReadsOozeWithADescription() throws IOException {
        JsonObject lang = classpathJson(EN_US);

        assertEquals(OOZE_KEY, AbilityJson.decode("xeno_ooze").displayName());
        assertNotNull(lang.get(OOZE_KEY), OOZE_KEY);
        assertEquals("Ooze", lang.get(OOZE_KEY).getAsString());
        assertNotNull(lang.get(OOZE_KEY + ".description"), OOZE_KEY + ".description");
        assertFalse(lang.get(OOZE_KEY + ".description").getAsString().isBlank(), OOZE_KEY + ".description");
    }

    private static JsonObject classpathJson(String path) throws IOException {
        try (InputStream in = OozeAbilityTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
