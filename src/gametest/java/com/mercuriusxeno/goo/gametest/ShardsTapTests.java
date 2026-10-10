package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.tap.TapDripScheduler;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametests for Crystal's tap: every crystal_shards_tap.json drip count
 * drops one glass shard onto the mob standing under the tap.
 * decision shards-drip-falls-as-a-glass-shard
 */
public final class ShardsTapTests {

    private static final BlockPos LANDING = new BlockPos(2, 0, 2);
    private static final int TAP_HEIGHT = 4;
    /** crystal_shards_tap.json's drips per shard. */
    private static final int DRIPS_PER_SHARD = 4;
    /** crystal_shards_tap.json's shard damage. */
    private static final float SHARD_DAMAGE = 3f;
    private static final float TOLERANCE = 0.01f;
    private static final String SHOULD_BE_WHOLE = "Drips short of the count should leave the husk whole, it lost %s";
    private static final String SHOULD_LOSE = "%s drips should have struck the husk %s times for %s, it lost %s";

    private ShardsTapTests() {
    }

    /**
     * A husk stands under a crystal tap: three drips leave it whole, the
     * fourth drops a shard on it, and the eighth a second.
     *
     * @param helper the gametest helper
     */
    public static void shardsTapDamagesPerCount(GameTestHelper helper) {
        helper.setBlock(LANDING, Blocks.STONE);
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, LANDING.above());
        float whole = husk.getHealth();

        drip(helper, DRIPS_PER_SHARD - 1);
        helper.assertTrue(Math.abs(whole - husk.getHealth()) < TOLERANCE,
                String.format(SHOULD_BE_WHOLE, whole - husk.getHealth()));
        drip(helper, 1);
        assertStruck(helper, husk, whole, 1);
        drip(helper, DRIPS_PER_SHARD);
        assertStruck(helper, husk, whole, 2);
        helper.succeed();
    }

    private static void assertStruck(GameTestHelper helper, Mob husk, float whole, int shards) {
        float lost = whole - husk.getHealth();
        helper.assertTrue(Math.abs(lost - shards * SHARD_DAMAGE) < TOLERANCE,
                String.format(SHOULD_LOSE, shards * DRIPS_PER_SHARD, shards, shards * SHARD_DAMAGE, lost));
    }

    /**
     * Lands crystal drips on the landing from the tap above it, each running
     * crystal's tap ability.
     *
     * @param helper the gametest helper
     * @param drips  how many drips land
     */
    private static void drip(GameTestHelper helper, int drips) {
        BlockPos landing = helper.absolutePos(LANDING);
        AbilityRegistry abilities = AbilityRegistry.of(helper.getLevel());
        for (int dripped = 0; dripped < drips; dripped++) {
            TapDripScheduler.runTapAbility(new TapDripScheduler.PendingDrip(helper.getLevel(),
                    landing.above(TAP_HEIGHT), landing, Direction.UP, GooTypes.CRYSTAL, 1, 0), abilities);
        }
    }
}
