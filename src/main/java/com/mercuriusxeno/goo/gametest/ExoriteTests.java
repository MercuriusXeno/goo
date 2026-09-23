package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.item.ExoriteUpgradeTemplate;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Gametests for the exorite upgrade smithing template (decision
 * exorite-template-from-ancient-city): it is registered with its own name
 * and slot descriptions, ancient city chests yield it, and the crafting
 * table duplicates it over a sculk block the way vanilla templates duplicate.
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

    private static void assertTranslated(GameTestHelper helper, Language language, Component line) {
        String key = line.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : NOT_TRANSLATABLE;
        helper.assertTrue(language.has(key), UNDESCRIBED + key);
    }
}
