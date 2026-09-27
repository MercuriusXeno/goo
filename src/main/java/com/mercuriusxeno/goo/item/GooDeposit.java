package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deposits goo drained by hand into what the player carries: canisters already holding
 * the type, then the first omniblob of the type, then a vat, then a new omniblob in a free
 * slot. What finds no home is answered back so it stays in the container it came from
 * (decision drained-goo-fills-carried-containers-first).
 */
public final class GooDeposit {

    /** Main inventory slots end here; a new omniblob never lands in the offhand. */
    private static final int MAIN_END = 36;

    private GooDeposit() {
    }

    /**
     * What a slot offers a deposit. Declaration order is the walk order.
     */
    enum Home {
        /** A canister, a home only for the type it already holds. */
        CANISTER,
        /** An omniblob, a home only for its own type. */
        OMNIBLOB,
        /** A vat, a home for any type while it has room. */
        VAT,
        /** An empty main-inventory slot, where a new omniblob goes. */
        EMPTY,
        /** Anything else: never a home. */
        NONE
    }

    /**
     * One inventory slot as the deposit walk reads it.
     */
    interface DepositSlot {
        /**
         * Answers what this slot offers a deposit.
         *
         * @return the home kind
         */
        Home home();

        /**
         * Answers whether this slot takes the given type: a canister or omniblob of that
         * type, or any vat or empty slot.
         *
         * @param type the goo type
         * @return true if the slot can hold the type
         */
        boolean takes(ResourceKey<GooTypeDefinition> type);

        /**
         * Puts up to the given volume into this slot.
         *
         * @param type   the goo type
         * @param volume the volume offered, in mB
         * @return the volume accepted
         */
        int accept(ResourceKey<GooTypeDefinition> type, int volume);
    }

    /**
     * Where a drain sends each type's goo: answers the volume that found no home.
     */
    @FunctionalInterface
    public interface Depositor {
        /**
         * Deposits up to the given volume of one type.
         *
         * @param type   the goo type
         * @param volume the volume offered, in mB
         * @return the volume that found no home
         */
        int deposit(ResourceKey<GooTypeDefinition> type, int volume);
    }

    /**
     * Takes goo out of the container being drained.
     */
    @FunctionalInterface
    public interface GooDrawer {
        /**
         * Removes up to the given volume of one type.
         *
         * @param type   the goo type
         * @param volume the volume to remove, in mB
         * @return the volume removed
         */
        int draw(ResourceKey<GooTypeDefinition> type, int volume);
    }

