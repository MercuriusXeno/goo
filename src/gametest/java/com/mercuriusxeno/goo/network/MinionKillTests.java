package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.yarn.LeadEvents;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Gametests for a minion's kill: a led or charmed mob's hit marks the
 * struck mob as its player's, as a tamed wolf's hit marks it as its
 * owner's, so the kill drops experience and player-only loot and counts in
 * that player's statistics and advancements, while the struck mob fights
 * the minion.
 * minion-hit-marks-the-struck-mob-as-its-players
 * charmed-mobs-take-the-rallied-kill-rule
 * minion-kill-counts-in-its-players-stats-and-advancements
 */
@RegistersGameTests
public final class MinionKillTests {
    private static final String LED_COWS_STRIKE_MARKS_THE_ZOMBIE_AS_ITS_LEADERS =
            "led_cows_strike_marks_the_zombie_as_its_leaders";
    private static final String LED_COWS_KILL_DROPS_EXPERIENCE_AND_PLAYER_ONLY_LOOT =
            "led_cows_kill_drops_experience_and_player_only_loot";
    private static final String WOLF_STRUCK_BY_A_LED_COW_TARGETS_THE_COW =
            "wolf_struck_by_a_led_cow_targets_the_cow";
    private static final String LED_COWS_MARK_STANDS_AFTER_ITS_LEAD_ENDS =
            "led_cows_mark_stands_after_its_lead_ends";
    private static final String CHARMED_ZOMBIES_HIT_MARKS_THE_SKELETON_AS_ITS_CHARMERS =
            "charmed_zombies_hit_marks_the_skeleton_as_its_charmers";
    private static final String CHARMED_SKELETONS_ARROW_MARKS_THE_ZOMBIE_AS_ITS_CHARMERS =
            "charmed_skeletons_arrow_marks_the_zombie_as_its_charmers";
    private static final String LED_COWS_KILL_COUNTS_IN_ITS_LEADERS_STATS_AND_ADVANCEMENTS =
            "led_cows_kill_counts_in_its_leaders_stats_and_advancements";

    private static final Identifier YARN_LEAD = Identifier.fromNamespaceAndPath(Goo.MODID, "yarn_lead");
    private static final Identifier HEX_CHARM = Identifier.fromNamespaceAndPath(Goo.MODID, "hex_charm");
    private static final Identifier KILL_A_MOB = Identifier.withDefaultNamespace("adventure/kill_a_mob");
    private static final BlockPos MINION_POS = new BlockPos(2, 1, 3);
    private static final BlockPos FOE_POS = new BlockPos(3, 1, 3);
    /** The hit the foe lands on the leader, which makes it the lead's foe. */
    private static final float FOE_HIT = 1f;
    /** The health a foe a test kills stands at, under one lead strike's 6. */
    private static final float ONE_STRIKE_HEALTH = 1f;
    /** A light hit the test lands for a minion, as its own attack would. */
    private static final float MINION_HIT = 2f;
    /**
     * The max health a charmed mob stands at: half a zombie's twenty, low
     * enough that the charm's chance, which falls as max health rises, stands whole.
     */
    private static final double CERTAIN_CHARM_HEALTH = 10;
    /** Ticks the wolf lives before the hit, so the hit's stamp reads past its first tick. */
    private static final int WOLF_SETTLE_TICKS = 2;
    /** Ticks between the lead ending and the mark read again. */
    private static final int AFTER_THE_LEAD_TICKS = 5;
    /** Reach around the foe's spot a drop or an orb is looked for in. */
    private static final double DROP_REACH = 6;

    private static final String ABILITY_MISSING = "%s should load";
    private static final String NOT_MARKED =
            "The struck %s should name %s as the player who last hurt it for a while, names %s for %s ticks";
    private static final String MINION_NOT_ATTACKER = "The struck %s should name the %s as the mob that last hurt it, "
            + "names %s";
    private static final String NOT_DEAD = "The struck %s should die of the minion's strike, stands at %s";
    private static final String NO_ORB = "Experience orbs should stand where the elder guardian died";
    private static final String NO_SPONGE = "A wet sponge, dropped only for a player's kill, should stand where the "
            + "elder guardian died";
    private static final String WOLF_TARGETS = "The struck wolf should target the cow, targets %s";
    private static final String WOLF_ANGRY_AT = "The struck wolf should stay angry at the cow, angry at %s";
    private static final String LEAD_STOOD = "The cow's lead should have ended";
    private static final String KILLS_UNCOUNTED = "The leader's mobs-killed statistic should read 1, reads %s";
    private static final String ADVANCEMENT_MISSING = "minecraft:adventure/kill_a_mob should load";
    private static final String ADVANCEMENT_UNDONE = "The leader should hold minecraft:adventure/kill_a_mob";
    private static final String NOT_A_MINION = "The %s should hold its %s";

