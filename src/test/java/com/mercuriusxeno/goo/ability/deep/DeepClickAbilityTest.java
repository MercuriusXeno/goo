package com.mercuriusxeno.goo.ability.deep;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.held.HeldEffects;
import com.mercuriusxeno.goo.ability.held.LaidState;
import com.mercuriusxeno.goo.ability.program.FxAnchor;
import com.mercuriusxeno.goo.ability.program.ScanVisualStep;
import com.mercuriusxeno.goo.ability.program.SoundStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.VibrateStep;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deep's Click loads from deep_click.json as a scan costing 1 mB of deep,
 * unlocked by the sculk sensor and ordered after Shriek, whose landing runs a
 * vibrate step and then a sound at the host, and en_us names it with a
 * description (decisions click-is-a-hit-scan-drawn-as-a-delayed-wave,
 * click-unlocks-with-the-sculk-sensor-recipe, click-vibration-carries-no-source-entity).
 */
class DeepClickAbilityTest {

    private static final String LANG = "assets/goo/lang/en_us.json";
    private static final String NAME_KEY = "goo.ability.deep.click";
    private static final Identifier SCULKER = Identifier.fromNamespaceAndPath("goo", "deep_sculker");

    private final AbilityDefinition click = AbilityJson.decode("deep_click");

    @Test
    void isADeepAbilityCostingOneMillibucket() {
        assertEquals(GooTypes.DEEP, click.gooType());
        assertEquals(1, click.cost().in(GooValueTable.EMPTY));
    }

    @Test
    void unlocksWithTheSculkSensor() {
        assertEquals(List.of(Identifier.withDefaultNamespace("sculk_sensor")), click.requires());
    }

    @Test
    void ordersAfterShriek() {
        assertTrue(click.order() > AbilityJson.decode("deep_shriek").order());
    }

    @Test
    void deliversAsAScanWithARangeAndAWaveSpeed() {
        assertEquals(DeliveryKind.SCAN, click.delivery().kind());
        assertTrue(click.delivery().range() > 0);
        assertTrue(click.delivery().blocksPerTick() > 0);
    }

    @Test
    void landsAVibrationThenASoundAtTheHost() {
        List<Step> effects = click.behaviors().stream().filter(step -> !(step instanceof ScanVisualStep)).toList();
        assertEquals(2, effects.size());
        assertInstanceOf(VibrateStep.class, effects.get(0));
        SoundStep sound = assertInstanceOf(SoundStep.class, effects.get(1));
        assertEquals(FxAnchor.HOST, sound.at());
    }

    /** Decisions click-is-a-hit-scan-drawn-as-a-delayed-wave, ability-json-names-its-choreography. */
    @Test
    void namesTheSoundWaveVisualInItsChoreography() {
        assertEquals("sound_wave", ScanVisualStep.in(click.behaviors()).map(ScanVisualStep::visual).orElse(""));
    }

    /** Decision click-costs-one-mb-and-nothing-under-sculker. */
    @Test
    void costsOneMillibucketWithoutSculker() {
        assertEquals(GooFraction.of(1), click.costFor(HeldEffects.NONE::holds, GooValueTable.EMPTY));
    }

    /**
     * A standing Sculker waives the price whatever its dive: the held record
     * knows nothing of diving, so this Sculker is one not diving.
     * Decision click-costs-one-mb-and-nothing-under-sculker.
     */
    @Test
    void costsNothingWhileSculkerStands() {
        HeldEffects sculker = HeldEffects.NONE
                .start(new HeldEffects.Held(SCULKER, GooTypes.DEEP, 1, Set.of(LaidState.HEART_OVERLAY), 0)).after();
        assertEquals(GooFraction.ZERO, click.costFor(sculker::holds, GooValueTable.EMPTY));
    }

    /** Decision click-costs-one-mb-and-nothing-under-sculker. */
    @Test
    void namesTheFreeUnderSculkerRule() {
        assertEquals(Optional.of(SCULKER), click.freeWhile());
        assertTrue(click.costFormula().orElse("").contains("Sculker"), click.costFormula().toString());
    }

    @Test
    void enUsNamesItAndDescribesIt() throws IOException {
        assertEquals(NAME_KEY, click.displayName());
        try (InputStream in = DeepClickAbilityTest.class.getClassLoader().getResourceAsStream(LANG)) {
            assertNotNull(in, LANG + " should ship");
            JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertTrue(lang.has(NAME_KEY), NAME_KEY);
            assertTrue(lang.has(click.description()), click.description());
        }
    }
}
