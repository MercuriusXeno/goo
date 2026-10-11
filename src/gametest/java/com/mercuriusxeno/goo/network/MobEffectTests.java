package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.hex.CharmEvents;
import com.mercuriusxeno.goo.ability.program.EntityFilter;
import com.mercuriusxeno.goo.ability.program.EntityScan;
import com.mercuriusxeno.goo.ability.stasis.StasisEvents;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    /** One block beside the spawn, where the charm tests stand their second mob. */
    private static final BlockPos BYSTANDER_POS = SPAWN_POS.east();
    /** The tick after spawning, once the level's entity index holds the spawned mobs. */
    private static final int SETTLE_TICKS = 1;
    private static final String SHOULD_HAVE_SLOWNESS = "Target should have slowness";
    private static final String SHOULD_HAVE_POISON = "Target should have poison";
    private static final String SKELETON_SHOULD_STAND_IDLE = "The skeleton should target no one before the charm";
    private static final String ZOMBIE_SHOULD_TURN_ON_SKELETON = "The charmed zombie should target the skeleton";
    private static final String ZOMBIE_SHOULD_SPARE_CHARMER = "The charmed zombie should turn from its charmer";
    private static final String ZOMBIE_SHOULD_BE_CHARMED = "The zombie should hold the charm";
    private static final String CHARMED_SLIME_SHOULD_SPARE = "A charmed slime's hit should land nothing on its charmer";
    private static final String WILD_SLIME_SHOULD_HURT = "An uncharmed slime's hit should hurt the player";
    private static final float SLIME_HIT = 4f;
    /** A hit too light to kill the zombie, so the charm, not the mob, is what ends. */
    private static final float LIGHT_HIT = 1f;
    /** Ticks the charm is held before the save, long past any timed ailment's fade. */
    private static final int CHARM_HOLD_TICKS = 100;
    private static final String CHARM_SHOULD_HOLD = "The charm should hold with no expiry";
    private static final String CHARM_SHOULD_RELOAD = "The reloaded zombie should hold the charm for its charmer";
    private static final String CHARMERS_HIT_SHOULD_BREAK = "The charmer's hit should end the charm";
    private static final String OTHER_HIT_SHOULD_LEAVE = "A hit from another mob should leave the charm";
    private static final String SHOULD_NOT_GLOW = "Target should wear the ailment overlay, not vanilla glowing";
    private static final String SHOULD_TAKE_DAMAGE = "Target should have taken damage";
    private static final String SHOULD_BE_ON_FIRE = "Target should be on fire";
    private static final String SHOULD_HAVE_NO_AI = "Target should have AI disabled";
    private static final String SHOULD_HAVE_LEVITATION = "Target should have levitation";
    private static final String SHOULD_TAKE_JAVELIN_DAMAGE = "Target should have taken the javelin's damage";
    private static final String ABILITIES_REQUIRED = "Ability registry must be loaded";
    private static final String ABILITY_METAL_JAVELIN = "goo:metal_javelin";
    private static final String ABILITY_TYPHOON_FLOAT = "goo:typhoon_float";
    private static final String LEVITATION_SHOULD_HIDE_PARTICLES = "Float's levitation should show no particles";
    private static final String FLOAT_SHOULD_END_WITH_LEVITATION = "The zombie's float should end when its levitation does";
    private static final String FLOAT_SHOULD_CLEAR_WITH_LEVITATION = "Removing the levitation should clear the float";
    private static final String ABILITY_FROST_SNAP = "goo:frost_snap";
    private static final String ABILITY_PULSE_SHORT_CIRCUIT = "goo:pulse_short_circuit";
    private static final String ABILITY_AEON_STASIS = "goo:aeon_stasis";
    /** The ticks a stasis is watched before the hits land. */
    private static final int STASIS_HOLD_TICKS = 100;
    private static final float STASIS_HIT_DAMAGE = 4.0f;
    private static final String SHOULD_STAY_IN_STASIS = "The zombie should stay in stasis until an attacker strikes it";
    private static final String SHOULD_STAY_PUT = "The zombie in stasis should not have moved";
    private static final String SHOULD_TAKE_NO_DAMAGE = "The zombie in stasis should take no damage";
    private static final String SHOULD_SURVIVE_LANDING_PUNCH = "The punch landing the stasis should not free the mob";
    private static final String SHOULD_DEAL_NOTHING_FROZEN = "A mob in stasis should deal no damage";
    private static final String SHOULD_BE_FREED = "An attacker's strike should free the zombie";
    private static final String SHOULD_HAVE_AI_AGAIN = "The freed zombie should have its AI back";
    /** The farthest a mob in stasis may drift and still count as standing put. */
    private static final double STASIS_MOST_DRIFT = 0.01;
    private static final String MOB_HAS_MAX_HEALTH = "The mob carries a max health attribute";
    private static final String ABILITY_UNSTABLE_EXPLODE = "goo:unstable_explode";
    private static final String ABILITY_HEX_CHARM = "goo:hex_charm";
    private static final String ABILITY_ZOO_ALLURE = "goo:zoo_allure";
    private static final String ABILITY_BLAZE_IGNITE = "goo:blaze_ignite";
    private static final String ABILITY_CRYSTAL_FLECHETTES = "goo:crystal_flechettes";
    private static final String ABILITY_ENDER_ZONE = "goo:ender_zone";
    /** Above ender_zone.json's radius of six, so the player sets nothing off until the test walks it in. */
    private static final double PLAYER_OUT_OF_REACH_ABOVE = 20.0;
    /** Over ender_zone.json's resist cap of a hundred max health. */
    private static final double RESISTING_MAX_HEALTH = 200.0;
    private static final String SHOULD_BE_CURSED = "The zombie should carry the curse after the first Zone";
    private static final String SHOULD_WARP = "The cursed zombie should warp away from the player standing on it";
    private static final String SHOULD_BE_EXILED = "A second Zone should exile the cursed zombie";
    private static final String SHOULD_RESIST = "A mob over the max health cap should resist the curse";
    private static final String SHOULD_BE_IN_LOVE = "A cow struck by Allure should be in love";
    /** Beside the cow, inside unstable_explode.json's blast of power 3. */
    private static final BlockPos BLAST_DIRT_POS = SPAWN_POS.south();
    /** The damage crystal_flechettes.json's first damage step names. */
    private static final float FLECHETTE_DAMAGE = 4.0f;
    private static final String LIVING_SHOULD_NOT_BURN = "A cow is not undead and should not burn";
    private static final String UNDEAD_SHOULD_BURN = "A zombie is undead and should burn";
    private static final String SHOULD_BE_HALF_FROZEN = "One snap should fill half a zombie's frozen gauge, stands %s";
    private static final String SHOULD_BE_ENCASED = "Two snaps should fill a zombie's frozen gauge";
    private static final String SHOULD_STAND_STILL = "An encased zombie should have no speed";
    private static final String SHOULD_HOLD_FULL = "A full gauge should hold through frost_snap.json's hold";
    private static final String SHOULD_THAW = "A full gauge should thaw below full once its hold runs out";
    private static final String SHOULD_HAVE_AI_BACK = "A thawed zombie should have its AI back";
    /** frost_snap.json's amount of ten over a zombie's twenty health. */
    private static final float HALF_FROZEN = 0.5f;
    /** frost_snap.json's hold. */
    private static final int SNAP_HOLD_TICKS = 300;
    /** Ticks past the hold by which the thaw has taken the gauge below full. */
    private static final int THAW_CHECK_TICKS = 3;
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
     * damage, a freeze step and a slowness potion step; one snap on a zombie
     * fills half its frozen gauge, ten of its twenty health.
     *
     * @param helper the gametest helper
     */
    public static void frostSnap(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPAWN_POS);
        float before = mob.getHealth();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, mob, ABILITY_FROST_SNAP);
            float gauge = mob.getData(GooAttachments.FROZEN).gauge();
            helper.assertTrue(mob.getHealth() < before, SHOULD_TAKE_DAMAGE);
            helper.assertTrue(gauge == HALF_FROZEN, String.format(SHOULD_BE_HALF_FROZEN, gauge));
            helper.assertTrue(mob.hasEffect(MobEffects.SLOWNESS), SHOULD_HAVE_SLOWNESS);
            helper.succeed();
        });
    }

    /**
     * Two snaps fill a helmeted zombie's frozen gauge: it stands encased
     * with no AI and no speed, and once frost_snap.json's hold runs out it
     * thaws below full with its AI back.
     *
     * @param helper the gametest helper
     */
    public static void snapEncasesThenThaws(GameTestHelper helper) {
        Mob zombie = helper.spawn(EntityType.ZOMBIE, SPAWN_POS);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, zombie, ABILITY_FROST_SNAP);
            strike(helper, zombie, ABILITY_FROST_SNAP);
            helper.assertTrue(zombie.getData(GooAttachments.FROZEN).full(), SHOULD_BE_ENCASED);
            helper.assertTrue(zombie.isNoAi(), SHOULD_HAVE_NO_AI);
            helper.assertTrue(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0, SHOULD_STAND_STILL);
        });
        helper.runAfterDelay(SETTLE_TICKS + SNAP_HOLD_TICKS - 1, () -> {
            helper.assertTrue(zombie.getData(GooAttachments.FROZEN).full(), SHOULD_HOLD_FULL);
            helper.assertTrue(zombie.isNoAi(), SHOULD_HAVE_NO_AI);
        });
        helper.runAfterDelay(SETTLE_TICKS + SNAP_HOLD_TICKS + THAW_CHECK_TICKS, () -> {
            helper.assertFalse(zombie.getData(GooAttachments.FROZEN).full(), SHOULD_THAW);
            helper.assertFalse(zombie.isNoAi(), SHOULD_HAVE_AI_BACK);
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
            strike(helper, zombie, ABILITY_TYPHOON_FLOAT);
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
     * too, and it wears no
     * vanilla glowing (decisions charm-glisten-and-icon-over-the-head,
     * ailment-overlay-shader-per-ailment). A zombie's max health of 20 makes
     * hex_charm.json's charm chance whole.
     *
     * @param helper the gametest helper
     */
    public static void charmTurnsZombieOnSkeleton(GameTestHelper helper) {
        ServerPlayer charmer = SurvivalPlayers.placeIn(helper);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPAWN_POS);
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
                helper.assertFalse(zombie.hasEffect(MobEffects.GLOWING), SHOULD_NOT_GLOW);
                helper.getLevel().getServer().getPlayerList().remove(charmer);
                helper.succeed();
            });
        });
    }

    /**
     * A charm holds with no expiry: the zombie still holds it after the
     * hold, and a save loaded back holds it for the same charmer
     * (decision charm-holds-until-struck).
     *
     * @param helper the gametest helper
     */
    public static void charmHasNoExpiry(GameTestHelper helper) {
        ServerPlayer charmer = SurvivalPlayers.placeIn(helper);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPAWN_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, zombie, ABILITY_HEX_CHARM, charmer);
            helper.runAfterDelay(CHARM_HOLD_TICKS, () -> {
                helper.assertTrue(zombie.hasData(GooAttachments.CHARMED), CHARM_SHOULD_HOLD);
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
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPAWN_POS);
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
     * A hit on a charmed zombie from another mob leaves the charm standing
     * (decision charm-holds-until-struck).
     *
     * @param helper the gametest helper
     */
    public static void charmSurvivesAnotherHit(GameTestHelper helper) {
        ServerPlayer charmer = SurvivalPlayers.placeIn(helper);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPAWN_POS);
        Mob skeleton = helper.spawnWithNoFreeWill(EntityType.SKELETON, BYSTANDER_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, zombie, ABILITY_HEX_CHARM, charmer);
            float before = zombie.getHealth();
            zombie.hurtServer(helper.getLevel(), zombie.damageSources().mobAttack(skeleton), LIGHT_HIT);
            helper.assertTrue(zombie.getHealth() < before && zombie.hasData(GooAttachments.CHARMED),
                    OTHER_HIT_SHOULD_LEAVE);
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
        Slime charmed = helper.spawnWithNoFreeWill(EntityType.SLIME, SPAWN_POS);
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
     * A zombie whose max health stands over ender_zone.json's cap of a
     * hundred resists the curse.
     *
     * @param helper the gametest helper
     */
    public static void zoneResistedByHighHealth(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, SPAWN_POS);
        AttributeInstance maxHealth = zombie.getAttribute(Attributes.MAX_HEALTH);
        helper.assertTrue(maxHealth != null, MOB_HAS_MAX_HEALTH);
        maxHealth.setBaseValue(RESISTING_MAX_HEALTH);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, zombie, ABILITY_ENDER_ZONE);
            helper.assertTrue(ZoneEvents.curseOf(zombie) == null, SHOULD_RESIST);
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
                helper.assertTrue(mob.position().distanceTo(frozenAt) < STASIS_MOST_DRIFT, SHOULD_STAY_PUT);
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
