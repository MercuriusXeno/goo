package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.mob.*;
import com.mercuriusxeno.goo.ability.program.EntityHost;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Gametests for the 16 per-goo-type mob effects. Each test spawns a mob,
 * applies the effect, and asserts the expected outcome (damage, status
 * effect, fire, AI state, etc.).
 */
public final class MobEffectTests {

    private static final BlockPos SPAWN_POS = new BlockPos(1, 1, 1);
    private static final String SHOULD_HAVE_SLOWNESS = "Target should have slowness";
    private static final String SHOULD_HAVE_POISON = "Target should have poison";
    private static final String SHOULD_HAVE_WEAKNESS = "Target should have weakness";
    private static final String SHOULD_HAVE_GLOWING = "Target should have glowing";
    private static final String SHOULD_HAVE_WITHER = "Target should have wither";
    private static final String SHOULD_TAKE_DAMAGE = "Target should have taken damage";
    private static final String SHOULD_BE_ON_FIRE = "Target should be on fire";
    private static final String SHOULD_HAVE_NO_AI = "Target should have AI disabled";
    private static final String SHOULD_BE_INVULNERABLE = "Target should be invulnerable";
    private static final String SHOULD_HAVE_LEVITATION = "Target should have levitation";
    private static final String SHOULD_TAKE_JAVELIN_DAMAGE = "Target should have taken the javelin's damage";
    private static final String ABILITIES_REQUIRED = "Ability registry must be loaded";
    private static final String ABILITY_METAL_JAVELIN = "goo:metal_javelin";
    private static final String ABILITY_LEAF_ENTANGLE = "goo:leaf_entangle";
    private static final String ABILITY_TYPHOON_LEVITATE = "goo:typhoon_levitate";
    private static final String ABILITY_SHROOM_DEBUFF = "goo:shroom_debuff";
    private static final String ABILITY_NETHER_WITHER = "goo:nether_wither";
    private static final String ABILITY_FROST_SNAP = "goo:frost_snap";
    private static final String ABILITY_PULSE_SHORT_CIRCUIT = "goo:pulse_short_circuit";
    /** The damage metal_javelin.json's damage step names. */
    private static final float JAVELIN_DAMAGE = 8.0f;

    private MobEffectTests() {
    }

    /**
     * Runs every program entry of the named ability on the struck entity
     * host over the mob, the way BlobEffectScheduler does at impact
     * (decision host-agnostic-runtime).
     *
     * @param helper    the gametest helper
     * @param mob       the struck mob
     * @param abilityId the ability whose programs run
     */
    private static void runEntityPrograms(GameTestHelper helper, Mob mob, String abilityId) {
        AbilityDefinition ability = AbilityRegistry.getAbility(Identifier.parse(abilityId));
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        for (AbilityDefinition.BehaviorEntry entry : ability.behaviors()) {
            ProgramBehavior program = ProgramBehavior.forHost(entry.steps(), HostKind.ENTITY);
            program.tick(new EntityHost(helper.getLevel(), mob, null));
        }
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
        runEntityPrograms(helper, mob, ABILITY_METAL_JAVELIN);
        helper.assertTrue(mob.getHealth() <= before - JAVELIN_DAMAGE, SHOULD_TAKE_JAVELIN_DAMAGE);
        helper.succeed();
    }

    /**
     * Crystal flechettes deal damage to the primary target.
     *
     * @param helper the gametest helper
     */
    public static void crystalFlechettes(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        float before = mob.getHealth();
        CrystalFlechettes.apply(helper.getLevel(), mob);
        helper.assertTrue(mob.getHealth() < before, SHOULD_TAKE_DAMAGE);
        helper.succeed();
    }

    /**
     * Leaf entangle is a program of two potion steps: slowness and poison.
     *
     * @param helper the gametest helper
     */
    public static void leafEntangle(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        runEntityPrograms(helper, mob, ABILITY_LEAF_ENTANGLE);
        helper.assertTrue(mob.hasEffect(MobEffects.SLOWNESS), SHOULD_HAVE_SLOWNESS);
        helper.assertTrue(mob.hasEffect(MobEffects.POISON), SHOULD_HAVE_POISON);
        helper.succeed();
    }

