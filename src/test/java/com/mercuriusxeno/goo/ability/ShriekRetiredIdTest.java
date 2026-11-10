package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.held.HeldEffects;
import com.mercuriusxeno.goo.ability.held.LaidState;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.StepTypes;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Convoke's retired ids load after its rename to Shriek: a step list holding
 * a step of the retired type convoke beside a known step loads with the
 * known step standing and the convoke step dropped, and a held record naming
 * goo:deep_convoke beside a known ability ends the convoke effect and keeps
 * the known one. This file alone under src names convoke, since it must name
 * the retired id to prove it loads (decision convoke-is-renamed-shriek).
 */
class ShriekRetiredIdTest {

    private static final Identifier RETIRED = Identifier.fromNamespaceAndPath("goo", "deep_convoke");
    private static final Identifier SCULKER = Identifier.fromNamespaceAndPath("goo", "deep_sculker");
    private static final String STEPS = """
            [
              { "type": "convoke", "every": 40, "reach": 16, "lasts": 600 },
              { "type": "wait", "ticks": 4 }
            ]""";

    @Test
    void aStepListDropsTheConvokeStepAndKeepsTheKnownOne() {
        JsonArray steps = JsonParser.parseString(STEPS).getAsJsonArray();
        List<Step> loaded = StepTypes.LIST_CODEC.parse(JsonOps.INSTANCE, steps).getOrThrow();
        assertEquals(1, loaded.size());
        assertEquals("wait", loaded.get(0).type().name());
    }

    @Test
    void aHeldRecordEndsTheConvokeEffectAndKeepsTheKnownOne() {
        HeldEffects held = HeldEffects.NONE
                .start(new HeldEffects.Held(RETIRED, GooTypes.DEEP, 1, Set.of(), 0)).after()
                .start(new HeldEffects.Held(SCULKER, GooTypes.DEEP, 1, Set.of(LaidState.HEART_OVERLAY), 0)).after();
        HeldEffects after = held.endUnknown(SCULKER::equals).after();
        assertFalse(after.holds(RETIRED));
        assertTrue(after.holds(SCULKER));
    }
}
