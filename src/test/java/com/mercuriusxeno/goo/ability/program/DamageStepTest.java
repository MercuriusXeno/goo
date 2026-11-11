package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The damage type a damage step hurts with, read from the shipped ability JSON through the kind's mapping.
 */
class DamageStepTest {

    private static final Identifier GOO_STALAGMITE = Identifier.parse("goo:stalagmite");

    /**
     * Urchin's stab deals goo:stalagmite, which no fall tag holds, so a fall-immune chicken takes it.
     */
    // decision urchin-rests-bristled-and-spears
    @Test
    void urchinsStabDealsGooStalagmiteDamage() {
        assertEquals(Optional.of(GOO_STALAGMITE), DamageStep.gooTypeOf(urchinsStab().source()).map(ResourceKey::identifier));
    }

    private static DamageStep urchinsStab() {
        FieldEffectStep field = LingerStep.bodyOf(AbilityJson.decode("metal_spikes").behaviors()).orElseThrow().stream()
                .filter(FieldEffectStep.class::isInstance).map(FieldEffectStep.class::cast).findFirst().orElseThrow();
        return (DamageStep) field.strike().getFirst();
    }
}
