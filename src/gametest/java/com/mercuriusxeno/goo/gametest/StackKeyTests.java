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
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametest for a thrown goo meeting a standing marker of another ability:
 * the marker is a target and a stack position only for a throw naming its
 * ability id (decision diagnose-then-fix-stack-key-match).
 */
public final class StackKeyTests {

    private static final String REMOVAL = "removal";
    private static final BlockPos MARKER_POS = new BlockPos(1, 2, 1);
    private static final BlockPos PLAYER_POS = new BlockPos(1, 2, 4);
    private static final Identifier CRYSTAL_CLOUD = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_cloud");
    private static final Identifier METAL_SPIKES = Identifier.fromNamespaceAndPath(Goo.MODID, "metal_spikes");
    private static final int NO_TARGET_ENTITY = -1;
    private static final int HELD_GOO = 10;
    private static final int THOUSAND = 1000;
    /** Ticks past the arc from the player to the marker. */
    private static final int ARRIVAL_TICKS = 8;

    private static final String ABILITIES_REQUIRED = "Ability registry must hold crystal_cloud and metal_spikes";
    private static final String PRICED_AT_MARKER = "A throw naming another ability should cost %d mB (stack 0), spent %d";
    private static final String STACKED = "A throw naming another ability should leave the marker at %d stacks, read %d";

    private StackKeyTests() {
    }

    /**
     * A metal_spikes throw at a standing crystal_cloud marker holding two
     * stacks leaves the marker's stack count unchanged and is priced at
     * stack 0.
     *
     * @param helper the gametest helper
     */
    public static void otherAbilityThrowLeavesMarker(GameTestHelper helper) {
        AbilityDefinition cloud = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_CLOUD);
        AbilityDefinition spikes = AbilityRegistry.of(helper.getLevel()).getAbility(METAL_SPIKES);
        helper.assertTrue(cloud != null && spikes != null, ABILITIES_REQUIRED);
        ChainMarkerBlockEntity marker = placeStackedMarker(helper, cloud);
        int stacks = marker.getStackCount();
        ServerPlayer player = makeThrower(helper, GooTypes.METAL);

        throwSpikesAtMarker(helper, player, spikes);
        helper.runAfterDelay(ARRIVAL_TICKS, () -> {
            int stacksAfter = marker.getStackCount();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(stacksAfter == stacks, String.format(STACKED, stacks, stacksAfter));
            helper.succeed();
        });
    }

    private static void throwSpikesAtMarker(GameTestHelper helper, ServerPlayer player, AbilityDefinition spikes) {
        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.METAL), NO_TARGET_ENTITY,
                helper.absolutePos(MARKER_POS), Direction.DOWN.ordinal(), false, METAL_SPIKES.toString(),
                player.getEyePosition()));
        int spent = HELD_GOO * THOUSAND
                - GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.METAL, 0);
        int firstThrow = spikes.throwCost(0);
        helper.assertTrue(spent == firstThrow, String.format(PRICED_AT_MARKER, firstThrow, spent));
    }

    private static ChainMarkerBlockEntity placeStackedMarker(GameTestHelper helper, AbilityDefinition ability) {
        helper.setBlock(MARKER_POS.below(), Blocks.STONE);
        helper.setBlock(MARKER_POS, GooBlocks.CHAIN_MARKER.get());
        ChainMarkerBlockEntity marker = helper.getBlockEntity(MARKER_POS, ChainMarkerBlockEntity.class);
        marker.initChainFromAbility(GooTypes.CRYSTAL, Direction.UP, ability);
        marker.splat();
        marker.tryStack();
        return marker;
    }

    /**
     * Stands a mock player holding the glove and ten thousand mB of one goo type.
     *
     * @param helper the gametest helper
     * @param type   the goo type the player holds
     * @return the player
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    static ServerPlayer makeThrower(GameTestHelper helper, ResourceKey<GooTypeDefinition> type) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos stand = helper.absolutePos(PLAYER_POS);
        player.setPos(stand.getX(), stand.getY(), stand.getZ());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(type, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
