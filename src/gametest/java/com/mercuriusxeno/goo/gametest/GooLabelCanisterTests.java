package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.fluid.PlayerInventorySlotHandler;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.hub.HubFluidHandler;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Gametests for the goo label on a canister (decisions label-goes-on-an-empty-or-matching-vessel
 * and labeled-vessel-accepts-only-its-label-type): a label goes on by world click, by craft and by
 * inventory click, and a labeled canister takes its label's type alone at every insert seam.
 */
@RegistersGameTests
public final class GooLabelCanisterTests {
    private static final String LABEL_CLICK_ON_CANISTER = "label_click_on_canister";
    private static final String LABEL_CRAFTS_ONTO_CANISTER = "label_crafts_onto_canister";
    private static final String LABEL_CLICK_ON_CANISTER_ITEM = "label_click_on_canister_item";
    private static final String LABELED_CANISTER_REFUSES_OTHER_FLUIDS = "labeled_canister_refuses_other_fluids";
    private static final String LABELED_CORNER_FEEDS_THE_REACTOR = "labeled_corner_feeds_the_reactor";

    private static final String REMOVAL = "removal";
    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos INPUT_POS = BE_POS.above();
    private static final int CENTER_SLOT = 4;
    private static final int LEAF_CORNER = 0;
    private static final int BLAZE_CORNER = 2;
    private static final int GRID_SIDE = 3;
    private static final int LABELS_HELD = 2;
    private static final int VOLUME = 200;
    private static final int INPUT_AMOUNT = 1000;
    private static final int REACTION_TICKS = 5;
    private static final int INVENTORY_SLOT = 9;
    private static final int HUB_SLOT = 0;

