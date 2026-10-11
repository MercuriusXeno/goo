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
import net.minecraft.world.phys.AABB;
import java.util.List;

/**
 * Gametests for Jelly Feed through {@link GooEffectScheduler#applyEffect}: a
 * blob striking an adult cow sets it in love, one striking a calf grows it to
 * an adult, and one landing on the ground lays a feed that two zombies
 * reaching together turn on each other over (decision feed-blob-feeds-and-draws-mobs).
 */
public final class FeedTests {

    private static final String FEED = "goo:jelly_feed";
    private static final int NO_ENTITY = -1;
    private static final BlockPos FLOOR = new BlockPos(2, 0, 2);
    /** Ticks for a laid feed to settle and run a tick of reaching. */
    private static final int SETTLE_TICKS = 5;
    private static final double SEARCH = 3.0;
    private static final String SHOULD_LOVE = "A fed adult cow should be in love";
    private static final String SHOULD_GROW = "A fed calf should grow to an adult, age %d";
    private static final String SHOULD_LAY = "A feed blob landing on the ground should lay a feed";
    private static final String SHOULD_FIGHT = "Two zombies at one feed should target each other: %s and %s";

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

    private static void strike(GameTestHelper helper, Mob struck) {
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.JELLY,
                struck.getId(), struck.blockPosition(), Direction.UP, FEED));
    }
}
