package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for Zoo's Rally: a peaceful mob near the caster takes the rally
 * and strikes the mob that hurt the caster
 * (decision zoo-rally-arms-the-peaceful).
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
        GooTestFunctions.registerTest(registrar, RALLY_TAKES_A_LATECOMER, RallyTests::rallyTakesALatecomer);
        GooTestFunctions.registerTest(registrar, RALLY_HOLDS_PAST_THE_OLD_RALLY, RallyTests::rallyHoldsPastTheOldRally);
        GooTestFunctions.registerTest(registrar, RALLY_STANDS_DOWN_WITH_THE_BREW,
                RallyTests::rallyStandsDownWithTheBrew);
    }

    private static final String RALLY_TAKES_A_LATECOMER = "rally_takes_a_latecomer";
    private static final String RALLY_HOLDS_PAST_THE_OLD_RALLY = "rally_holds_past_the_old_rally";
    private static final String RALLY_STANDS_DOWN_WITH_THE_BREW = "rally_stands_down_with_the_brew";
    /** The 600 ticks a rally lasted before it held for the brew, and a refresh's span past them. */
    private static final int PAST_THE_OLD_RALLY_TICKS = 600 + 50;
    /** A cow's own max health. */
    private static final float COW_MAX_HEALTH = 10f;
    private static final String LATECOMER_NOT_RALLIED = "A cow arriving after the eat should take the rally";
    private static final String RALLY_LAPSED = "The cow should hold the rally past 600 ticks while the brew holds";
    private static final String RALLY_STOOD = "The cow should stand down the tick the brew ends";
    private static final String STILL_BUFFED = "The stood-down cow's max health should be a cow's 10, stands %s";


    private static final Identifier ZOO_RALLY = Identifier.fromNamespaceAndPath(Goo.MODID, "zoo_rally");
    private static final BlockPos COW_POS = new BlockPos(2, 1, 3);
    private static final BlockPos ZOMBIE_POS = new BlockPos(3, 1, 3);
    /** The hit the zombie lands on the caster, which makes it the caster's enemy. */
    private static final float ZOMBIE_HIT = 1f;

    private static final String COW_NOT_RALLIED = "The cow near the caster should hold the rally";
    private static final String ZOMBIE_UNHURT = "The rallied cow should strike the zombie that hurt the caster";
    private static final String COW_NOT_BUFFED = "The rallied cow's max health should be zoo_rally.json's 2.5 times, stands %s";
    /** zoo_rally.json's damage of six, under what a zombie's two armor points take off it. */
    private static final float LEAST_STRIKE = 5f;
    /** A cow's max health of ten, lifted by zoo_rally.json's health share of 1.5. */
    private static final float RALLIED_COW_MAX_HEALTH = 25f;

    private static final String RELOADED_LOST = "The saved cow should load back as a mob";
    private static final String RELOADED_UNRALLIED = "The reloaded cow should still hold the rally";
    private static final String RELOADED_UNBUFFED =
            "The reloaded cow's max health should still be zoo_rally.json's 2.5 times, stands %s";

    private RallyTests() {
    }

    /**
     * A zombie hurts the caster, the caster casts Rally, and the cow beside
     * the zombie takes the rally and strikes it.
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
        caster.hurtServer(helper.getLevel(), zombie.damageSources().mobAttack(zombie), ZOMBIE_HIT);

        HeartOverlayTests.invoke(caster, GooTypes.ZOO, ZOO_RALLY);

        helper.succeedWhen(() -> {
            helper.assertTrue(cow.hasData(GooAttachments.RALLIED), COW_NOT_RALLIED);
            helper.assertTrue(cow.getMaxHealth() == RALLIED_COW_MAX_HEALTH,
                    String.format(COW_NOT_BUFFED, cow.getMaxHealth()));
            helper.assertTrue(zombie.getMaxHealth() - zombie.getHealth() >= LEAST_STRIKE, ZOMBIE_UNHURT);
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }

    /**
     * A rallied cow saved the way a chunk unload saves it and loaded back
     * keeps the rally and its buff for the rally's whole time
     * (decision zoo-rally-arms-the-peaceful).
     *
     * @param helper the gametest helper
     */
    public static void rallySurvivesAReload(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        HeartOverlayTests.invoke(caster, GooTypes.ZOO, ZOO_RALLY);
        Mob[] reloaded = new Mob[1];
        helper.succeedWhen(() -> {
            if (reloaded[0] == null) {
                helper.assertTrue(cow.hasData(GooAttachments.RALLIED), COW_NOT_RALLIED);
                reloaded[0] = reload(helper, cow);
            }
            helper.assertTrue(reloaded[0].hasData(GooAttachments.RALLIED), RELOADED_UNRALLIED);
            helper.assertTrue(reloaded[0].getMaxHealth() == RALLIED_COW_MAX_HEALTH,
                    String.format(RELOADED_UNBUFFED, reloaded[0].getMaxHealth()));
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }

    /**
     * A cow spawned near the caster only after the eat takes the rally and its
     * buff while the brew holds (decision zoo-rally-holds-for-the-brew).
     *
     * @param helper the gametest helper
     */
    public static void rallyTakesALatecomer(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        HeartOverlayTests.invoke(caster, GooTypes.ZOO, ZOO_RALLY);
        helper.onEachTick(caster::doTick);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        helper.succeedWhen(() -> {
            helper.assertTrue(cow.hasData(GooAttachments.RALLIED), LATECOMER_NOT_RALLIED);
            helper.assertTrue(cow.getMaxHealth() == RALLIED_COW_MAX_HEALTH,
                    String.format(COW_NOT_BUFFED, cow.getMaxHealth()));
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }

    /**
     * A rallied cow still holds the rally and its buff past the 600 ticks the
     * rally formerly lasted, the brew holding throughout
     * (decision zoo-rally-holds-for-the-brew).
     *
     * @param helper the gametest helper
     */
    public static void rallyHoldsPastTheOldRally(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        HeartOverlayTests.invoke(caster, GooTypes.ZOO, ZOO_RALLY);
        helper.onEachTick(caster::doTick);
        helper.runAfterDelay(PAST_THE_OLD_RALLY_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(cow.hasData(GooAttachments.RALLIED), RALLY_LAPSED);
            helper.assertTrue(cow.getMaxHealth() == RALLIED_COW_MAX_HEALTH,
                    String.format(COW_NOT_BUFFED, cow.getMaxHealth()));
            helper.succeed();
        });
    }

    /**
     * The caster invokes Rally again to end the brew, and the cow it rallied
     * stands down at once: no rally, a cow's own max health
     * (decision zoo-rally-holds-for-the-brew).
     *
     * @param helper the gametest helper
     */
    public static void rallyStandsDownWithTheBrew(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        HeartOverlayTests.invoke(caster, GooTypes.ZOO, ZOO_RALLY);
        helper.assertTrue(cow.hasData(GooAttachments.RALLIED), COW_NOT_RALLIED);

        SelfDeliveryTests.invoke(caster, GooTypes.ZOO, ZOO_RALLY);

        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.assertFalse(cow.hasData(GooAttachments.RALLIED), RALLY_STOOD);
        helper.assertTrue(cow.getMaxHealth() == COW_MAX_HEALTH, String.format(STILL_BUFFED, cow.getMaxHealth()));
        helper.succeed();
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
