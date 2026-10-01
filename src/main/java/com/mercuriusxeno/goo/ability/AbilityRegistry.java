package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The ability definitions one datapack load holds, by identifier and by goo
 * type. {@link AbilityLoader} builds one each load and hands it to the
 * server's datapack resources, so the abilities live as long as the load
 * that read them (decision type-package-and-per-server-holders).
 */
public final class AbilityRegistry {

    /**
     * The registry a side holds before any load, and a client level's.
     */
    public static final AbilityRegistry EMPTY = new AbilityRegistry(Map.of());

    private final Map<Identifier, AbilityDefinition> byId;
    private final Map<ResourceKey<GooTypeDefinition>, List<AbilityDefinition>> byType;

    /**
     * Builds a registry of the loaded abilities.
     *
     * @param abilities the loaded ability map keyed by resource id
     */
    public AbilityRegistry(Map<Identifier, AbilityDefinition> abilities) {
        byId = Map.copyOf(abilities);
        byType = Map.copyOf(abilities.values().stream()
                .sorted(AbilityBadge.fanOrder(AbilityDefinition::badge, AbilityDefinition::order))
                .collect(Collectors.groupingBy(
                        AbilityDefinition::gooType,
                        HashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList))));
    }

    /**
     * Answers the abilities the server's current datapack load holds.
     *
     * @param server the server
     * @return the registry
     */
    public static AbilityRegistry of(MinecraftServer server) {
        return ((AbilityRegistrySource) server.getServerResources().managers()).abilityRegistry();
    }

    /**
     * Answers the abilities the level's server holds; a client level holds
     * none, since the client reads the synced abilities instead.
     *
     * @param level the level
     * @return the registry, empty on the client
     */
    public static AbilityRegistry of(Level level) {
        MinecraftServer server = level.getServer();
        return server == null ? EMPTY : of(server);
    }

    /**
     * Returns the ability definition for the given id, or null.
     *
     * @param id the resource identifier
     * @return the definition, or null if not found
     */
    public @Nullable AbilityDefinition getAbility(Identifier id) {
        return byId.get(id);
    }

    /**
     * Returns all abilities for the given goo type, in fan order: badge rank, then order.
     *
     * @param type the goo type
     * @return immutable list, empty if none registered
     */
    public List<AbilityDefinition> getAbilitiesForType(ResourceKey<GooTypeDefinition> type) {
        return byType.getOrDefault(type, List.of());
    }

    /**
     * The ability a tap's drip of this type runs where it lands: the type's
     * tap-tagged ability lowest by order (decision tap-ability-tagged-program).
     *
     * @param type the goo type
     * @return the tap ability, or null when the type carries none
     */
    public @Nullable AbilityDefinition tapAbilityFor(ResourceKey<GooTypeDefinition> type) {
        return firstTagged(getAbilitiesForType(type), AbilityTags.TAP);
    }

    /**
     * Picks the definition carrying a tag with the lowest order.
     *
     * @param definitions the definitions to pick among
     * @param tag         the tag the pick carries
     * @return the pick, or null when none carries the tag
     */
    static @Nullable AbilityDefinition firstTagged(List<AbilityDefinition> definitions, String tag) {
        return definitions.stream()
                .filter(def -> def.hasTag(tag))
                .min(Comparator.comparingInt(AbilityDefinition::order))
                .orElse(null);
    }

    /**
     * Returns true if the goo type has any registered abilities.
     *
     * @param type the goo type
     * @return true if at least one ability is registered
     */
    public boolean hasAbilities(ResourceKey<GooTypeDefinition> type) {
        return !getAbilitiesForType(type).isEmpty();
    }

    /**
     * Returns true if the given ability id is valid for the given type.
     *
     * @param type      the goo type
     * @param abilityId the ability resource id
     * @return true if the ability exists and belongs to the type
     */
    public boolean isValidAbility(ResourceKey<GooTypeDefinition> type, Identifier abilityId) {
        AbilityDefinition def = byId.get(abilityId);
        return def != null && def.gooType() == type;
    }

    /**
     * Returns the total number of loaded abilities.
     *
     * @return the count
     */
    public int size() {
        return byId.size();
    }
}
