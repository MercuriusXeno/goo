package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.AwaitEntityStep;
import com.mercuriusxeno.goo.ability.program.CrushStep;
import com.mercuriusxeno.goo.ability.program.EntitiesStep;
import com.mercuriusxeno.goo.ability.program.ExplodeStep;
import com.mercuriusxeno.goo.ability.program.ExplosionMarch;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.FieldEffectStep;
import com.mercuriusxeno.goo.ability.program.LeafStep;
import com.mercuriusxeno.goo.ability.program.LeafSteps;
import com.mercuriusxeno.goo.ability.program.PhasedStep;
import com.mercuriusxeno.goo.ability.program.PullStep;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.StepTypes;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

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
 * @param area        the area the glove draws while right click is held; a sphere around an
 *                    explosion is drawn at the explosion's max reach, whatever size the JSON wrote,
 *                    and an arc throw at the world or the crosshair writing none draws its program's reach
 * @param indicator   when the ability's indicator shows, while held or whenever selected
 * @param consumes    the items a throw takes from the thrower's inventory, one of each, beside its goo cost
 * @param onPrism     the steps a landing on a prism runs in place of the type's prism ability, empty for none
 * @param upkeep      the mB a held self + brew effect pays each tick it stands, zero for every other ability
 *                    (decision self-effects-trickle-until-ended)
 * @param downSound   the cue a held effect plays when it ends, empty for the shared ability-down cue
 *                    (decision held-effects-sound-up-and-down)
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
        List<Identifier> consumes,
        List<Step> onPrism,
        int upkeep,
        Optional<SoundCue> downSound
) {

    /**
     * Draws an explosive ability's sphere at the reach its explosion cuts at most, and
     * gives an instant area throw that wrote no area the sphere its program reaches.
     * preview-sphere-is-max-reach
     * every-instant-aoe-shows-its-indicator-while-held
     */
    public AbilityDefinition {
        area = heldArea(area, delivery, badge, behaviors);
    }

    /**
     * An ability paying no upkeep, every ability but a held self + brew effect.
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
     * @param consumes    the items a throw takes, one of each
     * @param onPrism     the steps a landing on a prism runs, empty for none
     */
    public AbilityDefinition(Identifier id, ResourceKey<GooTypeDefinition> gooType, String displayName, String icon,
                             int order, int cost, Delivery delivery, List<Step> behaviors, List<String> tags,
                             AbilityBadge badge, List<Identifier> requires, AbilityArea area,
                             IndicatorShowing indicator, List<Identifier> consumes, List<Step> onPrism) {
        this(id, gooType, displayName, icon, order, cost, delivery, behaviors, tags, badge, requires, area,
                indicator, consumes, onPrism, NO_UPKEEP, Optional.empty());
    }

    /**
     * An ability with no prism reaction of its own.
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
     * @param consumes    the items a throw takes, one of each
     */
    public AbilityDefinition(Identifier id, ResourceKey<GooTypeDefinition> gooType, String displayName, String icon,
                             int order, int cost, Delivery delivery, List<Step> behaviors, List<String> tags,
                             AbilityBadge badge, List<Identifier> requires, AbilityArea area,
                             IndicatorShowing indicator, List<Identifier> consumes) {
        this(id, gooType, displayName, icon, order, cost, delivery, behaviors, tags, badge, requires, area,
                indicator, consumes, List.of());
    }

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
    private static final String FIELD_ON_PRISM = "on_prism";
    private static final String FIELD_UPKEEP = "upkeep";
    private static final String FIELD_DOWN_SOUND = "down_sound";
    /** The upkeep of an ability that holds nothing. */
    public static final int NO_UPKEEP = 0;
    /** The cost of a held self + brew effect, which starts free and pays its upkeep after. */
    private static final int NO_COST = 0;
    private static final String NOT_A_FLAT_COST = "Ability cost must be one whole amount, not %s";

    /** Reads the radius a radius-bearing step reaches, each reader answering empty for any other step. */
    private static final List<Function<Step, Optional<Expr>>> RADIUS_READERS = List.of(
            radiusOf(FieldEffectStep.class, FieldEffectStep::radius),
            radiusOf(PhasedStep.class, PhasedStep::radius),
            radiusOf(EntitiesStep.class, EntitiesStep::radius),
            radiusOf(PullStep.class, PullStep::radius),
            radiusOf(AwaitEntityStep.class, AwaitEntityStep::radius),
            radiusOf(CrushStep.class, crush -> Expr.literal(crush.radius())),
            AbilityDefinition::consumedBlocksRadius);

    /**
     * Codec for the cost: one whole number of mB per throw (decision flat-cost-per-throw).
     */
    private static final Codec<Integer> FLAT_COST_CODEC =
            Codec.DOUBLE.comapFlatMap(AbilityDefinition::flatCost, Integer::doubleValue);

    /**
     * What a held self + brew effect's JSON names beside its program: its
     * upkeep and its down sound, read as one slot so the ability's codec
     * stays within its sixteen-field group.
     *
     * @param upkeep    the mB the effect pays each tick
     * @param downSound the cue it plays when it ends, empty for the shared one
     */
    private record HeldTraits(int upkeep, Optional<SoundCue> downSound) {
    }

    private static final MapCodec<HeldTraits> HELD_TRAITS_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            FLAT_COST_CODEC.optionalFieldOf(FIELD_UPKEEP, NO_UPKEEP).forGetter(HeldTraits::upkeep),
            SoundCue.CODEC.optionalFieldOf(FIELD_DOWN_SOUND).forGetter(HeldTraits::downSound)
    ).apply(inst, HeldTraits::new));

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
                // self-effects-trickle-until-ended: a held effect names an upkeep in place of a cost
                FLAT_COST_CODEC.optionalFieldOf(FIELD_COST, NO_COST).forGetter(AbilityDefinition::cost),
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
                        .forGetter(AbilityDefinition::consumes),
                // prism-hosts-the-combos
                StepTypes.LIST_CODEC.optionalFieldOf(FIELD_ON_PRISM, List.of()).forGetter(AbilityDefinition::onPrism),
                // self-effects-trickle-until-ended, held-effects-sound-up-and-down
                HELD_TRAITS_CODEC.forGetter(def -> new HeldTraits(def.upkeep(), def.downSound()))
        ).apply(inst, (gooType, displayName, icon, order, cost, delivery, behaviors, tags, badge, requires, area,
                       indicator, consumes, onPrism, held) -> new AbilityDefinition(id, gooType, displayName, icon,
                        order, cost, delivery, behaviors, tags, badge, requires, area, indicator, consumes, onPrism,
                        held.upkeep(), held.downSound())));
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
     * The area the glove draws while right click is held. A written area stands, a
     * sphere sized to its explosion; an arc throw at the world or the crosshair that
     * wrote none draws a sphere at the reach its program covers, and every other
     * ability keeps the area it wrote.
     * every-instant-aoe-shows-its-indicator-while-held
     *
     * @param area      the area the JSON wrote
     * @param delivery  how the ability leaves the glove
     * @param badge     the target kind the ability declares
     * @param behaviors the ability's program
     * @return the area the glove draws
     */
    static AbilityArea heldArea(AbilityArea area, Delivery delivery, AbilityBadge badge, List<Step> behaviors) {
        if (area.shape() != AbilityArea.Shape.NONE || !isInstantAreaThrow(delivery, badge)) {
            return previewAtMaxReach(area, behaviors);
        }
        double reach = firstExplosion(behaviors)
                .map(step -> (double) ExplosionMarch.maxReach(step.power().evaluateFloat(Variables.NONE)))
                .orElseGet(() -> widestRadius(behaviors));
        return reach > 0 ? new AbilityArea(AbilityArea.Shape.SPHERE, reach, 0) : area;
    }

    private static boolean isInstantAreaThrow(Delivery delivery, AbilityBadge badge) {
        return delivery.kind() == DeliveryKind.ARC && (badge == AbilityBadge.WORLD || badge == AbilityBadge.FREE);
    }

    private static double widestRadius(List<Step> behaviors) {
        return behaviors.stream().flatMap(AbilityDefinition::withDescendants)
                .map(AbilityDefinition::radiusOf).flatMap(Optional::stream)
                .filter(radius -> radius.variables().isEmpty())
                .mapToDouble(radius -> radius.evaluate(Variables.NONE)).max().orElse(0);
    }

    private static Optional<Expr> radiusOf(Step step) {
        return RADIUS_READERS.stream().map(reader -> reader.apply(step)).flatMap(Optional::stream).findFirst();
    }

    private static <S extends Step> Function<Step, Optional<Expr>> radiusOf(Class<S> type, Function<S, Expr> radius) {
        return step -> type.isInstance(step) ? Optional.of(radius.apply(type.cast(step))) : Optional.empty();
    }

    private static Optional<Expr> consumedBlocksRadius(Step step) {
        return step instanceof LeafStep<?> leaf && leaf.leaf() == LeafSteps.CONSUME_BLOCKS
                && leaf.params() instanceof Expr radius ? Optional.of(radius) : Optional.empty();
    }

    /**
     * Sizes a sphere area to the max reach of the first explode step in the
     * program, its power read with no variables bound; any other area, or a
     * sphere over a program that never explodes, stands as written.
     *
     * @param area      the area the JSON wrote
     * @param behaviors the ability's program
     * @return the area the glove draws
     */
    static AbilityArea previewAtMaxReach(AbilityArea area, List<Step> behaviors) {
        if (area.shape() != AbilityArea.Shape.SPHERE) {
            return area;
        }
        return firstExplosion(behaviors)
                .map(step -> new AbilityArea(area.shape(),
                        ExplosionMarch.maxReach(step.power().evaluateFloat(Variables.NONE)), area.angle()))
                .orElse(area);
    }

    private static Optional<ExplodeStep> firstExplosion(List<Step> behaviors) {
        return behaviors.stream().flatMap(AbilityDefinition::withDescendants)
                .filter(ExplodeStep.class::isInstance).map(ExplodeStep.class::cast).findFirst();
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(AbilityDefinition::withDescendants));
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
