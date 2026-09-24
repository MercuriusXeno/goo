package com.mercuriusxeno.goo.lab;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Stocks the supply row from the registries at build time (decision
 * lab-iterates-the-registries): each station's canister block gets a canister
 * filled with its goo type, and its chest the items whose goo values
 * ({@link Goo#GOO_VALUES}) hold that type, so a type or a value a datapack adds
 * reaches the row with no lab edit.
 */
public final class LabStock {

    private LabStock() {
    }

    /**
     * Answers every registered goo type id, datapack types included, sorted.
     *
     * @param level the level whose registries to read
     * @return the goo type ids
     */
    public static List<String> gooTypeIds(ServerLevel level) {
        return level.registryAccess().lookupOrThrow(GooTypes.REGISTRY).listElementIds()
                .map(key -> key.identifier().toString()).sorted().toList();
    }

    /**
     * Answers the goo type a station serves.
     *
     * @param station the station
     * @return the goo type key
     */
    public static ResourceKey<GooTypeDefinition> gooType(LabSupply.Station station) {
        return ResourceKey.create(GooTypes.REGISTRY, Identifier.parse(station.gooTypeId()));
    }

    /**
     * Fills one station's canister block and chest.
     *
     * @param level   the level the station stands in
     * @param origin  the world position of the plan's zero offset
     * @param station the station
     */
    public static void stock(ServerLevel level, BlockPos origin, LabSupply.Station station) {
        ResourceKey<GooTypeDefinition> type = gooType(station);
        BlockPos canisterPos = LabBuilder.worldPos(origin, station.canisterOffset());
        if (level.getBlockEntity(canisterPos) instanceof CanisterBlockEntity canister) {
            canister.insertCanister(CanisterBlock.CENTER_SLOT, LabRigs.filledCanister(type), false);
        }
        BlockPos chestPos = LabBuilder.worldPos(origin, station.chestOffset());
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            List<Item> items = itemsHolding(type);
            for (int slot = 0; slot < chest.getContainerSize() && slot < items.size(); slot++) {
                Item item = items.get(slot);
                chest.setItem(slot, new ItemStack(item, item.getDefaultMaxStackSize()));
            }
        }
    }

    /**
     * Answers the items whose goo value holds a type, sorted by item id.
     *
     * @param type the goo type
     * @return the items
     */
    public static List<Item> itemsHolding(ResourceKey<GooTypeDefinition> type) {
        Map<Identifier, ?> values = Goo.GOO_VALUES.getEffectiveValues();
        return values.keySet().stream()
                .filter(id -> Goo.GOO_VALUES.lookup(id).get(type) > 0)
                .sorted(Comparator.comparing(Identifier::toString))
                .map(BuiltInRegistries.ITEM::getValue)
                .filter(item -> item != Items.AIR)
                .toList();
    }
}
