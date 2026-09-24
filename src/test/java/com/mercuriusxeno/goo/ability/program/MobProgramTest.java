package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Each mob ability JSON is a program that loads for the struck-entity host
 * (tasks mob-effect-programs and mob-world-programs). An effect step acts
 * on the entity the host hands over, which no unit test can build, so each
 * program's effects are proven by its mob gametest in MobEffectTests; a
 * program of world calls alone runs here on a Mockito host (decision
 * step-tick-holds-effect).
 */
class MobProgramTest {

    private static final String ABILITIES_DIR = "data/goo/goo_abilities/";
    private static final String JSON_SUFFIX = ".json";
    private static final float MOB_BLAST_POWER = 2;

    private static AbilityDefinition ability(String name) {
        String path = ABILITIES_DIR + name + JSON_SUFFIX;
        InputStream stream = MobProgramTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"metal_javelin", "crystal_flechettes", "leaf_entangle", "vital_clone", "shroom_debuff",
        "rock_petrify", "blaze_ignite", "frost_snap", "typhoon_levitate", "glow_laser", "hex_charm",
        "pulse_short_circuit", "nether_wither", "ender_teleport", "unstable_explode", "aeon_time_stop"})
    void everyMobAbilityIsAProgramThatLoadsForTheStruckEntityHost(String name) {
        for (AbilityDefinition.BehaviorEntry entry : ability(name).behaviors()) {
            assertEquals(ProgramBehavior.TYPE_NAME, entry.type(), name);
            assertDoesNotThrow(() -> ProgramBehavior.forHost(entry.steps(), HostKind.ENTITY), name);
        }
    }

    @Test
    void unstableExplodeDetonatesTntAtTheTarget() {
        StepHost host = mock(StepHost.class);
        when(host.kind()).thenReturn(HostKind.ENTITY);

        for (AbilityDefinition.BehaviorEntry entry : ability("unstable_explode").behaviors()) {
            ProgramBehavior program = ProgramBehavior.forHost(entry.steps(), HostKind.ENTITY);
            program.tick(host);
            assertFalse(program.isActive());
        }

        verify(host).explode(MOB_BLAST_POWER, ExplosionMode.TNT);
        verifyNoMoreInteractions(host);
    }
}
