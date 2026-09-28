package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.resources.ResourceKey;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The burnout explosion each chain-bearing goo type draws, one per type
 * (decision elemental-explosion-per-type). A type whose explosion is still
 * to be designed holds an undesigned visual.
 */
public final class BurnoutVisuals {

    /** Every goo type a chain ability carries. */
    public static final List<ResourceKey<GooTypeDefinition>> CHAIN_TYPES = List.of(
            GooTypes.ROCK, GooTypes.BLAZE, GooTypes.FROST, GooTypes.NETHER,
            GooTypes.METAL, GooTypes.CRYSTAL, GooTypes.GLOW, GooTypes.UNSTABLE);

    /** The explosions designed so far, by the goo type each draws for. */
    private static final Map<ResourceKey<GooTypeDefinition>, BurnoutVisual> DESIGNED = Map.of(
            GooTypes.UNSTABLE, UnstableExplosionVisual.INSTANCE,
            GooTypes.ROCK, RockExplosionVisual.INSTANCE,
            GooTypes.BLAZE, BlazeExplosionVisual.INSTANCE,
            GooTypes.FROST, FrostExplosionVisual.INSTANCE,
            GooTypes.NETHER, NetherExplosionVisual.INSTANCE);

    private static final Map<ResourceKey<GooTypeDefinition>, BurnoutVisual> BY_TYPE =
            CHAIN_TYPES.stream().collect(Collectors.toUnmodifiableMap(Function.identity(), BurnoutVisuals::designFor));

    private BurnoutVisuals() {
    }

    /**
     * Resolves the explosion a goo type draws.
     *
     * @param gooType the goo type
     * @return the type's visual, or an undesigned one for a type no chain ability carries
     */
    public static BurnoutVisual forType(ResourceKey<GooTypeDefinition> gooType) {
        BurnoutVisual visual = BY_TYPE.get(gooType);
        return visual != null ? visual : BurnoutVisual.undesigned(gooType);
    }

    /**
     * The designed explosion of a chain-bearing type.
     *
     * @param gooType the goo type
     * @return its visual
     */
    private static BurnoutVisual designFor(ResourceKey<GooTypeDefinition> gooType) {
        BurnoutVisual designed = DESIGNED.get(gooType);
        return designed != null ? designed : BurnoutVisual.undesigned(gooType);
    }
}
