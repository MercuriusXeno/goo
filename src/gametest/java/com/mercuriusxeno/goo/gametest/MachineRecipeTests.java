package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Gametests for the machine crafting recipes: each resolves a shaped grid
 * through the level's recipe access, the path a crafting table takes.
 */
@RegistersGameTests
public final class MachineRecipeTests {
    private static final String MACHINES_CRAFTED_FROM_GOLD = "machine_recipe_machines_crafted_from_gold";

    private static final int GRID_SIDE = 3;

    private static final String GOLD_MACHINE_NOT_CRAFTED = "Its gold grid should craft one ";

    private MachineRecipeTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, MACHINES_CRAFTED_FROM_GOLD, MachineRecipeTests::machinesCraftedFromGold);
    }

    /**
     * The reactor, plexer, crucible, hub, vat and canister each craft from
     * their grid with gold in every cell copper held; MachineRecipesTest holds
     * the gold key each recipe names.
     * decision copper-to-gold-one-to-one-scrap-stays
     *
     * @param helper the gametest helper
     */
    public static void machinesCraftedFromGold(GameTestHelper helper) {
        for (GoldMachineGrid machine : goldMachineGrids()) {
            ItemStack fromGold = craft(helper, grid(machine.pattern(), withMetal(machine.rest(), machine.gold())));
            helper.assertTrue(fromGold.is(machine.result()) && fromGold.getCount() == 1,
                    GOLD_MACHINE_NOT_CRAFTED + machine.result());
        }
        helper.succeed();
    }

    /**
     * One machine's shaped grid: M marks each cell copper held and gold holds now.
     */
    private record GoldMachineGrid(Item result, String[] pattern, Map<Character, Item> rest, Item gold) {
    }

    private static List<GoldMachineGrid> goldMachineGrids() {
        return List.of(
                new GoldMachineGrid(GooItems.REACTOR.get(), new String[] {"MRM", "NCN", "MMM"},
                        Map.of('R', Items.COMPARATOR, 'N', Items.NETHERITE_SCRAP, 'C', Items.CAULDRON),
                        Items.GOLD_INGOT),
                new GoldMachineGrid(GooItems.PLEXER.get(), new String[] {"MRM", "NC ", "MMM"},
                        Map.of('R', Items.COMPARATOR, 'N', Items.NETHERITE_SCRAP, 'C', Items.CRAFTER),
                        Items.GOLD_INGOT),
                new GoldMachineGrid(GooItems.CRUCIBLE.get(), new String[] {"M M", "NCN", "MFM"},
                        Map.of('N', Items.NETHERITE_SCRAP, 'C', Items.CAULDRON, 'F', Items.BLAST_FURNACE),
                        Items.GOLD_INGOT),
                new GoldMachineGrid(GooItems.HUB.get(), new String[] {"MMM", " I ", "MMM"},
                        Map.of('I', Items.IRON_BARS),
                        Items.GOLD_INGOT),
                new GoldMachineGrid(GooItems.VAT.get(), new String[] {"QMQ", "G G", "QMQ"},
                        Map.of('Q', Items.QUARTZ_BLOCK, 'G', Items.GLASS),
                        Items.GOLD_BLOCK),
                new GoldMachineGrid(GooItems.CANISTER.get(), new String[] {"QMQ", "G G", "QMQ"},
                        Map.of('Q', Items.QUARTZ, 'G', Items.GLASS_PANE),
                        Items.GOLD_INGOT));
    }

    private static Map<Character, Item> withMetal(Map<Character, Item> rest, Item metal) {
        Map<Character, Item> key = new HashMap<>(rest);
        key.put('M', metal);
        return key;
    }

    private static List<ItemStack> grid(String[] pattern, Map<Character, Item> key) {
        List<ItemStack> cells = new ArrayList<>(GRID_SIDE * GRID_SIDE);
        for (String row : pattern) {
            for (char symbol : row.toCharArray()) {
                Item item = key.get(symbol);
                cells.add(item == null ? ItemStack.EMPTY : new ItemStack(item));
            }
        }
        return cells;
    }

    private static ItemStack craft(GameTestHelper helper, List<ItemStack> cells) {
        CraftingInput grid = CraftingInput.of(GRID_SIDE, GRID_SIDE, cells);
        ServerLevel level = helper.getLevel();
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(holder -> holder.value().assemble(grid))
                .orElse(ItemStack.EMPTY);
    }
}
