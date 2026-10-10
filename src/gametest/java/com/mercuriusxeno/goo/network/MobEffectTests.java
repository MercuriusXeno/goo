package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.EntityFilter;
import com.mercuriusxeno.goo.ability.program.EntityScan;
import com.mercuriusxeno.goo.ability.stasis.StasisEvents;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Set;

/**
 * Gametests for the mob abilities: each test spawns a mob, lands a goo of
 * the ability on it through {@link GooEffectScheduler#applyEffect} and
 * asserts the live entity's reaction (damage, status effect, fire, AI
 * state, a move). What a program asks of its host without an entity is
 * graded in MobProgramTest. They sit in the scheduler's package because
 * the scheduler is package-private.
 */
public final class MobEffectTests {

    /**
     * Clear of the barrier shell the framework wraps the one-block empty
     * structure in: a mob spawned at (1, 1, 1) stands inside that shell's
     * corner, where an explosion's rays die on the barrier.
     */
    private static final BlockPos SPAWN_POS = new BlockPos(3, 1, 3);
    /** One block beside the spawn, where the glow laser test stands its zombie. */
    private static final BlockPos BYSTANDER_POS = SPAWN_POS.east();
    /** The tick after spawning, once the level's entity index holds the spawned mobs. */
    private static final int SETTLE_TICKS = 1;
    private static final String SHOULD_HAVE_SLOWNESS = "Target should have slowness";
    private static final String SHOULD_HAVE_POISON = "Target should have poison";
    private static final String SHOULD_HAVE_WEAKNESS = "Target should have weakness";
    private static final String SHOULD_HAVE_GLOWING = "Target should have glowing";
    private static final String SHOULD_NOT_GLOW = "Target should wear the ailment overlay, not vanilla glowing";
    private static final String SHOULD_HAVE_WITHER = "Target should have wither";
    private static final String SHOULD_TAKE_DAMAGE = "Target should have taken damage";
    private static final String SHOULD_BE_ON_FIRE = "Target should be on fire";
    private static final String SHOULD_HAVE_NO_AI = "Target should have AI disabled";
    private static final String SHOULD_HAVE_LEVITATION = "Target should have levitation";
    private static final String SHOULD_TAKE_JAVELIN_DAMAGE = "Target should have taken the javelin's damage";
    private static final String ABILITIES_REQUIRED = "Ability registry must be loaded";
    private static final String ABILITY_METAL_JAVELIN = "goo:metal_javelin";
    private static final String ABILITY_LEAF_ENTANGLE = "goo:leaf_entangle";
    private static final String ABILITY_TYPHOON_LEVITATE = "goo:typhoon_levitate";
    private static final String ABILITY_NETHER_WITHER = "goo:nether_wither";
    private static final String ABILITY_FROST_SNAP = "goo:frost_snap";
    private static final String ABILITY_PULSE_SHORT_CIRCUIT = "goo:pulse_short_circuit";
    private static final String ABILITY_AEON_STASIS = "goo:aeon_stasis";
    /** The ticks a stasis is watched before the hits land. */
    private static final int STASIS_HOLD_TICKS = 100;
    private static final float STASIS_HIT_DAMAGE = 4.0f;
    private static final String SHOULD_STAY_IN_STASIS = "The zombie should stay in stasis until an attacker strikes it";
    private static final String SHOULD_STAND_STILL = "The zombie in stasis should not have moved";
    private static final String SHOULD_TAKE_NO_DAMAGE = "The zombie in stasis should take no damage";
    private static final String SHOULD_SURVIVE_LANDING_PUNCH = "The punch landing the stasis should not free the mob";
    private static final String SHOULD_DEAL_NOTHING_FROZEN = "A mob in stasis should deal no damage";
    private static final String SHOULD_BE_FREED = "An attacker's strike should free the zombie";
    private static final String SHOULD_HAVE_AI_AGAIN = "The freed zombie should have its AI back";
    private static final String ABILITY_UNSTABLE_EXPLODE = "goo:unstable_explode";
    private static final String ABILITY_HEX_CHARM = "goo:hex_charm";
    private static final String ABILITY_VITAL_CLONE = "goo:vital_clone";
    private static final String ABILITY_BLAZE_IGNITE = "goo:blaze_ignite";
    private static final String ABILITY_GLOW_LASER = "goo:glow_laser";
    private static final String ABILITY_CRYSTAL_FLECHETTES = "goo:crystal_flechettes";
    private static final String ABILITY_ENDER_TELEPORT = "goo:ender_teleport";
    private static final String SHOULD_HAVE_MOVED = "Target should stand somewhere else";
    private static final String SHOULD_STAY_IN_RANGE = "Target should land within sixteen blocks on each axis";
    private static final String SHOULD_KEEP_HEIGHT = "Target should keep its height";
    /** A jump the random offset misses with vanishing odds. */
    private static final double TELEPORT_MIN_MOVE = 0.01;
    /** Half of ender_teleport.json's range of 32. */
    private static final double TELEPORT_MAX_AXIS_MOVE = 16.0;
    private static final String CHICKEN_HAS_MAX_HEALTH = "A chicken carries a max health attribute";
    private static final String SHOULD_HAVE_A_CLONE = "A second chicken should stand beside the target";
    /** A max health of one makes vital_clone.json's chance 100 / pow(1, 0.6), every roll. */
    private static final double CERTAIN_CLONE_MAX_HEALTH = 1.0;
    /** Wide enough that a gaussian step from the target cannot leave it. */
    private static final double CLONE_SEARCH_RADIUS = 8.0;
    private static final int CHICKENS_AFTER_CLONE = 2;
    /** Beside the cow, inside unstable_explode.json's blast of power 3. */
    private static final BlockPos BLAST_DIRT_POS = SPAWN_POS.south();
    /** The damage crystal_flechettes.json's first damage step names. */
    private static final float FLECHETTE_DAMAGE = 4.0f;
    private static final String LIVING_SHOULD_NOT_BURN = "A cow is not undead and should not burn";
    private static final String UNDEAD_SHOULD_BURN = "A zombie is undead and should burn";
    private static final String SHOULD_BE_FROZEN = "Target should hold frost_snap.json's full freeze";
    /** The frozen ticks frost_snap.json's freeze_ticks step adds. */
    private static final int FULL_FREEZE_TICKS = 140;
    /** The damage metal_javelin.json's damage step names. */
    private static final float JAVELIN_DAMAGE = 8.0f;

