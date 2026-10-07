package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.petrify.Petrification;
import com.mercuriusxeno.goo.ability.petrify.Statues;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for rock petrify: a held stream fills a penned zombie's gauge,
 * slowing it, until it becomes a statue block; a gauge the fog leaves drains
 * back; gravel under the fog builds toward cobblestone, regressing when let
 * go short of it and stepping once held long enough; and a statue mines like
 * cobblestone for cobblestone and the zombie's experience
 * (decision petrify-stone-encasement-and-calcify-map).
 */
public final class PetrifyStreamTests {

    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** Three blocks east of the player, at its height, on a block the test lays. */
    private static final BlockPos TARGET_POS = STAND_POS.east(3);
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward the zombie's middle three blocks off. */
    private static final float LOOKING_AT_ZOMBIE = 12f;
    /** Pitch down from the eye toward the gravel's center three blocks off. */
    private static final float LOOKING_AT_GRAVEL = 20.5f;
    private static final int HELD_GOO = 6;
    /** Partway: rock_petrify.json fills a twenty-health zombie's gauge in about sixty ticks. */
    private static final int PART_HOLD_TICKS = 25;
    private static final int STATUE_HOLD_TICKS = 70;
    /** rock_petrify.json's calcify ticks: thirty ticks of fog step a block one rung. */
    private static final int RUNG_TICKS = 30;
    /** Half a rung's fog, short of a step. */
    private static final int HALF_RUNG_TICKS = 15;
    /** Ticks for a released share to decay back to its floor and then some. */
    private static final int SETTLE_TICKS = 60;
    private static final double ITEM_SEARCH_RADIUS = 3;
    private static final Identifier ROCK_PETRIFY = Identifier.parse("goo:rock_petrify");
    private static final String ABILITY_REQUIRED = "Ability registry must hold rock_petrify";
    private static final String SHOULD_SLOW = "A part petrified zombie should be slower than %s, moves %s";
    private static final String SHOULD_LEAVE = "The petrified zombie should be gone into its statue";
    private static final String SHOULD_DRAIN = "A gauge the fog left should drain from %s, stands %s";
    private static final String SHOULD_REGRESS = "A released half share should regress to its floor, stands %s";
    private static final String SHOULD_DROP_EXPERIENCE = "A mined statue should drop experience";

    private PetrifyStreamTests() {
    }

