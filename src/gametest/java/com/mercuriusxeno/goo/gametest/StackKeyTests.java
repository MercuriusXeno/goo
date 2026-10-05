package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
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
 * Gametest for a goo thrown through the real throw path at a standing marker
 * of its own ability: it is priced at the ability's one cost and lands as
 * its own throw beside the marker, which runs on as it stood (decision
 * splat-runs-the-program-no-fuse).
 */
public final class StackKeyTests {

    private static final String REMOVAL = "removal";
    private static final BlockPos MARKER_POS = new BlockPos(1, 2, 1);
    private static final BlockPos PLAYER_POS = new BlockPos(1, 2, 4);
    private static final Identifier CRYSTAL_CLOUD = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_cloud");
    private static final int NO_TARGET_ENTITY = -1;
    private static final int HELD_GOO = 10;
    private static final int THOUSAND = 1000;
    /** Ticks past the arc from the player to the marker. */
    private static final int ARRIVAL_TICKS = 8;

    private static final String ABILITY_REQUIRED = "Ability registry must hold crystal_cloud";
    private static final String PRICED = "A second throw should cost the JSON's %d mB, spent %d";
    private static final String FIRST_CHANGED = "A second throw changed the standing marker";
    private static final String NOT_BESIDE = "A second throw landed no marker of its own beside the first";

    private StackKeyTests() {
    }

    /**
     * A crystal_cloud throw at a standing crystal_cloud marker costs the
     * JSON's cost, lands its own marker on top of the first, and leaves the
     * first running.
     *
     * @param helper the gametest helper
     */
    public static void secondThrowLandsItsOwnMarker(GameTestHelper helper) {
        AbilityDefinition cloud = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_CLOUD);
        helper.assertTrue(cloud != null, ABILITY_REQUIRED);
        AbilityBlockEntity first = placeMarker(helper, cloud);
        ServerPlayer player = makeThrower(helper, GooTypes.CRYSTAL);
        KnownRecipes.teachRequires(player, cloud);

        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.CRYSTAL), NO_TARGET_ENTITY,
                helper.absolutePos(MARKER_POS), Direction.UP.ordinal(), false, CRYSTAL_CLOUD.toString(),
                player.getEyePosition()));
        int spent = HELD_GOO * THOUSAND
                - GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.CRYSTAL, 0);
        helper.assertTrue(spent == cloud.cost(), String.format(PRICED, cloud.cost(), spent));
        helper.runAfterDelay(ARRIVAL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(helper.getBlockEntity(MARKER_POS, AbilityBlockEntity.class) == first, FIRST_CHANGED);
            AbilityBlockEntity second = helper.getBlockEntity(MARKER_POS.above(), AbilityBlockEntity.class);
            helper.assertTrue(second != first && second.getBehavior() != null, NOT_BESIDE);
            helper.succeed();
        });
    }

    private static AbilityBlockEntity placeMarker(GameTestHelper helper, AbilityDefinition ability) {
        helper.setBlock(MARKER_POS.below(), Blocks.STONE);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(MARKER_POS.below()), GooTypes.CRYSTAL, Direction.UP,
                ability);
        return helper.getBlockEntity(MARKER_POS, AbilityBlockEntity.class);
    }

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
