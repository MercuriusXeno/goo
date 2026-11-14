package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.zoo.Downed;
import com.mercuriusxeno.goo.ability.zoo.Rallied;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Gametests for a rallied mob falling downed instead of dying: a lethal
 * attack downs it where a plain mob dies, the world's harm kills it
 * outright, attacks bounce off it for as long as it is down and lava does not, and it
 * dies 15 seconds after going down, its rally standing or not
 * (decision rallied-mob-falls-downed-instead-of-dying).
 */
@RegistersGameTests
public final class DownedTests {

    private static final String LETHAL_ATTACK_DOWNS_A_RALLIED_COW = "downed_lethal_attack_downs_a_rallied_cow";
    private static final String ATTACK_PAST_FIVE_SECONDS_LANDS_NOTHING =
            "downed_attack_past_five_seconds_lands_nothing";
    private static final String LAVA_KILLS_A_COW_DOWN_PAST_FIVE_SECONDS =
            "downed_lava_kills_a_cow_down_past_five_seconds";
    private static final String LAVA_KILLS_A_RALLIED_COW_OUTRIGHT = "downed_lava_kills_a_rallied_cow_outright";
    private static final String COW_DIES_AT_FIFTEEN_SECONDS = "downed_cow_dies_at_fifteen_seconds";
    private static final String COW_DIES_AT_FIFTEEN_SECONDS_UNRALLIED =
            "downed_cow_dies_at_fifteen_seconds_unrallied";

    private static final BlockPos COW_POS = new BlockPos(2, 1, 3);
    private static final BlockPos PLAIN_COW_POS = new BlockPos(2, 1, 1);
    private static final BlockPos ZOMBIE_POS = new BlockPos(4, 1, 1);
    /** Far past any cow's health, so the blow is lethal whatever a rally lifted it to. */
    static final float LETHAL = 100f;
    /** Past the 5 second window a downed mob once shrugged hits off for, short of the death span. */
    private static final int PAST_FIVE_SECONDS_TICKS = 150;
    /** Short of the death span. */
    private static final int STILL_DOWN_TICKS = 200;
    /** Past the 15 second death span. */
    static final int PAST_THE_DEATH_TICKS = Downed.DEATH_TICKS + 10;

    private static final String RALLIED_COW_DIED = "The rallied cow should live through the lethal attack";
    private static final String NOT_DOWNED = "The rallied cow should hold DOWNED after the lethal attack";
    private static final String PLAIN_COW_LIVED = "The unrallied cow should die to the lethal attack";
    private static final String ATTACK_KILLED = "An attack past 5 seconds down should leave the cow alive";
    private static final String ATTACK_RAISED = "An attack past 5 seconds down should leave the cow holding DOWNED";
    private static final String ATTACK_MOVED_THE_COUNT =
            "An attack should leave the cow's negative health at %s, reads %s";
    private static final String LAVA_SPARED = "Lava should kill the cow";
    private static final String DOWNED_BY_LAVA = "Lava should kill the rallied cow without downing it";
    private static final String DIED_TOO_SOON = "The downed cow should be alive and downed at 200 ticks";
    private static final String OUTLIVED_THE_SPAN = "The downed cow should be dead past 300 ticks";

    private static final String BLOW_LEAVES_ITS_NEGATIVE_HEALTH = "downed_blow_leaves_the_cow_its_negative_health";
    /** The health the cow stands at when the blow lands. */
    private static final float HEALTH_BEFORE_THE_BLOW = 4f;
    /** A blow 3 past the cow's health, leaving it at minus 3. */
    private static final float BLOW_PAST_ITS_HEALTH = 7f;
    private static final String WRONG_NEGATIVE_HEALTH = "The downed cow should carry negative health %s, reads %s";

    private static final String ZOMBIE_DROPS_THE_DOWNED_COW = "downed_zombie_drops_the_downed_cow";
    private static final String NOT_AIMED = "The zombie should take the standing cow as its target";
    private static final String STILL_AIMED = "The zombie should drop the cow as its target once the cow is downed";
    private static final String AIMED_AGAIN = "The zombie should take no hold on the downed cow past 5 seconds down";

