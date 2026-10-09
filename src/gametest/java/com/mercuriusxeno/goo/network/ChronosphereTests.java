package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for aeon's Chronosphere, landed through
 * {@link GooEffectScheduler#applyEffect} on the bay floor: once its veil has
 * grown, a cow walking inside covers a fraction of the ground it covered
 * free and an arrow inside barely moves, while a player inside wears no
 * slowness and keeps its speed.
 * chronosphere-hastes-players-slows-mobs
 */
public final class ChronosphereTests {

    private static final BlockPos FLOOR_POS = new BlockPos(2, 0, 2);
    private static final BlockPos COW_START = new BlockPos(1, 1, 1);
    /** Four blocks east of the cow's start, inside the six-block bay. */
    private static final BlockPos COW_GOAL = COW_START.east(4);
    /** Where the cow walks once veiled: back west, so its walk has room. */
    private static final BlockPos COW_RETURN = COW_START;
    private static final BlockPos ARROW_POS = new BlockPos(2, 2, 3);
    private static final BlockPos PLAYER_POS = new BlockPos(3, 1, 2);
    private static final int NO_ENTITY = -1;
    private static final String AEON_CHRONOSPHERE = "goo:aeon_chronosphere";
    private static final double WALK_SPEED = 1.0;
    private static final int WALK_TICKS = 20;
    /** Past aeon_chronosphere.json's expand ticks of ten. */
    private static final int EXPAND_TICKS = 12;
    private static final int ARROW_TICKS = 5;
    private static final Vec3 ARROW_VELOCITY = new Vec3(0.8, 0, 0);
    /** A free arrow covers about four blocks in five ticks; a slowed one under one. */
    private static final double SLOWED_ARROW_REACH = 1.0;
    /** The share of its free walk a slowed cow may cover. */
    private static final double SLOWED_SHARE = 0.4;
    private static final double MIN_FREE_WALK = 0.5;
    private static final int SETTLE_TICKS = 5;
    private static final String SHOULD_WALK_FREE = "The free cow should walk off, walked %.2f";
    private static final String SHOULD_CRAWL = "The cow in the veil should crawl, walked %.2f against %.2f free";
    private static final String ARROW_SHOULD_CRAWL = "The arrow in the veil should barely move, moved %.2f";
    private static final String COW_SHOULD_SLOW = "A cow in the veil should wear slowness";
    private static final String PLAYER_UNSLOWED = "A player in the veil should wear no slowness";
    private static final String PLAYER_FULL_SPEED = "A player in the veil should keep its speed, %.3f against %.3f";

    private ChronosphereTests() {
    }

    /**
     * A cow walks free, then walks again once the veil has grown around it,
     * covering a fraction of the ground; an arrow loosed inside barely moves.
     *
     * @param helper the gametest helper
     */
    public static void chronosphereSlowsMobsAndProjectiles(GameTestHelper helper) {
        Mob cow = helper.spawn(EntityType.COW, COW_START);
        double[] freeWalk = new double[1];
        helper.runAfterDelay(1, () -> walk(helper, cow, COW_GOAL));
        helper.runAfterDelay(1 + WALK_TICKS, () -> {
            freeWalk[0] = cow.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(COW_START)));
            helper.assertTrue(freeWalk[0] > MIN_FREE_WALK, String.format(SHOULD_WALK_FREE, freeWalk[0]));
            cow.getNavigation().stop();
            landVeil(helper);
        });
        int veiled = 1 + WALK_TICKS + EXPAND_TICKS;
        Arrow[] arrow = new Arrow[1];
        Vec3[] veiledStart = new Vec3[1];
        helper.runAfterDelay(veiled, () -> {
            veiledStart[0] = cow.position();
            walk(helper, cow, COW_RETURN);
            arrow[0] = loose(helper);
        });
        helper.runAfterDelay(veiled + ARROW_TICKS, () -> {
            double moved = arrow[0].position().distanceTo(Vec3.atCenterOf(helper.absolutePos(ARROW_POS)));
            helper.assertTrue(moved < SLOWED_ARROW_REACH, String.format(ARROW_SHOULD_CRAWL, moved));
        });
        helper.runAfterDelay(veiled + WALK_TICKS, () -> {
            double crawled = cow.position().distanceTo(veiledStart[0]);
            helper.assertTrue(crawled < freeWalk[0] * SLOWED_SHARE, String.format(SHOULD_CRAWL, crawled, freeWalk[0]));
            helper.succeed();
        });
    }

    /**
     * A player standing in the grown veil wears no slowness and keeps its
     * speed, while a cow beside it wears the veil's slowness.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void chronosphereSparesPlayers(GameTestHelper helper) {
        landVeil(helper);
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_START);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(PLAYER_POS));
        player.setPos(stand.x, stand.y, stand.z);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        helper.runAfterDelay(EXPAND_TICKS + SETTLE_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(cow.hasEffect(MobEffects.SLOWNESS), COW_SHOULD_SLOW);
            helper.assertFalse(player.hasEffect(MobEffects.SLOWNESS), PLAYER_UNSLOWED);
            helper.assertTrue(speed != null && speed.getValue() == speed.getBaseValue(),
                    String.format(PLAYER_FULL_SPEED, speed == null ? 0 : speed.getValue(),
                            speed == null ? 0 : speed.getBaseValue()));
            helper.succeed();
        });
    }

    private static void landVeil(GameTestHelper helper) {
        helper.setBlock(FLOOR_POS, Blocks.STONE);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.AEON,
                NO_ENTITY, helper.absolutePos(FLOOR_POS), Direction.UP, AEON_CHRONOSPHERE));
    }

    private static void walk(GameTestHelper helper, Mob cow, BlockPos to) {
        Vec3 goal = Vec3.atBottomCenterOf(helper.absolutePos(to));
        cow.getNavigation().moveTo(goal.x, goal.y, goal.z, WALK_SPEED);
    }

    private static Arrow loose(GameTestHelper helper) {
        Arrow arrow = EntityType.ARROW.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(ARROW_POS));
        arrow.setPos(at.x, at.y, at.z);
        arrow.setDeltaMovement(ARROW_VELOCITY);
        arrow.setNoGravity(true);
        helper.getLevel().addFreshEntity(arrow);
        return arrow;
    }
}
