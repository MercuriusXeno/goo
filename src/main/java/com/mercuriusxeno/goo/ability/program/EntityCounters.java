package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import java.util.HashMap;
import java.util.Map;

/**
 * The named counters a struck entity keeps between blob hits, such as the
 * aeon ritual's {@code goo:ritual} (decision aeon-mob-ritual-drops-spawn-egg).
 * The entity holds it as a data attachment that saves with it, so a ritual
 * survives the mob unloading. Immutable: each write answers a new value
 * for the holder to store.
 *
 * @param values each counter's value by its id; a counter never written is absent
 */
public record EntityCounters(Map<Identifier, Double> values) {

    private static final String FIELD_VALUES = "values";

    /**
     * The empty counters a mob starts with.
     */
    public static final EntityCounters EMPTY = new EntityCounters(Map.of());

    /**
     * Codec the attachment saves with.
     */
    public static final MapCodec<EntityCounters> CODEC =
            Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).fieldOf(FIELD_VALUES)
                    .xmap(EntityCounters::new, EntityCounters::values);

    /**
     * Copies the map so a caller's later writes cannot reach the record.
     *
     * @param values each counter's value by its id
     */
    public EntityCounters {
        values = Map.copyOf(values);
    }

    /**
     * Reads a counter; a counter never written reads zero.
     *
     * @param id the counter id
     * @return the value
     */
    public double read(Identifier id) {
        return values.getOrDefault(id, 0.0);
    }

    /**
     * Answers these counters with one counter raised by an amount.
     *
     * @param id     the counter id
     * @param amount the amount to add, negative to lower it
     * @return the counters after the add
     */
    public EntityCounters withAdded(Identifier id, double amount) {
        return withValue(id, read(id) + amount);
    }

    /**
     * Answers these counters with one counter set to a value.
     *
     * @param id    the counter id
     * @param value the new value
     * @return the counters after the write
     */
    public EntityCounters withValue(Identifier id, double value) {
        Map<Identifier, Double> next = new HashMap<>(values);
        next.put(id, value);
        return new EntityCounters(next);
    }
}
