package com.mercuriusxeno.goo.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;

/**
 * A player's palm blob buffers, one per ability that stacks what it throws,
 * Knives and Dart: each ability's fill in half percents, from the 10% floor
 * to 100%. An ability with no fill stored stands full, as a buffer is born.
 * Immutable: a change answers a new value for the holder to store.
 * decision palm-blob-is-a-buffer-that-right-click-turns-into-weapons
 * decision each-ability-has-its-own-buffer-that-fills-only-while-held
 *
 * @param fills each ability's fill in half percents, by ability id
 */
public record PalmBuffers(Map<String, Integer> fills) {

    /** A full buffer, 100%, in half percents. */
    public static final int FULL = 200;
    /** The floor a buffer never falls below, 10%, in half percents. */
    public static final int FLOOR = 20;

    private static final String FIELD_FILLS = "fills";

    /** The buffers a new player starts with: every one full. */
    public static final PalmBuffers NONE = new PalmBuffers(Map.of());

    /** Codec the player attachment saves with. */
    public static final MapCodec<PalmBuffers> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
            .fieldOf(FIELD_FILLS).xmap(PalmBuffers::new, PalmBuffers::fills);

    /**
     * Copies the map so a caller's later writes cannot reach the record.
     *
     * @param fills each ability's fill in half percents, by ability id
     */
    public PalmBuffers {
        fills = Map.copyOf(fills);
    }

    /**
     * An ability's fill.
     *
     * @param abilityId the ability's id
     * @return the fill in half percents, full where none is stored
     */
    public int fillOf(String abilityId) {
        return fills.getOrDefault(abilityId, FULL);
    }

    /**
     * The buffers with an ability's fill set, held between the floor and full.
     *
     * @param abilityId the ability's id
     * @param fill      the fill in half percents
     * @return the new buffers
     */
    public PalmBuffers with(String abilityId, int fill) {
        Map<String, Integer> next = new HashMap<>(fills);
        next.put(abilityId, Math.clamp(fill, FLOOR, FULL));
        return new PalmBuffers(next);
    }
}