    /**
     * Vital clone attempts to clone the mob (probabilistic - just verify no crash).
     *
     * @param helper the gametest helper
     */
    public static void vitalClone(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.CHICKEN, SPAWN_POS);
        VitalClone.apply(helper.getLevel(), mob);
        helper.succeed();
    }

    /**
     * Shroom debuff is a program: a not_boss target selection wrapping
     * slowness, weakness and poison potion steps.
     *
     * @param helper the gametest helper
     */
    public static void shroomDebuff(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        runEntityPrograms(helper, mob, ABILITY_SHROOM_DEBUFF);
        helper.assertTrue(mob.hasEffect(MobEffects.SLOWNESS), SHOULD_HAVE_SLOWNESS);
        helper.assertTrue(mob.hasEffect(MobEffects.WEAKNESS), SHOULD_HAVE_WEAKNESS);
        helper.assertTrue(mob.hasEffect(MobEffects.POISON), SHOULD_HAVE_POISON);
        helper.succeed();
    }

    /**
     * Rock petrify applies max slowness.
     *
     * @param helper the gametest helper
     */
    public static void rockPetrify(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        RockPetrify.apply(helper.getLevel(), mob);
        helper.assertTrue(mob.hasEffect(MobEffects.SLOWNESS), SHOULD_HAVE_SLOWNESS);
        helper.succeed();
    }

    /**
     * Blaze ignite sets the target on fire.
     *
     * @param helper the gametest helper
     */
    public static void blazeIgnite(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        BlazeIgnite.apply(helper.getLevel(), mob);
        helper.assertTrue(mob.isOnFire(), SHOULD_BE_ON_FIRE);
        helper.succeed();
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
        runEntityPrograms(helper, mob, ABILITY_FROST_SNAP);
        helper.assertTrue(mob.getHealth() < before, SHOULD_TAKE_DAMAGE);
        helper.assertTrue(mob.hasEffect(MobEffects.SLOWNESS), SHOULD_HAVE_SLOWNESS);
        helper.succeed();
    }

    /**
     * Typhoon levitate is a program of one potion step: levitation.
     *
     * @param helper the gametest helper
     */
    public static void typhoonLevitate(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        runEntityPrograms(helper, mob, ABILITY_TYPHOON_LEVITATE);
        helper.assertTrue(mob.hasEffect(MobEffects.LEVITATION), SHOULD_HAVE_LEVITATION);
        helper.succeed();
    }

    /**
     * Glow laser deals damage and applies glowing.
     *
     * @param helper the gametest helper
     */
    public static void glowLaser(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        float before = mob.getHealth();
        GlowLaser.apply(helper.getLevel(), mob);
        helper.assertTrue(mob.getHealth() < before, SHOULD_TAKE_DAMAGE);
        helper.assertTrue(mob.hasEffect(MobEffects.GLOWING), SHOULD_HAVE_GLOWING);
        helper.succeed();
    }

    /**
     * Hex charm applies weakness and glowing to mobs.
     *
     * @param helper the gametest helper
     */
    public static void hexCharm(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        HexCharm.apply(mob, null);
        helper.assertTrue(mob.hasEffect(MobEffects.WEAKNESS), SHOULD_HAVE_WEAKNESS);
        helper.assertTrue(mob.hasEffect(MobEffects.GLOWING), SHOULD_HAVE_GLOWING);
        helper.succeed();
    }

    /**
     * Pulse short circuit is a program: a mob target selection wrapping a
     * set_ai step off and a max slowness potion step.
     *
     * @param helper the gametest helper
     */
    public static void pulseStun(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        runEntityPrograms(helper, mob, ABILITY_PULSE_SHORT_CIRCUIT);
        helper.assertTrue(mob.isNoAi(), SHOULD_HAVE_NO_AI);
        helper.assertTrue(mob.hasEffect(MobEffects.SLOWNESS), SHOULD_HAVE_SLOWNESS);
        helper.succeed();
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
        runEntityPrograms(helper, mob, ABILITY_NETHER_WITHER);
        helper.assertTrue(mob.getHealth() < before, SHOULD_TAKE_DAMAGE);
        helper.assertTrue(mob.hasEffect(MobEffects.WITHER), SHOULD_HAVE_WITHER);
        helper.succeed();
    }

    /**
     * Ender teleport moves the target (just verify no crash).
     *
     * @param helper the gametest helper
     */
    public static void enderTeleport(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        EnderTeleport.apply(helper.getLevel(), mob);
        helper.succeed();
    }

    /**
     * Unstable explode detonates at the target (just verify no crash).
     *
     * @param helper the gametest helper
     */
    public static void unstableExplode(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        UnstableExplode.apply(helper.getLevel(), mob);
        helper.succeed();
    }

    /**
     * Aeon time stop disables AI and makes invulnerable.
     *
     * @param helper the gametest helper
     */
    public static void aeonTimeStop(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        AeonTimeStop.apply(mob);
        helper.assertTrue(mob.isNoAi(), SHOULD_HAVE_NO_AI);
        helper.assertTrue(mob.isInvulnerable(), SHOULD_BE_INVULNERABLE);
        helper.assertTrue(mob.hasEffect(MobEffects.GLOWING), SHOULD_HAVE_GLOWING);
        helper.succeed();
    }

    /**
     * MobAbilities.apply() routes a goo type to the handler still standing
     * for it; blaze is the type checked while its handler awaits migration.
     *
     * @param helper the gametest helper
     */
    public static void dispatcherRoutes(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        MobAbilities.apply(helper.getLevel(), mob, GooType.BLAZE, null);
        helper.assertTrue(mob.isOnFire(), SHOULD_BE_ON_FIRE);
        helper.succeed();
    }
}
