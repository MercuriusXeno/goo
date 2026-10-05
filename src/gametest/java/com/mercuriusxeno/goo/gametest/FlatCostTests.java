package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
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
    private static final Identifier CRYSTAL_CLOUD = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_cloud");
    private static final int NO_TARGET_ENTITY = -1;

    private static final String ABILITY_REQUIRED = "Ability registry must hold crystal_cloud";
    private static final String CHARGED = "Throw %d at the marker should cost the JSON's %d mB, spent %d";

    private FlatCostTests() {
    }

    /**
     * Two crystal_cloud throws at a standing crystal_cloud marker each cost
     * crystal_cloud.json's cost.
     *
     * @param helper the gametest helper
     */
    public static void secondThrowCostsTheSameAsTheFirst(GameTestHelper helper) {
        AbilityDefinition cloud = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_CLOUD);
        helper.assertTrue(cloud != null, ABILITY_REQUIRED);
        placeMarker(helper, cloud);
        ServerPlayer player = StackKeyTests.makeThrower(helper, GooTypes.CRYSTAL);
        KnownRecipes.teachRequires(player, cloud);
        int held = crystalHeld(player);

        for (int throwNumber = 1; throwNumber <= 2; throwNumber++) {
            GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.CRYSTAL), NO_TARGET_ENTITY,
                    helper.absolutePos(MARKER_POS), Direction.DOWN.ordinal(), false, CRYSTAL_CLOUD.toString(),
                    player.getEyePosition()));
            int after = crystalHeld(player);
            helper.assertTrue(held - after == cloud.cost(),
                    String.format(CHARGED, throwNumber, cloud.cost(), held - after));
            held = after;
        }
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static int crystalHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.CRYSTAL, 0);
    }

    private static void placeMarker(GameTestHelper helper, AbilityDefinition ability) {
        helper.setBlock(MARKER_POS.below(), Blocks.STONE);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(MARKER_POS.below()), GooTypes.CRYSTAL, Direction.UP,
                ability);
    }
}
