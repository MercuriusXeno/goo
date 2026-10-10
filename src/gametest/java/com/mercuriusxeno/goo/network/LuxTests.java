package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.held.LuxEvents;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for glow's Lux as a held effect through the glove: eaten, it
 * stands and keeps night vision up without particles; invoked again, it
 * ends and takes the night vision with it; and its gaze picks the living
 * mob under the crosshair within 32 blocks in clear line (decisions
 * lux-night-vision-without-particles, self-effects-trickle-until-ended).
 */
public final class LuxTests {

    private static final Identifier GLOW_LUX = Identifier.parse("goo:glow_lux");
    private static final String SHOULD_HOLD = "Once the eat finishes the player should hold Lux";
    private static final String SHOULD_SEE_IN_THE_DARK = "Lux should grant its endless night vision with no particles or card, granted %s";
    private static final String SHOULD_END = "Invoking held Lux again should end it and its night vision";
    private static final String SHOULD_PICK = "Lux's gaze should pick the zombie in clear line within reach, picked %s";
    private static final String SHOULD_STOP_AT_BLOCKS = "Lux's gaze should stop at a block between, picked %s";
    private static final String SHOULD_STOP_AT_REACH = "Lux's gaze should not reach a zombie past 32 blocks, picked %s";
    /** The player stands this far over the bay's top, looking straight up into open sky. */
    private static final int OVER_THE_BAY = 2;
    private static final float STRAIGHT_UP = -90f;
    /** Blocks above the eyes the zombie floats within reach, the block between sits, and the zombie floats past reach. */
    private static final double WITHIN_REACH = 12;
    private static final int BLOCK_BETWEEN = 6;
    private static final double PAST_REACH = 36;

    private LuxTests() {
    }

    /**
     * A survival player eats Lux from the glove and the player ticks once:
     * Lux is held and stands, and night vision stands without particles.
     * Invoking Lux again ends it and the night vision both.
     *
     * @param helper the gametest helper
     */
    public static void luxSeesInTheDarkUntilEnded(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.GLOW, GLOW_LUX);
        player.doTick();
        boolean held = player.getData(GooAttachments.HELD_EFFECTS).holds(GLOW_LUX);
        boolean stands = player.getData(GooAttachments.LUX).standsAt(player.level().getGameTime());
        MobEffectInstance vision = player.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(held && stands, SHOULD_HOLD);
        helper.assertTrue(LuxEvents.isLuxVision(vision), String.format(SHOULD_SEE_IN_THE_DARK, vision));

        SelfDeliveryTests.invoke(player, GooTypes.GLOW, GLOW_LUX);
        player.doTick();
        boolean stillHeld = player.getData(GooAttachments.HELD_EFFECTS).holds(GLOW_LUX);
        boolean stillStands = player.getData(GooAttachments.LUX).standsAt(player.level().getGameTime());
        boolean stillSees = player.hasEffect(MobEffects.NIGHT_VISION);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertFalse(stillHeld || stillStands || stillSees, SHOULD_END);
        helper.succeed();
    }

    /**
     * A player stands over the bay looking straight up into open sky: a
     * zombie floating 12 blocks above the eyes is the one Lux's gaze picks; a
     * stone block set between hides it; and moved 36 blocks up, past the
     * gaze's 32, it is picked no more.
     *
     * @param helper the gametest helper
     */
    public static void luxGazePicksTheMobInClearLineWithinReach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.GLOW);
        AABB bay = helper.getBounds();
        player.snapTo(bay.getCenter().x, bay.maxY + OVER_THE_BAY, bay.getCenter().z, 0f, STRAIGHT_UP);
        Vec3 eye = player.getEyePosition();
        Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        zombie.setNoGravity(true);
        zombie.setNoAi(true);
        zombie.setPos(eye.x, eye.y + WITHIN_REACH, eye.z);
        level.addFreshEntity(zombie);
        LivingEntity seen = LuxEvents.gazedAt(player);
        BlockPos between = BlockPos.containing(eye.x, eye.y + BLOCK_BETWEEN, eye.z);
        level.setBlock(between, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        LivingEntity hidden = LuxEvents.gazedAt(player);
        level.setBlock(between, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        zombie.setPos(eye.x, eye.y + PAST_REACH, eye.z);
        LivingEntity tooFar = LuxEvents.gazedAt(player);
        zombie.discard();
        level.getServer().getPlayerList().remove(player);
        helper.assertTrue(seen == zombie, String.format(SHOULD_PICK, seen));
        helper.assertTrue(hidden == null, String.format(SHOULD_STOP_AT_BLOCKS, hidden));
        helper.assertTrue(tooFar == null, String.format(SHOULD_STOP_AT_REACH, tooFar));
        helper.succeed();
    }
}
