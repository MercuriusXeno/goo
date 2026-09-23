package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.fluid.GooBucketItem;
import com.mercuriusxeno.goo.item.*;
import com.mercuriusxeno.goo.item.gasket.ChoralGasketItem;
import com.mercuriusxeno.goo.item.gasket.ChoralTunerItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.List;

public class GooItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Goo.MODID);

    // Attack damage and speed baselines are the netherite pieces'; the exorite material adds its own bonus.
    private static final float PICKAXE_DAMAGE = 1.0F;
    private static final float PICKAXE_SPEED = -2.8F;
    private static final float AXE_DAMAGE = 5.0F;
    private static final float AXE_SPEED = -3.0F;
    private static final float SHOVEL_DAMAGE = 1.5F;
    private static final float SHOVEL_SPEED = -3.0F;
    /** Cancels the material's attack bonus so the hoe hits as a bare hand, as netherite's does. */
    private static final float HOE_DAMAGE = -5.0F;
    private static final float HOE_SPEED = 0.0F;
    private static final float SWORD_DAMAGE = 3.0F;
    private static final float SWORD_SPEED = -2.4F;

    /**
     * The one blob item, stackable to 64, each 1,000 mB, its type in the
     * GOO_TYPE component (decision generic-goo-items).
     */
    public static final DeferredItem<GooBlobItem> GOO_BLOB = ITEMS.registerItem("goo_blob", GooBlobItem::new);
    /**
     * The one omniblob item, unstackable, uncapped volume, its type in the
     * GOO_TYPE component.
     */
    public static final DeferredItem<GooOmniblobItem> GOO_OMNIBLOB = ITEMS.registerItem("goo_omniblob",
            props -> new GooOmniblobItem(props.stacksTo(1)));
    /**
     * The one goo bucket over the generic fluid, 1000 mB, its type in the
     * GOO_TYPE component.
     */
    public static final DeferredItem<GooBucketItem> GOO_BUCKET = ITEMS.registerItem("goo_bucket",
            props -> new GooBucketItem(GooFluids.SOURCE.get(), props.craftRemainder(Items.BUCKET).stacksTo(1)));
    // --- Block items ---
    public static final DeferredItem<BlockItem> CRUCIBLE = ITEMS.registerSimpleBlockItem("crucible", GooBlocks.CRUCIBLE);
    public static final DeferredItem<BlockItem> HUB = ITEMS.registerSimpleBlockItem("hub", GooBlocks.HUB);
    public static final DeferredItem<BlockItem> PLEXER = ITEMS.registerSimpleBlockItem("plexer", GooBlocks.PLEXER);
    public static final DeferredItem<BlockItem> REACTOR = ITEMS.registerSimpleBlockItem("reactor", GooBlocks.REACTOR);
    public static final DeferredItem<VatBlockItem> VAT = ITEMS.registerItem("vat",
            props -> new VatBlockItem(GooBlocks.VAT.get(), props.useBlockDescriptionPrefix()));
    public static final DeferredItem<BlockItem> TAP = ITEMS.registerSimpleBlockItem("tap", GooBlocks.TAP);
    // --- Canister ---
    public static final DeferredItem<CanisterItem> CANISTER = ITEMS.registerItem("canister",
            props -> new CanisterItem(GooBlocks.CANISTER.get(), props.stacksTo(1).useBlockDescriptionPrefix()));
    // --- Intermediate items ---
    public static final DeferredItem<ChoralGasketItem> CHORAL_GASKET = ITEMS.registerItem("choral_gasket",
            ChoralGasketItem::new);
    public static final DeferredItem<ChoralTunerItem> CHORAL_TUNER = ITEMS.registerItem("choral_tuner",
            ChoralTunerItem::new);
    public static final DeferredItem<Item> EXORITE = ITEMS.registerSimpleItem("exorite");
    public static final DeferredItem<SmithingTemplateItem> EXORITE_UPGRADE_SMITHING_TEMPLATE = ITEMS.registerItem(
            "exorite_upgrade_smithing_template", props -> ExoriteUpgradeTemplate.create(props.rarity(Rarity.RARE)));
    // --- Exorite tools and armor (decision exorite-tool-and-armor-set) ---
    public static final DeferredItem<Item> EXORITE_PICKAXE = ITEMS.registerItem("exorite_pickaxe",
            props -> new Item(props.pickaxe(ExoriteMaterials.TOOL, PICKAXE_DAMAGE, PICKAXE_SPEED).fireResistant()));
    public static final DeferredItem<AxeItem> EXORITE_AXE = ITEMS.registerItem("exorite_axe",
            props -> new AxeItem(ExoriteMaterials.TOOL, AXE_DAMAGE, AXE_SPEED, props.fireResistant()));
    public static final DeferredItem<ShovelItem> EXORITE_SHOVEL = ITEMS.registerItem("exorite_shovel",
            props -> new ShovelItem(ExoriteMaterials.TOOL, SHOVEL_DAMAGE, SHOVEL_SPEED, props.fireResistant()));
    public static final DeferredItem<HoeItem> EXORITE_HOE = ITEMS.registerItem("exorite_hoe",
            props -> new HoeItem(ExoriteMaterials.TOOL, HOE_DAMAGE, HOE_SPEED, props.fireResistant()));
    public static final DeferredItem<Item> EXORITE_SWORD = ITEMS.registerItem("exorite_sword",
            props -> new Item(props.sword(ExoriteMaterials.TOOL, SWORD_DAMAGE, SWORD_SPEED).fireResistant()));
    public static final DeferredItem<Item> EXORITE_HELMET = registerExoriteArmor("exorite_helmet", ArmorType.HELMET);
    public static final DeferredItem<Item> EXORITE_CHESTPLATE = registerExoriteArmor("exorite_chestplate",
            ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> EXORITE_LEGGINGS = registerExoriteArmor("exorite_leggings",
            ArmorType.LEGGINGS);
    public static final DeferredItem<Item> EXORITE_BOOTS = registerExoriteArmor("exorite_boots", ArmorType.BOOTS);
    // --- Equipment (gloves: right-click throw / radial select) ---
    public static final DeferredItem<GooGloveItem> GOO_GLOVE = ITEMS.registerItem("goo_glove",
            props -> new GooGloveItem(props.stacksTo(1)));
    public static final DeferredItem<GooGloveItem> GOO_GAUNTLET = ITEMS.registerItem("goo_gauntlet",
            props -> new GooGloveItem(props.stacksTo(1).fireResistant()));
    public static final DeferredItem<GooGloveItem> EXO_GAUNTLET = ITEMS.registerItem("exo_gauntlet",
            props -> new GooGloveItem(props.stacksTo(1).fireResistant()));
    // --- Partially Melted Item (crucible intermediate, not in creative tab) ---
    public static final DeferredItem<PartiallyMeltedItem> PARTIALLY_MELTED_ITEM = ITEMS.registerItem(
            "partially_melted_item", props -> new PartiallyMeltedItem(props.stacksTo(1)));
    // --- Depleted Blaze Rod (crucible fuel intermediate, not in creative tab) ---
    public static final DeferredItem<DepletedBlazeRodItem> DEPLETED_BLAZE_ROD = ITEMS.registerItem(
            "depleted_blaze_rod", props -> new DepletedBlazeRodItem(props.stacksTo(1)));

    /** The nine exorite tools and armor pieces, tools first, in creative tab order. */
    public static final List<DeferredItem<? extends Item>> EXORITE_SET = List.of(
            EXORITE_PICKAXE, EXORITE_AXE, EXORITE_SHOVEL, EXORITE_HOE, EXORITE_SWORD,
            EXORITE_HELMET, EXORITE_CHESTPLATE, EXORITE_LEGGINGS, EXORITE_BOOTS);

    private static DeferredItem<Item> registerExoriteArmor(String name, ArmorType type) {
        return ITEMS.registerItem(name, props -> new Item(props.humanoidArmor(ExoriteArmorMaterial.MATERIAL, type).fireResistant()));
    }

}
