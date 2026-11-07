package com.mercuriusxeno.goo.ability.pocket;

import com.mercuriusxeno.goo.item.GooDeposit;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.item.PlayerUtils;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * A player's Pocket as its open container reaches it on the server: storing
 * a stack draws the void its new slots reserve from what the player carries, and
 * taking items back deposits the void they release into what the player
 * carries, dropping what finds no home.
 * decision pocket-is-a-void-reserving-container-with-scroll-and-search
 *
 * @param player   the pocket's owner
 * @param shelves  the server's pockets
 * @param slotCost the void each slot the stored items fill reserves
 */
public record PocketVault(ServerPlayer player, PocketShelves shelves, int slotCost) {

    /**
     * The owner's entries.
     *
     * @return the shelf, in the order the kinds were first stored
     */
    public List<PocketEntry> entries() {
        return shelves.shelfOf(player.getUUID());
    }

    /**
     * Stores as much of a stack as the void the player carries reserves,
     * shrinking the stack by what was stored.
     *
     * @param offered the stack offered, left holding what was not stored
     * @return how many were stored
     */
    public int store(ItemStack offered) {
        if (offered.isEmpty()) {
            return 0;
        }
        long held = shelves.countOf(player.getUUID(), offered);
        int stackSize = offered.getMaxStackSize();
        long carried = GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.VOID, 0);
        long storable = PocketReserve.mostStorable(held, offered.getCount(), stackSize, slotCost, carried);
        if (storable == 0) {
            return 0;
        }
        int wanted = (int) PocketReserve.reserveToStore(held, storable, stackSize, slotCost);
        int drawn = GooSourceScanner.deplete(player, GooTypes.VOID, wanted);
        if (drawn < wanted) {
            storable = PocketReserve.mostStorable(held, storable, stackSize, slotCost, drawn);
            release(drawn - PocketReserve.reserveToStore(held, storable, stackSize, slotCost));
        }
        shelves.add(player.getUUID(), offered, storable);
        offered.shrink((int) storable);
        return (int) storable;
    }

    /**
     * A stack of an entry's kind as large as one stack of it holds, or as the
     * entry holds when that is less; nothing leaves the pocket.
     *
     * @param index the entry's place on the shelf
     * @return the stack, empty for no such entry
     */
    public ItemStack peek(int index) {
        List<PocketEntry> shelf = entries();
        if (index < 0 || index >= shelf.size()) {
            return ItemStack.EMPTY;
        }
        PocketEntry entry = shelf.get(index);
        int count = (int) Math.min(entry.count(), entry.kind().getMaxStackSize());
        return entry.kind().copyWithCount(count);
    }

    /**
     * Takes items out of an entry and releases the void they reserved.
     *
     * @param index the entry's place on the shelf
     * @param count how many were taken out
     */
    public void take(int index, int count) {
        if (count <= 0) {
            return;
        }
        List<PocketEntry> shelf = entries();
        if (index < 0 || index >= shelf.size()) {
            return;
        }
        PocketEntry entry = shelf.get(index);
        long taken = Math.min(count, entry.count());
        shelves.remove(player.getUUID(), index, taken);
        release(PocketReserve.releaseOnTake(entry.count(), taken, entry.kind().getMaxStackSize(), slotCost));
    }

    private void release(long voidReleased) {
        if (voidReleased <= 0) {
            return;
        }
        int homeless = GooDeposit.intoInventory(player, ItemStack.EMPTY).deposit(GooTypes.VOID, (int) voidReleased);
        if (homeless > 0) {
            PlayerUtils.addOrDrop(player, GooStacks.createForOutput(GooTypes.VOID, homeless));
        }
    }
}