    private MinionKillTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LED_COWS_STRIKE_MARKS_THE_ZOMBIE_AS_ITS_LEADERS,
                MinionKillTests::ledCowsStrikeMarksTheZombieAsItsLeaders);
        GooTestFunctions.registerTest(registrar, LED_COWS_KILL_DROPS_EXPERIENCE_AND_PLAYER_ONLY_LOOT,
                MinionKillTests::ledCowsKillDropsExperienceAndPlayerOnlyLoot);
        GooTestFunctions.registerTest(registrar, WOLF_STRUCK_BY_A_LED_COW_TARGETS_THE_COW,
                MinionKillTests::wolfStruckByARalliedCowTargetsTheCow);
        GooTestFunctions.registerTest(registrar, LED_COWS_MARK_STANDS_AFTER_ITS_LEAD_ENDS,
                MinionKillTests::ledCowsMarkStandsAfterItsLeadEnds);
        GooTestFunctions.registerTest(registrar, CHARMED_ZOMBIES_HIT_MARKS_THE_SKELETON_AS_ITS_CHARMERS,
                MinionKillTests::charmedZombiesHitMarksTheSkeletonAsItsCharmers);
        GooTestFunctions.registerTest(registrar, CHARMED_SKELETONS_ARROW_MARKS_THE_ZOMBIE_AS_ITS_CHARMERS,
                MinionKillTests::charmedSkeletonsArrowMarksTheZombieAsItsCharmers);
        GooTestFunctions.registerTest(registrar, LED_COWS_KILL_COUNTS_IN_ITS_LEADERS_STATS_AND_ADVANCEMENTS,
                MinionKillTests::ledCowsKillCountsInItsLeadersStatsAndAdvancements);
    }

    /**
     * A zombie hurts the leader and the led cow's lead strike lands on
     * it: the zombie names the leader as the player who last hurt it and the
     * cow as the mob that did.
     *
     * @param helper the gametest helper
     */
    public static void ledCowsStrikeMarksTheZombieAsItsLeaders(GameTestHelper helper) {
        ServerPlayer leader = survivalPlayer(helper, GooTypes.YARN);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, MINION_POS);
        Mob zombie = sunproof(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, FOE_POS));
        provoke(helper, leader, zombie);
        lead(helper, cow, leader);

        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.getLastHurtByMob() == cow,
                    String.format(MINION_NOT_ATTACKER, "zombie", "cow", zombie.getLastHurtByMob()));
            assertMarked(helper, zombie, leader);
            helper.getLevel().getServer().getPlayerList().remove(leader);
        });
    }

    /**
     * A led cow's strike kills an elder guardian left at 1 health: its
     * experience orbs and the wet sponge its loot table drops only for a
     * player's kill stand where it died.
     *
     * @param helper the gametest helper
     */
    public static void ledCowsKillDropsExperienceAndPlayerOnlyLoot(GameTestHelper helper) {
        ServerPlayer leader = survivalPlayer(helper, GooTypes.YARN);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, MINION_POS);
        Mob guardian = helper.spawnWithNoFreeWill(EntityType.ELDER_GUARDIAN, FOE_POS);
        provoke(helper, leader, guardian);
        guardian.setHealth(ONE_STRIKE_HEALTH);
        lead(helper, cow, leader);

        helper.succeedWhen(() -> {
            helper.assertTrue(guardian.isDeadOrDying(),
                    String.format(NOT_DEAD, "elder guardian", guardian.getHealth()));
            helper.assertFalse(helper.getEntities(EntityType.EXPERIENCE_ORB, FOE_POS, DROP_REACH).isEmpty(), NO_ORB);
            helper.assertTrue(helper.getEntities(EntityType.ITEM, FOE_POS, DROP_REACH).stream()
                    .anyMatch(item -> item.getItem().is(Items.WET_SPONGE)), NO_SPONGE);
            helper.getLevel().getServer().getPlayerList().remove(leader);
        });
    }

    /**
     * An untamed wolf with its AI running takes a hit sourced to a led
     * cow, built as the lead's strike builds it, beside the cow's survival
     * leader: the wolf targets the cow and stays angry at it, not at the
     * leader the hit marks it with.
     *
     * @param helper the gametest helper
     */
    public static void wolfStruckByARalliedCowTargetsTheCow(GameTestHelper helper) {
        ServerPlayer leader = survivalPlayer(helper, GooTypes.YARN);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, MINION_POS);
        lead(helper, cow, leader);
        helper.assertTrue(cow.hasData(GooAttachments.LED), String.format(NOT_A_MINION, "cow", "lead"));
        Wolf wolf = helper.spawn(EntityType.WOLF, FOE_POS);

        helper.startSequence()
                // vanilla's retaliation goal reads a hit stamped on the wolf's first tick, tick 0, as no hit
                .thenExecuteAfter(WOLF_SETTLE_TICKS, () ->
                        wolf.hurtServer(helper.getLevel(), cow.damageSources().mobAttack(cow), MINION_HIT))
                .thenWaitUntil(() -> {
                    helper.assertTrue(wolf.getTarget() == cow, String.format(WOLF_TARGETS, wolf.getTarget()));
                    EntityReference<LivingEntity> angryAt = wolf.getPersistentAngerTarget();
                    helper.assertTrue(angryAt != null && angryAt.getUUID().equals(cow.getUUID()),
                            String.format(WOLF_ANGRY_AT, angryAt == null ? "none" : angryAt.getUUID()));
                })
                .thenExecute(() -> helper.getLevel().getServer().getPlayerList().remove(leader))
                .thenSucceed();
    }

    /**
     * The led cow strikes the zombie and the test ends the cow's lead:
     * ticks later, the zombie still names the leader as the player who last
     * hurt it.
     *
     * @param helper the gametest helper
     */
    public static void ledCowsMarkStandsAfterItsLeadEnds(GameTestHelper helper) {
        ServerPlayer leader = survivalPlayer(helper, GooTypes.YARN);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, MINION_POS);
        Mob zombie = sunproof(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, FOE_POS));
        provoke(helper, leader, zombie);
        lead(helper, cow, leader);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(zombie.getLastHurtByMob() == cow,
                        String.format(MINION_NOT_ATTACKER, "zombie", "cow", zombie.getLastHurtByMob())))
                .thenExecute(() -> {
                    LeadEvents.endLead(cow);
                    helper.assertFalse(cow.hasData(GooAttachments.LED), LEAD_STOOD);
                })
                .thenExecuteAfter(AFTER_THE_LEAD_TICKS, () -> {
                    assertMarkedThenLeave(helper, zombie, leader);
                })
                .thenSucceed();
    }

    /**
     * A charmed zombie's own melee attack lands on a skeleton no one charmed:
     * the skeleton names the charmer as the player who last hurt it and the
     * zombie as the mob that did.
     *
     * @param helper the gametest helper
     */
    public static void charmedZombiesHitMarksTheSkeletonAsItsCharmers(GameTestHelper helper) {
        ServerPlayer charmer = survivalPlayer(helper, GooTypes.HEX);
        Mob zombie = sunproof(charmable(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, MINION_POS)));
        Mob skeleton = sunproof(helper.spawnWithNoFreeWill(EntityType.SKELETON, FOE_POS));
        charm(helper, zombie, charmer);
        helper.assertTrue(zombie.hasData(GooAttachments.CHARMED), String.format(NOT_A_MINION, "zombie", "charm"));

        zombie.doHurtTarget(helper.getLevel(), skeleton);

        helper.assertTrue(skeleton.getLastHurtByMob() == zombie,
                String.format(MINION_NOT_ATTACKER, "skeleton", "zombie", skeleton.getLastHurtByMob()));
        assertMarkedThenLeave(helper, skeleton, charmer);
        helper.succeed();
    }

    /**
     * An arrow a charmed skeleton shot lands on a zombie no one charmed: the
     * zombie names the charmer as the player who last hurt it.
     *
     * @param helper the gametest helper
     */
    public static void charmedSkeletonsArrowMarksTheZombieAsItsCharmers(GameTestHelper helper) {
        ServerPlayer charmer = survivalPlayer(helper, GooTypes.HEX);
        Mob skeleton = sunproof(charmable(helper.spawnWithNoFreeWill(EntityType.SKELETON, MINION_POS)));
        Mob zombie = sunproof(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, FOE_POS));
        charm(helper, skeleton, charmer);
        helper.assertTrue(skeleton.hasData(GooAttachments.CHARMED), String.format(NOT_A_MINION, "skeleton", "charm"));

        Arrow arrow = new Arrow(helper.getLevel(), skeleton, new ItemStack(Items.ARROW), null);
        zombie.hurtServer(helper.getLevel(), zombie.damageSources().arrow(arrow, skeleton), MINION_HIT);

        assertMarkedThenLeave(helper, zombie, charmer);
        helper.succeed();
    }

    /**
     * A led cow's strike kills a zombie left at 1 health: the leader's
     * mobs-killed statistic reads 1 and the leader holds the vanilla
     * advancement for killing a monster, as a tamed wolf's owner does. The
     * per-type count, zombies killed, goes to the entity behind the killing
     * damage alone, the cow, as it does for a wolf's kill.
     *
     * @param helper the gametest helper
     */
    public static void ledCowsKillCountsInItsLeadersStatsAndAdvancements(GameTestHelper helper) {
        ServerPlayer leader = survivalPlayer(helper, GooTypes.YARN);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, MINION_POS);
        Mob zombie = sunproof(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, FOE_POS));
        provoke(helper, leader, zombie);
        zombie.setHealth(ONE_STRIKE_HEALTH);
        lead(helper, cow, leader);
        AdvancementHolder killAMob = helper.getLevel().getServer().getAdvancements().get(KILL_A_MOB);
        helper.assertTrue(killAMob != null, ADVANCEMENT_MISSING);

        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.isDeadOrDying(), String.format(NOT_DEAD, "zombie", zombie.getHealth()));
            int kills = leader.getStats().getValue(Stats.CUSTOM, Stats.MOB_KILLS);
            helper.assertTrue(kills == 1, String.format(KILLS_UNCOUNTED, kills));
            helper.assertTrue(leader.getAdvancements().getOrStartProgress(killAMob).isDone(), ADVANCEMENT_UNDONE);
            helper.getLevel().getServer().getPlayerList().remove(leader);
        });
    }

    private static void assertMarked(GameTestHelper helper, Mob struck, ServerPlayer player) {
        assertMark(helper, struck, player, struck.getLastHurtByPlayer(), struck.getLastHurtByPlayerMemoryTime());
    }

    /**
     * Reads the struck mob's mark, removes the player and asserts the mark
     * read: the mark resolves its player through the level, so it is read
     * while the player stands there.
     *
     * @param helper the gametest helper
     * @param struck the mob struck
     * @param player the player the mark should name
     */
    private static void assertMarkedThenLeave(GameTestHelper helper, Mob struck, ServerPlayer player) {
        Player markedBy = struck.getLastHurtByPlayer();
        int memory = struck.getLastHurtByPlayerMemoryTime();
        helper.getLevel().getServer().getPlayerList().remove(player);
        assertMark(helper, struck, player, markedBy, memory);
    }

    private static void assertMark(GameTestHelper helper, Mob struck, ServerPlayer player, @Nullable Player markedBy,
                                   int memory) {
        helper.assertTrue(markedBy == player && memory > 0, String.format(NOT_MARKED,
                struck.getType().getDescription().getString(), player.getName().getString(), markedBy, memory));
    }

    /**
     * A mock player holding the type's goo, made survival with its client
     * loaded so hits land on it and it lands hits.
     *
     * @param helper  the gametest helper
     * @param gooType the goo type the player holds
     * @return the player
     */
    private static ServerPlayer survivalPlayer(GameTestHelper helper, ResourceKey<GooTypeDefinition> gooType) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, gooType);
        // the mock player helper makes a creative player, whom no hit lands on
        player.setGameMode(GameType.SURVIVAL);
        // a player whose client has not reported loaded is invulnerable, and no mock client reports
        player.connection.markClientLoaded();
        return player;
    }

    /**
     * The foe hurts the leader, which makes it the foe a led mob strikes.
     *
     * @param helper  the gametest helper
     * @param leader the leader hurt
     * @param foe     the mob hurting the leader
     */
    private static void provoke(GameTestHelper helper, ServerPlayer leader, Mob foe) {
        leader.hurtServer(helper.getLevel(), foe.damageSources().mobAttack(foe), FOE_HIT);
    }

    private static void lead(GameTestHelper helper, Mob mob, ServerPlayer leader) {
        throwAt(helper, mob, leader, YARN_LEAD);
    }

    private static void charm(GameTestHelper helper, Mob mob, ServerPlayer charmer) {
        throwAt(helper, mob, charmer, HEX_CHARM);
    }

    /**
     * Lands one goo of the ability on a mob as the player's throw, through
     * the scheduler a thrown goo's impact runs.
     *
     * @param helper  the gametest helper
     * @param mob     the mob struck
     * @param thrower the throwing player
     * @param id      the ability thrown
     */
    private static void throwAt(GameTestHelper helper, Mob mob, ServerPlayer thrower, Identifier id) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(id);
        helper.assertTrue(ability != null, String.format(ABILITY_MISSING, id));
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), thrower, ability.gooType(),
                mob.getId(), mob.blockPosition(), Direction.UP, id.toString()));
    }

    /**
     * Stands a mob a test charms at a max health its charm's chance stands whole at.
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
     * Helmets an undead mob, since the bay stands open to the sky and its
     * sunburn would pass for a hit.
     *
     * @param mob the mob
     * @return the mob, helmeted
     */
    private static Mob sunproof(Mob mob) {
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return mob;
    }
}
