package com.mercuriusxeno.goo.ability.gate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Dragon Gate pairs open on a server, saved in the overworld's data so a
 * pair closes on its clock however long its chunks stood unloaded or the
 * server stood stopped.
 * Decision dragon-gate-banishes-blocks-and-opens-a-portal.
 */
public class DragonGates extends SavedData {

    /** Saves the open pairs. */
    public static final Codec<DragonGates> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Pair.CODEC.listOf().fieldOf("pairs").forGetter(gates -> gates.pairs)
    ).apply(inst, DragonGates::new));

    /** Stored as goo/dragon_gates.dat in the overworld's data. */
    public static final SavedDataType<DragonGates> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("goo", "dragon_gates"), DragonGates::new, CODEC);

    private final List<Pair> pairs;

    /** Creates a server's gates with none open. */
    public DragonGates() {
        this(List.of());
    }

    /**
     * Creates a server's gates from what it saved.
     *
     * @param pairs the open pairs
     */
    public DragonGates(List<Pair> pairs) {
        this.pairs = new ArrayList<>(pairs);
    }

    /**
     * One open pair: the gate laid where the blob landed and its mirror in the
     * End, and the overworld game time both close at.
     *
     * @param near     the gate where the blob landed
     * @param far      the mirror gate on the End's platform
     * @param closesAt the overworld game time the pair closes at
     */
    public record Pair(GatePatch near, GatePatch far, long closesAt) {

        static final Codec<Pair> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                GatePatch.CODEC.fieldOf("near").forGetter(Pair::near),
                GatePatch.CODEC.fieldOf("far").forGetter(Pair::far),
                Codec.LONG.fieldOf("closes_at").forGetter(Pair::closesAt)
        ).apply(inst, Pair::new));
    }

    /**
     * The server's gates, kept in the overworld's data.
     *
     * @param level any level of the server
     * @return the gates
     */
    public static DragonGates get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Records a pair as open.
     *
     * @param pair the pair
     */
    public void open(Pair pair) {
        pairs.add(pair);
        setDirty();
    }

    /**
     * The gate a cell's gate carries travellers to.
     *
     * @param level the level the cell lies in
     * @param pos   the cell
     * @return the partner gate, empty for a cell of no open gate
     */
    public Optional<GatePatch> partnerOf(ResourceKey<Level> level, BlockPos pos) {
        for (Pair pair : pairs) {
            if (pair.near().covers(level, pos)) {
                return Optional.of(pair.far());
            }
            if (pair.far().covers(level, pos)) {
                return Optional.of(pair.near());
            }
        }
        return Optional.empty();
    }

    /**
     * Whether any open gate covers a cell.
     *
     * @param level the level the cell lies in
     * @param pos   the cell
     * @return true for a cell of an open gate
     */
    public boolean covers(ResourceKey<Level> level, BlockPos pos) {
        return partnerOf(level, pos).isPresent();
    }

    /**
     * Takes every pair whose clock has run out off the open list.
     *
     * @param now the overworld game time
     * @return the pairs to close
     */
    public List<Pair> takeExpired(long now) {
        List<Pair> expired = pairs.stream().filter(pair -> now >= pair.closesAt()).toList();
        if (!expired.isEmpty()) {
            pairs.removeAll(expired);
            setDirty();
        }
        return expired;
    }

    /**
     * The open pairs.
     *
     * @return the pairs, unmodifiable
     */
    public List<Pair> pairs() {
        return List.copyOf(pairs);
    }
}
