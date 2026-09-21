package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.OptionalDouble;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Each migrated mob ability JSON is a program that, run on a Mockito
 * struck-entity host, makes the same effect and damage calls its deleted
 * handler made (task mob-effect-programs).
 */
class MobProgramTest {

    private static final String ABILITIES_DIR = "data/goo/goo_abilities/";
    private static final String JSON_SUFFIX = ".json";
    private static final String LEVITATION = "minecraft:levitation";
    private static final String SLOWNESS = "minecraft:slowness";
    private static final String POISON = "minecraft:poison";
    private static final String WEAKNESS = "minecraft:weakness";
    private static final Set<EntityFilter> NOT_BOSS = Set.of(EntityFilter.NOT_BOSS);
    private static final double COW_HEALTH = 10;
    private static final int SHROOM_DURATION = 200;
    private static final String WITHER = "minecraft:wither";
    private static final int WITHER_DURATION = 200;
    private static final float HALF = 0.5f;
    private static final float SNAP_DAMAGE = 4;
    private static final int FULL_FREEZE = 140;
    private static final int SNAP_SLOW_DURATION = 60;
    private static final int SNAP_SLOW_AMPLIFIER = 3;
    private static final Set<EntityFilter> MOB = Set.of(EntityFilter.MOB);
    private static final int STUN_DURATION = 100;
    private static final int STUN_AMPLIFIER = 127;
    private static final String GLOWING = "minecraft:glowing";
    private static final int TIME_STOP_GLOW_DURATION = 60;
    private static final float MOB_BLAST_POWER = 2;
    /** floor(20 * 60 / pow(10, 0.4)), the charm's ticks at the cow's health of ten. */
    private static final int CHARM_TICKS_AT_COW_HEALTH = 477;
    private static final int CHARM_WEAKNESS_AMPLIFIER = 4;
    /** 100 / pow(10, 0.6), the clone's percent chance at the cow's max health of ten. */
    private static final float CLONE_CHANCE_AT_COW_HEALTH = (float) (100 / Math.pow(COW_HEALTH, 0.6));
    private static final String COBBLESTONE = "minecraft:cobblestone";
    private static final int PETRIFY_DURATION = 100;
    private static final int PETRIFY_AMPLIFIER = 127;
    private static final int CRUSH_DROP_MAX = 3;
    private static final int LEVITATE_DURATION = 100;
    private static final int LEVITATE_AMPLIFIER = 1;
    private static final int ENTANGLE_SLOW_DURATION = 100;
    private static final int ENTANGLE_SLOW_AMPLIFIER = 2;
    private static final int ENTANGLE_POISON_DURATION = 60;

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

    private static StepHost entityHost() {
        StepHost host = mock(StepHost.class);
        when(host.kind()).thenReturn(HostKind.ENTITY);
        when(host.read(HostVariables.HEALTH)).thenReturn(OptionalDouble.of(COW_HEALTH));
        when(host.read(HostVariables.MAX_HEALTH)).thenReturn(OptionalDouble.of(COW_HEALTH));
        return host;
    }

    /**
     * Runs every behavior entry of the ability on the host as the struck
     * entity path does, asserting each entry is a program.
     *
     * @param name the ability file name without its suffix
     * @param host the host to run against
     */
    private static void run(String name, StepHost host) {
        for (AbilityDefinition.BehaviorEntry entry : ability(name).behaviors()) {
            assertEquals(ProgramBehavior.TYPE_NAME, entry.type(), name);
            ProgramBehavior.forHost(entry.steps(), HostKind.ENTITY).tick(host);
        }
    }

    @Test
    void typhoonLevitateAppliesLevitation() {
        StepHost host = entityHost();

        run("typhoon_levitate", host);

        verify(host).applyPotion(Identifier.parse(LEVITATION), LEVITATE_DURATION, LEVITATE_AMPLIFIER, true);
    }

    @Test
    void shroomDebuffAppliesSlownessWeaknessAndPoisonToANonBoss() {
        StepHost host = entityHost();
        when(host.targetPasses(NOT_BOSS)).thenReturn(true);

        run("shroom_debuff", host);

        InOrder order = inOrder(host);
        order.verify(host).applyPotion(Identifier.parse(SLOWNESS), SHROOM_DURATION, 1, true);
        order.verify(host).applyPotion(Identifier.parse(WEAKNESS), SHROOM_DURATION, 1, true);
        order.verify(host).applyPotion(Identifier.parse(POISON), SHROOM_DURATION, 0, true);
    }

