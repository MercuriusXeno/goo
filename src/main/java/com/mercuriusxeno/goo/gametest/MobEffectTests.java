package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.EntityFilter;
import com.mercuriusxeno.goo.ability.program.EntityHost;
import com.mercuriusxeno.goo.ability.program.EntityScan;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Set;

/**
 * Gametests for the mob abilities, each a program on the struck entity
 * host: each test spawns a mob, runs the ability's programs on it and
 * asserts the outcome (damage, status effect, fire, AI state, a move).
 */
public final class MobEffectTests {

    /**
     * Clear of the barrier shell the framework wraps the one-block empty
     * structure in: a mob spawned at (1, 1, 1) stands inside that shell's
     * corner, where an explosion's rays die on the barrier.
     */
    private static final BlockPos SPAWN_POS = new BlockPos(3, 1, 3);
    /** One block beside the spawn, inside blaze ignite's splash radius. */
    private static final BlockPos BYSTANDER_POS = SPAWN_POS.east();
    /** The tick after spawning, once the level's entity index holds the spawned mobs. */
    private static final int SETTLE_TICKS = 1;
    private static final String BYSTANDER_SHOULD_BE_ON_FIRE = "Bystander should be on fire";
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
    private static final String ABILITY_AEON_TIME_STOP = "goo:aeon_time_stop";
    private static final String ABILITY_UNSTABLE_EXPLODE = "goo:unstable_explode";
    private static final String ABILITY_HEX_CHARM = "goo:hex_charm";
    private static final String ABILITY_VITAL_CLONE = "goo:vital_clone";
    private static final String ABILITY_ROCK_PETRIFY = "goo:rock_petrify";
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
    /** Beside the cow, inside unstable_explode.json's blast of power 2. */
    private static final BlockPos BLAST_DIRT_POS = SPAWN_POS.south();
    private static final String BYSTANDER_SHOULD_TAKE_SPLASH = "Bystander should have taken the splash damage";
    /** The damage crystal_flechettes.json's first damage step names. */
    private static final float FLECHETTE_DAMAGE = 4.0f;
    /** The damage crystal_flechettes.json's entities selection names. */
    private static final float SPLASH_DAMAGE = 2.0f;
    private static final String LIVING_SHOULD_NOT_BURN = "A cow is not undead and should not burn";
    private static final String UNDEAD_SHOULD_BURN = "A zombie is undead and should burn";
    private static final String SHOULD_BE_CRUSHED = "Target should be dead or dying";
    /** The damage metal_javelin.json's damage step names. */
    private static final float JAVELIN_DAMAGE = 8.0f;

    /** The counter aeon_time_stop.json's ritual adds to. */
    private static final Identifier RITUAL_COUNTER = Identifier.parse("goo:ritual");
    /** The percent numerator and health exponent of aeon_time_stop.json's ritual share. */
    private static final double RITUAL_PERCENT = 100;
    private static final double RITUAL_HEALTH_EXPONENT = 0.6;
    private static final int RITUAL_THROWS = 2;
    private static final double RITUAL_TOLERANCE = 0.01;
    private static final String SHOULD_COUNT_RITUAL = "Ritual counter should read %.2f after two throws, read %.2f";

