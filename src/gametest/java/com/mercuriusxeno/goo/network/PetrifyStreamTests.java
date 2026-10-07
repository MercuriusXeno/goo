package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.petrify.Petrification;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooItems;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for rock petrify: a held stream fills a penned zombie's gauge until it
 * stands a statue, its AI stopped and its motion zeroed; and a hold steps
 * gravel one rung along the calcify map, a second hold stepping it again
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
    private static final int HELD_GOO = 5;
    /** rock_petrify.json fills a twenty-health zombie's gauge in about sixty ticks. */
    private static final int ENCASE_TICKS = 70;
    private static final int HOLD_TICKS = 10;
    /** A tick with no stream between two holds, which starts the second hold anew. */
    private static final int GAP_TICKS = 2;
    private static final Identifier ROCK_PETRIFY = Identifier.parse("goo:rock_petrify");
    private static final String ABILITY_REQUIRED = "Ability registry must hold rock_petrify";
    private static final String SHOULD_BE_STATUE = "The streamed zombie should stand a statue, stands %s";
    private static final String SHOULD_LOSE_AI = "The statue should have no AI";
    private static final String SHOULD_STAND_STILL = "The statue should not move, moves %s";

    private PetrifyStreamTests() {
    }

    /**
     * A mock player holds petrify on a zombie three blocks ahead for seventy
     * ticks: the zombie's gauge fills, it stands a statue with no AI and no
     * motion.
     *
     * @param helper the gametest helper
     */
    public static void petrifyEncasesAZombie(GameTestHelper helper) {
        penIn(helper, TARGET_POS);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, TARGET_POS);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        ServerPlayer player = petrifier(helper, LOOKING_AT_ZOMBIE);
        GooStreamPayload tick = streamTick(player);
        for (int held = 1; held <= ENCASE_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(ENCASE_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            Petrification petrified = zombie.getData(GooAttachments.PETRIFICATION);
            helper.assertTrue(petrified.statue(), String.format(SHOULD_BE_STATUE, petrified));
            helper.assertTrue(zombie.isNoAi(), SHOULD_LOSE_AI);
            helper.assertTrue(zombie.getDeltaMovement().equals(Vec3.ZERO),
                    String.format(SHOULD_STAND_STILL, zombie.getDeltaMovement()));
            helper.succeed();
        });
    }

    /**
     * A mock player holds petrify on gravel for ten ticks, which steps it to
     * cobblestone and no further; after a gap a second hold steps the
     * cobblestone to stone.
     *
     * @param helper the gametest helper
     */
    public static void petrifyCalcifiesGravel(GameTestHelper helper) {
        helper.setBlock(TARGET_POS.below(), Blocks.STONE);
        helper.setBlock(TARGET_POS, Blocks.GRAVEL);
        ServerPlayer player = petrifier(helper, LOOKING_AT_GRAVEL);
        GooStreamPayload tick = streamTick(player);
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        int secondHold = HOLD_TICKS + GAP_TICKS;
        helper.runAfterDelay(secondHold, () -> helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET_POS));
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(secondHold + held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(secondHold + HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.STONE, TARGET_POS);
            helper.succeed();
        });
    }

    /**
     * Floors a cell with stone and rings it with glass two high, so the zombie standing in it stays in
     * the cone; the stream strikes the entities in its cone whatever stands between.
     *
     * @param helper the gametest helper
     * @param cell   the cell penned
     */
    private static void penIn(GameTestHelper helper, BlockPos cell) {
        helper.setBlock(cell.below(), Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.GLASS);
            helper.setBlock(cell.relative(side).above(), Blocks.GLASS);
        }
    }

    private static GooStreamPayload streamTick(ServerPlayer player) {
        return new GooStreamPayload(GooTypes.id(GooTypes.ROCK), ROCK_PETRIFY.toString(), player.getEyePosition(),
                player.getEyePosition(), player.getY());
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
