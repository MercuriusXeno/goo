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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for nether decay: a held stream of gnats steps the stone it
 * reaches to cobblestone and, within the same hold, no further, though the
 * hold runs long enough for a second step
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
    private static final int HELD_GOO = 6;
    /** nether_decay.json's degrade ticks: thirty ticks of swarm step a block. */
    private static final int STEP_TICKS = 30;
    /** Past two steps' worth of swarm, so a second step would have landed without the once rule. */
    private static final int HOLD_TICKS = 2 * STEP_TICKS + 10;
    private static final Identifier NETHER_DECAY = Identifier.parse("goo:nether_decay");
    private static final String ABILITY_REQUIRED = "Ability registry must hold nether_decay";

    private DecayStreamTests() {
    }

    /**
     * A mock player holds decay on stone for more than two steps' worth of
     * swarm: the stone stands as cobblestone, not gravel.
     *
     * @param helper the gametest helper
     */
    public static void decayDegradesOncePerActivation(GameTestHelper helper) {
        helper.setBlock(TARGET_POS.below(), Blocks.BEDROCK);
        helper.setBlock(TARGET_POS, Blocks.STONE);
        ServerPlayer player = decayer(helper);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.NETHER), NETHER_DECAY.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 0; held < HOLD_TICKS; held++) {
            helper.runAfterDelay(1 + held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET_POS);
            helper.succeed();
        });
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
