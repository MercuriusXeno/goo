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
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

/**
 * Gametest for Pulser: a hold aimed at a lever flips it again and again for
 * as long as the hold runs.
 * pulser-toggles-rapidly-while-held
 */
public final class PulserTests {

    private static final Identifier PULSER = Identifier.parse("goo:pulse_pulser");
    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** Three blocks ahead of the player, a lever on a stone post at eye height. */
    private static final BlockPos LEVER = STAND_POS.east(3).above();
    private static final float FACING_EAST = -90f;
    private static final float LEVEL_LOOK = 0f;
    private static final int HELD_GOO = 3;
    private static final int HOLD_TICKS = 40;
    /** pulse_pulser.json toggles every 4 held ticks, ten times in the hold; several is the fact. */
    private static final int SEVERAL_FLIPS = 5;
    private static final String ABILITY_REQUIRED = "Ability registry must hold pulse_pulser";
    private static final String FLIPS_SEVERAL = "The lever should flip several times in the hold, flipped %d times";

    private PulserTests() {
    }

    /**
     * A mock player holds Pulser at a lever for forty ticks: the lever flips
     * several times.
     *
     * @param helper the gametest helper
     */
    public static void pulserFlipsRepeatedly(GameTestHelper helper) {
        AbilityDefinition pulser = AbilityRegistry.of(helper.getLevel()).getAbility(PULSER);
        helper.assertTrue(pulser != null, ABILITY_REQUIRED);
        helper.setBlock(LEVER.below(), Blocks.STONE);
        helper.setBlock(LEVER, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
        ServerPlayer player = pulser(helper);
        KnownRecipes.teachRequires(player, pulser);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.PULSE), PULSER.toString(),
                player.getEyePosition(), player.getEyePosition());
        int[] flips = {0};
        boolean[] last = {powered(helper)};
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(player, tick);
                boolean now = powered(helper);
                if (now != last[0]) {
                    flips[0]++;
                    last[0] = now;
                }
            });
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(flips[0] >= SEVERAL_FLIPS, String.format(FLIPS_SEVERAL, flips[0]));
            helper.succeed();
        });
    }

    private static boolean powered(GameTestHelper helper) {
        return helper.getBlockState(LEVER).getValue(LeverBlock.POWERED);
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer pulser(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(LEVEL_LOOK);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.PULSE, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
