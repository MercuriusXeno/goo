package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;

/**
 * Gametests for Zap: a blob landing on a lever flips it, one landing beside
 * dust powers it for a moment, and every mob at the landing drops its target
 * and stands without AI until the stun wears off.
 * zap-ticks-the-device-and-stuns
 */
public final class ZapTests {

    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final String ZAP = "goo:pulse_zap";
    private static final BlockPos FLOOR = new BlockPos(2, 0, 2);
    private static final BlockPos ON_FLOOR = FLOOR.above();
    /** pulse_zap.json's stun ticks, and a tick past them for the wake to run. */
    private static final int STUN_TICKS = 60;
    private static final int PAST_THE_STUN = STUN_TICKS + 2;
    private static final int SETTLE_TICKS = 1;
    private static final String LEVER_FLIPPED = "A Zap landing on a lever should flip it on";
    private static final String TARGET_DROPPED = "A stunned zombie should hold no target";
    private static final String AI_OFF = "A stunned zombie should stand without AI";
    private static final String AI_BACK = "A zombie whose stun wore off should have its AI back";
    private static final String DUST_POWERED = "Dust beside a Zap landing should carry power";
    private static final String DUST_UNPOWERED = "Dust should lose its power once the pulse ends";
    private static final String HAD_TARGET = "The zombie should target the player before the Zap";
    /** Ticks the dust's pulse stands, and a tick past them for it to clear. */
    private static final int PAST_THE_PULSE = 4;

    private ZapTests() {
    }

    /**
     * Zap lands on top of a lever standing on the floor: the lever flips on.
     *
     * @param helper the gametest helper
     */
    public static void zapFlipsALever(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(ON_FLOOR, Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        landOn(helper, ON_FLOOR);
        helper.assertTrue(helper.getBlockState(ON_FLOOR).getValue(LeverBlock.POWERED), LEVER_FLIPPED);
        helper.succeed();
    }

    /**
     * Zap strikes a zombie targeting a player: the zombie drops its target
     * and stands without AI.
     *
     * @param helper the gametest helper
     */
    public static void zapStunsAZombie(GameTestHelper helper) {
        Mob zombie = targetingZombie(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(zombie.getTarget() != null, HAD_TARGET);
            strike(helper, zombie);
            helper.assertTrue(zombie.getTarget() == null, TARGET_DROPPED);
            helper.assertTrue(zombie.isNoAi(), AI_OFF);
            helper.succeed();
        });
    }

    /**
     * A zombie Zap stunned has its AI back once the JSON's stun ticks pass.
     *
     * @param helper the gametest helper
     */
    public static void zapStunWearsOff(GameTestHelper helper) {
        Mob zombie = targetingZombie(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            strike(helper, zombie);
            helper.assertTrue(zombie.isNoAi(), AI_OFF);
            helper.runAfterDelay(PAST_THE_STUN, () -> {
                helper.assertFalse(zombie.isNoAi(), AI_BACK);
                helper.succeed();
            });
        });
    }

    /**
     * Zap lands on top of dust on the floor: the dust carries power while
     * the pulse stands and none once it ends.
     *
     * @param helper the gametest helper
     */
    public static void zapPulsesDust(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(ON_FLOOR, Blocks.REDSTONE_WIRE);
        landOn(helper, ON_FLOOR);
        helper.assertTrue(helper.getBlockState(ON_FLOOR).getValue(RedStoneWireBlock.POWER) > 0, DUST_POWERED);
        helper.runAfterDelay(PAST_THE_PULSE, () -> {
            helper.assertTrue(helper.getBlockState(ON_FLOOR).getValue(RedStoneWireBlock.POWER) == 0, DUST_UNPOWERED);
            helper.succeed();
        });
    }

    private static Mob targetingZombie(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(ON_FLOOR.east(2).getCenter()));
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ON_FLOOR);
        zombie.setTarget(player);
        return zombie;
    }

    private static void landOn(GameTestHelper helper, BlockPos block) {
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.PULSE,
                NO_ENTITY, helper.absolutePos(block), Direction.UP, ZAP));
    }

    private static void strike(GameTestHelper helper, Mob mob) {
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.PULSE,
                mob.getId(), mob.blockPosition(), Direction.UP, ZAP));
    }
}
