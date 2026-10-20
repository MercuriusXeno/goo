package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.VatBlockItem;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooDataComponents;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Gametests for the goo label on a vat (decisions label-goes-on-an-empty-or-matching-vessel
 * and labeled-vessel-accepts-only-its-label-type): a label goes on by world click, craft and
 * inventory click and rides the vat through a break and a placement, a labeled vat takes its
 * type alone, and in a stack it takes only its type from redistribution.
 */
@RegistersGameTests
public final class GooLabelVatTests {
    private static final String LABEL_CLICK_ON_VAT = "label_click_on_vat";
    private static final String LABEL_ONTO_VAT_ITEM = "label_onto_vat_item";
    private static final String LABELED_VAT_REFUSES_OTHER_FLUIDS = "labeled_vat_refuses_other_fluids";
    private static final String LABELED_VAT_IN_A_STACK = "labeled_vat_in_a_stack";

    private static final String REMOVAL = "removal";
    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final int GRID_SIDE = 3;
    private static final int LABELS_HELD = 2;
    private static final int VOLUME = 200;
    private static final int INVENTORY_SLOT = 9;
    private static final int SETTLE_TICKS = 2;
    private static final int STACKED_VATS = 2;

    private GooLabelVatTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LABEL_CLICK_ON_VAT, GooLabelVatTests::clickOnVat);
        GooTestFunctions.registerTest(registrar, LABEL_ONTO_VAT_ITEM, GooLabelVatTests::ontoVatItem);
        GooTestFunctions.registerTest(registrar, LABELED_VAT_REFUSES_OTHER_FLUIDS, GooLabelVatTests::refusesOtherFluids);
        GooTestFunctions.registerTest(registrar, LABELED_VAT_IN_A_STACK, GooLabelVatTests::inAStack);
    }

    /**
     * A right click with a blaze label labels a placed empty vat and takes one label; the
     * label rides the vat's drop when broken and the vat placed from that drop.
     *
     * @param helper the gametest helper
     */
    public static void clickOnVat(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.VAT.get());
        VatBlockEntity vat = helper.getBlockEntity(BE_POS, VatBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, labels(GooTypes.BLAZE, LABELS_HELD));
        BlockPos abs = helper.absolutePos(BE_POS);
        helper.useBlock(BE_POS, player, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));

        helper.assertTrue(vat.getGooLabel() == GooTypes.BLAZE, "The clicked vat should carry the blaze label");
        helper.assertTrue(player.getMainHandItem().getCount() == LABELS_HELD - 1, "The click should take one label");

        ServerLevel level = helper.getLevel();
        BlockState state = level.getBlockState(abs);
        ItemStack drop = Block.getDrops(state, level, abs, vat).stream()
                .filter(stack -> stack.is(GooItems.VAT.get())).findFirst()
                .orElseThrow(() -> helper.assertionException("A broken vat should drop a vat"));
        helper.assertTrue(VatBlockItem.getGooLabel(drop) == GooTypes.BLAZE, "The dropped vat should carry the label");

        helper.setBlock(BE_POS, net.minecraft.world.level.block.Blocks.AIR);
        helper.setBlock(BE_POS, GooBlocks.VAT.get());
        VatBlockEntity placed = helper.getBlockEntity(BE_POS, VatBlockEntity.class);
        placed.applyComponentsFromItemStack(drop);
        helper.assertTrue(placed.getGooLabel() == GooTypes.BLAZE, "The vat placed from the drop should carry the label");
        helper.succeed();
    }

    /**
     * A vat item beside a blaze label crafts a blaze-labeled vat; a blaze label clicked from
     * the cursor onto an empty vat item labels it and shrinks the cursor by one, and onto a
     * vat item holding rock does nothing.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void ontoVatItem(GameTestHelper helper) {
        CraftingInput input = grid(new ItemStack(GooItems.VAT.get()), labels(GooTypes.BLAZE, 1));
        RecipeHolder<CraftingRecipe> recipe = recipeFor(helper, input)
                .orElseThrow(() -> helper.assertionException("A vat beside a label should match a recipe"));
        ItemStack crafted = recipe.value().assemble(input);
        helper.assertTrue(crafted.is(GooItems.VAT.get()) && VatBlockItem.getGooLabel(crafted) == GooTypes.BLAZE,
                "The craft should yield a blaze-labeled vat: " + crafted);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Slot slot = new Slot(player.getInventory(), INVENTORY_SLOT, 0, 0);
        ItemStack vat = new ItemStack(GooItems.VAT.get());
        ItemStack[] cursor = {labels(GooTypes.BLAZE, LABELS_HELD)};
        boolean handled = vat.overrideOtherStackedOnMe(cursor[0], slot, ClickAction.PRIMARY, player,
                SlotAccess.of(() -> cursor[0], stack -> cursor[0] = stack));
        helper.assertTrue(handled && VatBlockItem.getGooLabel(vat) == GooTypes.BLAZE, "The click should label the vat");
        helper.assertTrue(cursor[0].getCount() == LABELS_HELD - 1, "The click should take one label from the cursor");

        ItemStack rockVat = new ItemStack(GooItems.VAT.get());
        VatBlockItem.addGoo(rockVat, GooTypes.ROCK, VOLUME);
        ItemStack[] blazeCursor = {labels(GooTypes.BLAZE, 1)};
        boolean refused = rockVat.overrideOtherStackedOnMe(blazeCursor[0], slot, ClickAction.PRIMARY, player,
                SlotAccess.of(() -> blazeCursor[0], stack -> blazeCursor[0] = stack));
        helper.assertTrue(!refused && VatBlockItem.getGooLabel(rockVat) == null && blazeCursor[0].getCount() == 1
                        && VatBlockItem.getGooContents(rockVat).getVolume(GooTypes.ROCK) == VOLUME,
                "A rock vat should refuse a blaze label and leave both stacks as they stood");

        ItemStack vatStack = new ItemStack(GooItems.VAT.get(), STACKED_VATS);
        ItemStack[] stackCursor = {labels(GooTypes.BLAZE, 1)};
        boolean stackHandled = vatStack.overrideOtherStackedOnMe(stackCursor[0], slot, ClickAction.PRIMARY, player,
                SlotAccess.of(() -> stackCursor[0], stack -> stackCursor[0] = stack));
        helper.assertTrue(!stackHandled && VatBlockItem.getGooLabel(vatStack) == null && stackCursor[0].getCount() == 1,
                "One label should label no stack of " + STACKED_VATS + " vats: " + vatStack);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A blaze-labeled vat takes blaze and refuses rock and water through insertGoo and its fluid
     * handler, and a blaze-labeled vat item takes blaze and refuses rock.
     *
     * @param helper the gametest helper
     */
    public static void refusesOtherFluids(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.VAT.get());
        VatBlockEntity vat = helper.getBlockEntity(BE_POS, VatBlockEntity.class);
        vat.applyGooLabel(GooTypes.BLAZE);
        helper.assertTrue(vat.insertGoo(GooTypes.BLAZE, VOLUME) == VOLUME, "The labeled vat should take blaze");
        helper.assertTrue(vat.insertGoo(GooTypes.ROCK, VOLUME) == 0, "The labeled vat should refuse rock");
        helper.assertTrue(insert(vat, GooTypes.order().size(), FluidResource.of(Fluids.WATER)) == 0,
                "The labeled vat should refuse water");
        helper.assertTrue(insert(vat, GooTypes.indexOf(GooTypes.ROCK), GooFluids.resource(GooTypes.ROCK)) == 0,
                "The labeled vat's fluid handler should refuse rock");

        ItemStack item = new ItemStack(GooItems.VAT.get());
        item.set(GooDataComponents.GOO_LABEL.get(), GooTypes.BLAZE);
        helper.assertTrue(VatBlockItem.addGoo(item, GooTypes.BLAZE, VOLUME) == VOLUME, "The labeled vat item takes blaze");
        helper.assertTrue(VatBlockItem.addGoo(item, GooTypes.ROCK, VOLUME) == 0, "The labeled vat item refuses rock");
        helper.succeed();
    }

    /**
     * An unlabeled vat on top of a blaze-labeled vat: rock poured into the top vat stays there
     * when the stack redistributes, since the labeled vat below takes only blaze.
     *
     * @param helper the gametest helper
     */
    public static void inAStack(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.VAT.get());
        helper.setBlock(BE_POS.above(), GooBlocks.VAT.get());
        VatBlockEntity bottom = helper.getBlockEntity(BE_POS, VatBlockEntity.class);
        VatBlockEntity top = helper.getBlockEntity(BE_POS.above(), VatBlockEntity.class);
        bottom.applyGooLabel(GooTypes.BLAZE);
        top.insertGoo(GooTypes.ROCK, VOLUME);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            GooContents below = bottom.getContents();
            helper.assertTrue(below.getVolume(GooTypes.ROCK) == 0, "The blaze-labeled vat should hold no rock: " + below);
            helper.assertTrue(top.getContents().getVolume(GooTypes.ROCK) == VOLUME,
                    "The unlabeled vat should hold all the rock: " + top.getContents());
            helper.succeed();
        });
    }

    private static int insert(VatBlockEntity vat, int index, FluidResource fluid) {
        try (var tx = Transaction.openRoot()) {
            int inserted = vat.getFluidHandler().insert(index, fluid, VOLUME, tx);
            tx.commit();
            return inserted;
        }
    }

    private static ItemStack labels(ResourceKey<GooTypeDefinition> type, int count) {
        return GooItems.GOO_LABEL.get().labelOf(type).copyWithCount(count);
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
