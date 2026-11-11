package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.ScanVisualStep;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client registers a renderer for the wave visual Deep's Click names, so
 * the name its JSON choreography carries reaches a drawing
 * (decisions ability-json-names-its-choreography, click-is-a-hit-scan-drawn-as-a-delayed-wave).
 */
class ScanVisualsTest {

    @Test
    void registersARendererForTheVisualClickNames() {
        String named = ScanVisualStep.in(AbilityJson.decode("deep_click").behaviors()).orElseThrow().visual();
        assertTrue(ScanVisuals.rendererFor(named).isPresent(), named);
    }
}
