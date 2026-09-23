package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.item.ExoriteArmorMaterial;
import com.mercuriusxeno.goo.item.ExoriteUpgradeTemplate;
import com.mercuriusxeno.goo.registry.GooCreativeTabs;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredItem;
import java.util.Collection;
import java.util.List;

/**
 * Gametests for the exorite tier. The upgrade template (decision
 * exorite-template-from-ancient-city) is registered with its own name and
 * slot descriptions, ancient city chests yield it, and the crafting table
 * duplicates it over a sculk block. The tool and armor set (decision
 * exorite-tool-and-armor-set) is registered, smithed from netherite with
 * that template, and repaired by exorite.
 */
public final class ExoriteTests {

    private static final String TEMPLATE_ID = "goo:exorite_upgrade_smithing_template";
    /**
     * Chest rolls of the ancient city table: at a one in ten chance per chest,
     * the chance every roll misses is far below any flake a seeded run shows.
     */
    private static final int ANCIENT_CITY_ROLLS = 300;
    private static final long LOOT_SEED = 261L;
    private static final int GRID_SIDE = 3;
    private static final int DUPLICATED_COUNT = 2;
    private static final String NOT_TRANSLATABLE = "<literal, not a translation key>";

    private static final String NOT_REGISTERED = "Registry should hold " + TEMPLATE_ID;
    private static final String NOT_A_TEMPLATE = TEMPLATE_ID + " should be a smithing template item";
    private static final String UNNAMED = "en_us.json should name the template: ";
    private static final String UNDESCRIBED = "en_us.json should carry the template description line ";
    private static final String NOT_IN_LOOT = "Ancient city chests should yield the template in "
            + ANCIENT_CITY_ROLLS + " seeded rolls";
    private static final String NO_DUPLICATION = "The duplication grid should craft two exorite templates";
    private static final String EXORITE_PREFIX = "exorite_";
    private static final String NETHERITE_PREFIX = "netherite_";
    private static final String PIECE_NOT_REGISTERED = "Registry should hold ";
    private static final String PIECE_NOT_IN_TAB = "Goo creative tab should show ";
    private static final String PIECE_UNNAMED = "en_us.json should name ";
    private static final String NOT_SMITHED = "Exorite template, netherite piece and exorite should smith ";
    private static final String NETHERITE_TEMPLATE_SMITHED = "The netherite template should not smith ";
    private static final String NOT_REPAIRED = "Exorite should repair ";
    private static final String ARMOR_DURABILITY_LOW = "Exorite armor durability should exceed netherite's";
    private static final String ARMOR_TOUGHNESS_LOW = "Exorite armor toughness should exceed netherite's";
    private static final String ARMOR_DEFENSE_LOW = "Exorite armor defense should exceed netherite's in slot ";

    private ExoriteTests() {
    }

