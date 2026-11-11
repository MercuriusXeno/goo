package com.mercuriusxeno.goo.ability.pocket;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every player's shelf of stored items, saved in the overworld's data: each
 * player's entries in the order their kinds were first stored, as many as were
 * stored, with no slot count bounding it. Void's hole swallows onto it and
 * pours it out, and the void the old Pocket reserved for a shelf's slots is paid
 * back once.
 * decision hole-is-an-in-world-portable-hole-for-items
 */
public class PocketShelves extends SavedData {

    /** Saves each player's shelf and who was paid back the old reserve. */
    public static final Codec<PocketShelves> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, PocketEntry.CODEC.listOf()).fieldOf("shelves")
                    .forGetter(pockets -> pockets.shelves),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("reserves_paid", List.of())
                    .forGetter(pockets -> List.copyOf(pockets.reservesPaid))
    ).apply(inst, PocketShelves::new));

    /** Stored as goo/pockets.dat in the overworld's data, the id the old Pocket saved under. */
    public static final SavedDataType<PocketShelves> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("goo", "pockets"), PocketShelves::new, CODEC);

    /** The void the old Pocket reserved for each slot a shelf's items filled. */
    private static final int OLD_SLOT_COST = 6;

    private final Map<UUID, List<PocketEntry>> shelves;
    private final Set<UUID> reservesPaid;

    /** Creates a server's shelves with nothing stored. */
    public PocketShelves() {
        this(Map.of());
    }

    /**
     * Creates a server's shelves, none paid back yet.
     *
     * @param saved each player's shelf
     */
    public PocketShelves(Map<UUID, List<PocketEntry>> saved) {
        this(saved, List.of());
    }

    /**
     * Creates a server's shelves from what it saved.
     *
     * @param saved each player's shelf
     * @param paid  the players paid back the old reserve
     */
    public PocketShelves(Map<UUID, List<PocketEntry>> saved, List<UUID> paid) {
        this.shelves = new HashMap<>();
        saved.forEach((player, shelf) -> shelves.put(player, new ArrayList<>(shelf)));
        this.reservesPaid = new HashSet<>(paid);
    }

    /**
     * The server's shelves, kept in the overworld's data.
     *
     * @param level any level of the server
     * @return the shelves
     */
    public static PocketShelves get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * A player's shelf.
     *
     * @param player the player's id
     * @return the entries, in the order their kinds were first stored
     */
    public List<PocketEntry> shelfOf(UUID player) {
        return List.copyOf(shelves.getOrDefault(player, List.of()));
    }

    /**
     * How many of a stack's kind a player's shelf holds.
     *
     * @param player the player's id
     * @param kind   a stack of the kind
     * @return the count, 0 for a kind the shelf does not hold
     */
    public long countOf(UUID player, ItemStack kind) {
        return shelves.getOrDefault(player, List.of()).stream().filter(entry -> entry.holds(kind))
                .mapToLong(PocketEntry::count).findFirst().orElse(0);
    }

    /**
     * Stores items of a stack's kind, merging into the entry of that kind or
     * opening a new one at the shelf's end.
     *
     * @param player the player's id
     * @param kind   a stack of the kind stored
     * @param count  how many are stored
     */
    public void add(UUID player, ItemStack kind, long count) {
        if (count <= 0 || kind.isEmpty()) {
            return;
        }
        List<PocketEntry> shelf = shelves.computeIfAbsent(player, absent -> new ArrayList<>());
        for (int index = 0; index < shelf.size(); index++) {
            PocketEntry entry = shelf.get(index);
            if (entry.holds(kind)) {
                shelf.set(index, entry.withCount(entry.count() + count));
                setDirty();
                return;
            }
        }
        shelf.add(new PocketEntry(kind.copyWithCount(1), count));
        setDirty();
    }

    /**
     * Empties a player's shelf.
     *
     * @param player the player's id
     * @return every entry the shelf held, in shelf order
     */
    public List<PocketEntry> takeAll(UUID player) {
        List<PocketEntry> taken = shelves.remove(player);
        if (taken == null) {
            return List.of();
        }
        setDirty();
        return taken;
    }

    /**
     * Pays back, once, the void the old Pocket reserved for a player's shelf:
     * six for each slot its entries fill, a slot holding one stack of an item.
     * The first call marks the player paid, so every later call owes nothing.
     *
     * @param player the player's id
     * @return the void owed now
     */
    public long settleReserve(UUID player) {
        if (!reservesPaid.add(player)) {
            return 0;
        }
        setDirty();
        return shelves.getOrDefault(player, List.of()).stream()
                .mapToLong(entry -> slotsOf(entry.count(), entry.kind().getMaxStackSize())).sum() * OLD_SLOT_COST;
    }

    private static long slotsOf(long count, int stackSize) {
        long perSlot = Math.max(1, stackSize);
        return (Math.max(0, count) + perSlot - 1) / perSlot;
    }
}