    private GooLabelCanisterTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LABEL_CLICK_ON_CANISTER, GooLabelCanisterTests::clickOnCanister);
        GooTestFunctions.registerTest(registrar, LABEL_CRAFTS_ONTO_CANISTER, GooLabelCanisterTests::craftsOntoCanister);
        GooTestFunctions.registerTest(registrar, LABEL_CLICK_ON_CANISTER_ITEM, GooLabelCanisterTests::clickOnCanisterItem);
        GooTestFunctions.registerTest(registrar, LABELED_CANISTER_REFUSES_OTHER_FLUIDS,
                GooLabelCanisterTests::refusesOtherFluids);
        GooTestFunctions.registerTest(registrar, LABELED_CORNER_FEEDS_THE_REACTOR, GooLabelCanisterTests::cornerFeedsReactor);
    }

    /**
     * A right click with a blaze label on a placed empty canister labels it blaze and
     * takes one label from the hand.
     *
     * @param helper the gametest helper
     */
    public static void clickOnCanister(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity be = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);
        be.insertCanister(CENTER_SLOT, new ItemStack(GooItems.CANISTER.get()), false);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, labels(GooTypes.BLAZE, LABELS_HELD));

        BlockPos abs = helper.absolutePos(BE_POS);
        helper.useBlock(BE_POS, player, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));

        helper.assertTrue(labelOf(be.containerState().getCanister(CENTER_SLOT)) == GooTypes.BLAZE,
                "The clicked canister should carry the blaze label");
        helper.assertTrue(player.getMainHandItem().getCount() == LABELS_HELD - 1, "The click should take one label");
        helper.succeed();
    }

    /**
     * An empty canister beside a blaze label crafts a blaze-labeled canister and keeps no remainder;
     * a canister holding rock beside a blaze label crafts nothing.
     *
     * @param helper the gametest helper
     */
    public static void craftsOntoCanister(GameTestHelper helper) {
        CraftingInput input = grid(new ItemStack(GooItems.CANISTER.get()), labels(GooTypes.BLAZE, 1));
        RecipeHolder<CraftingRecipe> recipe = recipeFor(helper, input)
                .orElseThrow(() -> helper.assertionException("A canister beside a label should match a recipe"));
        ItemStack labeled = recipe.value().assemble(input);
        helper.assertTrue(labeled.is(GooItems.CANISTER.get()) && labelOf(labeled) == GooTypes.BLAZE,
                "The craft should yield a blaze-labeled canister: " + labeled);
        helper.assertTrue(recipe.value().getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
                "The craft should consume the label and leave nothing in the grid");

        ItemStack rockCanister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.addGoo(rockCanister, GooTypes.ROCK, VOLUME);
        helper.assertTrue(recipeFor(helper, grid(rockCanister, labels(GooTypes.BLAZE, 1))).isEmpty(),
                "A rock canister beside a blaze label should match nothing");
        helper.succeed();
    }

    /**
     * A blaze label clicked from the cursor onto an empty canister item labels it and
     * shrinks the cursor by one; onto a rock canister it does nothing.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void clickOnCanisterItem(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Slot slot = new Slot(player.getInventory(), INVENTORY_SLOT, 0, 0);
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        ItemStack[] cursor = {labels(GooTypes.BLAZE, LABELS_HELD)};
        boolean handled = canister.overrideOtherStackedOnMe(cursor[0], slot, ClickAction.PRIMARY, player,
                SlotAccess.of(() -> cursor[0], stack -> cursor[0] = stack));
        helper.assertTrue(handled && labelOf(canister) == GooTypes.BLAZE, "The click should label the canister");
        helper.assertTrue(cursor[0].getCount() == LABELS_HELD - 1, "The click should take one label from the cursor");

        ItemStack rockCanister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.addGoo(rockCanister, GooTypes.ROCK, VOLUME);
        ItemStack[] blazeCursor = {labels(GooTypes.BLAZE, 1)};
        boolean refused = rockCanister.overrideOtherStackedOnMe(blazeCursor[0], slot, ClickAction.PRIMARY, player,
                SlotAccess.of(() -> blazeCursor[0], stack -> blazeCursor[0] = stack));
        helper.assertTrue(!refused && labelOf(rockCanister) == null && blazeCursor[0].getCount() == 1
                        && CanisterItem.getFluidContent(rockCanister).volumeOf(GooTypes.ROCK) == VOLUME,
                "A rock canister should refuse a blaze label and leave both stacks as they stood");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A blaze-labeled canister takes blaze and refuses rock and water through the item
     * fluid handler, the hub slot handler and the player inventory slot handler.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void refusesOtherFluids(GameTestHelper helper) {
        FluidResource blaze = GooFluids.resource(GooTypes.BLAZE);
        FluidResource rock = GooFluids.resource(GooTypes.ROCK);
        FluidResource water = FluidResource.of(net.minecraft.world.level.material.Fluids.WATER);

        ItemStack canister = labeledCanister(GooTypes.BLAZE);
        ResourceHandler<FluidResource> item = canister.getCapability(Capabilities.Fluid.ITEM,
                ItemAccess.forStack(canister));
        helper.assertTrue(item != null, "A canister item should carry a fluid handler");
        assertTakesOnly(helper, "item handler", item, 0, blaze, List.of(rock, water));

        helper.setBlock(BE_POS, GooBlocks.HUB.get());
        HubBlockEntity hub = helper.getBlockEntity(BE_POS, HubBlockEntity.class);
        hub.insertCanister(HUB_SLOT, labeledCanister(GooTypes.BLAZE));
        HubFluidHandler hubHandler = hub.getFluidHandler();
        assertTakesOnly(helper, "hub handler", hubHandler, HUB_SLOT, blaze, List.of(rock, water));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(INVENTORY_SLOT, labeledCanister(GooTypes.BLAZE));
        PlayerInventorySlotHandler inventory = new PlayerInventorySlotHandler(player, INVENTORY_SLOT);
        int blazeTank = GooTypes.indexOf(GooTypes.BLAZE);
        int rockTank = GooTypes.indexOf(GooTypes.ROCK);
        helper.assertTrue(insert(inventory, blazeTank, blaze) == VOLUME, "inventory handler should take blaze");
        helper.assertTrue(!inventory.isValid(rockTank, rock) && insert(inventory, rockTank, rock) == 0,
                "inventory handler should refuse rock");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A reactor under a canister block whose corners are labeled leaf and blaze: blaze
     * routed first lands in the blaze corner rather than the first empty one, each corner
     * holds its label's type alone, and the blaze_from_blaze_leaf reaction runs.
     *
     * @param helper the gametest helper
     */
    public static void cornerFeedsReactor(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.REACTOR.get());
        ReactorBlockEntity reactor = helper.getBlockEntity(BE_POS, ReactorBlockEntity.class);
        helper.setBlock(INPUT_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity inputs = helper.getBlockEntity(INPUT_POS, CanisterBlockEntity.class);
        inputs.insertCanister(LEAF_CORNER, labeledCanister(GooTypes.LEAF), false);
        inputs.insertCanister(BLAZE_CORNER, labeledCanister(GooTypes.BLAZE), false);
        reactor.insertOutputCanister(new ItemStack(GooItems.CANISTER.get()));

        FluidResource blaze = GooFluids.resource(GooTypes.BLAZE);
        FluidResource leaf = GooFluids.resource(GooTypes.LEAF);
        inputs.containerState().routeFluid(blaze, INPUT_AMOUNT);
        inputs.containerState().routeFluid(leaf, INPUT_AMOUNT);
        helper.assertTrue(holdsAlone(inputs, LEAF_CORNER, leaf), "The leaf corner should hold leaf alone");
        helper.assertTrue(holdsAlone(inputs, BLAZE_CORNER, blaze), "The blaze corner should hold blaze alone");

        helper.runAfterDelay(REACTION_TICKS, () -> {
            CanisterFluidContent output = CanisterItem.getFluidContent(reactor.getOutputCanister());
            helper.assertTrue(output.volumeOf(blaze) > 0, "The reaction should run and fill the output with blaze");
            helper.succeed();
        });
    }

    private static boolean holdsAlone(CanisterBlockEntity be, int slot, FluidResource fluid) {
        CanisterFluidContent content = CanisterItem.getFluidContent(be.containerState().getCanister(slot));
        return content.portions().size() == 1 && content.volumeOf(fluid) > 0;
    }

    private static void assertTakesOnly(GameTestHelper helper, String seam, ResourceHandler<FluidResource> handler,
                                        int index, FluidResource taken, List<FluidResource> refused) {
        helper.assertTrue(handler.isValid(index, taken) && insert(handler, index, taken) == VOLUME,
                seam + " should take its label's type");
        for (FluidResource other : refused) {
            helper.assertTrue(!handler.isValid(index, other) && insert(handler, index, other) == 0,
                    seam + " should refuse " + other);
        }
    }

    private static int insert(ResourceHandler<FluidResource> handler, int index, FluidResource fluid) {
        try (var tx = Transaction.openRoot()) {
            int inserted = handler.insert(index, fluid, VOLUME, tx);
            tx.commit();
            return inserted;
        }
    }

    private static ItemStack labeledCanister(ResourceKey<GooTypeDefinition> type) {
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setMetadata(canister, CanisterMetadata.EMPTY.withGooLabel(type));
        return canister;
    }

    private static ItemStack labels(ResourceKey<GooTypeDefinition> type, int count) {
        return GooItems.GOO_LABEL.get().labelOf(type).copyWithCount(count);
    }

    private static ResourceKey<GooTypeDefinition> labelOf(ItemStack canister) {
        return CanisterItem.getMetadata(canister).gooLabel();
    }

    private static CraftingInput grid(ItemStack first, ItemStack second) {
        List<ItemStack> cells = new ArrayList<>(Collections.nCopies(GRID_SIDE * GRID_SIDE, ItemStack.EMPTY));
        cells.set(0, first);
        cells.set(1, second);
        return CraftingInput.of(GRID_SIDE, GRID_SIDE, cells);
    }

    private static Optional<RecipeHolder<CraftingRecipe>> recipeFor(GameTestHelper helper, CraftingInput input) {
        ServerLevel level = helper.getLevel();
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
    }
}
