package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.ability.held.HeldEffects;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The upkeep already standing on one goo type, which a time count on that type subtracts.
 * time-counts-net-standing-upkeep
 *
 * @param perTick the mB a tick the type's standing held effects draw
 * @param paying  the abilities standing on the glove and paying upkeep
 */
public record StandingUpkeep(double perTick, Set<Identifier> paying) {

    /** A type with nothing standing on it. */
    public static final StandingUpkeep NONE = new StandingUpkeep(0);

    /**
     * Copies the set so the record holds it unmodifiable.
     */
    public StandingUpkeep {
        paying = Set.copyOf(paying);
    }

    /**
     * Standing upkeep naming no standing ability.
     *
     * @param perTick the mB a tick the type's standing held effects draw
     */
    public StandingUpkeep(double perTick) {
        this(perTick, Set.of());
    }

    /**
     * The upkeep standing on a type, read through the rule ticksLeft divides by.
     *
     * @param effects the player's standing effects
     * @param type    the goo type a count reads
     * @return the type's standing upkeep
     */
    public static StandingUpkeep on(HeldEffects effects, ResourceKey<GooTypeDefinition> type) {
        return new StandingUpkeep(effects.upkeepPerTick().getOrDefault(type, 0.0),
                effects.held().stream().map(HeldEffects.Held::ability)
                        .collect(Collectors.toUnmodifiableSet()));
    }
}
