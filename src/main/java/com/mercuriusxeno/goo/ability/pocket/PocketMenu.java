package com.mercuriusxeno.goo.ability.pocket;

import com.mercuriusxeno.goo.network.PocketContentsPayload;
import com.mercuriusxeno.goo.registry.GooMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Pocket's container: the player's inventory beneath the pocket's shelf. A
 * stack shift-clicked from the inventory, or the carried stack dropped on
 * the shelf, is stored; an entry clicked comes back to the cursor, or with
 * shift into the inventory. The shelf holds no slots, so the server sends
 * its entries to the client each time they change, and the client asks for
 * a store or a take through the menu's button clicks.
 * decision pocket-is-a-void-reserving-container-with-scroll-and-search
 */
public class PocketMenu extends AbstractContainerMenu {

    /** The button that stores the carried stack. */
    public static final int STORE_CARRIED = 0;
    /** Where the inventory's slots start, from the screen's top left. */
    public static final int INVENTORY_LEFT = 8;
    /** The inventory's top, below the shelf's six rows. */
    public static final int INVENTORY_TOP = 140;
    private static final int TAKE_BUTTONS_START = 1;
    private static final int TAKE_KINDS = 2;
    private static final int TAKE_INTO_INVENTORY = 1;

    private final @Nullable PocketVault vault;
    private List<PocketEntry> shown = List.of();
    private boolean entriesChanged = true;

    /**
     * The client's menu, which shows the entries the server sends.
     *
     * @param containerId the menu's id
     * @param inventory   the player's inventory
     */
    public PocketMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    /**
     * The server's menu over a player's pocket.
     *
     * @param containerId the menu's id
     * @param inventory   the player's inventory
     * @param vault       the player's pocket, null on the client
     */
    public PocketMenu(int containerId, Inventory inventory, @Nullable PocketVault vault) {
        super(GooMenus.POCKET.get(), containerId);
        this.vault = vault;
        addStandardInventorySlots(inventory, INVENTORY_LEFT, INVENTORY_TOP);
    }

    /**
     * The button that takes an entry back.
     *
     * @param entry         the entry's place on the shelf
     * @param intoInventory true to take it into the inventory, false onto the cursor
     * @return the button id
     */
    public static int takeButton(int entry, boolean intoInventory) {
        return TAKE_BUTTONS_START + entry * TAKE_KINDS + (intoInventory ? TAKE_INTO_INVENTORY : 0);
    }

    /**
     * The pocket's entries: the shelf itself on the server, the last sent on the client.
     *
     * @return the entries, in shelf order
     */
    public List<PocketEntry> entries() {
        return vault == null ? shown : vault.entries();
    }

    /**
     * Shows the entries the server sent.
     *
     * @param entries the entries, in shelf order
     */
    public void showEntries(List<PocketEntry> entries) {
        shown = List.copyOf(entries);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (vault == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack moving = slot.getItem().copy();
        slot.set(ItemStack.EMPTY);
        storeAndChange(moving);
        slot.set(moving);
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (vault == null) {
            return false;
        }
        if (buttonId == STORE_CARRIED) {
            ItemStack moving = getCarried().copy();
            setCarried(ItemStack.EMPTY);
            storeAndChange(moving);
            setCarried(moving);
            return true;
        }
        int entry = (buttonId - TAKE_BUTTONS_START) / TAKE_KINDS;
        boolean intoInventory = (buttonId - TAKE_BUTTONS_START) % TAKE_KINDS == TAKE_INTO_INVENTORY;
        ItemStack taken = vault.peek(entry);
        if (taken.isEmpty()) {
            return false;
        }
        int before = taken.getCount();
        if (intoInventory) {
            player.getInventory().add(taken);
        } else {
            onto(taken);
        }
        vault.take(entry, before - taken.getCount());
        entriesChanged = true;
        return true;
    }

    /**
     * Moves a taken stack onto the cursor: an empty cursor takes it whole, a
     * cursor carrying its kind takes what fits.
     *
     * @param taken the stack taken, left holding what did not fit
     */
    private void onto(ItemStack taken) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            setCarried(taken.copy());
            taken.setCount(0);
        } else if (ItemStack.isSameItemSameComponents(carried, taken)) {
            int moved = Math.min(taken.getCount(), carried.getMaxStackSize() - carried.getCount());
            carried.grow(moved);
            taken.shrink(moved);
        }
    }

    private void storeAndChange(ItemStack moving) {
        if (vault != null && vault.store(moving) > 0) {
            entriesChanged = true;
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (vault != null && entriesChanged) {
            entriesChanged = false;
            ServerPlayer owner = vault.player();
            PocketContentsPayload contents = new PocketContentsPayload(containerId, vault.entries());
            if (owner.connection.hasChannel(contents)) {
                PacketDistributor.sendToPlayer(owner, contents);
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }
}
