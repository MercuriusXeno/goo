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
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for nether decay: a held stream of gnats paints the stone it
 * reaches, which keeps stepping toward cobblestone while the hold lasts
 * though the aim has left it, finishes alone once past nine tenths of its step even
 * after release, and within one hold steps no further than cobblestone
 * (decision decay-gnats-degrade-each-block-once).
 */
@RegistersGameTests
public final class DecayStreamTests {
    private static final String DECAY_PAINTED_STEPS_UNAIMED = "decay_painted_block_steps_unaimed";
    private static final String DECAY_PAST_NINE_TENTHS_FINISHES = "decay_past_nine_tenths_finishes_after_release";
    private static final String DECAY_STEPS_THROUGH_A_BITE = "decay_painted_block_steps_through_a_bite";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DECAY_PAINTED_STEPS_UNAIMED, DecayStreamTests::decayPaintedBlockStepsUnaimed);
        GooTestFunctions.registerTest(registrar, DECAY_PAST_NINE_TENTHS_FINISHES,
                DecayStreamTests::decayPastNineTenthsFinishesAfterRelease);
        GooTestFunctions.registerTest(registrar, DECAY_STEPS_THROUGH_A_BITE, DecayStreamTests::decayPaintedBlockStepsThroughABite);
    }


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
    /** A brush across the stone short of half its step. */
    private static final int BRUSH_TICKS = 3;
    /** Aim on the stone past nine tenths of its step, short of the whole. */
    private static final int PAST_NINE_TENTHS_TICKS = 14;
    /** Room for a block to finish its step, and then some. */
    private static final int FINISH_TICKS = STEP_TICKS + 10;
    private static final Identifier NETHER_DECAY = Identifier.parse("goo:nether_decay");
    private static final String ABILITY_REQUIRED = "Ability registry must hold nether_decay";
    private static final String SHOULD_BITE = "Decay's swarm should bite the zombie in its cone";
    /** A zombie stands two blocks out, in the cone the player aims at the stone three out. */
    private static final BlockPos IN_THE_CONE = STAND_POS.east(2);

    private DecayStreamTests() {
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
     * A mock player holds decay on stone past nine tenths of its step and lets go:
     * the stone finishes its step to cobblestone on its own.
     *
     * @param helper the gametest helper
     */
    public static void decayPastNineTenthsFinishesAfterRelease(GameTestHelper helper) {
        ServerPlayer player = decayerOverStone(helper);
        hold(helper, player, 1, PAST_NINE_TENTHS_TICKS);
        helper.runAfterDelay(PAST_NINE_TENTHS_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.STONE, TARGET_POS);
        });
        helper.runAfterDelay(PAST_NINE_TENTHS_TICKS + FINISH_TICKS, () -> {
            helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET_POS);
            helper.succeed();
        });
    }

    /**
     * A mock player brushes decay across stone for a few ticks, short of half
     * its step, then a zombie steps into the cone and the swarm bites it while
     * the hold goes on: the painted stone keeps stepping to cobblestone
     * through the bite (the operator's ruling on decision
     * decay-gnats-degrade-each-block-once).
     *
     * @param helper the gametest helper
     */
    public static void decayPaintedBlockStepsThroughABite(GameTestHelper helper) {
        ServerPlayer player = decayerOverStone(helper);
        helper.setBlock(IN_THE_CONE.below(), Blocks.BEDROCK);
        hold(helper, player, 1, BRUSH_TICKS + FINISH_TICKS);
        Mob[] zombie = new Mob[1];
        helper.runAfterDelay(BRUSH_TICKS + 1, () -> {
            zombie[0] = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, IN_THE_CONE);
            zombie[0].setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        });
        helper.runAfterDelay(BRUSH_TICKS + FINISH_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(zombie[0].getHealth() < zombie[0].getMaxHealth(), SHOULD_BITE);
            helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET_POS);
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
