package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.entity.FeedPile;
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
 * Gametests for Jelly Feed through {@link GooEffectScheduler#applyEffect}: a
 * blob striking an adult cow sets it in love, one striking a calf grows it to
 * an adult, one landing on the ground lays a feed that draws a cow from across
 * the bay to eat it, and two zombies reaching a feed together turn on each other
 * over it (decision feed-blob-feeds-and-draws-mobs). A slime struck by feed, or
 * eating a laid one, grows a size step (decision slimes-grow-on-jelly-feed).
 */
@RegistersGameTests
public final class FeedTests {
    private static final String FEED_COURTS_A_COW = "feed_courts_a_cow";
    private static final String FEED_GROWS_A_CALF = "feed_grows_a_calf";
    private static final String FEED_ON_THE_GROUND_STARTS_A_FIGHT = "feed_on_the_ground_starts_a_fight";
    private static final String FEED_ON_THE_GROUND_DRAWS_A_MOB = "feed_on_the_ground_draws_a_mob";
    private static final String FEED_GROWS_A_SLIME = "feed_grows_a_slime";
    private static final String SLIME_EATS_A_LAID_FEED = "slime_eats_a_laid_feed";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, FEED_COURTS_A_COW, FeedTests::feedCourtsACow);
        GooTestFunctions.registerTest(registrar, FEED_GROWS_A_CALF, FeedTests::feedGrowsACalf);
        GooTestFunctions.registerTest(registrar, FEED_ON_THE_GROUND_STARTS_A_FIGHT, FeedTests::feedOnTheGroundStartsAFight);
        GooTestFunctions.registerTest(registrar, FEED_ON_THE_GROUND_DRAWS_A_MOB, FeedTests::feedOnTheGroundDrawsAMob);
        GooTestFunctions.registerTest(registrar, FEED_GROWS_A_SLIME, FeedTests::feedGrowsASlime);
        GooTestFunctions.registerTest(registrar, SLIME_EATS_A_LAID_FEED, FeedTests::slimeEatsALaidFeed);
    }


    private static final String FEED = "goo:jelly_feed";
    private static final int NO_ENTITY = -1;
    private static final BlockPos FLOOR = new BlockPos(2, 0, 2);
    /** The bay's corner floor the drawn feed lands on, and the far corner the cow starts from, seven blocks off. */
    private static final BlockPos NEAR_CORNER = new BlockPos(0, 0, 0);
    private static final BlockPos FAR_CORNER = new BlockPos(5, 1, 5);
    /** Ticks for a laid feed to settle and run a tick of reaching. */
    private static final int SETTLE_TICKS = 5;
    private static final double SEARCH = 3.0;
    /** Blocks about the near corner a feed lies within, the whole bay. */
    private static final double FAR_REACH = 8.0;
    private static final String SHOULD_LOVE = "A fed adult cow should be in love";
    private static final String SHOULD_GROW = "A fed calf should grow to an adult, age %d";
    private static final String SHOULD_LAY = "A feed blob landing on the ground should lay a feed";
    private static final String SHOULD_START_OUT_OF_REACH = "The cow should start out of the feed's reach, %.2f off";
    private static final String SHOULD_DRAW = "The feed should draw the cow to eat it: cow %.2f off, in love %b, feeds left %d, heading to %s, panicking %b";
    private static final String SHOULD_FIGHT = "Two zombies at one feed should target each other: %s and %s";
    private static final String SHOULD_GROW_SLIME = "Feed number %d should leave the slime size %d, not %d";
    private static final String SHOULD_EAT_AND_GROW = "The slime should eat the feed and grow to size 2: feeds left %d, size %d";
    /** The slime's size after each of three feeds: one step, a second, then the cap. */
    private static final int[] SLIME_SIZES_FED = {2, 4, 4};

    private FeedTests() {
    }

    /**
     * Feed strikes an adult cow: the cow is in love, as fed wheat would leave it.
     *
     * @param helper the gametest helper
     */
    public static void feedCourtsACow(GameTestHelper helper) {
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, FLOOR.above());
        helper.runAfterDelay(1, () -> {
            strike(helper, cow);
            helper.assertTrue(cow.isInLove(), SHOULD_LOVE);
            helper.succeed();
        });
    }

    /**
     * Feed strikes a calf: the calf grows to an adult.
     *
     * @param helper the gametest helper
     */
    public static void feedGrowsACalf(GameTestHelper helper) {
        Cow calf = helper.spawnWithNoFreeWill(EntityType.COW, FLOOR.above());
        calf.setBaby(true);
        helper.runAfterDelay(1, () -> {
            strike(helper, calf);
            helper.assertTrue(!calf.isBaby(), String.format(SHOULD_GROW, calf.getAge()));
            helper.succeed();
        });
    }

    /**
     * Feed lands on the ground beside two zombies: a feed lies there, and the
     * zombies reaching it together turn on each other.
     *
     * @param helper the gametest helper
     */
    public static void feedOnTheGroundStartsAFight(GameTestHelper helper) {
        Mob first = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, FLOOR.above().east());
        Mob second = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, FLOOR.above().west());
        helper.runAfterDelay(1, () -> GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null,
                GooTypes.JELLY, NO_ENTITY, helper.absolutePos(FLOOR), Direction.UP, FEED)));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            List<FeedPile> feeds = helper.getLevel().getEntities(GooEntities.FEED_PILE.get(),
                    new AABB(helper.absolutePos(FLOOR)).inflate(SEARCH), FeedPile::isAlive);
            helper.assertFalse(feeds.isEmpty(), SHOULD_LAY);
            helper.assertTrue(first.getTarget() == second && second.getTarget() == first,
                    String.format(SHOULD_FIGHT, first.getTarget(), second.getTarget()));
            helper.succeed();
        });
    }

    /**
     * Feed lands on the ground in one corner of the bay while a cow with its
     * AI stands in the far corner, out of the feed's reach and inside its
     * radius: the cow walks to the feed and eats it, falling in love.
     *
     * @param helper the gametest helper
     */
    public static void feedOnTheGroundDrawsAMob(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, FAR_CORNER);
        double start = cow.position().distanceTo(helper.absoluteVec(NEAR_CORNER.above().getBottomCenter()));
        helper.assertTrue(start > FeedPile.REACH, String.format(SHOULD_START_OUT_OF_REACH, start));
        helper.runAfterDelay(1, () -> GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null,
                GooTypes.JELLY, NO_ENTITY, helper.absolutePos(NEAR_CORNER), Direction.UP, FEED)));
        helper.succeedWhen(() -> {
            int left = helper.getLevel().getEntities(GooEntities.FEED_PILE.get(),
                    new AABB(helper.absolutePos(NEAR_CORNER)).inflate(FAR_REACH), FeedPile::isAlive).size();
            double off = cow.position().distanceTo(helper.absoluteVec(NEAR_CORNER.above().getBottomCenter()));
            helper.assertTrue(left == 0 && cow.isInLove(), String.format(SHOULD_DRAW, off, cow.isInLove(), left,
                    cow.getNavigation().getTargetPos(), cow.isPanicking()));
        });
    }

    /**
     * Feed strikes a size-1 slime three times: it grows to 2, then 4, and
     * stays at 4 (decision slimes-grow-on-jelly-feed).
     *
     * @param helper the gametest helper
     */
    public static void feedGrowsASlime(GameTestHelper helper) {
        Slime slime = helper.spawnWithNoFreeWill(EntityType.SLIME, FLOOR.above());
        slime.setSize(1, true);
        helper.runAfterDelay(1, () -> {
            for (int feed = 0; feed < SLIME_SIZES_FED.length; feed++) {
                strike(helper, slime);
                helper.assertTrue(slime.getSize() == SLIME_SIZES_FED[feed],
                        String.format(SHOULD_GROW_SLIME, feed + 1, SLIME_SIZES_FED[feed], slime.getSize()));
            }
            helper.succeed();
        });
    }

    /**
     * Feed lands on the ground beside a size-1 slime: the slime eats the laid
     * feed and grows to size 2 (decision slimes-grow-on-jelly-feed).
     *
     * @param helper the gametest helper
     */
    public static void slimeEatsALaidFeed(GameTestHelper helper) {
        Slime slime = helper.spawnWithNoFreeWill(EntityType.SLIME, FLOOR.above());
        slime.setSize(1, true);
        helper.runAfterDelay(1, () -> GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null,
                GooTypes.JELLY, NO_ENTITY, helper.absolutePos(FLOOR), Direction.UP, FEED)));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            int left = helper.getLevel().getEntities(GooEntities.FEED_PILE.get(),
                    new AABB(helper.absolutePos(FLOOR)).inflate(SEARCH), FeedPile::isAlive).size();
            helper.assertTrue(left == 0 && slime.getSize() == 2,
                    String.format(SHOULD_EAT_AND_GROW, left, slime.getSize()));
            helper.succeed();
        });
    }

    private static void strike(GameTestHelper helper, Mob struck) {
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.JELLY,
                struck.getId(), struck.blockPosition(), Direction.UP, FEED));
    }
}
