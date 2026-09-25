package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.program.PlaceBlockStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.StepTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A single ability within a goo type's repertoire. Loaded from datapack
 * JSON under {@code data/<ns>/goo_abilities/}. Defines the step program,
 * cost formula, chain parameters, and display metadata.
 *
 * @param id          the datapack resource identifier (from filename)
 * @param gooType     the goo type this ability belongs to
 * @param displayName the translation key for the ability name
 * @param icon        the texture path for the radial menu icon
 * @param order       sort order within the type's ability list
 * @param cost        the cost formula for this ability
 * @param chain       chain marker parameters (nullable for non-chain abilities)
 * @param behaviors   the step trees the ability runs, in order
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
        List<Step> behaviors,
        List<String> tags
) {

    private static final String FIELD_GOO_TYPE = "gooType";
    private static final String FIELD_DISPLAY_NAME = "displayName";
    private static final String FIELD_ICON = "icon";
    private static final String NO_ICON = "";
    private static final String FIELD_ORDER = "order";
    private static final String FIELD_COST = "cost";
    private static final String FIELD_CHAIN = "chain";
    private static final String FIELD_BEHAVIORS = "behaviors";
    private static final String FIELD_TAGS = "tags";

    /**
     * Builds the codec for one ability file. The id comes from the filename, not the
     * JSON body, so the loader builds a codec per file and every definition carries
     * its id from construction (decision delete-dead-fold-mirrors).
     *
     * @param id the ability's id, from its filename
     * @return the codec decoding that file into a definition with that id
     */
    public static Codec<AbilityDefinition> codecFor(Identifier id) {
        return RecordCodecBuilder.create(inst -> inst.group(
                GooTypes.ID_CODEC.fieldOf(FIELD_GOO_TYPE).forGetter(AbilityDefinition::gooType),
                Codec.STRING.fieldOf(FIELD_DISPLAY_NAME).forGetter(AbilityDefinition::displayName),
                Codec.STRING.optionalFieldOf(FIELD_ICON, NO_ICON).forGetter(AbilityDefinition::icon),
                Codec.INT.optionalFieldOf(FIELD_ORDER, 0).forGetter(AbilityDefinition::order),
                AbilityCost.CODEC.fieldOf(FIELD_COST).forGetter(AbilityDefinition::cost),
                ChainConfig.CODEC.optionalFieldOf(FIELD_CHAIN, ChainConfig.DEFAULT).forGetter(AbilityDefinition::chain),
                StepTypes.LIST_CODEC.fieldOf(FIELD_BEHAVIORS).forGetter(AbilityDefinition::behaviors),
                Codec.STRING.listOf().optionalFieldOf(FIELD_TAGS, List.of()).forGetter(AbilityDefinition::tags)
        ).apply(inst, (gooType, displayName, icon, order, cost, chain, behaviors, tags) ->
                new AbilityDefinition(id, gooType, displayName, icon, order,
                        cost, chain, behaviors, tags)));
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
                .flatMap(AbilityDefinition::withDescendants)
                .filter(PlaceBlockStep.class::isInstance)
                .map(step -> ((PlaceBlockStep) step).block())
                .collect(Collectors.toUnmodifiableSet());
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
     */
    public record ChainConfig(
            int fuseTicks,
            int maxStacks,
            String blobShape
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
        static final ChainConfig DEFAULT = new ChainConfig(30, 1, SHAPE_BLOB);

        static final Codec<ChainConfig> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.optionalFieldOf("fuseTicks", 30).forGetter(ChainConfig::fuseTicks),
                Codec.INT.optionalFieldOf("maxStacks", 1).forGetter(ChainConfig::maxStacks),
                Codec.STRING.optionalFieldOf("blobShape", SHAPE_BLOB).forGetter(ChainConfig::blobShape)
        ).apply(inst, ChainConfig::new));
    }
}
