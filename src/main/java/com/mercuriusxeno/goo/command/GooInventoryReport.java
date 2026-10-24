package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.data.GooValueRecipeAdapter;
import com.mercuriusxeno.goo.data.RecipeInput;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Writes config/goo_inventory.json, every item, item tag, goo type and recipe the loadout registers; no block,
 * since the mod values a block only through its item (decision workbench-drops-the-blocks-list). The /goo inventory command and the inventory-dump gametest both call it, so a headless run and
 * a modded server each write the inventory the values workbench data-drives from
 * (decision inventory-dump-from-a-gametest-and-a-command). Each item names the image a slot capture draws at
 * goo_inventory/namespace/path.png; the report writes no image, since only a client's renderer draws an item as
 * its slot does.
 * decision images-are-slot-captures-taken-on-detection
 */
public final class GooInventoryReport {
    /**
     * The inventory file, under the config directory.
     */
    public static final String FILE_INVENTORY = "goo_inventory.json";
    /**
     * The image folder, under the config directory.
     */
    public static final String DIR_IMAGES = "goo_inventory";

    private static final String IMAGE_SUFFIX = ".png";
    private static final String PATH_SEPARATOR = "/";
    private static final String UNKNOWN_TYPE = "";
    private static final String GRID_SYMBOLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final char EMPTY_CELL = ' ';
    private static final String MSG_WRITTEN = "Inventory written to config/goo_inventory.json: ";
    private static final String MSG_ITEMS = " items, ";
    private static final String MSG_RECIPES = " recipes";

    private GooInventoryReport() {
    }

