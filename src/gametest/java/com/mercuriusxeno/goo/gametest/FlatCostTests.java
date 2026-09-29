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
    private static final Identifier FROST_SPHERE = Identifier.fromNamespaceAndPath(Goo.MODID, "frost_sphere");
    private static final int NO_TARGET_ENTITY = -1;

    private static final String ABILITY_REQUIRED = "Ability registry must hold frost_sphere";
    private static final String CHARGED = "Throw %d at the marker should cost the JSON's %d mB, spent %d";

    private FlatCostTests() {
    }

    /**
     * Two frost_sphere throws at a standing frost_sphere marker each cost
     * frost_sphere.json's cost, the second at a marker already stacked.
     *
     * @param helper the gametest helper
     */
    public static void secondThrowCostsTheSameAsTheFirst(GameTestHelper helper) {
        AbilityDefinition sphere = AbilityRegistry.of(helper.getLevel()).getAbility(FROST_SPHERE);
        helper.assertTrue(sphere != null, ABILITY_REQUIRED);
        placeMarker(helper, sphere);
        ServerPlayer player = StackKeyTests.makeFrostThrower(helper);
        int held = frostHeld(player);

        for (int throwNumber = 1; throwNumber <= 2; throwNumber++) {
            GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.FROST), NO_TARGET_ENTITY,
                    helper.absolutePos(MARKER_POS), Direction.DOWN.ordinal(), false, FROST_SPHERE.toString(),
                    player.getEyePosition()));
            int after = frostHeld(player);
            helper.assertTrue(held - after == sphere.cost(),
                    String.format(CHARGED, throwNumber, sphere.cost(), held - after));
            held = after;
        }
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static int frostHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.FROST, 0);
    }

    private static void placeMarker(GameTestHelper helper, AbilityDefinition ability) {
        helper.setBlock(MARKER_POS.below(), Blocks.STONE);
        helper.setBlock(MARKER_POS, GooBlocks.CHAIN_MARKER.get());
        ChainMarkerBlockEntity marker = helper.getBlockEntity(MARKER_POS, ChainMarkerBlockEntity.class);
        marker.initChainFromAbility(GooTypes.FROST, Direction.UP, ability);
    }
}
