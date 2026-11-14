package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for Zoo's Rally: the one peaceful mob a thrown Rally lands on
 * takes the rally, strikes the mob that hurt the caster, and holds the rally
 * until its rallier strikes it
 * (decisions zoo-rally-arms-the-peaceful, rally-is-a-mob-effect-on-one-target).
 */
@RegistersGameTests
public final class RallyTests {
    private static final String RALLY_COW_ATTACKS_THE_ZOMBIE = "rally_cow_attacks_the_zombie";
    private static final String RALLY_SURVIVES_A_RELOAD = "rally_survives_a_reload";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, RALLY_COW_ATTACKS_THE_ZOMBIE, RallyTests::rallyCowAttacksTheZombie);
        GooTestFunctions.registerTest(registrar, RALLY_SURVIVES_A_RELOAD, RallyTests::rallySurvivesAReload);
        GooTestFunctions.registerTest(registrar, RALLY_COW_STRIKES_NOTHING_THROUGH_A_WALL,
                RallyTests::rallyCowStrikesNothingThroughAWall);
        GooTestFunctions.registerTest(registrar, RALLY_TAKES_ONE_OF_TWO_COWS, RallyTests::rallyTakesOneOfTwoCows);
        GooTestFunctions.registerTest(registrar, RALLY_HOLDS_PAST_THE_OLD_RALLY, RallyTests::rallyHoldsPastTheOldRally);
        GooTestFunctions.registerTest(registrar, RALLY_ENDS_ON_THE_RALLIERS_HIT, RallyTests::rallyEndsOnTheRalliersHit);
        GooTestFunctions.registerTest(registrar, RALLY_COW_TAKES_THE_SIDE_ITS_RALLIER_WALKS_TOWARD,
                RallyTests::rallyCowTakesTheSideItsRallierWalksToward);
    }

    private static final String RALLY_COW_TAKES_THE_SIDE_ITS_RALLIER_WALKS_TOWARD =
            "rally_cow_takes_the_side_its_rallier_walks_toward";
    /** Where the walking rallier starts, at the bay's west edge. */
    private static final Vec3 WALK_START = new Vec3(0.5, 1.0, 1.5);
    /** Where the walking rallier ends, at the bay's east edge. */
    private static final Vec3 WALK_END = new Vec3(5.5, 1.0, 1.5);
    /** The rallied cow's start, two blocks to the side of the rallier's start. */
    private static final BlockPos BESIDE_THE_WALK_POS = new BlockPos(0, 1, 3);
    /**
     * The rallier's pace in blocks a tick: above the standing speed, and slow
     * enough that the cow has most of the walk to get ahead inside the bay.
     */
    private static final double RALLIER_PACE = 0.04;
    /** How far along the walk past the rallier the cow must stand to count as ahead of it. */
    private static final double AHEAD_MARGIN = 1.0;
    private static final String COW_NOT_AHEAD =
            "The rallied cow should stand %s blocks past its walking rallier, stands %s past it";

    /**
     * A mock rallier walks steadily east beside a rallied cow with no foe
     * near, and the cow walks out ahead of it along the walk, to the side the
     * rallier heads toward
     * (decision unengaged-minions-move-where-you-move).
     *
     * @param helper the gametest helper
     */
    public static void rallyCowTakesTheSideItsRallierWalksToward(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Vec3 start = helper.absoluteVec(WALK_START);
        Vec3 end = helper.absoluteVec(WALK_END);
        // the bay may stand rotated, so the walk's world heading is read from its world ends
        Vec3 heading = end.subtract(start).normalize();
        caster.setPos(start);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, BESIDE_THE_WALK_POS);
        rally(helper, cow, caster);
        int[] walked = new int[1];
        helper.onEachTick(() -> {
            Vec3 step = start.add(heading.scale(RALLIER_PACE * walked[0]++));
            caster.setPos(step.distanceTo(start) < end.distanceTo(start) ? step : end);
        });
        helper.succeedWhen(() -> {
            double ahead = cow.position().subtract(caster.position()).dot(heading);
            helper.assertTrue(ahead > AHEAD_MARGIN, String.format(COW_NOT_AHEAD, AHEAD_MARGIN, ahead));
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }

    private static final String RALLY_TAKES_ONE_OF_TWO_COWS = "rally_takes_one_of_two_cows";
    private static final String RALLY_HOLDS_PAST_THE_OLD_RALLY = "rally_holds_past_the_old_rally";
    private static final String RALLY_ENDS_ON_THE_RALLIERS_HIT = "rally_ends_on_the_ralliers_hit";
    /** The 100 ticks a rally lasted as a durative area, and a span past them. */
    private static final int PAST_THE_OLD_RALLY_TICKS = 100 + 50;
    /** A cow's own max health. */
    private static final float COW_MAX_HEALTH = 10f;
    /** A hit too light to kill a rallied cow, so the rally, not the cow, is what ends. */
    private static final float LIGHT_HIT = 1f;
    /** The block beside the targeted cow, where the second cow stands. */
    private static final BlockPos BESIDE_COW_POS = new BlockPos(1, 1, 3);
    private static final String TARGET_NOT_RALLIED = "The cow Rally landed on should hold the rally";
    private static final String BYSTANDER_RALLIED = "The cow beside the target should hold no rally";
    private static final String RALLY_LAPSED = "The cow should hold the rally past 100 ticks with nothing held";
    private static final String RALLY_SURVIVED_THE_HIT = "The rallier's hit should end the rally";
    private static final String STILL_BUFFED = "The struck cow's max health should be a cow's 10, stands %s";
    private static final String ABILITIES_REQUIRED = "zoo_rally should load";


    private static final Identifier ZOO_RALLY = Identifier.fromNamespaceAndPath(Goo.MODID, "zoo_rally");
    private static final BlockPos COW_POS = new BlockPos(2, 1, 3);
    private static final BlockPos ZOMBIE_POS = new BlockPos(3, 1, 3);
    /** The hit the zombie lands on the caster, which makes it the caster's enemy. */
    private static final float ZOMBIE_HIT = 1f;

    private static final String COW_NOT_RALLIED = "The cow near the caster should hold the rally";
    private static final String ZOMBIE_UNHURT = "The rallied cow should strike the zombie that hurt the caster";
    private static final String COW_NOT_BUFFED = "The rallied cow's max health should rise past a cow's 10, stands %s";

    private static final String RALLY_COW_STRIKES_NOTHING_THROUGH_A_WALL = "rally_cow_strikes_nothing_through_a_wall";
    /** The wall's block, between the cow's and the zombie's. */
    private static final BlockPos WALL_POS = new BlockPos(3, 1, 3);
    /** The cow's centre off the wall block's west edge, its side against the post spanning 0.25 to 0.75. */
    private static final double COW_OFF_THE_POST = 0.25 - 0.46;
    /** The zombie's centre off the wall block's west edge, its side against the post's east face. */
    private static final double ZOMBIE_OFF_THE_POST = 0.75 + 0.31;
    private static final double BLOCK_CENTER = 0.5;
    /** Ticks for a cow in range to have struck several times over at 15 ticks a strike. */
    private static final int THROUGH_THE_WALL_TICKS = 40;
    private static final String COW_OUT_OF_RANGE = "The cow and zombie should stand in melee range across the wall";
    private static final String STRUCK_THROUGH_THE_WALL =
            "The zombie across the wall took %s, last from %s, by the cow: %s";

    private static final String RELOADED_LOST = "The saved cow should load back as a mob";
    private static final String RELOADED_UNRALLIED = "The reloaded cow should still hold the rally";
    private static final String RELOADED_UNBUFFED =
            "The reloaded cow's max health should still rise past a cow's 10, stands %s";

    private RallyTests() {
    }

    /**
     * A zombie hurts the caster, the caster throws Rally at the cow beside
     * the zombie, and the cow takes the rally and strikes it.
     *
     * @param helper the gametest helper
     */
    public static void rallyCowAttacksTheZombie(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        // the mock player helper makes a creative player, whom no hit lands on
        caster.setGameMode(GameType.SURVIVAL);
        // a player whose client has not reported loaded is invulnerable, and no mock client reports
        caster.connection.markClientLoaded();
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        // the bay stands open to the sky, and a bare-headed zombie's sunburn would pass for the cow's strike
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        caster.hurtServer(helper.getLevel(), zombie.damageSources().mobAttack(zombie), ZOMBIE_HIT);

        rally(helper, cow, caster);

        helper.succeedWhen(() -> {
            helper.assertTrue(cow.hasData(GooAttachments.RALLIED), COW_NOT_RALLIED);
            helper.assertTrue(cow.getMaxHealth() > COW_MAX_HEALTH,
                    String.format(COW_NOT_BUFFED, cow.getMaxHealth()));
            helper.assertTrue(zombie.getMaxHealth() - zombie.getHealth() > 0, ZOMBIE_UNHURT);
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }

    /**
     * A rallied cow and the zombie that hurt the caster stand within melee
     * range of each other on either side of a cobblestone wall two blocks
     * high, and the cow lands no strike through it, the wall blocking its
     * line of sight (decision rallied-mobs-follow-the-player-who-rallied-them).
     *
     * @param helper the gametest helper
     */
    public static void rallyCowStrikesNothingThroughAWall(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        caster.setGameMode(GameType.SURVIVAL);
        caster.connection.markClientLoaded();
        helper.setBlock(WALL_POS, Blocks.COBBLESTONE_WALL);
        helper.setBlock(WALL_POS.above(), Blocks.COBBLESTONE_WALL);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        // the bay stands open to the sky, so a bare-headed zombie burns and its health reads more than strikes
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        // placed from the wall's world block on the world X axis, since the bay may stand rotated
        BlockPos wall = helper.absolutePos(WALL_POS);
        cow.setPos(wall.getX() + COW_OFF_THE_POST, wall.getY(), wall.getZ() + BLOCK_CENTER);
        // a cow with no free will still walks its path, and would round the post to strike in the open
        cow.setNoAi(true);
        zombie.setPos(wall.getX() + ZOMBIE_OFF_THE_POST, wall.getY(), wall.getZ() + BLOCK_CENTER);
        caster.hurtServer(helper.getLevel(), zombie.damageSources().mobAttack(zombie), ZOMBIE_HIT);
        helper.assertTrue(cow.isWithinMeleeAttackRange(zombie), COW_OUT_OF_RANGE);

        rally(helper, cow, caster);

        helper.runAfterDelay(THROUGH_THE_WALL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(cow.hasData(GooAttachments.RALLIED), COW_NOT_RALLIED);
            helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(),
                    String.format(STRUCK_THROUGH_THE_WALL, zombie.getMaxHealth() - zombie.getHealth(),
                            zombie.getLastDamageSource() == null ? "none" : zombie.getLastDamageSource().getMsgId(),
                            zombie.getLastHurtByMob() == cow));
            helper.succeed();
        });
    }

    /**
     * A rallied cow saved the way a chunk unload saves it and loaded back
     * keeps the rally and its buff
     * (decision zoo-rally-arms-the-peaceful).
     *
     * @param helper the gametest helper
     */
    public static void rallySurvivesAReload(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        rally(helper, cow, caster);
        Mob[] reloaded = new Mob[1];
        helper.succeedWhen(() -> {
            if (reloaded[0] == null) {
                helper.assertTrue(cow.hasData(GooAttachments.RALLIED), COW_NOT_RALLIED);
                reloaded[0] = reload(helper, cow);
            }
            helper.assertTrue(reloaded[0].hasData(GooAttachments.RALLIED), RELOADED_UNRALLIED);
            helper.assertTrue(reloaded[0].getMaxHealth() > COW_MAX_HEALTH,
                    String.format(RELOADED_UNBUFFED, reloaded[0].getMaxHealth()));
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }

    /**
     * Rally thrown at one of two adjacent cows rallies that cow and leaves
     * the other unrallied (decision rally-is-a-mob-effect-on-one-target).
     *
     * @param helper the gametest helper
     */
    public static void rallyTakesOneOfTwoCows(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        Cow bystander = helper.spawnWithNoFreeWill(EntityType.COW, BESIDE_COW_POS);

        rally(helper, cow, caster);

        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.assertTrue(cow.hasData(GooAttachments.RALLIED), TARGET_NOT_RALLIED);
        helper.assertFalse(bystander.hasData(GooAttachments.RALLIED), BYSTANDER_RALLIED);
        helper.succeed();
    }

    /**
     * A rallied cow still holds the rally past the 100 ticks the
     * area rally lasted, its caster holding nothing and casting nothing again
     * (decision rally-is-a-mob-effect-on-one-target).
     *
     * @param helper the gametest helper
     */
    public static void rallyHoldsPastTheOldRally(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        rally(helper, cow, caster);
        helper.runAfterDelay(PAST_THE_OLD_RALLY_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(cow.hasData(GooAttachments.RALLIED), RALLY_LAPSED);
            helper.succeed();
        });
    }

    /**
     * The rallier's own hit on its rallied cow ends the rally and takes its
     * buff off: no rally, a cow's own max health
     * (decision rally-is-a-mob-effect-on-one-target).
     *
     * @param helper the gametest helper
     */
    public static void rallyEndsOnTheRalliersHit(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        rally(helper, cow, caster);
        helper.assertTrue(cow.hasData(GooAttachments.RALLIED), COW_NOT_RALLIED);

        cow.hurtServer(helper.getLevel(), cow.damageSources().playerAttack(caster), LIGHT_HIT);

        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.assertFalse(cow.hasData(GooAttachments.RALLIED), RALLY_SURVIVED_THE_HIT);
        helper.assertTrue(cow.getMaxHealth() == COW_MAX_HEALTH, String.format(STILL_BUFFED, cow.getMaxHealth()));
        helper.succeed();
    }

    /**
     * Lands one Rally goo on a mob as the caster's throw, through the
     * scheduler a thrown goo's impact runs.
     *
     * @param helper the gametest helper
     * @param mob    the mob struck
     * @param caster the throwing player
     */
    private static void rally(GameTestHelper helper, Mob mob, ServerPlayer caster) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(ZOO_RALLY);
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), caster, ability.gooType(),
                mob.getId(), mob.blockPosition(), Direction.UP, ZOO_RALLY.toString()));
    }

    /**
     * Saves a mob the way a chunk unload saves it, discards it and loads the
     * save back into the level.
     *
     * @param helper the gametest helper
     * @param mob    the mob saved
     * @return the mob loaded from the save
     */
    private static Mob reload(GameTestHelper helper, Mob mob) {
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess());
        mob.save(saved);
        mob.discard();
        Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), saved.buildResult()), helper.getLevel(), EntitySpawnReason.LOAD,
                entity -> entity);
        helper.assertTrue(loaded instanceof Mob, RELOADED_LOST);
        helper.getLevel().addFreshEntity(loaded);
        return (Mob) loaded;
    }
}
