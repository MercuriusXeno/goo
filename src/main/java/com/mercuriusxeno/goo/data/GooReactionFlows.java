package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluid;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Indexes reactor reactions by the fluids they yield and spend, for the audit's
 * REACTIONS section.
 * decision goo-reactions-reviewed-against-the-new-types
 *
 * @param yieldedBy each fluid spelling to the reactions producing it
 * @param spentBy   each fluid spelling to the reactions consuming it
 */
public record GooReactionFlows(SortedMap<String, SortedSet<Identifier>> yieldedBy,
                               SortedMap<String, SortedSet<Identifier>> spentBy) {

    /**
     * One reaction reduced to the spellings of its inputs and outputs.
     *
     * @param reaction the reaction's id
     * @param inputs   the spellings it spends
     * @param outputs  the spellings it yields
     */
    public record Flow(Identifier reaction, List<String> inputs, List<String> outputs) {

        /**
         * @param reaction a loaded reaction
         * @return its flow, a goo type spelled by its short id and a vanilla fluid by its id
         */
        public static Flow of(GooReaction reaction) {
            return new Flow(reaction.id(),
                    reaction.inputs().stream().map(entry -> spell(entry.fluid())).toList(),
                    reaction.outputs().stream().map(entry -> spell(entry.fluid())).toList());
        }
    }

    /**
     * @param flows the reactions, each reduced to its spellings
     * @return the reactions yielding and spending each spelling
     */
    public static GooReactionFlows index(List<Flow> flows) {
        SortedMap<String, SortedSet<Identifier>> yielded = new TreeMap<>();
        SortedMap<String, SortedSet<Identifier>> spent = new TreeMap<>();
        for (Flow flow : flows) {
            flow.outputs().forEach(fluid -> yielded.computeIfAbsent(fluid, key -> new TreeSet<>()).add(flow.reaction()));
            flow.inputs().forEach(fluid -> spent.computeIfAbsent(fluid, key -> new TreeSet<>()).add(flow.reaction()));
        }
        return new GooReactionFlows(yielded, spent);
    }

    /**
     * @param fluid a goo type key or a vanilla fluid
     * @return the goo type's short id, or the vanilla fluid's id
     */
    public static String spell(Either<ResourceKey<GooTypeDefinition>, Fluid> fluid) {
        return fluid.map(GooTypes::id, vanilla -> BuiltInRegistries.FLUID.getKey(vanilla).toString());
    }
}
