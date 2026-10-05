package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametest for the flat cost: two throws at one marker through the real
 * throw path each deduct the one figure the ability's JSON names
 * (decision flat-cost-per-throw).
 */
public final class FlatCostTests {

    private static final BlockPos MARKER_POS = new BlockPos(1, 2, 1);
    private static final Identifier TIMED_BOMB = Identifier.fromNamespaceAndPath(Goo.MODID, "unstable_timed_bomb");
    private static final int NO_TARGET_ENTITY = -1;

    private static final String ABILITY_REQUIRED = "Ability registry must hold unstable_timed_bomb";
    private static final String CHARGED = "Throw %d at the marker should cost the JSON's %d mB, spent %d";

    private FlatCostTests() {
    }

    /**
     * Two unstable_timed_bomb throws at a standing unstable_timed_bomb marker each
     * cost unstable_timed_bomb.json's cost, the second at a marker already stacked.
     *
     * @param helper the gametest helper
     */
    public static void secondThrowCostsTheSameAsTheFirst(GameTestHelper helper) {
        AbilityDefinition bomb = AbilityRegistry.of(helper.getLevel()).getAbility(TIMED_BOMB);
        helper.assertTrue(bomb != null, ABILITY_REQUIRED);
        placeMarker(helper, bomb);
        ServerPlayer player = StackKeyTests.makeUnstableThrower(helper);
        int held = unstableHeld(player);

        for (int throwNumber = 1; throwNumber <= 2; throwNumber++) {
            GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.UNSTABLE), NO_TARGET_ENTITY,
                    helper.absolutePos(MARKER_POS), Direction.DOWN.ordinal(), false, TIMED_BOMB.toString(),
                    player.getEyePosition()));
            int after = unstableHeld(player);
            helper.assertTrue(held - after == bomb.cost(),
                    String.format(CHARGED, throwNumber, bomb.cost(), held - after));
            held = after;
        }
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static int unstableHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.UNSTABLE, 0);
    }

    private static void placeMarker(GameTestHelper helper, AbilityDefinition ability) {
        helper.setBlock(MARKER_POS.below(), Blocks.STONE);
        helper.setBlock(MARKER_POS, GooBlocks.CHAIN_MARKER.get());
        ChainMarkerBlockEntity marker = helper.getBlockEntity(MARKER_POS, ChainMarkerBlockEntity.class);
        marker.initChainFromAbility(GooTypes.UNSTABLE, Direction.UP, ability);
    }
}
