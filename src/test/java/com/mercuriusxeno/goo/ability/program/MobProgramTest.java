package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static final float MOB_BLAST_POWER = 2;

    private static AbilityDefinition ability(String name) {
        return AbilityJson.decode(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"metal_javelin", "crystal_flechettes", "leaf_entangle", "vital_clone", "shroom_debuff",
        "rock_petrify", "blaze_ignite", "frost_snap", "typhoon_levitate", "glow_laser", "hex_charm",
        "pulse_short_circuit", "nether_wither", "ender_teleport", "unstable_explode", "aeon_time_stop"})
    void everyMobAbilityIsAProgramThatLoadsForTheStruckEntityHost(String name) {
        assertDoesNotThrow(() -> ProgramBehavior.forHost(ability(name).behaviors(), HostKind.ENTITY), name);
    }

    @Test
    void unstableExplodeDetonatesTntAtTheTarget() {
        StepHost host = mock(StepHost.class);
        when(host.kind()).thenReturn(HostKind.ENTITY);

        ProgramBehavior program = ProgramBehavior.forHost(ability("unstable_explode").behaviors(), HostKind.ENTITY);
        program.tick(host);
        assertFalse(program.isActive());

        verify(host).explode(MOB_BLAST_POWER, ExplosionMode.TNT);
        verifyNoMoreInteractions(host);
    }
}
