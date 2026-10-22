package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.data.StoredRemainderLedger;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The remainders owed to players who left before a payout reached them, saved in
 * the overworld's data and handed to each player's own ledger when they return,
 * so a drink finishing after its player logs out loses nothing under 1 mB.
 * decision every-producer-consumer-and-container-holds-a-remainder-buffer
 */
public class OfflineRemainders extends SavedData {

    /** Saves each absent player's owed remainders. */
    public static final Codec<OfflineRemainders> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, GooRemainders.CODEC).fieldOf("owed")
                    .forGetter(offline -> offline.owed)
    ).apply(inst, OfflineRemainders::new));

    /** Stored as goo/offline_remainders.dat in the overworld's data. */
    public static final SavedDataType<OfflineRemainders> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("goo", "offline_remainders"), OfflineRemainders::new, CODEC);

    private final Map<UUID, GooRemainders> owed;

    /** Creates a server's record owing no one. */
    public OfflineRemainders() {
        this(Map.of());
    }

    /**
     * Creates a server's record from what it saved.
     *
     * @param owed each absent player's owed remainders
     */
    public OfflineRemainders(Map<UUID, GooRemainders> owed) {
        this.owed = new HashMap<>(owed);
    }

    /**
     * The server's record, kept in the overworld's data.
     *
     * @param level any level of the server
     * @return the record
     */
    public static OfflineRemainders get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * The ledger standing in for an absent player's own, which a payout credits.
     *
     * @param player the absent player's id
     * @return the ledger over what they are owed
     */
    public IRemainderLedger ledgerOf(UUID player) {
        return new StoredRemainderLedger(() -> owed.getOrDefault(player, GooRemainders.EMPTY), remainders -> {
            if (remainders.isEmpty()) {
                owed.remove(player);
            } else {
                owed.put(player, remainders);
            }
            setDirty();
        });
    }

    /**
     * Credits what a returning player is owed through their own ledger and forgets it.
     *
     * @param player the returning player's id
     * @param ledger their own ledger
     * @return the whole mB the remainders made up together, which the player is paid
     */
    public GooContents handOver(UUID player, IRemainderLedger ledger) {
        GooRemainders due = owed.remove(player);
        if (due == null) {
            return GooContents.EMPTY;
        }
        setDirty();
        return new GooContents(ledger.creditAllExact(due.parts()));
    }
}
