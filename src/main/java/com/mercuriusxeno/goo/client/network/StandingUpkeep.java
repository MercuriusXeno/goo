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
 * @param perTick  the mB a tick the type's standing glove effects draw, Extender's halving applied
 * @param extended whether an Extender stands, which halves a brew's own upkeep
 * @param paying   the abilities standing on the glove and paying upkeep, a prepaid brew not among them
 */
public record StandingUpkeep(double perTick, boolean extended, Set<Identifier> paying) {

    /** A type with nothing standing on it. */
    public static final StandingUpkeep NONE = new StandingUpkeep(0, false);

    /**
     * Copies the set so the record holds it unmodifiable.
     */
    public StandingUpkeep {
        paying = Set.copyOf(paying);
    }

    /**
     * Standing upkeep naming no standing ability.
     *
     * @param perTick  the mB a tick the type's standing glove effects draw
     * @param extended whether an Extender stands
     */
    public StandingUpkeep(double perTick, boolean extended) {
        this(perTick, extended, Set.of());
    }

    /**
     * The upkeep standing on a type, read through the rule ticksLeft divides by.
     *
     * @param effects the player's standing effects
     * @param type    the goo type a count reads
     * @return the type's standing upkeep
     */
    public static StandingUpkeep on(HeldEffects effects, ResourceKey<GooTypeDefinition> type) {
        return new StandingUpkeep(effects.upkeepPerTick().getOrDefault(type, 0.0), effects.extended(),
                effects.held().stream().filter(standing -> !standing.prepaid()).map(HeldEffects.Held::ability)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    /**
     * A brew's own upkeep a tick once it stands beside these effects: halved while an Extender
     * stands, unless the brew is the Extender.
     *
     * @param upkeep        the brew's upkeep a tick
     * @param extendsOthers whether the brew is itself an Extender
     * @return the upkeep it would draw a tick
     */
    double ownBrewUpkeep(int upkeep, boolean extendsOthers) {
        return extended && !extendsOthers ? (double) upkeep / HeldEffects.extendedDivisor() : upkeep;
    }
}