    /**
     * The registry holds the template as a smithing template item whose name
     * and four description lines each have a translation in en_us.json.
     *
     * @param helper the gametest helper
     */
    public static void templateRegistered(GameTestHelper helper) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(TEMPLATE_ID));
        helper.assertTrue(item == GooItems.EXORITE_UPGRADE_SMITHING_TEMPLATE.get(), NOT_REGISTERED);
        helper.assertTrue(item instanceof SmithingTemplateItem, NOT_A_TEMPLATE);
        Language language = Language.getInstance();
        String nameKey = item.getDescriptionId();
        helper.assertTrue(language.has(nameKey), UNNAMED + nameKey);
        SmithingTemplateItem template = (SmithingTemplateItem) item;
        List<Component> lines = List.of(template.getBaseSlotDescription(), template.getAdditionSlotDescription());
        for (Component line : lines) {
            assertTranslated(helper, language, line);
        }
        for (String key : List.of(ExoriteUpgradeTemplate.APPLIES_TO_KEY, ExoriteUpgradeTemplate.INGREDIENTS_KEY)) {
            helper.assertTrue(language.has(key), UNDESCRIBED + key);
        }
        helper.succeed();
    }

    /**
     * Rolls minecraft:chests/ancient_city, the loot modifier applied, a few
     * hundred seeded times and reads at least one exorite template.
     *
     * @param helper the gametest helper
     */
    public static void templateInAncientCityLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.ANCIENT_CITY);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
                .create(LootContextParamSets.CHEST);
        boolean found = false;
        for (int roll = 0; roll < ANCIENT_CITY_ROLLS && !found; roll++) {
            found = table.getRandomItems(params, LOOT_SEED + roll).stream()
                    .anyMatch(stack -> stack.is(GooItems.EXORITE_UPGRADE_SMITHING_TEMPLATE.get()));
        }
        helper.assertTrue(found, NOT_IN_LOOT);
        helper.succeed();
    }

    /**
     * Seven diamonds around one template with a sculk block beneath it craft
     * two templates.
     *
     * @param helper the gametest helper
     */
    public static void templateDuplicates(GameTestHelper helper) {
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        CraftingInput grid = CraftingInput.of(GRID_SIDE, GRID_SIDE, List.of(
                diamond, new ItemStack(GooItems.EXORITE_UPGRADE_SMITHING_TEMPLATE.get()), diamond,
                diamond, new ItemStack(Items.SCULK), diamond,
                diamond, diamond, diamond));
        ServerLevel level = helper.getLevel();
        ItemStack result = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(holder -> holder.value().assemble(grid))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(result.is(GooItems.EXORITE_UPGRADE_SMITHING_TEMPLATE.get())
                && result.getCount() == DUPLICATED_COUNT, NO_DUPLICATION);
        helper.succeed();
    }

    /**
     * All nine exorite pieces stand in the item registry, in the goo creative
     * tab's display items, and under a name in en_us.json.
     *
     * @param helper the gametest helper
     */
    public static void setRegistered(GameTestHelper helper) {
        CreativeModeTab tab = GooCreativeTabs.GOO_TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), false, helper.getLevel().registryAccess()));
        Collection<ItemStack> shown = tab.getDisplayItems();
        Language language = Language.getInstance();
        for (DeferredItem<? extends Item> piece : GooItems.EXORITE_SET) {
            Identifier id = piece.getId();
            helper.assertTrue(BuiltInRegistries.ITEM.getValue(id) == piece.get(), PIECE_NOT_REGISTERED + id);
            helper.assertTrue(shown.stream().anyMatch(stack -> stack.is(piece.get())), PIECE_NOT_IN_TAB + id);
            helper.assertTrue(language.has(piece.get().getDescriptionId()), PIECE_UNNAMED + id);
        }
        helper.succeed();
    }

    /**
     * Each exorite piece is smithed from its netherite piece with the exorite
     * template and exorite; the netherite template with the same base and
     * addition matches nothing.
     *
     * @param helper the gametest helper
     */
    public static void setSmithing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack exoriteTemplate = new ItemStack(GooItems.EXORITE_UPGRADE_SMITHING_TEMPLATE.get());
        ItemStack netheriteTemplate = new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        ItemStack exorite = new ItemStack(GooItems.EXORITE.get());
        for (DeferredItem<? extends Item> piece : GooItems.EXORITE_SET) {
            ItemStack netheritePiece = new ItemStack(netheriteCounterpart(piece));
            ItemStack upgraded = smith(level, new SmithingRecipeInput(exoriteTemplate, netheritePiece, exorite));
            helper.assertTrue(upgraded.is(piece.get()), NOT_SMITHED + piece.getId());
            ItemStack refused = smith(level, new SmithingRecipeInput(netheriteTemplate, netheritePiece, exorite));
            helper.assertTrue(refused.isEmpty(), NETHERITE_TEMPLATE_SMITHED + piece.getId());
        }
        helper.succeed();
    }

    /**
     * Exorite is a valid repair item for every exorite piece, read through the
     * goo:exorite_tool_materials tag as the server bound it.
     *
     * @param helper the gametest helper
     */
    public static void setRepairs(GameTestHelper helper) {
        ItemStack exorite = new ItemStack(GooItems.EXORITE.get());
        for (DeferredItem<? extends Item> piece : GooItems.EXORITE_SET) {
            helper.assertTrue(new ItemStack(piece.get()).isValidRepairItem(exorite), NOT_REPAIRED + piece.getId());
        }
        helper.succeed();
    }

    /**
     * The exorite armor material beats netherite's on durability, toughness
     * and the defense of every armor slot.
     *
     * @param helper the gametest helper
     */
    public static void armorOutranksNetherite(GameTestHelper helper) {
        ArmorMaterial exorite = ExoriteArmorMaterial.MATERIAL;
        ArmorMaterial netherite = ArmorMaterials.NETHERITE;
        helper.assertTrue(exorite.durability() > netherite.durability(), ARMOR_DURABILITY_LOW);
        helper.assertTrue(exorite.toughness() > netherite.toughness(), ARMOR_TOUGHNESS_LOW);
        for (ArmorType slot : ArmorType.values()) {
            helper.assertTrue(exorite.defense().getOrDefault(slot, 0) > netherite.defense().getOrDefault(slot, 0),
                    ARMOR_DEFENSE_LOW + slot.getName());
        }
        helper.succeed();
    }

    private static Item netheriteCounterpart(DeferredItem<? extends Item> piece) {
        String slot = piece.getId().getPath().substring(EXORITE_PREFIX.length());
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(NETHERITE_PREFIX + slot));
    }

    private static ItemStack smith(ServerLevel level, SmithingRecipeInput input) {
        return level.recipeAccess().getRecipeFor(RecipeType.SMITHING, input, level)
                .map(holder -> holder.value().assemble(input))
                .orElse(ItemStack.EMPTY);
    }

    private static void assertTranslated(GameTestHelper helper, Language language, Component line) {
        String key = line.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : NOT_TRANSLATABLE;
        helper.assertTrue(language.has(key), UNDESCRIBED + key);
    }
}
