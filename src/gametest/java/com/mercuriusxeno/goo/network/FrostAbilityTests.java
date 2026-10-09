package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.tap.TapDripScheduler;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for the frost abilities: each lands a frost ability the way the
 * glove would and reads the frozen gauges and the world it leaves.
 * Decisions nova-ring-grows-with-the-hold, nova-drip-pulses-a-short-lasting-freeze,
 * cold-streams-wind-lines-and-snowflakes, orb-carries-a-swirling-nova.
 */
public final class FrostAbilityTests {

    private static final BlockPos CASTER_POS = new BlockPos(1, 1, 1);
    /** About one and a half blocks from the caster: inside Nova's uncharged reach of two. */
    private static final BlockPos NEAR_POS = new BlockPos(2, 1, 2);
    /** About four blocks from the caster: past Nova's uncharged reach, inside its full reach of eight. */
    private static final BlockPos FAR_POS = new BlockPos(4, 1, 4);
    private static final Identifier FROST_NOVA = Identifier.parse("goo:frost_nova");
    /** frost_nova.json's charge max_ticks. */
    private static final int FULL_HOLD_TICKS = 60;
    private static final int NO_HOLD_TICKS = 0;
    /**
     * A full hold freezes two zombies by 16 health each, thinned by the crowd
     * to about 15.2 of their 20 health; an uncharged one by 4 of 20.
     */
    private static final float FULL_HOLD_FLOOR = 0.7f;
    private static final float NO_HOLD_CEILING = 0.25f;
    private static final int SETTLE_TICKS = 1;
    /** The stone floor a frost tap drips onto. */
    private static final BlockPos TAP_FLOOR = new BlockPos(2, 0, 2);
    /** Still water beside the drips' landing, inside the tap's freeze. */
    private static final BlockPos TAP_WATER = new BlockPos(3, 1, 2);
    /** A zombie standing beside the landing, inside the tap's nova. */
    private static final BlockPos TAP_ZOMBIE = new BlockPos(2, 1, 3);
    /** frost_nova_tap.json's drip count. */
    private static final int TAP_DRIPS = 6;
    /** Ticks past which a never-thawing ice would have melted were it vanilla ice under light. */
    private static final int ICE_STANDS_TICKS = 40;
    private static final Identifier FROST_COLD = Identifier.parse("goo:frost_cold");
    private static final BlockPos STREAMER_POS = new BlockPos(0, 1, 2);
    /** Grass three blocks east of the streamer, inside Cold's cone below the look. */
    private static final BlockPos GRASS_POS = new BlockPos(3, 1, 2);
    /** A zombie four blocks east of the streamer, inside Cold's cone above the look. */
    private static final BlockPos COLD_ZOMBIE_POS = new BlockPos(4, 1, 2);
    private static final float FACING_EAST = -90f;
    /** Pitched down between the grass and the zombie's middle, so both stand in the cone. */
    private static final float BETWEEN_GRASS_AND_ZOMBIE = 14f;
    private static final int COLD_HOLD_TICKS = 10;
    /** A full Nova's freeze on a zombie, 16 of its 20 health, spread over the 60 ticks it took to charge. */
    private static final float NOVA_PER_HELD_TICK = 0.8f / FULL_HOLD_TICKS;
    private static final String SHOULD_KILL_GRASS = "Cold should break the grass in its cone";
    private static final String SHOULD_OUTFREEZE_NOVA = "Ten ticks of Cold should freeze past %s, Nova's ten held ticks; stands %s";
    private static final Identifier FROST_ORB = Identifier.parse("goo:frost_orb");
    /** Two still water blocks in the floor under the Orb's path. */
    private static final BlockPos POOL_NEAR = new BlockPos(2, 0, 2);
    private static final BlockPos POOL_FAR = new BlockPos(3, 0, 2);
    /** A zombie two blocks off the Orb's path, inside its swirl. */
    private static final BlockPos ORB_BYSTANDER_POS = new BlockPos(3, 1, 4);
    /** Where the Orb lands, at the east end of the bay. */
    private static final BlockPos ORB_LANDING = new BlockPos(5, 1, 2);
    /** Enough frost for the Orb's cost and some over. */
    private static final int ORB_GOO = 4;
    /** frost_orb.json's slow arc covers the five blocks in seventeen ticks; this is past its landing. */
    private static final int ORB_LANDED_TICKS = 30;
    private static final String SHOULD_ICE_THE_POOL = "The Orb should freeze the pool under its path to ice";
    private static final String SHOULD_FREEZE_BYSTANDER = "The Orb should freeze the zombie beside its path";
    private static final String SHOULD_NOT_PULSE_YET = "Drips short of the count should freeze nothing, stands %s";
    private static final String SHOULD_FREEZE_ZOMBIE = "The tap's nova should raise the zombie's gauge";
    private static final String ABILITY_REQUIRED = "Ability registry must hold frost_nova";
    private static final String SHOULD_FREEZE_HARD = "A full Nova should freeze the %s zombie past %s, stands %s";
    private static final String SHOULD_FREEZE_LIGHTLY = "An uncharged Nova should freeze the near zombie lightly, stands %s";
    private static final String SHOULD_NOT_REACH = "An uncharged Nova should not reach the far zombie, stands %s";

