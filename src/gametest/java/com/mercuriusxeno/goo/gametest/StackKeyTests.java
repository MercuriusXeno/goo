package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametest for a thrown goo meeting a standing marker of another ability:
 * the marker is a target, a fuse stall and a stack position only for a
 * throw naming its ability id (decision diagnose-then-fix-stack-key-match).
 */
public final class StackKeyTests {

    private static final String REMOVAL = "removal";
    private static final BlockPos MARKER_POS = new BlockPos(1, 2, 1);
    private static final BlockPos PLAYER_POS = new BlockPos(1, 2, 4);
    private static final Identifier TIMED_BOMB = Identifier.fromNamespaceAndPath(Goo.MODID, "unstable_timed_bomb");
    private static final Identifier PROXIMITY_MINE = Identifier.fromNamespaceAndPath(Goo.MODID, "unstable_proximity_mine");
    private static final int NO_TARGET_ENTITY = -1;
    private static final int UNSTABLE_GOO = 10;
    private static final int THOUSAND = 1000;
    /** Ticks the marker's fuse runs down before the throw, so a stall reads as a rise. */
    private static final int FUSE_RUNDOWN_TICKS = 5;
    /** Ticks past the arc from the player to the marker, short of the fuse left. */
    private static final int ARRIVAL_TICKS = 8;

    private static final String ABILITIES_REQUIRED = "Ability registry must hold unstable_timed_bomb and unstable_proximity_mine";
    private static final String FUSE_STALLED = "A throw naming another ability should leave the marker's fuse at %d, read %d";
    private static final String PRICED_AT_MARKER = "A throw naming another ability should cost %d mB (stack 0), spent %d";
    private static final String STACKED = "A throw naming another ability should leave the marker at %d stacks, read %d";

    private StackKeyTests() {
    }

    /**
     * An unstable_proximity_mine throw at a standing unstable_timed_bomb marker holding two
     * stacks leaves the marker's fuse and stack count unchanged and is
     * priced at stack 0.
     *
     * @param helper the gametest helper
     */
    public static void otherAbilityThrowLeavesMarker(GameTestHelper helper) {
        AbilityDefinition bomb = AbilityRegistry.of(helper.getLevel()).getAbility(TIMED_BOMB);
        AbilityDefinition mine = AbilityRegistry.of(helper.getLevel()).getAbility(PROXIMITY_MINE);
        helper.assertTrue(bomb != null && mine != null, ABILITIES_REQUIRED);
        ChainMarkerBlockEntity marker = placeStackedMarker(helper, bomb);
        int stacks = marker.getStackCount();
        ServerPlayer player = makeUnstableThrower(helper);

        helper.runAfterDelay(FUSE_RUNDOWN_TICKS, () -> {
            throwMineAtMarker(helper, player, marker, mine);
            helper.runAfterDelay(ARRIVAL_TICKS, () -> {
                int stacksAfter = marker.getStackCount();
                helper.getLevel().getServer().getPlayerList().remove(player);
                helper.assertTrue(stacksAfter == stacks, String.format(STACKED, stacks, stacksAfter));
                helper.succeed();
            });
        });
    }

    private static void throwMineAtMarker(GameTestHelper helper, ServerPlayer player,
                                          ChainMarkerBlockEntity marker, AbilityDefinition mine) {
        int fuse = marker.getFuseRemaining();
        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.UNSTABLE), NO_TARGET_ENTITY,
                helper.absolutePos(MARKER_POS), Direction.DOWN.ordinal(), false, PROXIMITY_MINE.toString(),
                player.getEyePosition()));
        int spent = UNSTABLE_GOO * THOUSAND
                - GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.UNSTABLE, 0);
        int fuseAfter = marker.getFuseRemaining();
        helper.assertTrue(fuseAfter == fuse, String.format(FUSE_STALLED, fuse, fuseAfter));
        int firstThrow = mine.throwCost(0);
        helper.assertTrue(spent == firstThrow, String.format(PRICED_AT_MARKER, firstThrow, spent));
    }

    private static ChainMarkerBlockEntity placeStackedMarker(GameTestHelper helper, AbilityDefinition ability) {
        helper.setBlock(MARKER_POS.below(), Blocks.STONE);
        helper.setBlock(MARKER_POS, GooBlocks.CHAIN_MARKER.get());
        ChainMarkerBlockEntity marker = helper.getBlockEntity(MARKER_POS, ChainMarkerBlockEntity.class);
        marker.initChainFromAbility(GooTypes.UNSTABLE, Direction.UP, ability);
        marker.tryStack();
        return marker;
    }

    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    static ServerPlayer makeUnstableThrower(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos stand = helper.absolutePos(PLAYER_POS);
        player.setPos(stand.getX(), stand.getY(), stand.getZ());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.UNSTABLE, UNSTABLE_GOO * GooStacks.THOUSAND));
        return player;
    }
}
