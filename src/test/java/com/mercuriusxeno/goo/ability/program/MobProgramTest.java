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
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Each mob ability JSON is a program that, run on a Mockito struck-entity
 * host, makes the same effect, damage and world calls its deleted handler
 * made (tasks mob-effect-programs and mob-world-programs).
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
    private static final int IGNITE_SECONDS = 10;
    private static final int SPLASH_IGNITE_SECONDS = 5;
    private static final double IGNITE_RADIUS = 2.5;
    private static final Set<EntityFilter> BURNABLE = Set.of(EntityFilter.LIVING, EntityFilter.NOT_FIRE_IMMUNE);
    /** The body consumer's position among forEachEntityWithin's arguments. */
    private static final int ENTITIES_BODY_ARGUMENT = 3;
    private static final Set<EntityFilter> UNDEAD = Set.of(EntityFilter.UNDEAD);
    private static final Set<EntityFilter> ALIVE = Set.of(EntityFilter.ALIVE);
    private static final float LASER_DAMAGE = 4;
    private static final String CRIT = "minecraft:crit";
    private static final int CRIT_COUNT = 10;
    private static final double CRIT_SPREAD = 0.5;
    private static final double CRIT_SPEED = 0.1;
    private static final int LASER_GLOW_DURATION = 200;
    private static final int LASER_IGNITE_SECONDS = 1;
    private static final float FLECHETTE_DAMAGE = 4;
    private static final float SPLASH_DAMAGE = 2;
    private static final double FLECHETTE_RADIUS = 3;
    private static final Set<EntityFilter> OTHER_LIVING = Set.of(EntityFilter.LIVING, EntityFilter.NOT_TARGET);
    private static final String DAMAGE_INDICATOR = "minecraft:damage_indicator";
    private static final int INDICATOR_COUNT = 15;
    private static final double INDICATOR_SPREAD_ALONG = 0.5;
    private static final double INDICATOR_SPREAD_ACROSS = 1.5;
    private static final double TELEPORT_RANGE = 32;
    private static final String ENDERMAN_TELEPORT = "minecraft:entity.enderman.teleport";

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
        order.verify(host).damageTarget(SNAP_DAMAGE, DamageKind.FREEZE, true);
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
        order.verify(host).damageTarget((float) COW_HEALTH, DamageKind.MAGIC, true);
        order.verify(host).dropItemAtTarget(eq(Identifier.parse(COBBLESTONE)),
                intThat(count -> count >= 1 && count <= CRUSH_DROP_MAX));
    }

    @Test
    void blazeIgniteBurnsTheTargetThenEveryBurnableLivingEntityAroundIt() {
        StepHost host = entityHost();
        StepHost bystander = entityHost();
        doAnswer(invocation -> {
            invocation.<Consumer<StepHost>>getArgument(ENTITIES_BODY_ARGUMENT).accept(bystander);
            return null;
        }).when(host).forEachEntityWithin(eq(SelectionShape.SPHERE), eq(IGNITE_RADIUS), eq(BURNABLE), any());

        run("blaze_ignite", host);

        InOrder order = inOrder(host, bystander);
        order.verify(host).igniteTarget(IGNITE_SECONDS);
        order.verify(bystander).igniteTarget(SPLASH_IGNITE_SECONDS);
        verify(host, never()).igniteTarget(SPLASH_IGNITE_SECONDS);
    }

    @Test
    void glowLaserDamagesShowsCritsAndMarksALivingTarget() {
        StepHost host = entityHost();
        when(host.read(HostVariables.UNDEAD)).thenReturn(OptionalDouble.of(0));
        when(host.targetPasses(UNDEAD)).thenReturn(false);
        when(host.targetPasses(ALIVE)).thenReturn(true);

        run("glow_laser", host);

        InOrder order = inOrder(host);
        order.verify(host).damageTarget(LASER_DAMAGE, DamageKind.MAGIC, true);
        order.verify(host).spawnParticles(FxAnchor.TARGET, new ParticleBurst(Identifier.parse(CRIT),
                CRIT_COUNT, CRIT_SPREAD, CRIT_SPREAD, CRIT_SPEED, 0));
        order.verify(host).applyPotion(Identifier.parse(GLOWING), LASER_GLOW_DURATION, 0, true);
        verify(host, never()).igniteTarget(anyInt());
    }

    @Test
    void glowLaserDoublesDamageAndBurnsAnUndeadTarget() {
        StepHost host = entityHost();
        when(host.read(HostVariables.UNDEAD)).thenReturn(OptionalDouble.of(1));
        when(host.targetPasses(UNDEAD)).thenReturn(true);
        when(host.targetPasses(ALIVE)).thenReturn(false);

        run("glow_laser", host);

        InOrder order = inOrder(host);
        order.verify(host).damageTarget(LASER_DAMAGE * 2, DamageKind.MAGIC, true);
        order.verify(host).igniteTarget(LASER_IGNITE_SECONDS);
        verify(host, never()).applyPotion(any(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void crystalFlechettesHurtTheTargetThenSplashEveryOtherLivingEntityThenShowIndicators() {
        StepHost host = entityHost();
        StepHost bystander = entityHost();
        doAnswer(invocation -> {
            invocation.<Consumer<StepHost>>getArgument(ENTITIES_BODY_ARGUMENT).accept(bystander);
            return null;
        }).when(host).forEachEntityWithin(eq(SelectionShape.SPHERE), eq(FLECHETTE_RADIUS), eq(OTHER_LIVING), any());

        run("crystal_flechettes", host);

        InOrder order = inOrder(host, bystander);
        order.verify(host).damageTarget(FLECHETTE_DAMAGE, DamageKind.MAGIC, true);
        order.verify(bystander).damageTarget(SPLASH_DAMAGE, DamageKind.MAGIC, true);
        order.verify(host).spawnParticles(FxAnchor.TARGET, new ParticleBurst(Identifier.parse(DAMAGE_INDICATOR),
                INDICATOR_COUNT, INDICATOR_SPREAD_ALONG, INDICATOR_SPREAD_ACROSS, 0, 0));
        verify(host, never()).damageTarget(SPLASH_DAMAGE, DamageKind.MAGIC, true);
    }

    @Test
    void enderTeleportJumpsTheTargetThenPlaysTheEndermanSoundThere() {
        StepHost host = entityHost();

        run("ender_teleport", host);

        InOrder order = inOrder(host);
        order.verify(host).teleportTarget(TeleportMode.RANDOM_OFFSET, TELEPORT_RANGE);
        order.verify(host).playSound(FxAnchor.TARGET,
                new SoundCue(Identifier.parse(ENDERMAN_TELEPORT), SoundKind.HOSTILE, 1, 1));
        verifyNoMoreInteractions(host);
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
