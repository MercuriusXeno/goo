package com.mercuriusxeno.goo.ability.frost;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;

/**
 * The glacial prisms standing on the server, each holding the frozen
 * gauges within its radius from thawing. A glacial prism renews its field
 * every tick its combo runs, so a field whose prism was mined or unloaded
 * lapses a tick later and needs no removal of its own
 * (decision glacial-prism-holds-the-area-frozen).
 */
public final class GlacialFields {

    /** Ticks a field outlives its last renewal, covering the tick order between prisms and mobs. */
    static final long LAPSE_TICKS = 2;

    /**
     * Where a prism stands.
     *
     * @param dimension the level it stands in
     * @param prism     its block position
     */
    private record Key(ResourceKey<Level> dimension, BlockPos prism) {
    }

    /**
     * One prism's field.
     *
     * @param center  the prism's center
     * @param radius  the field's reach in blocks
     * @param renewed the game time the prism last renewed it
     */
    record Field(Vec3 center, double radius, long renewed) {

        boolean holds(Vec3 point, long now) {
            return now - renewed <= LAPSE_TICKS && point.distanceToSqr(center) <= radius * radius;
        }
    }

    private final Map<Key, Field> fields = new HashMap<>();

    /**
     * Renews a glacial prism's field for this tick.
     *
     * @param dimension the level the prism stands in
     * @param prism     its block position
     * @param radius    the field's reach in blocks
     * @param now       the game time
     */
    public void renew(ResourceKey<Level> dimension, BlockPos prism, double radius, long now) {
        fields.put(new Key(dimension, prism.immutable()), new Field(Vec3.atCenterOf(prism), radius, now));
    }

    /**
     * Whether a standing glacial field holds a point frozen, dropping every lapsed field.
     *
     * @param dimension the level the point is in
     * @param point     the point
     * @param now       the game time
     * @return true inside a field renewed within LAPSE_TICKS
     */
    public boolean holds(ResourceKey<Level> dimension, Vec3 point, long now) {
        fields.values().removeIf(field -> now - field.renewed() > LAPSE_TICKS);
        return fields.entrySet().stream().anyMatch(entry -> entry.getKey().dimension().equals(dimension)
                && entry.getValue().holds(point, now));
    }

    /** Drops every field, as a server stop does. */
    public void clear() {
        fields.clear();
    }
}
