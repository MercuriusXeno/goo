package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.entity.NectarPile;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooEntities;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.AABB;
import java.util.List;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for Jelly's Nectar through {@link GooEffectScheduler#applyEffect}: a
 * blob striking an adult cow sets it in love, one striking a calf grows it to
 * an adult, one landing on the ground lays a nectar that draws a cow from across
 * the bay to eat it, and two zombies reaching a nectar together turn on each other
 * over it (decision feed-blob-feeds-and-draws-mobs). A slime struck by nectar, or
 * eating a laid one, grows a size step (decision slimes-grow-on-jelly-feed).
 */
@RegistersGameTests
public final class NectarTests {
    private static final String NECTAR_COURTS_A_COW = "nectar_courts_a_cow";
    private static final String NECTAR_ON_THE_GROUND_STARTS_A_FIGHT = "nectar_on_the_ground_starts_a_fight";
    private static final String NECTAR_ON_THE_GROUND_DRAWS_A_MOB = "nectar_on_the_ground_draws_a_mob";
    private static final String NECTAR_GROWS_A_SLIME = "nectar_grows_a_slime";
    private static final String SLIME_EATS_LAID_NECTAR = "slime_eats_laid_nectar";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, NECTAR_COURTS_A_COW, NectarTests::nectarCourtsACow);
        GooTestFunctions.registerTest(registrar, NECTAR_ON_THE_GROUND_STARTS_A_FIGHT, NectarTests::nectarOnTheGroundStartsAFight);
        GooTestFunctions.registerTest(registrar, NECTAR_ON_THE_GROUND_DRAWS_A_MOB, NectarTests::nectarOnTheGroundDrawsAMob);
        GooTestFunctions.registerTest(registrar, NECTAR_GROWS_A_SLIME, NectarTests::nectarGrowsASlime);
        GooTestFunctions.registerTest(registrar, SLIME_EATS_LAID_NECTAR, NectarTests::slimeEatsLaidNectar);
    }


    private static final String NECTAR = "goo:jelly_nectar";
    private static final int NO_ENTITY = -1;
    private static final BlockPos FLOOR = new BlockPos(2, 0, 2);
    /** The bay's corner floor the drawn nectar lands on, and the far corner the cow starts from, seven blocks off. */
    private static final BlockPos NEAR_CORNER = new BlockPos(0, 0, 0);
    private static final BlockPos FAR_CORNER = new BlockPos(5, 1, 5);
    /** Ticks for a laid nectar to settle and run a tick of reaching. */
    private static final int SETTLE_TICKS = 5;
    private static final double SEARCH = 3.0;
    /** Blocks about the near corner a nectar lies within, the whole bay. */
    private static final double FAR_REACH = 8.0;
    private static final String SHOULD_LOVE = "A fed adult cow should be in love";
    private static final String SHOULD_LAY = "A Nectar blob landing on the ground should lay a nectar";
    private static final String SHOULD_START_OUT_OF_REACH = "The cow should start out of the nectar's reach, %.2f off";
    private static final String SHOULD_DRAW = "The nectar should draw the cow to eat it: cow %.2f off, in love %b, piles left %d, heading to %s, panicking %b";
    private static final String SHOULD_FIGHT = "Two zombies at one nectar should target each other: %s and %s";
    private static final String SHOULD_GROW_SLIME = "A fed slime of size 1 should grow to size 2, stands at %d";
    private static final String SHOULD_EAT_AND_GROW = "The slime should eat the nectar and grow to size 2: piles left %d, size %d";

    private NectarTests() {
    }

    /**
     * Nectar strikes an adult cow: the cow is in love, as fed wheat would leave it.
     *
     * @param helper the gametest helper
     */
    public static void nectarCourtsACow(GameTestHelper helper) {
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, FLOOR.above());
        helper.runAfterDelay(1, () -> {
            strike(helper, cow);
            helper.assertTrue(cow.isInLove(), SHOULD_LOVE);
            helper.succeed();
        });
    }

    /**
     * Nectar lands on the ground beside two zombies: a nectar lies there, and the
     * zombies reaching it together turn on each other.
     *
     * @param helper the gametest helper
     */
    public static void nectarOnTheGroundStartsAFight(GameTestHelper helper) {
        Mob first = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, FLOOR.above().east());
        Mob second = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, FLOOR.above().west());
        helper.runAfterDelay(1, () -> GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null,
                GooTypes.JELLY, NO_ENTITY, helper.absolutePos(FLOOR), Direction.UP, NECTAR)));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            List<NectarPile> piles = helper.getLevel().getEntities(GooEntities.NECTAR_PILE.get(),
                    new AABB(helper.absolutePos(FLOOR)).inflate(SEARCH), NectarPile::isAlive);
            helper.assertFalse(piles.isEmpty(), SHOULD_LAY);
            helper.assertTrue(first.getTarget() == second && second.getTarget() == first,
                    String.format(SHOULD_FIGHT, first.getTarget(), second.getTarget()));
            helper.succeed();
        });
    }

    /**
     * Nectar lands on the ground in one corner of the bay while a cow with its
     * AI stands in the far corner, out of the nectar's reach and inside its
     * radius: the cow walks to the nectar and eats it, falling in love.
     *
     * @param helper the gametest helper
     */
    public static void nectarOnTheGroundDrawsAMob(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, FAR_CORNER);
        double start = cow.position().distanceTo(helper.absoluteVec(NEAR_CORNER.above().getBottomCenter()));
        helper.assertTrue(start > NectarPile.REACH, String.format(SHOULD_START_OUT_OF_REACH, start));
        helper.runAfterDelay(1, () -> GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null,
                GooTypes.JELLY, NO_ENTITY, helper.absolutePos(NEAR_CORNER), Direction.UP, NECTAR)));
        helper.succeedWhen(() -> {
            int left = helper.getLevel().getEntities(GooEntities.NECTAR_PILE.get(),
                    new AABB(helper.absolutePos(NEAR_CORNER)).inflate(FAR_REACH), NectarPile::isAlive).size();
            double off = cow.position().distanceTo(helper.absoluteVec(NEAR_CORNER.above().getBottomCenter()));
            helper.assertTrue(left == 0 && cow.isInLove(), String.format(SHOULD_DRAW, off, cow.isInLove(), left,
                    cow.getNavigation().getTargetPos(), cow.isPanicking()));
        });
    }

    /**
     * Nectar strikes a size-1 slime: it grows to 2 (decision slimes-grow-on-jelly-feed).
     *
     * @param helper the gametest helper
     */
    public static void nectarGrowsASlime(GameTestHelper helper) {
        Slime slime = helper.spawnWithNoFreeWill(EntityType.SLIME, FLOOR.above());
        slime.setSize(1, true);
        helper.runAfterDelay(1, () -> {
            strike(helper, slime);
            helper.assertTrue(slime.getSize() == 2, String.format(SHOULD_GROW_SLIME, slime.getSize()));
            helper.succeed();
        });
    }

    /**
     * Nectar lands on the ground beside a size-1 slime: the slime eats the laid
     * feed and grows to size 2 (decision slimes-grow-on-jelly-feed).
     *
     * @param helper the gametest helper
     */
    public static void slimeEatsLaidNectar(GameTestHelper helper) {
        Slime slime = helper.spawnWithNoFreeWill(EntityType.SLIME, FLOOR.above());
        slime.setSize(1, true);
        helper.runAfterDelay(1, () -> GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null,
                GooTypes.JELLY, NO_ENTITY, helper.absolutePos(FLOOR), Direction.UP, NECTAR)));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            int left = helper.getLevel().getEntities(GooEntities.NECTAR_PILE.get(),
                    new AABB(helper.absolutePos(FLOOR)).inflate(SEARCH), NectarPile::isAlive).size();
            helper.assertTrue(left == 0 && slime.getSize() == 2,
                    String.format(SHOULD_EAT_AND_GROW, left, slime.getSize()));
            helper.succeed();
        });
    }

    private static void strike(GameTestHelper helper, Mob struck) {
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.JELLY,
                struck.getId(), struck.blockPosition(), Direction.UP, NECTAR));
    }
}
