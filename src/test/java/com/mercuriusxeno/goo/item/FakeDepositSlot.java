package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An inventory slot for the deposit walk, holding goo in a map with an optional capacity,
 * so a test reads what each home took without an ItemStack.
 */
final class FakeDepositSlot implements GooDeposit.DepositSlot {

    private GooDeposit.Home home;
    private @Nullable ResourceKey<GooTypeDefinition> ownType;
    private final int capacity;
    private final Map<ResourceKey<GooTypeDefinition>, Integer> held = new HashMap<>();

    private FakeDepositSlot(GooDeposit.Home home, @Nullable ResourceKey<GooTypeDefinition> ownType,
                            int volume, int capacity) {
        this.home = home;
        this.ownType = ownType;
        this.capacity = capacity;
        if (ownType != null && volume > 0) {
            held.put(ownType, volume);
        }
    }

    static FakeDepositSlot canister(ResourceKey<GooTypeDefinition> type, int volume, int capacity) {
        return new FakeDepositSlot(GooDeposit.Home.CANISTER, type, volume, capacity);
    }

    static FakeDepositSlot omniblob(ResourceKey<GooTypeDefinition> type, int volume) {
        return new FakeDepositSlot(GooDeposit.Home.OMNIBLOB, type, volume, Integer.MAX_VALUE);
    }

    static FakeDepositSlot vat(int capacity) {
        return new FakeDepositSlot(GooDeposit.Home.VAT, null, 0, capacity);
    }

    static FakeDepositSlot empty() {
        return new FakeDepositSlot(GooDeposit.Home.EMPTY, null, 0, Integer.MAX_VALUE);
    }

    static FakeDepositSlot unrelated() {
        return new FakeDepositSlot(GooDeposit.Home.NONE, null, 0, 0);
    }

    int volume(ResourceKey<GooTypeDefinition> type) {
        return held.getOrDefault(type, 0);
    }

    int total() {
        return held.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Override
    public GooDeposit.Home home() {
        return home;
    }

    @Override
    public boolean takes(ResourceKey<GooTypeDefinition> type) {
        return ownType == null || Objects.equals(ownType, type);
    }

    @Override
    public int accept(ResourceKey<GooTypeDefinition> type, int volume) {
        if (home == GooDeposit.Home.NONE) {
            return 0;
        }
        int taken = Math.min(volume, capacity - total());
        held.merge(type, taken, Integer::sum);
        if (home == GooDeposit.Home.EMPTY) {
            home = GooDeposit.Home.OMNIBLOB;
            ownType = type;
        }
        return taken;
    }
}
