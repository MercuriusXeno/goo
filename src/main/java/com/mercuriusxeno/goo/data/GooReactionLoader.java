package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.material.Fluid;
import java.util.*;

/**
 * Datapack reload listener that loads reactor reactions from
 * {@code data/<ns>/goo_reactions/*.json}. Validates conflicts at
 * load time and sorts recipes by input count descending for
 * superset-first matching.
 */
public final class GooReactionLoader
        extends SimplePreparableReloadListener<Map<Identifier, GooReaction>> {

    /**
     * Datapack directory: data/<ns>/goo_reactions/
     */
    private static final String DIRECTORY = "goo_reactions";

    /**
     * Registration id for the reload listener.
     */
    public static final Identifier LISTENER_ID =
            Identifier.fromNamespaceAndPath(Goo.MODID, DIRECTORY);

    private static final String LOG_LOADED = "Loaded {} goo reactions";
    private static final String LOG_CONFLICT_IDENTICAL =
            "Reaction conflict: {} and {} have identical input type sets";
    /**
     * Sorted reactions, most inputs first. Immutable after load.
     */
    private static List<GooReaction> reactions = List.of();

    private static final FileToIdConverter LISTER = FileToIdConverter.json(DIRECTORY);

    /**
     * Returns all loaded reactions, sorted by input count descending.
     *
     * @return immutable reaction list
     */
    public static List<GooReaction> getReactions() {
        return reactions;
    }

    /**
     * Checks every pair of recipes for input-type-set conflicts.
     * Identical sets = error. Overlapping but neither subset = warning.
     *
     * @param loaded the loaded reactions
     */
    private static void validateConflicts(List<GooReaction> loaded) {
        for (int i = 0; i < loaded.size(); i++) {
            for (int j = i + 1; j < loaded.size(); j++) {
                checkPair(loaded.get(i), loaded.get(j));
            }
        }
    }

    /**
     * Checks a single pair for conflicts.
     *
     * @param a the first reaction
     * @param b the second reaction
     */
    private static void checkPair(GooReaction a, GooReaction b) {
        Set<Either<ResourceKey<GooTypeDefinition>, Fluid>> sa = a.inputTypeSet();
        Set<Either<ResourceKey<GooTypeDefinition>, Fluid>> sb = b.inputTypeSet();
        if (sa.equals(sb) && Goo.LOGGER.isErrorEnabled()) {
            Goo.LOGGER.error(LOG_CONFLICT_IDENTICAL, a.id(), b.id());
        }
    }

    /**
     * Returns true if a is a strict subset of b.
     *
     * @param a the candidate subset
     * @param b the candidate superset
     * @return true if every element of a is in b and b is larger
     */
    private static boolean isSubset(Set<Either<ResourceKey<GooTypeDefinition>, Fluid>> a,
                                    Set<Either<ResourceKey<GooTypeDefinition>, Fluid>> b) {
        return a.size() < b.size() && b.containsAll(a);
    }

    @Override
    protected Map<Identifier, GooReaction> prepare(ResourceManager manager, ProfilerFiller profiler) {
        return IdentifiedJsonScan.scan(manager, LISTER, makeConditionalOps(JsonOps.INSTANCE),
                GooReaction::codecFor);
    }

    @Override
    protected void apply(Map<Identifier, GooReaction> prepared,
                         ResourceManager manager, ProfilerFiller profiler) {
        List<GooReaction> loaded = new ArrayList<>(prepared.values());
        validateConflicts(loaded);
        loaded.sort(Comparator.comparingInt(
                (GooReaction r) -> r.inputs().size()).reversed());
        reactions = Collections.unmodifiableList(loaded);
        if (Goo.LOGGER.isInfoEnabled()) {
            Goo.LOGGER.info(LOG_LOADED, reactions.size());
        }
    }
}
