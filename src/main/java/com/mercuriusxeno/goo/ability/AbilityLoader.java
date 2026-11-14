package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.data.IdentifiedJsonScan;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Datapack reload listener that loads ability definitions from
 * {@code data/<ns>/goo_abilities/*.json}. Populates the
 * {@link AbilityRegistry} on each reload.
 */
public final class AbilityLoader
        extends SimplePreparableReloadListener<Map<Identifier, AbilityDefinition>> {

    /**
     * Datapack directory: data/<ns>/goo_abilities/
     */
    private static final String DIRECTORY = "goo_abilities";

    /**
     * Registration id for the reload listener.
     */
    public static final Identifier LISTENER_ID =
            Identifier.fromNamespaceAndPath(Goo.MODID, DIRECTORY);

    private static final String LOG_LOADED = "Loaded {} goo abilities";
    private static final String SECOND_DURATIVE = "Goo type %s carries two durative abilities, %s and %s; it may carry one";

    private static final FileToIdConverter LISTER = FileToIdConverter.json(DIRECTORY);

    private final AbilityRegistrySource source;

    /**
     * A loader that hands what it reads to the load's resources.
     *
     * @param source the reloadable resources this load builds
     */
    public AbilityLoader(AbilityRegistrySource source) {
        this.source = source;
    }

    @Override
    protected Map<Identifier, AbilityDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        return IdentifiedJsonScan.scan(manager, LISTER, makeConditionalOps(JsonOps.INSTANCE),
                AbilityDefinition::codecFor);
    }

    @Override
    protected void apply(Map<Identifier, AbilityDefinition> prepared,
                         ResourceManager manager, ProfilerFiller profiler) {
        refuseASecondDurative(prepared);
        source.holdAbilityRegistry(new AbilityRegistry(prepared));
        if (Goo.LOGGER.isInfoEnabled()) {
            Goo.LOGGER.info(LOG_LOADED, prepared.size());
        }
    }

    /**
     * Holds each goo type to at most one durative ability: a type carrying a
     * second fails the load naming both, and a type carrying none loads.
     * decision every-type-ships-one-brew-ability
     * decision goo-type-ships-without-a-durative-ability
     *
     * @param abilities the loaded abilities
     * @throws ProgramLoadException when a type carries two durative abilities
     */
    static void refuseASecondDurative(Map<Identifier, AbilityDefinition> abilities) {
        Map<ResourceKey<GooTypeDefinition>, Identifier> durativeOf = new HashMap<>();
        abilities.values().stream()
                .filter(def -> DurativeRoute.isDurative(def.delivery(), def.badge()))
                .sorted(Comparator.comparing(AbilityDefinition::id))
                .forEach(def -> {
                    Identifier first = durativeOf.putIfAbsent(def.gooType(), def.id());
                    if (first != null) {
                        throw new ProgramLoadException(
                                String.format(SECOND_DURATIVE, def.gooType().identifier(), first, def.id()));
                    }
                });
    }
}
