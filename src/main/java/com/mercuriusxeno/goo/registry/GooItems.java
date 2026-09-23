package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.fluid.GooBucketItem;
import com.mercuriusxeno.goo.item.*;
import com.mercuriusxeno.goo.item.gasket.ChoralGasketItem;
import com.mercuriusxeno.goo.item.gasket.ChoralTunerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class GooItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Goo.MODID);

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
    /**
     * Stacks to 1: HUB_CANISTERS covers the whole stack, so goo routed into a
     * stack of hubs would copy onto every hub in it (decision hub-item-blob-insert).
     */
    public static final DeferredItem<HubBlockItem> HUB = ITEMS.registerItem("hub",
            props -> new HubBlockItem(GooBlocks.HUB.get(), props.stacksTo(1).useBlockDescriptionPrefix()));
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

}
