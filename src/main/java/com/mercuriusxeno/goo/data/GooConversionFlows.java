package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Each recipe whose craft loses goo of one type and gains goo of another, read at the values the derivation
 * settled: a loophole that trivializes collecting a type is a recipe that turns a cheap type into it, so the
 * recipe gaining the most per craft reads first. The audit section reads this one computation.
 * decision workbench-lists-type-to-type-paths-worst-first
 */
public final class GooConversionFlows {
    /**
     * The most gained per craft first, then the least lost for it; a tie falls to the output's id and the types,
     * so the order reads the same every run.
     */
    private static final Comparator<Flow> FLOW_ORDER = Comparator
            .comparing(Flow::targetGained, Comparator.reverseOrder())
            .thenComparing(Flow::sourceLost)
            .thenComparing(flow -> flow.recipe().output())
            .thenComparing(flow -> GooTypes.id(flow.source()))
            .thenComparing(flow -> GooTypes.id(flow.target()));

    private GooConversionFlows() {
    }

    /**
     * One recipe moving goo from one type into another.
     *
     * @param recipe       the recipe, as the registry adapted it
     * @param source       the type the craft loses
     * @param target       the type the craft gains
     * @param sourceLost   the source goo the inputs carry beyond the outputs, per craft
     * @param targetGained the target goo the outputs carry beyond the inputs, per craft
     */
    public record Flow(RecipeInput recipe, ResourceKey<GooTypeDefinition> source, ResourceKey<GooTypeDefinition> target,
                       GooFraction sourceLost, GooFraction targetGained) {
    }

    /**
     * @param values a registry after derivation
     * @return one flow per recipe and per lost-and-gained type pair, the most gained per craft first
     */
    public static List<Flow> flowsOf(GooValueRegistry values) {
        Map<Identifier, GooValue> effective = values.table().getEffectiveValues();
        List<Flow> flows = new ArrayList<>();
        for (RecipeInput recipe : new LinkedHashSet<>(values.lastRecipes())) {
            Map<ResourceKey<GooTypeDefinition>, GooFraction> change = changePerCraft(recipe, effective);
            flows.addAll(flowsOfChange(recipe, change));
        }
        flows.sort(FLOW_ORDER);
        return List.copyOf(flows);
    }

    /**
     * @param recipe    the recipe
     * @param effective each item's effective value
     * @return per type, the output's goo less the inputs' goo for one craft; empty where an input or the output
     *         holds no value, since such a craft moves goo nobody can read
     */
    private static Map<ResourceKey<GooTypeDefinition>, GooFraction> changePerCraft(RecipeInput recipe,
                                                                                 Map<Identifier, GooValue> effective) {
        GooValue output = effective.get(recipe.output());
        if (output == null || output.isEmpty() || recipe.hasNoIngredients()) {
            return Map.of();
        }
        Map<ResourceKey<GooTypeDefinition>, GooFraction> change = new HashMap<>();
        output.exactAll().forEach((type, amount) -> change.merge(type, amount.times(recipe.resultCount()),
                GooFraction::plus));
        for (Set<Identifier> slot : recipe.ingredientAlternatives()) {
            GooValue cost = GooRecipeEvaluator.computeSlotCost(slot, recipe.containerItems(), effective, Map.of());
            if (cost == null) {
                return Map.of();
            }
            cost.exactAll().forEach((type, amount) -> change.merge(type, amount.negated(), GooFraction::plus));
        }
        return change;
    }

    private static List<Flow> flowsOfChange(RecipeInput recipe, Map<ResourceKey<GooTypeDefinition>, GooFraction> change) {
        List<Flow> flows = new ArrayList<>();
        change.forEach((source, lost) -> {
            if (!lost.isNegative()) {
                return;
            }
            change.forEach((target, gained) -> {
                if (gained.isPositive()) {
                    flows.add(new Flow(recipe, source, target, lost.negated(), gained));
                }
            });
        });
        return flows;
    }
}
