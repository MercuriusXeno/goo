package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametest for rock bore: streaming bore east into a stone wall for sixty
 * ticks cuts a one-wide tunnel along the eye line to the stream's reach,
 * dropping cobblestone, and leaves the stone around the tunnel and the
 * obsidian past its reach standing
 * (decision bore-vortex-with-a-worldspace-shake).
 */
public final class BoreStreamTests {

    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** The row the eye line runs along: a standing player's eye sits in the block above its feet. */
    private static final BlockPos EYE_ROW = STAND_POS.above();
    /** rock_bore.json's range: the tunnel runs from the eye's block to four blocks east. */
    private static final int REACH = 4;
    private static final int HELD_GOO = 4;
    private static final int HOLD_TICKS = 60;
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    private static final double ITEM_SEARCH_RADIUS = 4;
    private static final Identifier ROCK_BORE = Identifier.parse("goo:rock_bore");
    private static final String ABILITY_REQUIRED = "Ability registry must hold rock_bore";

    private BoreStreamTests() {
    }

    /**
     * A mock player facing east streams bore into a stone wall for sixty
     * ticks: every block of the eye row to the reach is cut, the stone above,
     * below and beside it stands, the obsidian past the reach stands, and
     * cobblestone dropped.
     *
     * @param helper the gametest helper
     */
    public static void boreCutsATunnel(GameTestHelper helper) {
        AbilityDefinition bore = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_BORE);
        helper.assertTrue(bore != null, ABILITY_REQUIRED);
        for (int east = 1; east <= REACH; east++) {
            BlockPos tunnel = EYE_ROW.east(east);
            for (BlockPos wall : new BlockPos[] {tunnel, tunnel.above(), tunnel.below(), tunnel.north(),
                    tunnel.south()}) {
                helper.setBlock(wall, Blocks.STONE);
            }
        }
        BlockPos pastReach = EYE_ROW.east(REACH + 1);
        helper.setBlock(pastReach, Blocks.OBSIDIAN);
        ServerPlayer player = borer(helper);
        KnownRecipes.teachRequires(player, bore);
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.ROCK), ROCK_BORE.toString(),
                player.getEyePosition(), player.getEyePosition(), player.getY());
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            for (int east = 1; east <= REACH; east++) {
                BlockPos tunnel = EYE_ROW.east(east);
                helper.assertBlockPresent(Blocks.AIR, tunnel);
                for (Direction side : new Direction[] {Direction.UP, Direction.DOWN, Direction.NORTH,
                        Direction.SOUTH}) {
                    helper.assertBlockPresent(Blocks.STONE, tunnel.relative(side));
                }
            }
            helper.assertBlockPresent(Blocks.OBSIDIAN, pastReach);
            helper.assertItemEntityPresent(Items.COBBLESTONE, EYE_ROW.east(2), ITEM_SEARCH_RADIUS);
            helper.succeed();
        });
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
