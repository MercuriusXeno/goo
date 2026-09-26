package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Each mob ability JSON is a program that loads for the struck-entity host,
 * and what a program asks of that host (an explosion, an entities
 * selection) runs here on a Mockito host. An effect step acts on the entity
 * the host hands over, which no unit test can build, so the struck mob's
 * reaction is graded by MobEffectTests through the throw's impact (decision
 * world-tests-assert-one-observation).
 */
class MobProgramTest {

    private static final float MOB_BLAST_POWER = 2;
    private static final double FLECHETTE_SPLASH_RADIUS = 3;
    private static final double IGNITE_SPLASH_RADIUS = 2.5;

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
        EntityHost host = mock(EntityHost.class);
        when(host.kind()).thenReturn(HostKind.ENTITY);

        ProgramBehavior program = ProgramBehavior.forHost(ability("unstable_explode").behaviors(), HostKind.ENTITY);
        program.tick(host);
        assertFalse(program.isActive());

        verify(host).explode(MOB_BLAST_POWER, ExplosionMode.TNT);
        verifyNoMoreInteractions(host);
    }

    @Test
    void crystalFlechettesSplashesLivingBesideTheTargetWithinThree() {
        verify(tickSplash("crystal_flechettes")).forEachEntityWithin(eq(SelectionShape.SPHERE), eq(FLECHETTE_SPLASH_RADIUS),
                eq(Set.of(EntityFilter.LIVING, EntityFilter.NOT_TARGET)), any());
    }

    @Test
    void blazeIgniteSplashesBurnableLivingWithinTwoAndAHalf() {
        verify(tickSplash("blaze_ignite")).forEachEntityWithin(eq(SelectionShape.SPHERE), eq(IGNITE_SPLASH_RADIUS),
                eq(Set.of(EntityFilter.LIVING, EntityFilter.NOT_FIRE_IMMUNE)), any());
    }

    /**
     * Ticks the entities selections of the named ability on a mock struck
     * entity host, leaving out the steps that act on the target itself,
     * which need a live entity (MobEffectTests grades those).
     */
    private static EntityHost tickSplash(String name) {
        EntityHost host = mock(EntityHost.class);
        when(host.kind()).thenReturn(HostKind.ENTITY);
        List<Step> splash = ability(name).behaviors().stream().filter(EntitiesStep.class::isInstance).toList();
        new ProgramBehavior(splash).tick(host);
        return host;
    }
}
