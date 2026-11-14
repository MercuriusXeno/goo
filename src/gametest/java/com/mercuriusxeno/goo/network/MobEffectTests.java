package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.hex.CharmEvents;
import com.mercuriusxeno.goo.ability.zone.ZoneEvents;
import com.mercuriusxeno.goo.gametest.SurvivalPlayers;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for the mob abilities: each test spawns a mob, lands a goo of
 * the ability on it through {@link GooEffectScheduler#applyEffect} and
 * asserts the live entity's reaction (damage, status effect, fire, AI
 * state, a move). What a program asks of its host without an entity is
 * graded in MobProgramTest. They sit in the scheduler's package because
 * the scheduler is package-private.
 */
@RegistersGameTests
public final class MobEffectTests {
    private static final String ALLURE_COURTS = "allure_courts_the_struck_animal";
    private static final String FLOAT_LEVITATES_A_ZOMBIE = "float_levitates_a_zombie";
    private static final String CHARM_TURNS_ZOMBIE = "charm_turns_zombie_on_skeleton";
    private static final String CHARMED_SLIME_SPARES = "charmed_slime_spares_its_charmer";
    private static final String CHARM_HOLDS_THROUGH_A_SAVE = "charm_holds_through_a_save";
    private static final String CHARM_BREAKS_ON_THE_CHARMERS_HIT = "charm_breaks_on_the_charmers_hit";
    private static final String ZONE_WARPS_THEN_EXILES = "zone_warps_then_exiles";
    private static final String MOB_UNSTABLE = "mob_unstable_blast";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, ALLURE_COURTS, MobEffectTests::allureCourtsTheStruckAnimal);
        GooTestFunctions.registerTest(registrar, FLOAT_LEVITATES_A_ZOMBIE, MobEffectTests::floatLevitatesAZombie);
        GooTestFunctions.registerTest(registrar, CHARM_TURNS_ZOMBIE, MobEffectTests::charmTurnsZombieOnSkeleton);
        GooTestFunctions.registerTest(registrar, CHARMED_SLIME_SPARES, MobEffectTests::charmedSlimeSparesItsCharmer);
        GooTestFunctions.registerTest(registrar, CHARM_HOLDS_THROUGH_A_SAVE, MobEffectTests::charmHoldsThroughASave);
        GooTestFunctions.registerTest(registrar, CHARM_BREAKS_ON_THE_CHARMERS_HIT, MobEffectTests::charmBreaksOnTheCharmersHit);
        GooTestFunctions.registerTest(registrar, ZONE_WARPS_THEN_EXILES, MobEffectTests::zoneWarpsThenExiles);
        GooTestFunctions.registerTest(registrar, MOB_UNSTABLE, MobEffectTests::unstableExplode);
    }


    /**
     * Clear of the barrier shell the framework wraps the one-block empty
     * structure in: a mob spawned at (1, 1, 1) stands inside that shell's
     * corner, where an explosion's rays die on the barrier.
     */
    private static final BlockPos SPAWN_POS = new BlockPos(3, 1, 3);
    /** One block beside the spawn, where the charm tests stand their second mob. */
    private static final BlockPos BYSTANDER_POS = SPAWN_POS.east();
    /** The tick after spawning, once the level's entity index holds the spawned mobs. */
    private static final int SETTLE_TICKS = 1;
    private static final String SKELETON_SHOULD_STAND_IDLE = "The skeleton should target no one before the charm";
    private static final String ZOMBIE_SHOULD_TURN_ON_SKELETON = "The charmed zombie should target the skeleton";
    private static final String ZOMBIE_SHOULD_SPARE_CHARMER = "The charmed zombie should turn from its charmer";
    private static final String ZOMBIE_SHOULD_BE_CHARMED = "The zombie should hold the charm";
    private static final String CHARMED_SLIME_SHOULD_SPARE = "A charmed slime's hit should land nothing on its charmer";
    private static final String WILD_SLIME_SHOULD_HURT = "An uncharmed slime's hit should hurt the player";
    private static final float SLIME_HIT = 4f;
    /** A hit too light to kill the zombie, so the charm, not the mob, is what ends. */
    private static final float LIGHT_HIT = 1f;
    /**
     * The max health a charm test gives the mob it charms: half a zombie's twenty, low enough that the charm's
     * chance, which falls as max health rises, stands whole with a wide margin for its tuning.
     */
    private static final double CERTAIN_CHARM_HEALTH = 10;
    private static final String CHARM_SHOULD_RELOAD = "The reloaded zombie should hold the charm for its charmer";
    private static final String CHARMERS_HIT_SHOULD_BREAK = "The charmer's hit should end the charm";
    private static final String SHOULD_TAKE_DAMAGE = "Target should have taken damage";
    private static final String SHOULD_HAVE_LEVITATION = "Target should have levitation";
    private static final String ABILITIES_REQUIRED = "Ability registry must be loaded";
    private static final String ABILITY_TYPHOON_SHULK = "goo:typhoon_shulk";
    private static final String LEVITATION_SHOULD_HIDE_PARTICLES = "Shulk's levitation should show no particles";
    private static final String FLOAT_SHOULD_END_WITH_LEVITATION = "The zombie's float should end when its levitation does";
    private static final String FLOAT_SHOULD_CLEAR_WITH_LEVITATION = "Removing the levitation should clear the float";
    /** The gametests' own thrown blast, today's Blast before it was drag-sized, struck at a mob. */
    private static final String ABILITY_UNSTABLE_BLAST = "goo:test_blast";
    private static final String ABILITY_HEX_CHARM = "goo:hex_charm";
    private static final String ABILITY_ZOO_ALLURE = "goo:zoo_allure";
    private static final String ABILITY_ENDER_ZONE = "goo:ender_zone";
    /** Well above Zone's radius, so the player sets nothing off until the test walks it in. */
    private static final double PLAYER_OUT_OF_REACH_ABOVE = 20.0;
    private static final String SHOULD_BE_CURSED = "The zombie should carry the curse after the first Zone";
    private static final String SHOULD_WARP = "The cursed zombie should warp away from the player standing on it";
    private static final String SHOULD_BE_EXILED = "A second Zone should exile the cursed zombie";
    private static final String SHOULD_BE_IN_LOVE = "A cow struck by Allure should be in love";
    /** Beside the cow, inside test_blast.json's blast of power 3. */
    private static final BlockPos BLAST_DIRT_POS = SPAWN_POS.south();

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
        strike(helper, mob, abilityId, null);
    }

    /**
     * Lands one goo of the named ability on the mob as a player's throw.
     *
     * @param helper    the gametest helper
     * @param mob       the struck mob
     * @param abilityId the ability the throw names
     * @param thrower   the throwing player, or null for none
     */
    private static void strike(GameTestHelper helper, Mob mob, String abilityId, @Nullable ServerPlayer thrower) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(abilityId));
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), thrower, ability.gooType(),
                mob.getId(), mob.blockPosition(), Direction.UP, abilityId));
    }

    /**
     * Allure courts the struck animal: a grown cow hit by the blob is in love
     * (decision allure-courts-the-struck-animal).
     *
     * @param helper the gametest helper
     */
    public static void allureCourtsTheStruckAnimal(GameTestHelper helper) {
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, cow, ABILITY_ZOO_ALLURE);
            helper.assertTrue(cow.isInLove(), SHOULD_BE_IN_LOVE);
            helper.succeed();
        });
    }

    /**
     * Typhoon float lifts a zombie with levitation that shows no particles,
     * marks it floating until the levitation's last tick, and drops the mark
     * when the levitation is removed (decision float-blob-levitates-the-mob).
     *
     * @param helper the gametest helper
     */
    public static void floatLevitatesAZombie(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPAWN_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, zombie, ABILITY_TYPHOON_SHULK);
            MobEffectInstance levitation = zombie.getEffect(MobEffects.LEVITATION);
            helper.assertTrue(levitation != null, SHOULD_HAVE_LEVITATION);
            helper.assertFalse(levitation.isVisible(), LEVITATION_SHOULD_HIDE_PARTICLES);
            long endsAt = helper.getLevel().getGameTime() + levitation.getDuration();
            helper.assertTrue(zombie.hasData(GooAttachments.FLOATING)
                    && zombie.getData(GooAttachments.FLOATING).expiresAt() == endsAt, FLOAT_SHOULD_END_WITH_LEVITATION);
            zombie.removeEffect(MobEffects.LEVITATION);
            helper.assertFalse(zombie.hasData(GooAttachments.FLOATING), FLOAT_SHOULD_CLEAR_WITH_LEVITATION);
            helper.succeed();
        });
    }

    /**
     * Hex charm turns a zombie on a skeleton before the skeleton aggresses:
     * once it looks for a foe, the zombie targets the idle skeleton, and a
     * target of its survival charmer it would take turns to the skeleton
     * too (decision charm-glisten-and-icon-over-the-head). The zombie stands at a max
     * health low enough that the charm always takes.
     *
     * @param helper the gametest helper
     */
    public static void charmTurnsZombieOnSkeleton(GameTestHelper helper) {
        ServerPlayer charmer = SurvivalPlayers.placeIn(helper);
        Mob zombie = charmable(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPAWN_POS));
        Mob skeleton = helper.spawnWithNoFreeWill(EntityType.SKELETON, BYSTANDER_POS);
        // spawnWithNoFreeWill leaves the target goals, which can lock the skeleton onto the survival
        // charmer on its first tick; the test stands a skeleton that has not aggressed
        skeleton.targetSelector.removeAllGoals(goal -> true);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(skeleton.getTarget() == null, SKELETON_SHOULD_STAND_IDLE);
            strike(helper, zombie, ABILITY_HEX_CHARM, charmer);
            helper.assertTrue(zombie.hasData(GooAttachments.CHARMED), ZOMBIE_SHOULD_BE_CHARMED);
            helper.runAfterDelay(CharmEvents.LOOK_INTERVAL_TICKS, () -> {
                helper.assertTrue(zombie.getTarget() == skeleton, ZOMBIE_SHOULD_TURN_ON_SKELETON);
                zombie.setTarget(charmer);
                helper.assertTrue(zombie.getTarget() == skeleton, ZOMBIE_SHOULD_SPARE_CHARMER);
                helper.getLevel().getServer().getPlayerList().remove(charmer);
                helper.succeed();
            });
        });
    }

    /**
     * A charm holds through a save: the charmed zombie loaded back holds it
     * for the same charmer (decision charm-holds-until-struck).
     *
     * @param helper the gametest helper
     */
    public static void charmHoldsThroughASave(GameTestHelper helper) {
        ServerPlayer charmer = SurvivalPlayers.placeIn(helper);
        Mob zombie = charmable(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPAWN_POS));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, zombie, ABILITY_HEX_CHARM, charmer);
            TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                    helper.getLevel().registryAccess());
            zombie.save(saved);
            zombie.discard();
            Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,
                    helper.getLevel().registryAccess(), saved.buildResult()), helper.getLevel(),
                    EntitySpawnReason.LOAD, entity -> entity);
            helper.assertTrue(loaded != null && loaded.hasData(GooAttachments.CHARMED)
                    && loaded.getData(GooAttachments.CHARMED).charmer().equals(charmer.getUUID()),
                    CHARM_SHOULD_RELOAD);
            helper.getLevel().getServer().getPlayerList().remove(charmer);
            helper.succeed();
        });
    }

    /**
     * The charmer's own hit on a charmed zombie ends the charm
     * (decision charm-holds-until-struck).
     *
     * @param helper the gametest helper
     */
    public static void charmBreaksOnTheCharmersHit(GameTestHelper helper) {
        ServerPlayer charmer = SurvivalPlayers.placeIn(helper);
        Mob zombie = charmable(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPAWN_POS));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, zombie, ABILITY_HEX_CHARM, charmer);
            helper.assertTrue(zombie.hasData(GooAttachments.CHARMED), ZOMBIE_SHOULD_BE_CHARMED);
            zombie.hurtServer(helper.getLevel(), zombie.damageSources().playerAttack(charmer), LIGHT_HIT);
            helper.assertFalse(zombie.hasData(GooAttachments.CHARMED), CHARMERS_HIT_SHOULD_BREAK);
            helper.getLevel().getServer().getPlayerList().remove(charmer);
            helper.succeed();
        });
    }

    /**
     * A charmed slime's touch, which hurts any player it bumps whatever it
     * targets, lands nothing on its charmer, while an uncharmed slime's
     * lands in full (decision charm-glisten-and-icon-over-the-head).
     *
     * @param helper the gametest helper
     */
    public static void charmedSlimeSparesItsCharmer(GameTestHelper helper) {
        ServerPlayer charmer = SurvivalPlayers.placeIn(helper);
        Slime charmed = charmable(helper.spawnWithNoFreeWill(EntityType.SLIME, SPAWN_POS));
        Slime wild = helper.spawnWithNoFreeWill(EntityType.SLIME, BYSTANDER_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, charmed, ABILITY_HEX_CHARM, charmer);
            float before = charmer.getHealth();
            charmer.hurtServer(helper.getLevel(), charmer.damageSources().mobAttack(charmed), SLIME_HIT);
            helper.assertTrue(charmer.getHealth() == before, CHARMED_SLIME_SHOULD_SPARE);
            charmer.invulnerableTime = 0;
            charmer.hurtServer(helper.getLevel(), charmer.damageSources().mobAttack(wild), SLIME_HIT);
            helper.assertTrue(charmer.getHealth() < before, WILD_SLIME_SHOULD_HURT);
            helper.getLevel().getServer().getPlayerList().remove(charmer);
            helper.succeed();
        });
    }

    /**
     * Stands a mob a charm test charms at a max health its charm's chance stands whole at.
     *
     * @param mob the mob
     * @param <T> the mob's class
     * @return the mob, at full health
     */
    private static <T extends Mob> T charmable(T mob) {
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(CERTAIN_CHARM_HEALTH);
        mob.setHealth(mob.getMaxHealth());
        return mob;
    }

    /**
     * Zone's first hit curses a zombie: a player walked onto it sets off a
     * warp away, and a second Zone hit exiles it from existence
     * (decision zone-curses-with-ender-shimmer).
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void zoneWarpsThenExiles(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPAWN_POS);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(zombie.getX(), zombie.getY() + PLAYER_OUT_OF_REACH_ABOVE, zombie.getZ());
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, zombie, ABILITY_ENDER_ZONE);
            helper.assertTrue(ZoneEvents.curseOf(zombie) != null, SHOULD_BE_CURSED);
            Vec3 home = zombie.position();
            player.setPos(home);
            // The assertion runs each tick until the warp lands, then brings the zombie home, where the
            // test's chunks keep it ticking, and lands the second Zone on it.
            helper.succeedWhen(() -> {
                helper.assertFalse(zombie.position().equals(home), SHOULD_WARP);
                player.setPos(home.add(0, PLAYER_OUT_OF_REACH_ABOVE, 0));
                zombie.teleportTo(home.x, home.y, home.z);
                strike(helper, zombie, ABILITY_ENDER_ZONE);
                helper.assertTrue(zombie.isRemoved(), SHOULD_BE_EXILED);
            });
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
            strike(helper, mob, ABILITY_UNSTABLE_BLAST);
            helper.assertTrue(mob.getHealth() < before, SHOULD_TAKE_DAMAGE);
            helper.assertBlockNotPresent(Blocks.DIRT, BLAST_DIRT_POS);
            helper.succeed();
        });
    }
}