    /**
     * Runs /goo inventory: writes the inventory and reports its size to chat.
     *
     * @param ctx the command context
     * @return 1 on success
     */
    static int run(CommandContext<CommandSourceStack> ctx) {
        GooInventory inventory = writeInventory(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(MSG_WRITTEN + inventory.items().size() + MSG_ITEMS
                + inventory.recipes().size() + MSG_RECIPES).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /**
     * Collects the loadout from the server, writes the inventory file, and answers what it wrote. The
     * inventory-dump gametest calls it headless.
     *
     * @param server the running server
     * @return the inventory written
     */
    public static GooInventory writeInventory(MinecraftServer server) {
        return writeInventory(server, FMLPaths.CONFIGDIR.get());
    }

    /**
     * Writes the inventory file into a folder, as the in-game workbench writes it under the world
     * (decision goo-workbench-command-serves-the-page-from-the-game).
     *
     * @param server the running server
     * @param target the folder goo_inventory.json lands in, beside the goo_inventory/ image folder
     * @return the inventory written
     */
    public static GooInventory writeInventory(MinecraftServer server, Path target) {
        RegistryAccess registries = server.registryAccess();
        GooInventory inventory = new GooInventory(
                items(),
                tags(registries.lookupOrThrow(Registries.ITEM), BuiltInRegistries.ITEM),
                GooTypes.all(registries).stream().map(GooTypes::id).toList(),
                recipes(server.getRecipeManager().getRecipes(), registries));
        GooAuditReport.writeFile(target, FILE_INVENTORY, List.of(GooInventoryJson.toJson(inventory)));
        return inventory;
    }

    private static List<GooInventory.NamedEntry> items() {
        List<GooInventory.NamedEntry> items = new ArrayList<>();
        for (Identifier id : sortedKeys(BuiltInRegistries.ITEM)) {
            Item item = BuiltInRegistries.ITEM.getValue(id);
            String name = item.getName(item.getDefaultInstance()).getString();
            items.add(new GooInventory.NamedEntry(id.toString(), name, imagePath(id)));
        }
        return items;
    }

    /**
     * @param item the item's id
     * @return the path under the image folder its slot capture lands at
     */
    private static String imagePath(Identifier item) {
        return item.getNamespace() + PATH_SEPARATOR + item.getPath() + IMAGE_SUFFIX;
    }

    private static List<Identifier> sortedKeys(Registry<?> registry) {
        return registry.keySet().stream().sorted().toList();
    }

    /**
     * Reads every tag from the server's registry access, so a datapack's tags count beside the mods'.
     *
     * @param lookup   the server's lookup of the registry
     * @param registry the registry naming each member
     * @param <T>      the registry's element type
     * @return every tag with its members, sorted by id
     */
    private static <T> List<GooInventory.TagEntry> tags(HolderLookup.RegistryLookup<T> lookup, Registry<T> registry) {
        return lookup.listTags()
                .map(tag -> new GooInventory.TagEntry(tag.key().location().toString(), members(tag, registry)))
                .sorted(Comparator.comparing(GooInventory.TagEntry::id))
                .toList();
    }

    private static <T> List<String> members(HolderSet.Named<T> tag, Registry<T> registry) {
        return tag.stream().map(holder -> registry.getKey(holder.value()))
                .filter(Objects::nonNull).map(Identifier::toString).toList();
    }

    private static List<GooInventory.RecipeEntry> recipes(Iterable<RecipeHolder<?>> holders,
                                                          HolderLookup.Provider registries) {
        List<GooInventory.RecipeEntry> recipes = new ArrayList<>();
        for (RecipeHolder<?> holder : holders) {
            recipes.add(recipeEntry(holder.id().identifier().toString(), holder.value(), registries));
        }
        recipes.sort(Comparator.comparing(GooInventory.RecipeEntry::id));
        return recipes;
    }

    private static GooInventory.RecipeEntry recipeEntry(String id, Recipe<?> recipe,
                                                        HolderLookup.Provider registries) {
        Identifier type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        String typeId = type == null ? UNKNOWN_TYPE : type.toString();
        RecipeInput adapted = GooValueRecipeAdapter.adaptRecipe(recipe, registries);
        GooInventory.ShapedGrid grid = recipe instanceof ShapedRecipe shaped ? grid(shaped.pattern) : null;
        if (adapted == null) {
            return new GooInventory.RecipeEntry(id, typeId, null, 0, List.of(), Map.of(), grid);
        }
        List<GooInventory.SlotEntry> slots = new ArrayList<>();
        for (int i = 0; i < adapted.slotCount(); i++) {
            slots.add(new GooInventory.SlotEntry(sorted(adapted.ingredientAlternatives().get(i)),
                    adapted.slotTagIds().get(i).map(Identifier::toString).orElse(null)));
        }
        Map<String, String> containers = new TreeMap<>();
        adapted.containerItems().forEach((item, left) -> containers.put(item.toString(), left.toString()));
        return new GooInventory.RecipeEntry(id, typeId, adapted.output().toString(), adapted.resultCount(), slots,
                containers, grid);
    }

    /**
     * Rebuilds a shaped recipe's pattern and key from its grid of ingredients, one symbol per distinct slot,
     * so the workbench draws the crafting grid.
     *
     * @param pattern the shaped recipe's pattern
     * @return the rows and each symbol's slot
     */
    private static GooInventory.ShapedGrid grid(ShapedRecipePattern pattern) {
        Map<GooInventory.SlotEntry, String> symbols = new LinkedHashMap<>();
        List<String> rows = new ArrayList<>();
        for (int row = 0; row < pattern.height(); row++) {
            StringBuilder line = new StringBuilder();
            for (int column = 0; column < pattern.width(); column++) {
                Optional<Ingredient> cell = pattern.ingredients().get(row * pattern.width() + column);
                line.append(cell.map(ingredient -> symbolOf(slotOf(ingredient), symbols)).orElse(String.valueOf(EMPTY_CELL)));
            }
            rows.add(line.toString());
        }
        Map<String, GooInventory.SlotEntry> key = new TreeMap<>();
        symbols.forEach((slot, symbol) -> key.put(symbol, slot));
        return new GooInventory.ShapedGrid(rows, key);
    }

    private static GooInventory.SlotEntry slotOf(Ingredient ingredient) {
        return new GooInventory.SlotEntry(sorted(GooValueRecipeAdapter.adaptOneIngredient(ingredient)),
                GooValueRecipeAdapter.extractTagId(ingredient).map(Identifier::toString).orElse(null));
    }

    private static String symbolOf(GooInventory.SlotEntry slot, Map<GooInventory.SlotEntry, String> symbols) {
        return symbols.computeIfAbsent(slot, unused -> String.valueOf(GRID_SYMBOLS.charAt(symbols.size())));
    }

    private static List<String> sorted(Set<Identifier> ids) {
        return ids.stream().sorted().map(Identifier::toString).toList();
    }
}
