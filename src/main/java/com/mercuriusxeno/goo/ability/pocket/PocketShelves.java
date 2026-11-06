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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every player's Pocket, saved in the overworld's data: each player's shelf
 * of entries in the order their kinds were first stored, as many as were
 * stored, with no slot count bounding it.
 * decision pocket-is-a-void-reserving-container-with-scroll-and-search
 */
public class PocketShelves extends SavedData {

    /** Saves each player's shelf. */
    public static final Codec<PocketShelves> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, PocketEntry.CODEC.listOf()).fieldOf("shelves")
                    .forGetter(pockets -> pockets.shelves)
    ).apply(inst, PocketShelves::new));

    /** Stored as goo/pockets.dat in the overworld's data. */
    public static final SavedDataType<PocketShelves> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("goo", "pockets"), PocketShelves::new, CODEC);

    private final Map<UUID, List<PocketEntry>> shelves;

    /** Creates a server's pockets with nothing stored. */
    public PocketShelves() {
        this(Map.of());
    }

    /**
     * Creates a server's pockets from what it saved.
     *
     * @param saved each player's shelf
     */
    public PocketShelves(Map<UUID, List<PocketEntry>> saved) {
        this.shelves = new HashMap<>();
        saved.forEach((player, shelf) -> shelves.put(player, new ArrayList<>(shelf)));
    }

    /**
     * The server's pockets, kept in the overworld's data.
     *
     * @param level any level of the server
     * @return the pockets
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
     * The items a player's pocket holds, every entry counted.
     *
     * @param player the player's id
     * @return the item count
     */
    public long itemsHeld(UUID player) {
        return shelves.getOrDefault(player, List.of()).stream().mapToLong(PocketEntry::count).sum();
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
     * Removes items from an entry, dropping the entry once it holds none.
     *
     * @param player the player's id
     * @param index  the entry's place on the shelf
     * @param count  how many are removed, at most what the entry holds
     */
    public void remove(UUID player, int index, long count) {
        List<PocketEntry> shelf = shelves.get(player);
        if (shelf == null || index < 0 || index >= shelf.size() || count <= 0) {
            return;
        }
        PocketEntry entry = shelf.get(index);
        long left = entry.count() - Math.min(count, entry.count());
        if (left == 0) {
            shelf.remove(index);
        } else {
            shelf.set(index, entry.withCount(left));
        }
        setDirty();
    }
}
