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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Gametest for rock bore: streaming bore east into stone cuts a 3x3 tunnel
 * along the look to the stream's reach, each slice breaking its ring first and
 * its middle last at two blocks a tick, dropping cobblestone, and leaves the
 * stone past the reach standing (decision bore-vortex-with-a-worldspace-shake).
 */
public final class BoreStreamTests {

    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** The row the eye line runs along: a standing player's eye sits in the block above its feet. */
    private static final BlockPos EYE_ROW = STAND_POS.above();
    /** rock_bore.json's range: the tunnel runs from the eye's block to four blocks east. */
    private static final int REACH = 4;
    private static final int HELD_GOO = 4;
    /** The 3x3 to the reach is 36 blocks at two a tick: thirty ticks cut it whole. */
    private static final int HOLD_TICKS = 30;
    /** rock_bore.json's count. */
    private static final int BREAKS_A_TICK = 2;
    private static final String SHOULD_CUT_THE_RING_FIRST = "One tick should cut two of the first slice, cut %s";
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    private static final double ITEM_SEARCH_RADIUS = 4;
    private static final Identifier ROCK_BORE = Identifier.parse("goo:rock_bore");
    private static final String ABILITY_REQUIRED = "Ability registry must hold rock_bore";

    private BoreStreamTests() {
    }

    /**
     * A mock player facing east streams bore into a block of stone: after one
     * tick the first slice has lost two ring blocks and kept its middle; after
     * the hold a 3x3 tunnel runs from the eye's block to the reach, cobblestone
     * dropped, and the slice past the reach stands.
     *
     * @param helper the gametest helper
     */
    public static void boreCutsATunnel(GameTestHelper helper) {
        AbilityDefinition bore = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_BORE);
        helper.assertTrue(bore != null, ABILITY_REQUIRED);
        for (int east = 1; east <= REACH + 1; east++) {
            for (BlockPos pos : slice(EYE_ROW.east(east))) {
                helper.setBlock(pos, Blocks.STONE);
            }
        }
        ServerPlayer player = borer(helper);
        KnownRecipes.teachRequires(player, bore);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.ROCK), ROCK_BORE.toString(),
                player.getEyePosition(), player.getEyePosition());
        helper.runAfterDelay(1, () -> {
            GooStreamHandler.streamTick(player, tick);
            long cut = slice(EYE_ROW.east(1)).stream().filter(pos -> helper.getBlockState(pos).isAir()).count();
            helper.assertTrue(cut == BREAKS_A_TICK, String.format(SHOULD_CUT_THE_RING_FIRST, cut));
            helper.assertBlockPresent(Blocks.STONE, EYE_ROW.east(1));
        });
        for (int held = 2; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            for (int east = 1; east <= REACH; east++) {
                for (BlockPos pos : slice(EYE_ROW.east(east))) {
                    helper.assertBlockPresent(Blocks.AIR, pos);
                }
            }
            for (BlockPos pos : slice(EYE_ROW.east(REACH + 1))) {
                helper.assertBlockPresent(Blocks.STONE, pos);
            }
            helper.assertItemEntityPresent(Items.COBBLESTONE, EYE_ROW.east(2), ITEM_SEARCH_RADIUS);
            helper.succeed();
        });
    }

    /**
     * The 3x3 slice square to x about a block.
     *
     * @param middle the slice's middle, relative
     * @return the nine blocks
     */
    private static List<BlockPos> slice(BlockPos middle) {
        List<BlockPos> slice = new ArrayList<>();
        for (int up = -1; up <= 1; up++) {
            for (int across = -1; across <= 1; across++) {
                slice.add(middle.offset(0, up, across));
            }
        }
        return slice;
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer borer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.ROCK, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