    private DownedTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LETHAL_ATTACK_DOWNS_A_RALLIED_COW,
                DownedTests::lethalAttackDownsARalliedCow);
        GooTestFunctions.registerTest(registrar, ATTACK_PAST_FIVE_SECONDS_LANDS_NOTHING,
                DownedTests::attackPastFiveSecondsLandsNothing);
        GooTestFunctions.registerTest(registrar, LAVA_KILLS_A_COW_DOWN_PAST_FIVE_SECONDS,
                DownedTests::lavaKillsACowDownPastFiveSeconds);
        GooTestFunctions.registerTest(registrar, LAVA_KILLS_A_RALLIED_COW_OUTRIGHT,
                DownedTests::lavaKillsARalliedCowOutright);
        GooTestFunctions.registerTest(registrar, COW_DIES_AT_FIFTEEN_SECONDS,
                helper -> cowDiesAtFifteenSeconds(helper, false));
        GooTestFunctions.registerTest(registrar, COW_DIES_AT_FIFTEEN_SECONDS_UNRALLIED,
                helper -> cowDiesAtFifteenSeconds(helper, true));
        GooTestFunctions.registerTest(registrar, ZOMBIE_DROPS_THE_DOWNED_COW, DownedTests::zombieDropsTheDownedCow);
        GooTestFunctions.registerTest(registrar, BLOW_LEAVES_ITS_NEGATIVE_HEALTH,
                DownedTests::blowLeavesTheCowItsNegativeHealth);
    }

    /**
     * A rallied cow at 4 health struck for 7 goes down carrying a negative
     * health of 4 less 7, minus 3
     * (decision downed-mob-revives-only-healed-past-zero).
     *
     * @param helper the gametest helper
     */
    public static void blowLeavesTheCowItsNegativeHealth(GameTestHelper helper) {
        Zombie zombie = attacker(helper);
        Cow cow = ralliedCow(helper);
        float expected = HEALTH_BEFORE_THE_BLOW - BLOW_PAST_ITS_HEALTH;
        downTo(helper, cow, zombie, HEALTH_BEFORE_THE_BLOW, BLOW_PAST_ITS_HEALTH);
        helper.assertTrue(cow.hasData(GooAttachments.DOWNED), NOT_DOWNED);
        float carried = cow.getData(GooAttachments.DOWNED).negativeHealth();
        helper.assertTrue(carried == expected, String.format(WRONG_NEGATIVE_HEALTH, expected, carried));
        helper.succeed();
    }

    /**
     * Sets a cow's health and strikes it with a zombie's attack of a set damage.
     *
     * @param helper the gametest helper
     * @param cow    the cow struck
     * @param zombie the zombie dealing the attack
     * @param health the health the cow stands at when struck
     * @param damage the attack's damage
     */
    static void downTo(GameTestHelper helper, Cow cow, Zombie zombie, float health, float damage) {
        cow.setHealth(health);
        cow.hurtServer(helper.getLevel(), zombie.damageSources().mobAttack(zombie), damage);
    }

    /**
     * A zombie targeting a rallied cow holds no target the tick after the
     * cow goes down, and an explicit aim at the cow past 5 seconds
     * down takes no hold either
     * (decision nothing-targets-a-downed-mob).
     *
     * @param helper the gametest helper
     */
    public static void zombieDropsTheDownedCow(GameTestHelper helper) {
        Zombie zombie = attacker(helper);
        Cow cow = ralliedCow(helper);
        zombie.setTarget(cow);
        helper.assertTrue(zombie.getTarget() == cow, NOT_AIMED);
        strike(helper, cow, zombie);
        helper.runAfterDelay(1, () -> helper.assertFalse(zombie.getTarget() == cow, STILL_AIMED));
        helper.runAfterDelay(PAST_FIVE_SECONDS_TICKS, () -> {
            zombie.setTarget(cow);
            helper.assertFalse(zombie.getTarget() == cow, AIMED_AGAIN);
            helper.succeed();
        });
    }

    /**
     * A lethal zombie attack downs a rallied cow, alive and holding DOWNED,
     * and kills a plain cow.
     *
     * @param helper the gametest helper
     */
    public static void lethalAttackDownsARalliedCow(GameTestHelper helper) {
        Zombie zombie = attacker(helper);
        Cow rallied = ralliedCow(helper);
        Cow plain = helper.spawnWithNoFreeWill(EntityType.COW, PLAIN_COW_POS);

        strike(helper, rallied, zombie);
        strike(helper, plain, zombie);

        helper.assertTrue(rallied.isAlive(), RALLIED_COW_DIED);
        helper.assertTrue(rallied.hasData(GooAttachments.DOWNED), NOT_DOWNED);
        helper.assertFalse(plain.isAlive(), PLAIN_COW_LIVED);
        helper.succeed();
    }

    /**
     * A downed cow struck lethally again by a zombie past 5 seconds down
     * stays alive, holds DOWNED and keeps the negative health it held
     * (decision no-attack-lands-on-a-downed-mob).
     *
     * @param helper the gametest helper
     */
    public static void attackPastFiveSecondsLandsNothing(GameTestHelper helper) {
        Zombie zombie = attacker(helper);
        Cow cow = ralliedCow(helper);
        strike(helper, cow, zombie);
        helper.runAfterDelay(PAST_FIVE_SECONDS_TICKS, () -> {
            float before = cow.getData(GooAttachments.DOWNED).negativeHealth();
            strike(helper, cow, zombie);
            helper.assertTrue(cow.isAlive(), ATTACK_KILLED);
            helper.assertTrue(cow.hasData(GooAttachments.DOWNED), ATTACK_RAISED);
            float after = cow.getData(GooAttachments.DOWNED).negativeHealth();
            helper.assertTrue(after == before, String.format(ATTACK_MOVED_THE_COUNT, before, after));
            helper.succeed();
        });
    }

    /**
     * Lava damage on a cow downed past 5 seconds kills it
     * (decision no-attack-lands-on-a-downed-mob).
     *
     * @param helper the gametest helper
     */
    public static void lavaKillsACowDownPastFiveSeconds(GameTestHelper helper) {
        Zombie zombie = attacker(helper);
        Cow cow = ralliedCow(helper);
        strike(helper, cow, zombie);
        helper.assertTrue(cow.hasData(GooAttachments.DOWNED), NOT_DOWNED);
        helper.runAfterDelay(PAST_FIVE_SECONDS_TICKS, () -> {
            cow.hurtServer(helper.getLevel(), cow.damageSources().lava(), LETHAL);
            helper.assertFalse(cow.isAlive(), LAVA_SPARED);
            helper.succeed();
        });
    }

    /**
     * Lethal lava damage on a rallied cow, never downed, kills it and lays no DOWNED.
     *
     * @param helper the gametest helper
     */
    public static void lavaKillsARalliedCowOutright(GameTestHelper helper) {
        Cow cow = ralliedCow(helper);
        cow.hurtServer(helper.getLevel(), cow.damageSources().lava(), LETHAL);
        helper.assertFalse(cow.isAlive(), LAVA_SPARED);
        helper.assertFalse(cow.hasData(GooAttachments.DOWNED), DOWNED_BY_LAVA);
        helper.succeed();
    }

    /**
     * A downed cow left unhealed is alive and downed at 200 ticks and dead
     * past 300; with its rally taken off right after it went down, the same.
     *
     * @param helper        the gametest helper
     * @param removeTheRally whether the rally is taken off right after the cow goes down
     */
    public static void cowDiesAtFifteenSeconds(GameTestHelper helper, boolean removeTheRally) {
        Zombie zombie = attacker(helper);
        Cow cow = ralliedCow(helper);
        strike(helper, cow, zombie);
        if (removeTheRally) {
            cow.removeData(GooAttachments.RALLIED);
        }
        helper.runAfterDelay(STILL_DOWN_TICKS, () -> {
            helper.assertTrue(cow.isAlive(), DIED_TOO_SOON);
            helper.assertTrue(cow.hasData(GooAttachments.DOWNED), DIED_TOO_SOON);
        });
        helper.runAfterDelay(PAST_THE_DEATH_TICKS, () -> {
            helper.assertFalse(cow.isAlive(), OUTLIVED_THE_SPAN);
            helper.succeed();
        });
    }

    /**
     * A cow holding a rally for a caster absent from the level, so the
     * rally's own tick leaves it be.
     *
     * @param helper the gametest helper
     * @return the rallied cow
     */
    static Cow ralliedCow(GameTestHelper helper) {
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        cow.setData(GooAttachments.RALLIED, new Rallied(UUID.randomUUID(), 1f));
        return cow;
    }

    /**
     * A still zombie to deal the attacks, its helmet keeping the open bay's sun off it.
     *
     * @param helper the gametest helper
     * @return the zombie
     */
    static Zombie attacker(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setNoAi(true);
        return zombie;
    }

    static void strike(GameTestHelper helper, Cow cow, Zombie zombie) {
        cow.hurtServer(helper.getLevel(), zombie.damageSources().mobAttack(zombie), LETHAL);
    }
}
