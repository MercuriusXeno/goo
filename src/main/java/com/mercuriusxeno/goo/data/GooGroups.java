package com.mercuriusxeno.goo.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The base values' custom groups, each member in file order, merged over every pack and held for the running
 * server, so an ability reads "this becomes that" from two groups paired by position. The groups load on every
 * datapack load, apart from the value cache, and are dropped when the server stops.
 * this-becomes-that-is-two-parallel-groups-in-the-goo-workbench
 */
public final class GooGroups extends SimplePreparableReloadListener<Map<String, List<Identifier>>> {

    /** Registration id for the reload listener. */
    public static final Identifier LISTENER_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "goo_groups");

    private static final String GROUPS = "_groups";
    private static final String LOG_LOADED = "Loaded {} goo groups";

    private static volatile Map<String, List<Identifier>> current = Map.of();

    /**
     * Each group the current load holds, its members in file order.
     *
     * @return each group's members by group name
     */
    public static Map<String, List<Identifier>> current() {
        return current;
    }

    /** Drops the held groups, as a server stop does. */
    public static void clear() {
        current = Map.of();
    }

    /**
     * Reads each custom group of a base_values.json object, its members in file order. A member written twice stays
     * at each position it holds, since a group paired by position may name one block for several partners.
     *
     * @param baseValues the base values, one pack's or the merge of every pack's
     * @return each group's members by group name
     */
    public static Map<String, List<Identifier>> groupsOf(JsonObject baseValues) {
        if (!baseValues.has(GROUPS) || !baseValues.get(GROUPS).isJsonObject()) {
            return Map.of();
        }
        Map<String, List<Identifier>> groups = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> group : baseValues.getAsJsonObject(GROUPS).entrySet()) {
            if (group.getValue().isJsonArray()) {
                groups.put(group.getKey(), membersInOrder(group.getValue().getAsJsonArray()));
            }
        }
        return Map.copyOf(groups);
    }

    private static List<Identifier> membersInOrder(JsonArray members) {
        List<Identifier> ordered = new ArrayList<>();
        for (JsonElement member : members) {
            ordered.add(Identifier.parse(member.getAsString()));
        }
        return List.copyOf(ordered);
    }

    /**
     * The member of one group standing at the position another group's member stands at.
     *
     * @param groups    each group's members by group name
     * @param fromGroup the group the member stands in
     * @param toGroup   the group read at the same position
     * @param member    the member looked up
     * @return the paired member, empty where the member stands in no such group or the other holds no partner
     */
    public static Optional<Identifier> pairedMember(Map<String, List<Identifier>> groups, String fromGroup,
                                                   String toGroup, Identifier member) {
        int position = groups.getOrDefault(fromGroup, List.of()).indexOf(member);
        List<Identifier> partners = groups.getOrDefault(toGroup, List.of());
        return position < 0 || position >= partners.size() ? Optional.empty() : Optional.of(partners.get(position));
    }

    /**
     * Holds the groups of the base values a load merged, so a values manager Save or a regen reaches the abilities
     * without a /reload.
     *
     * @param mergedBaseValues the merged base values, null where the load merged none
     */
    static void hold(@Nullable JsonObject mergedBaseValues) {
        if (mergedBaseValues != null) {
            current = groupsOf(mergedBaseValues);
        }
    }

    @Override
    protected Map<String, List<Identifier>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Identifier location = Identifier.fromNamespaceAndPath(GooValueLoader.modNamespace(),
                GooValueLoader.baseValuesResource());
        List<JsonObject> layers = GooValueLoader.parseResourceLayers(manager.getResourceStack(location));
        return groupsOf(GooValueMerger.mergeBaseValueJsonLayers(layers));
    }

    @Override
    protected void apply(Map<String, List<Identifier>> prepared, ResourceManager manager, ProfilerFiller profiler) {
        current = prepared;
        Goo.LOGGER.info(LOG_LOADED, prepared.size());
    }
}
