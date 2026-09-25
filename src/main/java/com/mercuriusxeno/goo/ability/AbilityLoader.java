package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.IdentifiedJsonScan;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
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

    private static final FileToIdConverter LISTER = FileToIdConverter.json(DIRECTORY);

    @Override
    protected Map<Identifier, AbilityDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        return IdentifiedJsonScan.scan(manager, LISTER, makeConditionalOps(JsonOps.INSTANCE),
                AbilityDefinition::codecFor);
    }

    @Override
    protected void apply(Map<Identifier, AbilityDefinition> prepared,
                         ResourceManager manager, ProfilerFiller profiler) {
        AbilityRegistry.reload(prepared);
        if (Goo.LOGGER.isInfoEnabled()) {
            Goo.LOGGER.info(LOG_LOADED, prepared.size());
        }
    }
}
