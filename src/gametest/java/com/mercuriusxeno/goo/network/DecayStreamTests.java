package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
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
 * Gametests for nether decay: a held stream of gnats paints the stone it
 * reaches, which keeps stepping toward cobblestone while the hold lasts
 * though the aim has left it, finishes alone once past half its step even
 * after release, and within one hold steps no further than cobblestone
 * (decision decay-gnats-degrade-each-block-once).
 */
public final class DecayStreamTests {

    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** Three blocks east of the player, at its height, on a block the test lays. */
    private static final BlockPos TARGET_POS = STAND_POS.east(3);
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward the block's center three blocks off. */
    private static final float LOOKING_AT_BLOCK = 20.5f;
    /** Pitch up into open air, the cone well clear of the stone. */
    private static final float LOOKING_AT_SKY = -80f;
    private static final int HELD_GOO = 6;
    /** nether_decay.json's degrade ticks: fifteen ticks of swarm step a block. */
    private static final int STEP_TICKS = 15;
    /** Past two steps' worth of swarm, so a second step would have landed without the once rule. */
    private static final int TWO_STEPS_AND_MORE = 2 * STEP_TICKS + 10;
    /** A brush across the stone short of half its step. */
    private static final int BRUSH_TICKS = 3;
    /** Aim on the stone past half its step. */
    private static final int PAST_HALF_TICKS = 10;
    /** Room for a block to finish its step, and then some. */
    private static final int FINISH_TICKS = STEP_TICKS + 10;
    private static final Identifier NETHER_DECAY = Identifier.parse("goo:nether_decay");
    private static final String ABILITY_REQUIRED = "Ability registry must hold nether_decay";
    private static final String SHOULD_BITE = "Decay's swarm should bite the zombie in its cone";
    /**
     * Pitch down onto the middle of a zombie four blocks off; the stone five
     * off, behind it, lies under three degrees from that line, inside the cone.
     */
    private static final float LOOKING_AT_ZOMBIE = 9.2f;
    /**
     * Pitch down onto the stone's top face a tenth of a block short of its far
     * edge, ten degrees off the stone's center, outside the cone's five.
     */
    private static final float LOOKING_AT_FAR_EDGE = 10.3f;
    /** The stone the swarm passes over for the zombie in front of it. */
    private static final BlockPos BEHIND_ZOMBIE = TARGET_POS.east(2);

    private DecayStreamTests() {
    }

    /**
     * A mock player holds decay on stone for more than two steps' worth of
     * swarm: the stone stands as cobblestone, not gravel.
     *
     * @param helper the gametest helper
     */
    public static void decayDegradesOncePerActivation(GameTestHelper helper) {
        ServerPlayer player = decayerOverStone(helper);
        hold(helper, player, 1, TWO_STEPS_AND_MORE);
        helper.runAfterDelay(TWO_STEPS_AND_MORE + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET_POS);
            helper.succeed();
        });
    }

    /**
     * A mock player brushes decay across stone for a few ticks, then turns
     * its aim to the sky and keeps holding: the painted stone steps to
     * cobblestone with the aim off it.
     *
     * @param helper the gametest helper
     */
    public static void decayPaintedBlockStepsUnaimed(GameTestHelper helper) {
        ServerPlayer player = decayerOverStone(helper);
        hold(helper, player, 1, BRUSH_TICKS + FINISH_TICKS);
        helper.runAfterDelay(BRUSH_TICKS + 1, () -> player.setXRot(LOOKING_AT_SKY));
        helper.runAfterDelay(BRUSH_TICKS + FINISH_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET_POS);
            helper.succeed();
        });
    }

    /**
     * A mock player holds decay on stone past half its step and lets go:
     * the stone finishes its step to cobblestone on its own.
     *
     * @param helper the gametest helper
     */
    public static void decayPastHalfFinishesAfterRelease(GameTestHelper helper) {
        ServerPlayer player = decayerOverStone(helper);
        hold(helper, player, 1, PAST_HALF_TICKS);
        helper.runAfterDelay(PAST_HALF_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.STONE, TARGET_POS);
        });
        helper.runAfterDelay(PAST_HALF_TICKS + FINISH_TICKS, () -> {
            helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET_POS);
            helper.succeed();
        });
    }

    /**
     * A mock player holds decay with the crosshair near the far edge of the
     * stone's top face, its center well outside the narrow cone: the stone
     * under the crosshair steps to cobblestone all the same.
     *
     * @param helper the gametest helper
     */
    public static void decayPaintsTheCrosshairBlockOffCenter(GameTestHelper helper) {
        ServerPlayer player = decayerOverStone(helper);
        player.setXRot(LOOKING_AT_FAR_EDGE);
        hold(helper, player, 1, FINISH_TICKS);
        helper.runAfterDelay(FINISH_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET_POS);
            helper.succeed();
        });
    }

    /**
     * A mock player holds decay on a zombie standing on stone: the swarm bites
     * the zombie and paints no block, so the stone under it stands as stone
     * though the hold runs past a block's step.
     *
     * @param helper the gametest helper
     */
    public static void decayBitesTheMobAndSparesTheBlocks(GameTestHelper helper) {
        BlockPos zombiePos = TARGET_POS.east();
        helper.setBlock(zombiePos.below(), Blocks.BEDROCK);
        helper.setBlock(BEHIND_ZOMBIE.below(), Blocks.BEDROCK);
        helper.setBlock(BEHIND_ZOMBIE, Blocks.STONE);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, zombiePos);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        ServerPlayer player = decayer(helper);
        player.setXRot(LOOKING_AT_ZOMBIE);
        hold(helper, player, 1, FINISH_TICKS);
        helper.runAfterDelay(FINISH_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), SHOULD_BITE);
            helper.assertBlockPresent(Blocks.STONE, BEHIND_ZOMBIE);
            helper.succeed();
        });
    }

    /**
     * Lays stone on bedrock at the target and stands a decaying player aimed at it.
     *
     * @param helper the gametest helper
     * @return the player
     */
    private static ServerPlayer decayerOverStone(GameTestHelper helper) {
        helper.setBlock(TARGET_POS.below(), Blocks.BEDROCK);
        helper.setBlock(TARGET_POS, Blocks.STONE);
        return decayer(helper);
    }

    /**
     * Streams decay every tick of a hold, aimed wherever the player looks that tick.
     *
     * @param helper the gametest helper
     * @param player the streaming player
     * @param from   the first tick of the hold
     * @param ticks  how many ticks it lasts
     */
    private static void hold(GameTestHelper helper, ServerPlayer player, int from, int ticks) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.NETHER), NETHER_DECAY.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 0; held < ticks; held++) {
            helper.runAfterDelay(from + held, () -> GooStreamHandler.streamTick(player, tick));
        }
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer decayer(GameTestHelper helper) {
        AbilityDefinition decay = AbilityRegistry.of(helper.getLevel()).getAbility(NETHER_DECAY);
        helper.assertTrue(decay != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_AT_BLOCK);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.NETHER, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, decay);
        return player;
    }
}
