package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.program.ChargedMultipliers;
import com.mercuriusxeno.goo.ability.program.DamageKind;
import com.mercuriusxeno.goo.ability.program.DamageStep;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.StepContext;
import com.mercuriusxeno.goo.ability.program.StepHost;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.OptionalDouble;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Charged's multipliers read 1 wherever an ability's JSON names none, and a
 * charged cast's programs read them as variables
 * (decision charged-scales-channel-params-by-json).
 */
class ChargedMultipliersTest {

    private static final double DELTA = 1e-9;

    private static ChargedMultipliers decode(String json) {
        return ChargedMultipliers.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Nested
    class Defaults {

        @Test
        void anEmptyChargedObjectChangesNothing() {
            assertEquals(ChargedMultipliers.NONE, decode("{}"));
        }

        @Test
        void eachMultiplierTheJsonLeavesOutReadsOne() {
            assertEquals(new ChargedMultipliers(1.5, 1, 1, 2), decode("{\"area\": 1.5, \"efficacy\": 2}"));
        }

        @Test
        void anAbilityNamingNoChargedTakesNone() {
            assertEquals(ChargedMultipliers.NONE, AbilityJson.decode("unstable_explode").charged());
        }

        @Test
        void spitfireNamesItsOwnMultipliers() {
            assertEquals(new ChargedMultipliers(1.5, 2, 1, 1), AbilityJson.decode("blaze_spitfire").charged());
        }
    }

    @Nested
    class Variables {

        private final StepHost host = mock(StepHost.class);

        @Test
        void aChargedCastReadsItsMultipliersAndAnUnchargedOneReadsOne() {
            when(host.read(anyString())).thenReturn(OptionalDouble.empty());
            ChargedMultipliers charged = new ChargedMultipliers(1.5, 2, 3, 4);

            StepContext context = new StepContext(host, 0, 0, charged);

            assertEquals(4.0, Expr.parse(ChargedMultipliers.VAR_EFFICACY).getOrThrow().evaluate(context), DELTA);
            assertEquals(3.0, Expr.parse(ChargedMultipliers.VAR_SPEED).getOrThrow().evaluate(context), DELTA);
            assertEquals(1.0, Expr.parse(ChargedMultipliers.VAR_AREA).getOrThrow()
                    .evaluate(new StepContext(host, 0, 0)), DELTA);
        }

        @Test
        void everyHostLoadsAProgramReadingTheChargedVariables() {
            List<Step> program = List.of(new DamageStep(
                    Expr.parse("2 * charged_efficacy * charged_duration").getOrThrow(), DamageKind.MAGIC));

            assertDoesNotThrow(() -> ProgramBehavior.forHost(program, HostKind.ENTITY));
            assertDoesNotThrow(() -> ProgramBehavior.forHost(program, HostKind.PLAYER));
        }
    }
}
