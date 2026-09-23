package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Static registry of loaded ability definitions. Populated by
 * {@link AbilityLoader} during datapack reload. Provides lookup
 * by identifier and by goo type.
 */
public final class AbilityRegistry {

    private static Map<Identifier, AbilityDefinition> byId = Map.of();
    private static Map<ResourceKey<GooTypeDefinition>, List<AbilityDefinition>> byType = new HashMap<>();

    private AbilityRegistry() {
    }

    /**
     * Replaces the registry contents. Called by the loader after parsing.
     *
     * @param abilities the loaded ability map keyed by resource id
     */
    static void reload(Map<Identifier, AbilityDefinition> abilities) {
        byId = Map.copyOf(abilities);
        byType = abilities.values().stream()
                .sorted(Comparator.comparingInt(AbilityDefinition::order))
                .collect(Collectors.groupingBy(
                        AbilityDefinition::gooType,
                        () -> new HashMap<>(),
                        Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)));
    }

    /**
     * Returns the ability definition for the given id, or null.
     *
     * @param id the resource identifier
     * @return the definition, or null if not found
     */
    public static @Nullable AbilityDefinition getAbility(Identifier id) {
        return byId.get(id);
    }

    /**
     * Returns all abilities for the given goo type, sorted by order.
     *
     * @param type the goo type
     * @return immutable list, empty if none registered
     */
    public static List<AbilityDefinition> getAbilitiesForType(ResourceKey<GooTypeDefinition> type) {
        return byType.getOrDefault(type, List.of());
    }

    /**
     * The ability a tap's drip of this type runs where it lands: the type's
     * tap-tagged ability lowest by order (decision tap-ability-tagged-program).
     *
     * @param type the goo type
     * @return the tap ability, or null when the type carries none
     */
    public static @Nullable AbilityDefinition tapAbilityFor(ResourceKey<GooTypeDefinition> type) {
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
    public static boolean hasAbilities(ResourceKey<GooTypeDefinition> type) {
        return !getAbilitiesForType(type).isEmpty();
    }

    /**
     * Returns true if the given ability id is valid for the given type.
     *
     * @param type      the goo type
     * @param abilityId the ability resource id
     * @return true if the ability exists and belongs to the type
     */
    public static boolean isValidAbility(ResourceKey<GooTypeDefinition> type, Identifier abilityId) {
        AbilityDefinition def = byId.get(abilityId);
        return def != null && def.gooType() == type;
    }

    /**
     * Returns the total number of loaded abilities.
     *
     * @return the count
     */
    public static int size() {
        return byId.size();
    }
}