    /**
     * Drains every type a container holds through the depositor, drawing from the
     * container only what found a home.
     *
     * @param held      the container's volume per type
     * @param drawer    removes goo from the container
     * @param depositor where the goo goes
     * @return true if any goo moved
     */
    public static boolean drainEveryType(Map<ResourceKey<GooTypeDefinition>, Integer> held,
                                         GooDrawer drawer, Depositor depositor) {
        boolean moved = false;
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : held.entrySet()) {
            int volume = entry.getValue();
            int placed = volume - depositor.deposit(entry.getKey(), volume);
            if (placed > 0) {
                drawer.draw(entry.getKey(), placed);
                moved = true;
            }
        }
        return moved;
    }

    /**
     * The depositor into a player's inventory, skipping the stack being drained.
     *
     * @param player the player receiving the goo
     * @param source the stack being drained, never its own home; EMPTY for a block
     * @return the depositor
     */
    public static Depositor intoInventory(Player player, ItemStack source) {
        List<DepositSlot> slots = inventorySlots(player.getInventory(), source);
        return (type, volume) -> depositInto(slots, type, volume);
    }

    /**
     * Walks the slots in {@link Home} order, each home taking what it can.
     *
     * @param slots  the slots in inventory order
     * @param type   the goo type
     * @param volume the volume to deposit, in mB
     * @return the volume that found no home
     */
    static int depositInto(List<? extends DepositSlot> slots, ResourceKey<GooTypeDefinition> type, int volume) {
        int left = volume;
        for (Home pass : Home.values()) {
            if (pass != Home.NONE) {
                left = depositPass(slots, pass, type, left);
            }
        }
        return left;
    }

    /**
     * Offers the volume to each slot of one home kind that takes the type.
     *
     * @param slots  the slots in inventory order
     * @param pass   the home kind this pass fills
     * @param type   the goo type
     * @param volume the volume still wanting a home, in mB
     * @return the volume still wanting a home after the pass
     */
    private static int depositPass(List<? extends DepositSlot> slots, Home pass,
                                   ResourceKey<GooTypeDefinition> type, int volume) {
        int left = volume;
        for (DepositSlot slot : slots) {
            if (left <= 0) {
                return 0;
            }
            if (slot.home() == pass && slot.takes(type)) {
                left -= slot.accept(type, left);
            }
        }
        return left;
    }

    /**
     * The player's carried slots as deposit slots, the source stack left out.
     *
     * @param inventory the player inventory
     * @param source    the stack being drained
     * @return the slots in walk order
     */
    private static List<DepositSlot> inventorySlots(Inventory inventory, ItemStack source) {
        List<DepositSlot> slots = new ArrayList<>();
        for (int index : GooSourceScanner.carriedSlots()) {
            if (source.isEmpty() || inventory.getItem(index) != source) {
                slots.add(new InventorySlot(inventory, index));
            }
        }
        return slots;
    }

    /**
     * The volume an omniblob can still grow by before its int volume overflows.
     *
     * @param held    the omniblob's volume
     * @param offered the volume offered
     * @return the volume it takes
     */
    static int omniblobRoom(int held, int offered) {
        return Math.min(offered, Integer.MAX_VALUE - held);
    }

    /**
     * A live inventory slot, read fresh on every call so a new omniblob placed by an
     * earlier deposit is seen by the next.
     *
     * @param inventory the player inventory
     * @param index     the slot index
     */
    private record InventorySlot(Inventory inventory, int index) implements DepositSlot {
        private ItemStack stack() {
            return inventory.getItem(index);
        }

        @Override
        public Home home() {
            ItemStack stack = stack();
            if (stack.isEmpty()) {
                return index < MAIN_END ? Home.EMPTY : Home.NONE;
            }
            if (stack.getItem() instanceof CanisterItem) {
                return Home.CANISTER;
            }
            if (stack.getItem() instanceof GooOmniblobItem) {
                return Home.OMNIBLOB;
            }
            return stack.getItem() instanceof VatBlockItem ? Home.VAT : Home.NONE;
        }

        @Override
        public boolean takes(ResourceKey<GooTypeDefinition> type) {
            ItemStack stack = stack();
            if (stack.getItem() instanceof CanisterItem) {
                CanisterFluidContent content = CanisterItem.getFluidContent(stack);
                return !content.isEmpty() && Objects.equals(content.getGooType(), type);
            }
            return !(stack.getItem() instanceof GooOmniblobItem) || Objects.equals(BlobStacks.keyOf(stack), type);
        }

        @Override
        public int accept(ResourceKey<GooTypeDefinition> type, int volume) {
            ItemStack stack = stack();
            return switch (home()) {
                case CANISTER -> CanisterItem.addGoo(stack, type, volume);
                case OMNIBLOB -> growOmniblob(stack, volume);
                case VAT -> VatBlockItem.addGoo(stack, type, volume);
                case EMPTY -> placeOmniblob(type, volume);
                case NONE -> 0;
            };
        }

        private static int growOmniblob(ItemStack stack, int volume) {
            int held = GooOmniblobItem.getVolume(stack);
            int taken = omniblobRoom(held, volume);
            GooOmniblobItem.setVolume(stack, held + taken);
            return taken;
        }

        private int placeOmniblob(ResourceKey<GooTypeDefinition> type, int volume) {
            inventory.setItem(index, BlobStacks.createForOutput(type, volume));
            return volume;
        }
    }
}
