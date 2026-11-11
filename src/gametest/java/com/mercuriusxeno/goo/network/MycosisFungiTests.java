package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.FungalBudBlock;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for Mycosis tending fungi: a held stream random-ticks the nether
 * wart and fungal buds in its cone, so wart planted three blocks ahead of the
 * player ripens and is reaped within the hold, dropping wart and standing
 * replanted at age 0, and a bud there grows into its mushroom.
 * mycosis-grows-and-reaps-nether-wart
 */
@RegistersGameTests
public final class MycosisFungiTests {
    private static final String MYCOSIS_REAPS_WART = "mycosis_grows_and_reaps_nether_wart";
    private static final String MYCOSIS_GROWS_BUDS = "mycosis_grows_fungal_buds";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, MYCOSIS_REAPS_WART, MycosisFungiTests::mycosisGrowsAndReapsNetherWart);
        GooTestFunctions.registerTest(registrar, MYCOSIS_GROWS_BUDS, MycosisFungiTests::mycosisGrowsFungalBuds);
    }


    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** Three blocks east of the player, at its height. */
    private static final BlockPos FUNGUS_POS = STAND_POS.east(3);
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward the fungus's middle three blocks off. */
    private static final float LOOKING_AT_FUNGUS = 20.5f;
    /** The longest hold; wart and buds under the stream ripen in a fraction of it. */
    private static final int HOLD_TICKS = 300;
    /** The stream costs a thousand a second; the player holds enough for the whole hold and then some. */
    private static final int HELD_GOO = 20;
    private static final double ITEM_SEARCH_RADIUS = 3;
    private static final Identifier SHROOM_MYCOSIS = Identifier.parse("goo:shroom_mycosis");
    private static final String ABILITY_REQUIRED = "Ability registry must hold %s";
    private static final String SHOULD_REPLANT = "Reaped wart should stand replanted at age 0 beside dropped wart, stands %s";
    private static final String SHOULD_MUSHROOM = "A bud under the stream should grow into a mushroom, stands %s";

    private MycosisFungiTests() {
    }

    /**
     * A mock player holds Mycosis on freshly planted nether wart on soul
     * sand: the wart ripens, is reaped, and its wart drops while a fresh wart
     * stands replanted at age 0.
     *
     * @param helper the gametest helper
     */
    public static void mycosisGrowsAndReapsNetherWart(GameTestHelper helper) {
        helper.setBlock(FUNGUS_POS.below(), Blocks.SOUL_SAND);
        helper.setBlock(FUNGUS_POS, Blocks.NETHER_WART);
        hold(helper, GooTypes.SHROOM, SHROOM_MYCOSIS, HOLD_TICKS);
        helper.succeedWhen(() -> {
            BlockState wart = helper.getBlockState(FUNGUS_POS);
            helper.assertTrue(wart.is(Blocks.NETHER_WART) && wart.getValue(NetherWartBlock.AGE) == 0,
                    String.format(SHOULD_REPLANT, wart));
            helper.assertItemEntityPresent(Items.NETHER_WART, FUNGUS_POS, ITEM_SEARCH_RADIUS);
        });
    }

    /**
     * A mock player holds Mycosis on a fresh fungal bud on stone, and the
     * cell holds a brown or red mushroom, no bud, within the hold.
     *
     * @param helper the gametest helper
     */
    public static void mycosisGrowsFungalBuds(GameTestHelper helper) {
        helper.setBlock(FUNGUS_POS.below(), Blocks.STONE);
        helper.setBlock(FUNGUS_POS, GooBlocks.FUNGAL_BUD.get().defaultBlockState().setValue(FungalBudBlock.AGE, 0));
        hold(helper, GooTypes.SHROOM, SHROOM_MYCOSIS, HOLD_TICKS);
        helper.succeedWhen(() -> {
            BlockState grown = helper.getBlockState(FUNGUS_POS);
            helper.assertTrue(grown.is(Blocks.BROWN_MUSHROOM) || grown.is(Blocks.RED_MUSHROOM),
                    String.format(SHOULD_MUSHROOM, grown));
        });
    }

    /**
     * Stands a mock player facing the fungus and holds a stream ability on it
     * for a number of ticks.
     *
     * @param helper    the gametest helper
     * @param gooType   the goo the ability spends
     * @param abilityId the stream ability held
     * @param ticks     the ticks it is held
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static void hold(GameTestHelper helper, ResourceKey<GooTypeDefinition> gooType,
                             Identifier abilityId, int ticks) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(abilityId);
        helper.assertTrue(ability != null, String.format(ABILITY_REQUIRED, abilityId));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_AT_FUNGUS);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(gooType, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, ability);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(gooType), abilityId.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= ticks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
    }
}
