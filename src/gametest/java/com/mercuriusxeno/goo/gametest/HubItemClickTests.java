package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.item.HubBlockItem;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for thousands and gooStacks clicked onto a hub item in inventory
 * (decision hub-item-goo-insert).
 */
@RegistersGameTests
public final class HubItemClickTests {
    private static final String IX_HUB_ITEM_GOO_INSERT = "ix_hub_item_goo_insert";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, IX_HUB_ITEM_GOO_INSERT, HubItemClickTests::gooInsertFillsCanisterAndPlaces);
    }


    private static final BlockPos FLOOR_POS = new BlockPos(1, 1, 1);
    private static final BlockPos HUB_POS = FLOOR_POS.above();
    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final ResourceKey<GooTypeDefinition> NETHER = GooTypes.NETHER;
    private static final int GOO_COUNT = 5;
    private static final double HALF_BLOCK = 0.5;
    private static final String REGISTERED_OVERRIDE = "Registered hub item carries the override";
    private static final String THOUSANDS_HANDLED = "Goo insert should be handled";
    private static final String CURSOR_EMPTIED = "Cursor should be empty once all its goo went in";
    private static final String CANISTER_TYPE = "Canister goo type";
    private static final String CANISTER_AMOUNT = "Canister amount";

    private HubItemClickTests() {
    }

    /**
     * A five-goo onto a hub holding an empty canister fills it, empties the cursor,
     * and a hub placed from that stack holds the goo in slot 0.
     *
     * @param helper the gametest helper
     */
    public static void gooInsertFillsCanisterAndPlaces(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hub = hubHolding(new ItemStack(GooItems.CANISTER.get()));
        CursorHolder cursor = new CursorHolder(GooStacks.createForOutput(ROCK, GOO_COUNT * GooStacks.THOUSAND));

        helper.assertTrue(GooItems.HUB.get() instanceof HubBlockItem, REGISTERED_OVERRIDE);
        helper.assertTrue(primaryClick(hub, cursor, player), THOUSANDS_HANDLED);
        assertCanister(helper, canistersOf(hub).getFirst(), ROCK, GOO_COUNT * GooStacks.THOUSAND);
        helper.assertTrue(cursor.get().isEmpty(), CURSOR_EMPTIED);

        helper.setBlock(FLOOR_POS, Blocks.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, hub);
        BlockPos floor = helper.absolutePos(FLOOR_POS);
        helper.useBlock(FLOOR_POS, player,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, HALF_BLOCK, 0), Direction.UP, floor, false));

        HubBlockEntity placed = helper.getBlockEntity(HUB_POS, HubBlockEntity.class);
        assertCanister(helper, placed.getCanister(0), ROCK, GOO_COUNT * GooStacks.THOUSAND);
        helper.succeed();
    }

    private static boolean primaryClick(ItemStack hub, CursorHolder cursor, Player player) {
        return hub.getItem().overrideOtherStackedOnMe(hub, cursor.get(),
                new Slot(player.getInventory(), 0, 0, 0), ClickAction.PRIMARY, player, cursor);
    }

    private static ItemStack hubHolding(ItemStack... canisters) {
        ItemStack hub = new ItemStack(GooItems.HUB.get());
        hub.set(GooDataComponents.HUB_CANISTERS.get(), List.of(canisters));
        return hub;
    }

    private static List<ItemStack> canistersOf(ItemStack hub) {
        return hub.getOrDefault(GooDataComponents.HUB_CANISTERS.get(), List.of());
    }

    private static void assertCanister(GameTestHelper helper, ItemStack canister,
            ResourceKey<GooTypeDefinition> type, int amount) {
        CanisterFluidContent content = CanisterItem.getFluidContent(canister);
        helper.assertValueEqual(content.dominantGooType(), type, CANISTER_TYPE);
        helper.assertValueEqual(content.totalVolume(), amount, CANISTER_AMOUNT);
    }
}
