package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.program.PlaceBlockStep;
import com.mercuriusxeno.goo.ability.program.ProgressiveAreaStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.StepTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A single ability within a goo type's repertoire. Loaded from datapack
 * JSON under {@code data/<ns>/goo_abilities/}. Defines the behavior
 * composition, cost formula, chain parameters, and display metadata.
 *
 * @param id          the datapack resource identifier (from filename)
 * @param gooType     the goo type this ability belongs to
 * @param displayName the translation key for the ability name
 * @param icon        the texture path for the radial menu icon
 * @param order       sort order within the type's ability list
 * @param cost        the cost formula for this ability
 * @param chain       chain marker parameters (nullable for non-chain abilities)
 * @param behaviors   the composed behavior building blocks
 * @param tags        categorical tags (explosive, instant, trap, field-effect, etc.)
 */
public record AbilityDefinition(
        Identifier id,
        ResourceKey<GooTypeDefinition> gooType,
        String displayName,
        String icon,
        int order,
        AbilityCost cost,
        ChainConfig chain,
        List<BehaviorEntry> behaviors,
        List<String> tags
) {

    /**
     * Placeholder id used during codec parsing; replaced by filename in the loader.
     */
    private static final Identifier PLACEHOLDER_ID = Identifier.withDefaultNamespace("unknown");

    /**
     * Codec for the ability JSON. The id comes from the filename, not the JSON body.
     */
    public static final Codec<AbilityDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            GooTypes.ID_CODEC.fieldOf("gooType").forGetter(AbilityDefinition::gooType),
            Codec.STRING.fieldOf("displayName").forGetter(AbilityDefinition::displayName),
            Codec.STRING.optionalFieldOf("icon", "").forGetter(AbilityDefinition::icon),
            Codec.INT.optionalFieldOf("order", 0).forGetter(AbilityDefinition::order),
            AbilityCost.CODEC.fieldOf("cost").forGetter(AbilityDefinition::cost),
            ChainConfig.CODEC.optionalFieldOf("chain", ChainConfig.DEFAULT).forGetter(AbilityDefinition::chain),
            BehaviorEntry.CODEC.listOf().fieldOf("behaviors").forGetter(AbilityDefinition::behaviors),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(AbilityDefinition::tags)
    ).apply(inst, (gooType, displayName, icon, order, cost, chain, behaviors, tags) ->
            new AbilityDefinition(PLACEHOLDER_ID, gooType, displayName, icon, order,
                    cost, chain, behaviors, tags)));

    /**
     * Returns a copy with the datapack resource id set.
     *
     * @param resourceId the resource identifier from the filename
     * @return the definition with id applied
     */
    public AbilityDefinition withId(Identifier resourceId) {
        return new AbilityDefinition(resourceId, gooType, displayName, icon, order,
                cost, chain, behaviors, tags);
    }

    /**
     * Returns true if this ability has the given tag.
     *
     * @param tag the tag to check
     * @return true if present
     */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /**
     * Returns the ids of the blocks this ability's programs place through a
     * place_block step, at any depth of the step tree.
     *
     * @return the placed block ids
     */
    public Set<Identifier> placedBlocks() {
        return behaviors.stream()
                .flatMap(entry -> entry.steps().stream())
                .flatMap(AbilityDefinition::withDescendants)
                .filter(PlaceBlockStep.class::isInstance)
                .map(step -> ((PlaceBlockStep) step).block())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Prices the next throw at a target already holding some stacks, with
     * a block_count cost charging the blocks that stack adds to this
     * ability's footprint (decision diagnose-then-fix-fuse-and-cost).
     *
     * @param existingStacks the stacks the target already holds; zero for a first throw
     * @return the cost in mB
     */
    public int throwCost(int existingStacks) {
        return cost.costForStack(existingStacks, this::footprintBlocks);
    }

    /**
     * Counts the blocks this ability's footprint covers at a stack count:
     * its first progressive_area step's footprint, or one block per stack
     * for an ability walking no area.
     *
     * @param stacks the stack count
     * @return the block count
     */
    int footprintBlocks(int stacks) {
        return behaviors.stream()
                .flatMap(entry -> entry.steps().stream())
                .flatMap(AbilityDefinition::withDescendants)
                .filter(ProgressiveAreaStep.class::isInstance)
                .map(ProgressiveAreaStep.class::cast)
                .findFirst()
                .map(area -> area.footprintBlocks(stacks))
                .orElse(Math.max(stacks, 0));
    }

    /**
     * Streams a step and every step beneath it.
     *
     * @param step the root step
     * @return the step and its descendants
     */
    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(AbilityDefinition::withDescendants));
    }

    /**
     * Chain marker parameters for abilities that use the chain system.
     *
     * @param fuseTicks    fuse countdown (-1 for trigger-based)
     * @param maxStacks    maximum blob stacks
     * @param blobShape    cosmetic blob shape: "blob" (default) or "flat" (squished)
     * @param rangeFormula range formula name (constant, tunnel_depth, freeze_radius, etc.)
     * @param rangeValue   base value for constant range formulas
     */
    public record ChainConfig(
            int fuseTicks,
            int maxStacks,
            String blobShape,
            String rangeFormula,
            int rangeValue
    ) {
        /**
         * Default blob shape.
         */
        public static final String SHAPE_BLOB = "blob";
        /**
         * Squished blob shape.
         */
        public static final String SHAPE_FLAT = "flat";

        /**
         * Default chain config for abilities that don't specify one.
         */
        static final ChainConfig DEFAULT = new ChainConfig(30, 1, SHAPE_BLOB, "constant", 1);

        static final Codec<ChainConfig> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.optionalFieldOf("fuseTicks", 30).forGetter(ChainConfig::fuseTicks),
                Codec.INT.optionalFieldOf("maxStacks", 1).forGetter(ChainConfig::maxStacks),
                Codec.STRING.optionalFieldOf("blobShape", SHAPE_BLOB).forGetter(ChainConfig::blobShape),
                Codec.STRING.optionalFieldOf("rangeFormula", "constant").forGetter(ChainConfig::rangeFormula),
                Codec.INT.optionalFieldOf("rangeValue", 1).forGetter(ChainConfig::rangeValue)
        ).apply(inst, ChainConfig::new));
    }

    /**
     * A single behavior building block with its parameters.
     * Parameters are stored as string key-value pairs; behavior factories
     * parse them into typed values (float, boolean, etc.). A {@code program}
     * entry carries its step tree in {@code steps} instead, each step typed
     * by its own codec (decision ability-params-in-datapack).
     *
     * @param type   the behavior type name (program, entity_effect, etc.)
     * @param params the parameter map for the behavior factory
     * @param steps  the step tree of a program entry; empty for other types
     */
    public record BehaviorEntry(String type, Map<String, String> params, List<Step> steps) {

        static final Codec<BehaviorEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("type").forGetter(BehaviorEntry::type),
                Codec.unboundedMap(Codec.STRING, Codec.STRING)
                        .optionalFieldOf("params", Map.of())
                        .forGetter(BehaviorEntry::params),
                StepTypes.LIST_CODEC
                        .optionalFieldOf("steps", List.of())
                        .forGetter(BehaviorEntry::steps)
        ).apply(inst, BehaviorEntry::new));

        /**
         * Creates an entry without a step tree, the shape every
         * non-program type reads.
         *
         * @param type   the behavior type name
         * @param params the parameter map
         */
        public BehaviorEntry(String type, Map<String, String> params) {
            this(type, params, List.of());
        }

        /**
         * Gets a float parameter, returning the default if absent or unparseable.
         *
         * @param key          the parameter name
         * @param defaultValue the fallback value
         * @return the parsed float
         */
        public float getFloat(String key, float defaultValue) {
            String v = params.get(key);
            if (v == null) {
                return defaultValue;
            }
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        /**
         * Gets a boolean parameter, returning the default if absent.
         *
         * @param key          the parameter name
         * @param defaultValue the fallback value
         * @return the parsed boolean
         */
        public boolean getBool(String key, boolean defaultValue) {
            String v = params.get(key);
            return v != null ? Boolean.parseBoolean(v) : defaultValue;
        }
    }
}