    /** The reach within which the ritual's egg drop, or its absence, is read. */
    private static final double ITEM_SEARCH_RADIUS = 2.0;
    private static final String SHOULD_DROP_NOTHING = "No item should drop beside a mob short of the ritual";
    private static final String SHOULD_DROP_ONLY_EGG = "Exactly one spawn egg of the mob, and no loot, should drop; found %s";
    private static final String SHOULD_VANISH = "The mob should be gone once its ritual completes";
    private static final String MOB_HAS_MAX_HEALTH = "The mob carries a max health attribute";
    /** A max health of one makes aeon_time_stop.json's ritual share 100 / pow(1, 0.6), the full hundred. */
    private static final double ONE_HIT_RITUAL_MAX_HEALTH = 1.0;
    private static final String SHOULD_BE_BABY = "An adult with a baby form should stand as a baby";
    private static final String SHOULD_RESTART_RITUAL = "The ritual counter should restart at zero, read %.2f";
    private static final String SHOULD_HAVE_BABY_FORM = "%s should have a baby form";
    private static final String SHOULD_LACK_BABY_FORM = "%s should have no baby form";

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
     * Crystal flechettes is a program: four magic damage to the struck mob,
     * an entities selection sparing the target that deals two to every
     * other living entity in three blocks, and damage indicator particles.
     *
     * @param helper the gametest helper
     */
    public static void crystalFlechettes(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        Mob bystander = helper.spawnWithNoFreeWill(EntityType.COW, BYSTANDER_POS);
        float before = mob.getHealth();
        float bystanderBefore = bystander.getHealth();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            runEntityPrograms(helper, mob, ABILITY_CRYSTAL_FLECHETTES);
            helper.assertTrue(mob.getHealth() <= before - FLECHETTE_DAMAGE, SHOULD_TAKE_DAMAGE);
            helper.assertTrue(bystander.getHealth() <= bystanderBefore - SPLASH_DAMAGE, BYSTANDER_SHOULD_TAKE_SPLASH);
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
        runEntityPrograms(helper, mob, ABILITY_LEAF_ENTANGLE);
        helper.assertTrue(mob.hasEffect(MobEffects.SLOWNESS), SHOULD_HAVE_SLOWNESS);
        helper.assertTrue(mob.hasEffect(MobEffects.POISON), SHOULD_HAVE_POISON);
        helper.succeed();
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
        runEntityPrograms(helper, mob, ABILITY_VITAL_CLONE);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            long chickens = helper.getLevel()
                    .getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(CLONE_SEARCH_RADIUS))
                    .stream().filter(found -> found.getType() == EntityType.CHICKEN).count();
            helper.assertTrue(chickens == CHICKENS_AFTER_CLONE, SHOULD_HAVE_A_CLONE);
            helper.succeed();
        });
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
     * Rock petrify is a program: max slowness, magic damage of the mob's
     * max health and a cobblestone drop, so the cow is crushed.
     *
     * @param helper the gametest helper
     */
    public static void rockPetrify(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        runEntityPrograms(helper, mob, ABILITY_ROCK_PETRIFY);
        helper.assertTrue(mob.hasEffect(MobEffects.SLOWNESS), SHOULD_HAVE_SLOWNESS);
        helper.assertTrue(mob.isDeadOrDying(), SHOULD_BE_CRUSHED);
        helper.succeed();
    }

    /**
     * Blaze ignite is a program: an ignite step on the struck mob and an
     * entities selection igniting every burnable living entity around it,
     * so a cow beside the target burns too.
     *
     * @param helper the gametest helper
     */
    public static void blazeIgnite(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        Mob bystander = helper.spawnWithNoFreeWill(EntityType.COW, BYSTANDER_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            runEntityPrograms(helper, mob, ABILITY_BLAZE_IGNITE);
            helper.assertTrue(mob.isOnFire(), SHOULD_BE_ON_FIRE);
            helper.assertTrue(bystander.isOnFire(), BYSTANDER_SHOULD_BE_ON_FIRE);
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
        runEntityPrograms(helper, mob, ABILITY_GLOW_LASER);
        runEntityPrograms(helper, zombie, ABILITY_GLOW_LASER);
        helper.assertTrue(mob.getHealth() < before, SHOULD_TAKE_DAMAGE);
        helper.assertTrue(mob.hasEffect(MobEffects.GLOWING), SHOULD_HAVE_GLOWING);
        helper.assertFalse(mob.isOnFire(), LIVING_SHOULD_NOT_BURN);
        helper.assertTrue(zombie.isOnFire(), UNDEAD_SHOULD_BURN);
        helper.succeed();
    }

    /**
     * Hex charm is a program: a mob target selection wrapping weakness and
     * glowing potion steps whose duration falls with the mob's health.
     *
     * @param helper the gametest helper
     */
    public static void hexCharm(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        runEntityPrograms(helper, mob, ABILITY_HEX_CHARM);
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
     * Ender teleport is a program: a random_offset teleport of range 32
     * and the enderman teleport sound, so the cow stands somewhere else
     * within sixteen blocks on each horizontal axis at the same height.
     *
     * @param helper the gametest helper
     */
    public static void enderTeleport(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        Vec3 before = mob.position();
        runEntityPrograms(helper, mob, ABILITY_ENDER_TELEPORT);
        Vec3 after = mob.position();
        helper.assertTrue(after.distanceTo(before) > TELEPORT_MIN_MOVE, SHOULD_HAVE_MOVED);
        helper.assertTrue(Math.abs(after.x - before.x) <= TELEPORT_MAX_AXIS_MOVE, SHOULD_STAY_IN_RANGE);
        helper.assertTrue(Math.abs(after.z - before.z) <= TELEPORT_MAX_AXIS_MOVE, SHOULD_STAY_IN_RANGE);
        helper.assertTrue(after.y == before.y, SHOULD_KEEP_HEIGHT);
        helper.succeed();
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
            runEntityPrograms(helper, mob, ABILITY_UNSTABLE_EXPLODE);
            helper.assertTrue(mob.getHealth() < before, SHOULD_TAKE_DAMAGE);
            helper.assertBlockNotPresent(Blocks.DIRT, BLAST_DIRT_POS);
            helper.succeed();
        });
    }

    /**
     * Aeon time stop is a program: a mob target selection wrapping set_ai
     * off, set_invulnerable on and a glowing potion step.
     *
     * @param helper the gametest helper
     */
    public static void aeonTimeStop(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        runEntityPrograms(helper, mob, ABILITY_AEON_TIME_STOP);
        helper.assertTrue(mob.isNoAi(), SHOULD_HAVE_NO_AI);
        helper.assertTrue(mob.isInvulnerable(), SHOULD_BE_INVULNERABLE);
        helper.assertTrue(mob.hasEffect(MobEffects.GLOWING), SHOULD_HAVE_GLOWING);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(itemsNear(helper, mob.position()).isEmpty(), SHOULD_DROP_NOTHING);
            helper.succeed();
        });
    }

    /**
     * Aeon's ritual completes on a baby chicken: one whose max health is one
     * takes 100 / pow(1, 0.6), the full hundred, on its first throw, so it
     * drops its own spawn egg and vanishes without dying or dropping loot
     * (decision aeon-mob-ritual-drops-spawn-egg).
     *
     * @param helper the gametest helper
     */
    public static void aeonRitualEgg(GameTestHelper helper) {
        Mob mob = spawnOneHitRitual(helper, EntityType.CHICKEN);
        mob.setBaby(true);
        assertRitualLeavesEgg(helper, mob, Items.CHICKEN_SPAWN_EGG);
    }

    /**
     * An adult cow with max health one completes the ritual on its first
     * throw and, having a baby form, becomes a baby that keeps standing
     * with its ritual restarted and no egg dropped.
     *
     * @param helper the gametest helper
     */
    public static void aeonRitualBaby(GameTestHelper helper) {
        Mob mob = spawnOneHitRitual(helper, EntityType.COW);
        runEntityPrograms(helper, mob, ABILITY_AEON_TIME_STOP);
        double ritual = new EntityHost(helper.getLevel(), mob, null).counters().read(RITUAL_COUNTER);
        helper.assertTrue(mob.isAlive() && mob.isBaby(), SHOULD_BE_BABY);
        helper.assertTrue(ritual == 0, String.format(SHOULD_RESTART_RITUAL, ritual));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(itemsNear(helper, mob.position()).isEmpty(), SHOULD_DROP_NOTHING);
            helper.succeed();
        });
    }

    /**
     * A baby cow with max health one completes the ritual on its first
     * throw and drops its own spawn egg.
     *
     * @param helper the gametest helper
     */
    public static void aeonRitualBabyEgg(GameTestHelper helper) {
        Mob mob = spawnOneHitRitual(helper, EntityType.COW);
        mob.setBaby(true);
        assertRitualLeavesEgg(helper, mob, Items.COW_SPAWN_EGG);
    }

    /**
     * A creeper has no baby form, so completing the ritual drops its egg
     * straight away.
     *
     * @param helper the gametest helper
     */
    public static void aeonRitualNoBabyForm(GameTestHelper helper) {
        assertRitualLeavesEgg(helper, spawnOneHitRitual(helper, EntityType.CREEPER), Items.CREEPER_SPAWN_EGG);
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

    /**
     * Spawns a mob whose max health is one, so one aeon throw adds the full
     * hundred to its ritual.
     *
     * @param helper the gametest helper
     * @param type   the mob type
     * @return the mob
     */
    private static Mob spawnOneHitRitual(GameTestHelper helper, EntityType<? extends Mob> type) {
        Mob mob = helper.spawnWithNoFreeWill(type, SPAWN_POS);
        AttributeInstance maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
        helper.assertTrue(maxHealth != null, MOB_HAS_MAX_HEALTH);
        maxHealth.setBaseValue(ONE_HIT_RITUAL_MAX_HEALTH);
        return mob;
    }

    /**
     * Runs one aeon throw on the mob, then reads one spawn egg of its type
     * and no other item beside where it stood, and the mob gone. The read
     * retries each tick: at the far test positions a chunk's entities can
     * reach the level's entity scan some ticks after they are added.
     *
     * @param helper the gametest helper
     * @param mob    the struck mob
     * @param egg    the mob's spawn egg
     */
    private static void assertRitualLeavesEgg(GameTestHelper helper, Mob mob, Item egg) {
        Vec3 stood = mob.position();
        runEntityPrograms(helper, mob, ABILITY_AEON_TIME_STOP);
        helper.assertTrue(mob.isRemoved(), SHOULD_VANISH);
        helper.succeedWhen(() -> {
            List<ItemEntity> items = itemsNear(helper, stood);
            helper.assertTrue(items.size() == 1 && items.get(0).getItem().is(egg)
                    && items.get(0).getItem().getCount() == 1,
                    String.format(SHOULD_DROP_ONLY_EGG, items.stream().map(ItemEntity::getItem).toList()));
            helper.assertTrue(mobsNear(helper, stood, mob.getType()).isEmpty(), SHOULD_VANISH);
        });
    }

    /**
     * Collects the mobs of one type within two blocks of a point; a wider
     * reach catches the mobs of the tests beside this one.
     *
     * @param helper the gametest helper
     * @param center the point
     * @param type   the mob type
     * @return the mobs
     */
    private static List<Mob> mobsNear(GameTestHelper helper, Vec3 center, EntityType<?> type) {
        return helper.getLevel().getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(ITEM_SEARCH_RADIUS),
                found -> found.getType() == type);
    }

    /**
     * Collects the item entities within two blocks of a point.
     *
     * @param helper the gametest helper
     * @param center the point
     * @return the item entities
     */
    private static List<ItemEntity> itemsNear(GameTestHelper helper, Vec3 center) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(center, center).inflate(ITEM_SEARCH_RADIUS));
    }

    /**
     * Aeon's ritual counter persists on the struck mob: each throw adds
     * 100 / pow(max_health, 0.6) to the cow's goo:ritual counter, so two
     * throws on a cow of max health ten read twice that (decision
     * aeon-mob-ritual-drops-spawn-egg).
     *
     * @param helper the gametest helper
     */
    public static void aeonRitualCounts(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        runEntityPrograms(helper, mob, ABILITY_AEON_TIME_STOP);
        runEntityPrograms(helper, mob, ABILITY_AEON_TIME_STOP);
        double expected = RITUAL_THROWS * RITUAL_PERCENT / Math.pow(mob.getMaxHealth(), RITUAL_HEALTH_EXPONENT);
        double ritual = new EntityHost(helper.getLevel(), mob, null).counters().read(RITUAL_COUNTER);
        helper.assertTrue(Math.abs(ritual - expected) < RITUAL_TOLERANCE,
                String.format(SHOULD_COUNT_RITUAL, expected, ritual));
        helper.succeed();
    }
}