    private FrostAbilityTests() {
    }

    /**
     * A full hold of Nova released beside two zombies reaches both and
     * freezes each hard.
     *
     * @param helper the gametest helper
     */
    public static void novaHeldReachesBothZombies(GameTestHelper helper) {
        Mob near = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, NEAR_POS);
        Mob far = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, FAR_POS);
        ServerPlayer caster = caster(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            release(caster, FULL_HOLD_TICKS);
            float nearGauge = near.getData(GooAttachments.FROZEN).gauge();
            float farGauge = far.getData(GooAttachments.FROZEN).gauge();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(nearGauge > FULL_HOLD_FLOOR,
                    String.format(SHOULD_FREEZE_HARD, "near", FULL_HOLD_FLOOR, nearGauge));
            helper.assertTrue(farGauge > FULL_HOLD_FLOOR,
                    String.format(SHOULD_FREEZE_HARD, "far", FULL_HOLD_FLOOR, farGauge));
            helper.succeed();
        });
    }

    /**
     * Nova let go at once reaches only the near zombie, and freezes it lightly.
     *
     * @param helper the gametest helper
     */
    public static void novaTappedReachesOnlyTheNear(GameTestHelper helper) {
        Mob near = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, NEAR_POS);
        Mob far = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, FAR_POS);
        ServerPlayer caster = caster(helper);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            release(caster, NO_HOLD_TICKS);
            float nearGauge = near.getData(GooAttachments.FROZEN).gauge();
            float farGauge = far.getData(GooAttachments.FROZEN).gauge();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(nearGauge > 0f && nearGauge < NO_HOLD_CEILING,
                    String.format(SHOULD_FREEZE_LIGHTLY, nearGauge));
            helper.assertTrue(farGauge == 0f, String.format(SHOULD_NOT_REACH, farGauge));
            helper.succeed();
        });
    }

    /**
     * A frost tap's drips gather to its count and pulse a small nova: the
     * still water beside the landing becomes magicked ice that stands, and
     * the zombie beside it freezes.
     *
     * @param helper the gametest helper
     */
    public static void novaTapFreezesBelow(GameTestHelper helper) {
        helper.setBlock(TAP_FLOOR, Blocks.STONE);
        helper.setBlock(TAP_WATER.below(), Blocks.STONE);
        helper.setBlock(TAP_WATER, Blocks.WATER);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, TAP_ZOMBIE);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            drip(helper, TAP_DRIPS - 1);
            float early = zombie.getData(GooAttachments.FROZEN).gauge();
            helper.assertTrue(early == 0f, String.format(SHOULD_NOT_PULSE_YET, early));
            helper.assertBlockPresent(Blocks.WATER, TAP_WATER);
            drip(helper, 1);
            helper.assertTrue(zombie.getData(GooAttachments.FROZEN).started(), SHOULD_FREEZE_ZOMBIE);
            helper.assertBlockPresent(GooBlocks.MAGICKED_ICE.get(), TAP_WATER);
        });
        helper.runAfterDelay(SETTLE_TICKS + ICE_STANDS_TICKS, () -> {
            helper.assertBlockPresent(GooBlocks.MAGICKED_ICE.get(), TAP_WATER);
            helper.succeed();
        });
    }

    /**
     * Cold streamed along a line of grass at a zombie breaks the grass and
     * freezes the zombie faster than Nova's hold charges.
     *
     * @param helper the gametest helper
     */
    public static void coldBreaksGrassAndFreezesFaster(GameTestHelper helper) {
        helper.setBlock(GRASS_POS.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(GRASS_POS, Blocks.SHORT_GRASS);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, COLD_ZOMBIE_POS);
        ServerPlayer streamer = SelfDeliveryTests.invoker(helper, GooTypes.FROST);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STREAMER_POS));
        streamer.setPos(stand.x, stand.y, stand.z);
        streamer.setYRot(FACING_EAST);
        streamer.setXRot(BETWEEN_GRASS_AND_ZOMBIE);
        AbilityDefinition cold = AbilityRegistry.of(helper.getLevel()).getAbility(FROST_COLD);
        helper.assertTrue(cold != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(streamer, cold);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.FROST), FROST_COLD.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
        for (int held = 1; held <= COLD_HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
        helper.runAfterDelay(COLD_HOLD_TICKS + 1, () -> {
            float gauge = zombie.getData(GooAttachments.FROZEN).gauge();
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            helper.assertBlockNotPresent(Blocks.SHORT_GRASS, GRASS_POS);
            float novaFloor = NOVA_PER_HELD_TICK * COLD_HOLD_TICKS;
            helper.assertTrue(gauge > novaFloor, String.format(SHOULD_OUTFREEZE_NOVA, novaFloor, gauge));
            helper.succeed();
        });
    }

    /**
     * The Orb thrown along the bay over a pool and past a zombie freezes the
     * pool to ice and the zombie as it rolls by.
     *
     * @param helper the gametest helper
     */
    public static void orbFreezesPathAndPool(GameTestHelper helper) {
        helper.setBlock(POOL_NEAR, Blocks.WATER);
        helper.setBlock(POOL_FAR, Blocks.WATER);
        Mob bystander = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ORB_BYSTANDER_POS);
        ServerPlayer thrower = SelfDeliveryTests.invoker(helper, GooTypes.FROST);
        thrower.getInventory().add(GooStacks.createForOutput(GooTypes.FROST, ORB_GOO * GooStacks.THOUSAND));
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STREAMER_POS));
        thrower.setPos(stand.x, stand.y, stand.z);
        thrower.setYRot(FACING_EAST);
        AbilityDefinition orb = AbilityRegistry.of(helper.getLevel()).getAbility(FROST_ORB);
        helper.assertTrue(orb != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(thrower, orb);
        BlockPos landing = helper.absolutePos(ORB_LANDING);
        helper.runAfterDelay(SETTLE_TICKS, () -> GooThrowHandler.execute(thrower, new GooThrowPayload(
                GooTypes.id(GooTypes.FROST), -1, landing.below(), Direction.UP.ordinal(), false,
                FROST_ORB.toString(), thrower.getEyePosition(), Vec3.atBottomCenterOf(landing))));
        helper.runAfterDelay(ORB_LANDED_TICKS, () -> {
            boolean frozenBy = bystander.getData(GooAttachments.FROZEN).started();
            helper.getLevel().getServer().getPlayerList().remove(thrower);
            helper.assertTrue(helper.getBlockState(POOL_NEAR).is(Blocks.ICE)
                    && helper.getBlockState(POOL_FAR).is(Blocks.ICE), SHOULD_ICE_THE_POOL);
            helper.assertTrue(frozenBy, SHOULD_FREEZE_BYSTANDER);
            helper.succeed();
        });
    }

    private static void drip(GameTestHelper helper, int drips) {
        BlockPos landing = helper.absolutePos(TAP_FLOOR);
        AbilityRegistry abilities = AbilityRegistry.of(helper.getLevel());
        for (int dripped = 0; dripped < drips; dripped++) {
            TapDripScheduler.runTapAbility(new TapDripScheduler.PendingDrip(helper.getLevel(), landing.above(),
                    landing, Direction.UP, GooTypes.FROST, 1, 0), abilities);
        }
    }

    private static void release(ServerPlayer caster, int heldTicks) {
        GooThrowHandler.releaseCharge(caster,
                new GooChargePayload(GooTypes.id(GooTypes.FROST), FROST_NOVA.toString(), heldTicks));
    }

    private static ServerPlayer caster(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.FROST);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(CASTER_POS));
        player.setPos(stand.x, stand.y, stand.z);
        AbilityDefinition nova = AbilityRegistry.of(helper.getLevel()).getAbility(FROST_NOVA);
        helper.assertTrue(nova != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(player, nova);
        return player;
    }
}