    @Test
    void shroomDebuffLeavesABossAlone() {
        StepHost host = entityHost();
        when(host.targetPasses(NOT_BOSS)).thenReturn(false);

        run("shroom_debuff", host);

        verify(host, never()).applyPotion(any(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void netherWitherHalvesHealthThenWithersANonBoss() {
        StepHost host = entityHost();
        when(host.targetPasses(NOT_BOSS)).thenReturn(true);

        run("nether_wither", host);

        InOrder order = inOrder(host);
        order.verify(host).setTargetHealthFraction(HALF);
        order.verify(host).applyPotion(Identifier.parse(WITHER), WITHER_DURATION, 1, true);
    }

    @Test
    void frostSnapDamagesFreezesAndSlowsANonBoss() {
        StepHost host = entityHost();
        when(host.targetPasses(NOT_BOSS)).thenReturn(true);

        run("frost_snap", host);

        InOrder order = inOrder(host);
        order.verify(host).damageTarget(SNAP_DAMAGE, DamageKind.FREEZE);
        order.verify(host).addTargetFreezeTicks(FULL_FREEZE);
        order.verify(host).applyPotion(Identifier.parse(SLOWNESS), SNAP_SLOW_DURATION, SNAP_SLOW_AMPLIFIER, true);
    }

    @Test
    void pulseShortCircuitStopsAMobsAiThenSlowsItToAStandstill() {
        StepHost host = entityHost();
        when(host.targetPasses(MOB)).thenReturn(true);

        run("pulse_short_circuit", host);

        InOrder order = inOrder(host);
        order.verify(host).setTargetAi(false);
        order.verify(host).applyPotion(Identifier.parse(SLOWNESS), STUN_DURATION, STUN_AMPLIFIER, true);
    }

    @Test
    void aeonTimeStopFreezesAMobInPlaceAndMarksIt() {
        StepHost host = entityHost();
        when(host.targetPasses(MOB)).thenReturn(true);

        run("aeon_time_stop", host);

        InOrder order = inOrder(host);
        order.verify(host).setTargetAi(false);
        order.verify(host).setTargetInvulnerable(true);
        order.verify(host).applyPotion(Identifier.parse(GLOWING), TIME_STOP_GLOW_DURATION, 0, true);
    }

    @Test
    void unstableExplodeDetonatesTntAtTheTarget() {
        StepHost host = entityHost();

        run("unstable_explode", host);

        verify(host).explode(MOB_BLAST_POWER, ExplosionMode.TNT);
        verifyNoMoreInteractions(host);
    }

    @Test
    void hexCharmWeakensAndMarksAMobForLongerTheWeakerItIs() {
        StepHost host = entityHost();
        when(host.targetPasses(MOB)).thenReturn(true);

        run("hex_charm", host);

        InOrder order = inOrder(host);
        order.verify(host).applyPotion(Identifier.parse(WEAKNESS), CHARM_TICKS_AT_COW_HEALTH, CHARM_WEAKNESS_AMPLIFIER, true);
        order.verify(host).applyPotion(Identifier.parse(GLOWING), CHARM_TICKS_AT_COW_HEALTH, 0, true);
    }

    @Test
    void vitalCloneRollsAMobsCloneAgainstItsMaxHealth() {
        StepHost host = entityHost();
        when(host.targetPasses(MOB)).thenReturn(true);

        run("vital_clone", host);

        verify(host).cloneTarget(CLONE_CHANCE_AT_COW_HEALTH);
    }

    @Test
    void rockPetrifyStillsCrushesAndDropsOneToThreeCobblestone() {
        StepHost host = entityHost();

        run("rock_petrify", host);

        InOrder order = inOrder(host);
        order.verify(host).applyPotion(Identifier.parse(SLOWNESS), PETRIFY_DURATION, PETRIFY_AMPLIFIER, true);
        order.verify(host).damageTarget((float) COW_HEALTH, DamageKind.MAGIC);
        order.verify(host).dropItemAtTarget(eq(Identifier.parse(COBBLESTONE)),
                intThat(count -> count >= 1 && count <= CRUSH_DROP_MAX));
    }

    @Test
    void leafEntangleAppliesSlownessThenPoison() {
        StepHost host = entityHost();

        run("leaf_entangle", host);

        InOrder order = inOrder(host);
        order.verify(host).applyPotion(Identifier.parse(SLOWNESS), ENTANGLE_SLOW_DURATION, ENTANGLE_SLOW_AMPLIFIER, true);
        order.verify(host).applyPotion(Identifier.parse(POISON), ENTANGLE_POISON_DURATION, 0, true);
        verifyNoMoreInteractions(host);
    }
}