    /**
     * A mock player holds petrify on a penned zombie: partway through it is
     * slower than it stood, and once its gauge fills a statue stands where it
     * stood and the zombie is gone.
     *
     * @param helper the gametest helper
     */
    public static void petrifySlowsThenStatues(GameTestHelper helper) {
        Mob zombie = pennedZombie(helper);
        double baseSpeed = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED);
        ServerPlayer player = petrifier(helper, LOOKING_AT_ZOMBIE);
        hold(helper, player, 1, STATUE_HOLD_TICKS);
        helper.runAfterDelay(PART_HOLD_TICKS + 1, () -> {
            double speed = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED);
            helper.assertTrue(speed < baseSpeed, String.format(SHOULD_SLOW, baseSpeed, speed));
        });
        helper.runAfterDelay(STATUE_HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(GooBlocks.STATUE.get(), TARGET_POS);
            helper.assertTrue(zombie.isRemoved(), SHOULD_LEAVE);
            helper.succeed();
        });
    }

    /**
     * A mock player holds petrify on a penned zombie partway, then lets go:
     * once the fog has left it a while its gauge drains below where it stood.
     *
     * @param helper the gametest helper
     */
    public static void petrifyGaugeDrains(GameTestHelper helper) {
        Mob zombie = pennedZombie(helper);
        ServerPlayer player = petrifier(helper, LOOKING_AT_ZOMBIE);
        hold(helper, player, 1, PART_HOLD_TICKS);
        float[] atRelease = new float[1];
        helper.runAfterDelay(PART_HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            atRelease[0] = zombie.getData(GooAttachments.PETRIFICATION).gauge();
        });
        helper.runAfterDelay(PART_HOLD_TICKS + SETTLE_TICKS, () -> {
            Petrification now = zombie.getData(GooAttachments.PETRIFICATION);
            helper.assertTrue(now.gauge() < atRelease[0], String.format(SHOULD_DRAIN, atRelease[0], now.gauge()));
            helper.succeed();
        });
    }

    /**
     * A mock player holds petrify on gravel for half a rung, which leaves it
     * gravel and regresses once let go; then holds it a full rung, which
     * steps it to cobblestone.
     *
     * @param helper the gametest helper
     */
    public static void petrifyCalcifiesGradually(GameTestHelper helper) {
        helper.setBlock(TARGET_POS.below(), Blocks.STONE);
        helper.setBlock(TARGET_POS, Blocks.GRAVEL);
        ServerPlayer player = petrifier(helper, LOOKING_AT_GRAVEL);
        hold(helper, player, 1, HALF_RUNG_TICKS);
        int second = HALF_RUNG_TICKS + SETTLE_TICKS;
        helper.runAfterDelay(second, () -> {
            helper.assertBlockPresent(Blocks.GRAVEL, TARGET_POS);
            float share = GooServerState.of(helper.getLevel().getServer()).blockExposures()
                    .shareAt(helper.getLevel(), helper.absolutePos(TARGET_POS));
            helper.assertTrue(share == 0f, String.format(SHOULD_REGRESS, share));
        });
        hold(helper, player, second + 1, RUNG_TICKS + 1);
        helper.runAfterDelay(second + RUNG_TICKS + 2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET_POS);
            helper.succeed();
        });
    }

    /**
     * A zombie turned to a statue, mined by a survival player with a pickaxe,
     * leaves cobblestone and experience behind.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void statueMinesForCobblestoneAndExperience(GameTestHelper helper) {
        Mob zombie = pennedZombie(helper);
        ServerPlayer miner = helper.makeMockServerPlayerInLevel();
        miner.setGameMode(GameType.SURVIVAL);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        miner.setPos(stand.x, stand.y, stand.z);
        miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        helper.runAfterDelay(1, () -> {
            Statues.encase(helper.getLevel(), zombie, miner);
            helper.assertBlockPresent(GooBlocks.STATUE.get(), TARGET_POS);
            miner.gameMode.destroyBlock(helper.absolutePos(TARGET_POS));
        });
        helper.runAfterDelay(2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(miner);
            helper.assertBlockPresent(Blocks.AIR, TARGET_POS);
            helper.assertItemEntityPresent(Items.COBBLESTONE, TARGET_POS, ITEM_SEARCH_RADIUS);
            helper.assertTrue(!helper.getEntities(EntityType.EXPERIENCE_ORB, TARGET_POS, ITEM_SEARCH_RADIUS).isEmpty(),
                    SHOULD_DROP_EXPERIENCE);
            helper.succeed();
        });
    }

    /**
     * Floors the target cell with stone, rings it with glass two high so the
     * zombie in it stays in the cone, and stands a helmeted zombie there; the
     * stream strikes the entities in its cone whatever stands between.
     *
     * @param helper the gametest helper
     * @return the zombie
     */
    private static Mob pennedZombie(GameTestHelper helper) {
        helper.setBlock(TARGET_POS.below(), Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(TARGET_POS.relative(side), Blocks.GLASS);
            helper.setBlock(TARGET_POS.relative(side).above(), Blocks.GLASS);
        }
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, TARGET_POS);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }

    /**
     * Streams petrify every tick of a hold.
     *
     * @param helper the gametest helper
     * @param player the streaming player
     * @param from   the first tick of the hold
     * @param ticks  how many ticks it lasts
     */
    private static void hold(GameTestHelper helper, ServerPlayer player, int from, int ticks) {
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.ROCK), ROCK_PETRIFY.toString(),
                player.getEyePosition(), player.getEyePosition(), player.getY());
        for (int held = 0; held < ticks; held++) {
            helper.runAfterDelay(from + held, () -> GooStreamHandler.streamTick(player, tick));
        }
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer petrifier(GameTestHelper helper, float pitch) {
        AbilityDefinition petrify = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_PETRIFY);
        helper.assertTrue(petrify != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(pitch);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.ROCK, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, petrify);
        return player;
    }
}