    private static final String SHOULD_HAVE_BABY_FORM = "%s should have a baby form";
    private static final String SHOULD_LACK_BABY_FORM = "%s should have no baby form";

    private MobEffectTests() {
    }

    /**
     * Lands one goo of the named ability on the mob through the
     * scheduler's impact, the path an arrived throw takes, so the ability
     * resolves from the registry and its program runs on the struck
     * entity host (decision world-tests-assert-one-observation).
     *
     * @param helper    the gametest helper
     * @param mob       the struck mob
     * @param abilityId the ability the throw names
     */
    private static void strike(GameTestHelper helper, Mob mob, String abilityId) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(abilityId));
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, ability.gooType(),
                mob.getId(), mob.blockPosition(), Direction.UP, abilityId));
    }

    /**
     * Metal javelin is a program: its damage step, loaded for the struck
     * entity host, deals the javelin's magic damage to the target.
     *
     * @param helper the gametest helper
     */
    public static void metalJavelin(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        float before = mob.getHealth();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, mob, ABILITY_METAL_JAVELIN);
            helper.assertTrue(mob.getHealth() <= before - JAVELIN_DAMAGE, SHOULD_TAKE_JAVELIN_DAMAGE);
            helper.succeed();
        });
    }

    /**
     * Crystal flechettes deals four magic damage to the struck mob; the
     * splash selection it asks of the host is graded in MobProgramTest.
     *
     * @param helper the gametest helper
     */
    public static void crystalFlechettes(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        float before = mob.getHealth();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, mob, ABILITY_CRYSTAL_FLECHETTES);
            helper.assertTrue(mob.getHealth() <= before - FLECHETTE_DAMAGE, SHOULD_TAKE_DAMAGE);
            helper.succeed();
        });
    }

    /**
     * Leaf entangle is a program of two potion steps: slowness and poison.
     *
     * @param helper the gametest helper
     */
    public static void leafEntangle(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, mob, ABILITY_LEAF_ENTANGLE);
            helper.assertTrue(mob.hasEffect(MobEffects.SLOWNESS), SHOULD_HAVE_SLOWNESS);
            helper.assertTrue(mob.hasEffect(MobEffects.POISON), SHOULD_HAVE_POISON);
            helper.succeed();
        });
    }

    /**
     * Vital clone is a program: a mob target selection wrapping a
     * clone_entity step whose chance is 100 / pow(max_health, 0.6), so a
     * chicken whose max health is one is cloned on every roll and a second
     * chicken stands beside it.
     *
     * @param helper the gametest helper
     */
    public static void vitalClone(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.CHICKEN, SPAWN_POS);
        AttributeInstance maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
        helper.assertTrue(maxHealth != null, CHICKEN_HAS_MAX_HEALTH);
        maxHealth.setBaseValue(CERTAIN_CLONE_MAX_HEALTH);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, mob, ABILITY_VITAL_CLONE);
            helper.runAfterDelay(SETTLE_TICKS, () -> {
                long chickens = helper.getLevel()
                        .getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(CLONE_SEARCH_RADIUS))
                        .stream().filter(found -> found.getType() == EntityType.CHICKEN).count();
                helper.assertTrue(chickens == CHICKENS_AFTER_CLONE, SHOULD_HAVE_A_CLONE);
                helper.succeed();
            });
        });
    }

    /**
     * Blaze ignite sets the struck mob on fire; the splash selection it
     * asks of the host is graded in MobProgramTest.
     *
     * @param helper the gametest helper
     */
    public static void blazeIgnite(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, mob, ABILITY_BLAZE_IGNITE);
            helper.assertTrue(mob.isOnFire(), SHOULD_BE_ON_FIRE);
            helper.succeed();
        });
    }

    /**
     * Frost snap is a program: a not_boss target selection wrapping freeze
     * damage, a freeze_ticks step and a slowness potion step.
     *
     * @param helper the gametest helper
     */
    public static void frostSnap(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        float before = mob.getHealth();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, mob, ABILITY_FROST_SNAP);
            helper.assertTrue(mob.getHealth() < before, SHOULD_TAKE_DAMAGE);
            helper.assertTrue(mob.getTicksFrozen() >= FULL_FREEZE_TICKS, SHOULD_BE_FROZEN);
            helper.assertTrue(mob.hasEffect(MobEffects.SLOWNESS), SHOULD_HAVE_SLOWNESS);
            helper.succeed();
        });
    }

    /**
     * Typhoon levitate is a program of one potion step: levitation.
     *
     * @param helper the gametest helper
     */
    public static void typhoonLevitate(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, mob, ABILITY_TYPHOON_LEVITATE);
            helper.assertTrue(mob.hasEffect(MobEffects.LEVITATION), SHOULD_HAVE_LEVITATION);
            helper.succeed();
        });
    }

    /**
     * Glow laser is a program: magic damage doubled by the undead variable,
     * crit particles, an ignite step under an undead target selection and
     * glowing under an alive one, so a zombie burns and a cow does not.
     *
     * @param helper the gametest helper
     */
    public static void glowLaser(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BYSTANDER_POS);
        float before = mob.getHealth();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, mob, ABILITY_GLOW_LASER);
            strike(helper, zombie, ABILITY_GLOW_LASER);
            helper.assertTrue(mob.getHealth() < before, SHOULD_TAKE_DAMAGE);
            helper.assertTrue(mob.hasEffect(MobEffects.GLOWING), SHOULD_HAVE_GLOWING);
            helper.assertFalse(mob.isOnFire(), LIVING_SHOULD_NOT_BURN);
            helper.assertTrue(zombie.isOnFire(), UNDEAD_SHOULD_BURN);
            helper.succeed();
        });
    }

    /**
     * Hex charm is a program: a mob target selection wrapping a weakness
     * potion step and the hex ailment overlay, whose durations fall with the
     * mob's health; the overlay replaces vanilla glowing
     * (decision ailment-overlay-shader-per-ailment).
     *
     * @param helper the gametest helper
     */
    public static void hexCharm(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, mob, ABILITY_HEX_CHARM);
            helper.assertTrue(mob.hasEffect(MobEffects.WEAKNESS), SHOULD_HAVE_WEAKNESS);
            helper.assertFalse(mob.hasEffect(MobEffects.GLOWING), SHOULD_NOT_GLOW);
            helper.succeed();
        });
    }

    /**
     * Pulse short circuit is a program: a mob target selection wrapping a
     * set_ai step off and a max slowness potion step.
     *
     * @param helper the gametest helper
     */
    public static void pulseStun(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, mob, ABILITY_PULSE_SHORT_CIRCUIT);
            helper.assertTrue(mob.isNoAi(), SHOULD_HAVE_NO_AI);
            helper.assertTrue(mob.hasEffect(MobEffects.SLOWNESS), SHOULD_HAVE_SLOWNESS);
            helper.succeed();
        });
    }

    /**
     * Nether wither is a program: a not_boss target selection wrapping a
     * set_health step at half and a wither potion step.
     *
     * @param helper the gametest helper
     */
    public static void netherWither(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        float before = mob.getHealth();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, mob, ABILITY_NETHER_WITHER);
            helper.assertTrue(mob.getHealth() < before, SHOULD_TAKE_DAMAGE);
            helper.assertTrue(mob.hasEffect(MobEffects.WITHER), SHOULD_HAVE_WITHER);
            helper.succeed();
        });
    }

    /**
     * Ender teleport is a program: a random_offset teleport of range 32
     * and the enderman teleport sound, so the cow stands somewhere else
     * within sixteen blocks on each horizontal axis at the same height.
     *
     * @param helper the gametest helper
     */
    public static void enderTeleport(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        Vec3 before = mob.position();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, mob, ABILITY_ENDER_TELEPORT);
            Vec3 after = mob.position();
            helper.assertTrue(after.distanceTo(before) > TELEPORT_MIN_MOVE, SHOULD_HAVE_MOVED);
            helper.assertTrue(Math.abs(after.x - before.x) <= TELEPORT_MAX_AXIS_MOVE, SHOULD_STAY_IN_RANGE);
            helper.assertTrue(Math.abs(after.z - before.z) <= TELEPORT_MAX_AXIS_MOVE, SHOULD_STAY_IN_RANGE);
            helper.assertTrue(after.y == before.y, SHOULD_KEEP_HEIGHT);
            helper.succeed();
        });
    }

    /**
     * Unstable explode is a program of one explode step at the target,
     * whose tnt blast hurts the struck mob and breaks the dirt beside it.
     *
     * @param helper the gametest helper
     */
    public static void unstableExplode(GameTestHelper helper) {
        helper.setBlock(BLAST_DIRT_POS, Blocks.DIRT);
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        float before = mob.getHealth();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, mob, ABILITY_UNSTABLE_EXPLODE);
            helper.assertTrue(mob.getHealth() < before, SHOULD_TAKE_DAMAGE);
            helper.assertBlockNotPresent(Blocks.DIRT, BLAST_DIRT_POS);
            helper.succeed();
        });
    }

    /**
     * Aeon stasis freezes a zombie with its AI on: across a hundred ticks and
     * a hit with no attacker it stays in stasis, AI-less, unmoved and
     * unharmed; a strike from another zombie frees it, its AI back and its
     * health untouched.
     * stasis-holds-mob-with-golden-shimmer
     *
     * @param helper the gametest helper
     */
    public static void stasisHoldsUntilStruck(GameTestHelper helper) {
        Mob mob = helper.spawn(EntityType.ZOMBIE, SPAWN_POS);
        Mob attacker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BYSTANDER_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, mob, ABILITY_AEON_STASIS);
            Vec3 frozenAt = mob.position();
            float health = mob.getHealth();
            // stasis-holds-mob-with-golden-shimmer: the punch that lands the blob arrives the same tick and frees nothing
            mob.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), STASIS_HIT_DAMAGE);
            helper.assertTrue(StasisEvents.held(mob), SHOULD_SURVIVE_LANDING_PUNCH);
            float attackerHealth = attacker.getHealth();
            attacker.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(mob), STASIS_HIT_DAMAGE);
            helper.assertTrue(attacker.getHealth() == attackerHealth, SHOULD_DEAL_NOTHING_FROZEN);
            helper.runAfterDelay(STASIS_HOLD_TICKS, () -> {
                mob.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), STASIS_HIT_DAMAGE);
                helper.assertTrue(StasisEvents.held(mob), SHOULD_STAY_IN_STASIS);
                helper.assertTrue(mob.isNoAi(), SHOULD_HAVE_NO_AI);
                helper.assertTrue(mob.position().distanceTo(frozenAt) < TELEPORT_MIN_MOVE, SHOULD_STAND_STILL);
                helper.assertTrue(mob.getHealth() == health, SHOULD_TAKE_NO_DAMAGE);
                mob.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker),
                        STASIS_HIT_DAMAGE);
                helper.assertFalse(StasisEvents.held(mob), SHOULD_BE_FREED);
                helper.assertFalse(mob.isNoAi(), SHOULD_HAVE_AI_AGAIN);
                helper.assertTrue(mob.getHealth() == health, SHOULD_TAKE_NO_DAMAGE);
                helper.succeed();
            });
        });
    }

    /**
     * The has_baby_form filter keeps exactly the mobs the 26.1 tree lets
     * be a baby.
     *
     * @param helper the gametest helper
     */
    public static void aeonBabyFormFilter(GameTestHelper helper) {
        Set<EntityFilter> hasBabyForm = Set.of(EntityFilter.HAS_BABY_FORM);
        for (EntityType<? extends Mob> type : List.of(EntityType.COW, EntityType.ZOMBIE, EntityType.PIGLIN,
                EntityType.ZOGLIN)) {
            Mob mob = helper.spawnWithNoFreeWill(type, SPAWN_POS);
            helper.assertTrue(EntityScan.passes(mob, hasBabyForm, mob), String.format(SHOULD_HAVE_BABY_FORM, type));
        }
        for (EntityType<? extends Mob> type : List.of(EntityType.FROG, EntityType.PARROT, EntityType.CAMEL_HUSK,
                EntityType.ZOMBIE_NAUTILUS, EntityType.CREEPER, EntityType.SKELETON)) {
            Mob mob = helper.spawnWithNoFreeWill(type, SPAWN_POS);
            helper.assertFalse(EntityScan.passes(mob, hasBabyForm, mob), String.format(SHOULD_LACK_BABY_FORM, type));
        }
        helper.succeed();
    }
}
