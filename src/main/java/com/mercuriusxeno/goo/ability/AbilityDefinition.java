package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.StepTypes;
import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.List;

/**
 * A single ability within a goo type's repertoire. Loaded from datapack
 * JSON under {@code data/<ns>/goo_abilities/}. Defines the step program,
 * flat cost per throw, and display metadata.
 *
 * @param id          the datapack resource identifier (from filename)
 * @param gooType     the goo type this ability belongs to
 * @param displayName the translation key for the ability name
 * @param icon        the texture path for the radial menu icon
 * @param order       sort order within the type's ability list
 * @param cost        the mB a throw costs
 * @param delivery    how the ability leaves the glove (decision delivery-block-in-ability-json)
 * @param behaviors   the step trees the ability runs, in order
 * @param tags        categorical tags (explosive, instant, trap, field-effect, etc.)
 * @param badge       the target kind the radial marks on the icon
 * @param requires    the items a player must know before the ability is theirs
 * @param area        the area the glove draws while right click is held
 * @param indicator   when the ability's indicator shows, while held or whenever selected
 * @param consumes    the items a throw takes from the thrower's inventory, one of each, beside its goo cost
 */
public record AbilityDefinition(
        Identifier id,
        ResourceKey<GooTypeDefinition> gooType,
        String displayName,
        String icon,
        int order,
        int cost,
        Delivery delivery,
        List<Step> behaviors,
        List<String> tags,
        AbilityBadge badge,
        List<Identifier> requires,
        AbilityArea area,
        IndicatorShowing indicator,
        List<Identifier> consumes
) {

    /**
     * An ability consuming no item beside its goo cost.
     *
     * @param id          the datapack resource identifier
     * @param gooType     the goo type this ability belongs to
     * @param displayName the translation key for the ability name
     * @param icon        the texture path for the radial menu icon
     * @param order       sort order within the type's ability list
     * @param cost        the mB a throw costs
     * @param delivery    how the ability leaves the glove
     * @param behaviors   the step trees the ability runs
     * @param tags        categorical tags
     * @param badge       the target kind the radial marks on the icon
     * @param requires    the items a player must know before the ability is theirs
     * @param area        the area the glove draws while right click is held
     * @param indicator   when the ability's indicator shows
     */
    public AbilityDefinition(Identifier id, ResourceKey<GooTypeDefinition> gooType, String displayName, String icon,
                             int order, int cost, Delivery delivery, List<Step> behaviors, List<String> tags,
                             AbilityBadge badge, List<Identifier> requires, AbilityArea area,
                             IndicatorShowing indicator) {
        this(id, gooType, displayName, icon, order, cost, delivery, behaviors, tags, badge, requires, area,
                indicator, List.of());
    }

    /**
     * An ability declaring no area.
     *
     * @param id          the datapack resource identifier
     * @param gooType     the goo type this ability belongs to
     * @param displayName the translation key for the ability name
     * @param icon        the texture path for the radial menu icon
     * @param order       sort order within the type's ability list
     * @param cost        the mB a throw costs
     * @param delivery    how the ability leaves the glove
     * @param behaviors   the step trees the ability runs
     * @param tags        categorical tags
     * @param badge       the target kind the radial marks on the icon
     * @param requires    the items a player must know before the ability is theirs
     */
    public AbilityDefinition(Identifier id, ResourceKey<GooTypeDefinition> gooType, String displayName, String icon,
                             int order, int cost, Delivery delivery, List<Step> behaviors, List<String> tags,
                             AbilityBadge badge, List<Identifier> requires) {
        this(id, gooType, displayName, icon, order, cost, delivery, behaviors, tags, badge, requires,
                AbilityArea.NONE);
    }

    /**
     * An ability whose indicator shows while right click is held.
     *
     * @param id          the datapack resource identifier
     * @param gooType     the goo type this ability belongs to
     * @param displayName the translation key for the ability name
     * @param icon        the texture path for the radial menu icon
     * @param order       sort order within the type's ability list
     * @param cost        the mB a throw costs
     * @param delivery    how the ability leaves the glove
     * @param behaviors   the step trees the ability runs
     * @param tags        categorical tags
     * @param badge       the target kind the radial marks on the icon
     * @param requires    the items a player must know before the ability is theirs
     * @param area        the area the glove draws while right click is held
     */
    public AbilityDefinition(Identifier id, ResourceKey<GooTypeDefinition> gooType, String displayName, String icon,
                             int order, int cost, Delivery delivery, List<Step> behaviors, List<String> tags,
                             AbilityBadge badge, List<Identifier> requires, AbilityArea area) {
        this(id, gooType, displayName, icon, order, cost, delivery, behaviors, tags, badge, requires, area,
                IndicatorShowing.HELD);
    }

    private static final String FIELD_GOO_TYPE = "gooType";
    private static final String FIELD_DISPLAY_NAME = "displayName";
    private static final String FIELD_ICON = "icon";
    private static final String NO_ICON = "";
    private static final String FIELD_ORDER = "order";
    private static final String FIELD_COST = "cost";
    private static final String FIELD_DELIVERY = "delivery";
    private static final String FIELD_BEHAVIORS = "behaviors";
    private static final String FIELD_TAGS = "tags";
    private static final String FIELD_BADGE = "badge";
    private static final String FIELD_REQUIRES = "requires";
    private static final String FIELD_AREA = "area";
    private static final String FIELD_INDICATOR = "indicator";
    private static final String FIELD_CONSUMES = "consumes";
    private static final String NOT_A_FLAT_COST = "Ability cost must be one whole amount, not %s";

    /**
     * Codec for the cost: one whole number of mB per throw (decision flat-cost-per-throw).
     */
    private static final Codec<Integer> FLAT_COST_CODEC =
            Codec.DOUBLE.comapFlatMap(AbilityDefinition::flatCost, Integer::doubleValue);

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
                FLAT_COST_CODEC.fieldOf(FIELD_COST).forGetter(AbilityDefinition::cost),
                Delivery.CODEC.fieldOf(FIELD_DELIVERY).forGetter(AbilityDefinition::delivery),
                StepTypes.LIST_CODEC.fieldOf(FIELD_BEHAVIORS).forGetter(AbilityDefinition::behaviors),
                Codec.STRING.listOf().optionalFieldOf(FIELD_TAGS, List.of()).forGetter(AbilityDefinition::tags),
                // badge-marks-the-target-kind: required, so every ability declares its kind
                AbilityBadge.CODEC.fieldOf(FIELD_BADGE).forGetter(AbilityDefinition::badge),
                // ability-hidden-until-recipes-known
                Identifier.CODEC.listOf().optionalFieldOf(FIELD_REQUIRES, List.of())
                        .forGetter(AbilityDefinition::requires),
                // right-click-held-previews-release-throws
                AbilityArea.CODEC.optionalFieldOf(FIELD_AREA, AbilityArea.NONE).forGetter(AbilityDefinition::area),
                // ripple-outline-is-the-blink-cursor
                IndicatorShowing.CODEC.optionalFieldOf(FIELD_INDICATOR, IndicatorShowing.HELD)
                        .forGetter(AbilityDefinition::indicator),
                // ability-json-names-its-reagent
                Identifier.CODEC.listOf().optionalFieldOf(FIELD_CONSUMES, List.of())
                        .forGetter(AbilityDefinition::consumes)
        ).apply(inst, (gooType, displayName, icon, order, cost, delivery, behaviors, tags, badge, requires, area,
                       indicator, consumes) -> new AbilityDefinition(id, gooType, displayName, icon, order,
                        cost, delivery, behaviors, tags, badge, requires, area, indicator, consumes)));
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
     * Whether a player who knows these items may have this ability: they know
     * every item it requires (decision ability-hidden-until-recipes-known).
     *
     * @param known the items the player knows
     * @return true when no required item is unknown
     */
    public boolean isKnownTo(KnownItems known) {
        return known.containsAll(requires);
    }

    /**
     * Accepts a cost that is one whole, non-negative number of mB and refuses any
     * other, naming the cost it read.
     *
     * @param cost the cost as the JSON wrote it
     * @return the cost, or an error naming it
     */
    private static DataResult<Integer> flatCost(double cost) {
        return cost >= 0 && cost <= Integer.MAX_VALUE && cost == Math.rint(cost)
                ? DataResult.success((int) cost)
                : DataResult.error(() -> NOT_A_FLAT_COST.formatted(cost));
    }
}
