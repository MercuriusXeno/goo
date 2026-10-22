package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.ArmPose;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DistancePrice;
import com.mercuriusxeno.goo.ability.HeldRoute;
import com.mercuriusxeno.goo.ability.IndicatorShowing;
import com.mercuriusxeno.goo.ability.program.BlinkLanding;
import com.mercuriusxeno.goo.ability.program.ExtenderStep;
import com.mercuriusxeno.goo.ability.program.SpireStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.client.throwing.BlinkAim;
import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Client-side handler for the ability sync payload. Hands the abilities to
 * the connection, which holds them for its life, and reads them back for the
 * radial menu and the throw without server-side registry access
 * (decision type-package-and-per-server-holders).
 */
public final class AbilitySyncHandler {

    private static final String LOG_SYNCED = "Synced {} abilities from server";

    private AbilitySyncHandler() {
    }

    /**
     * Hands the sync payload to the connection on the client thread.
     *
     * @param payload the sync payload
     * @param context the network context
     */
    public static void handle(AbilitySyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ((ClientAbilityConnection) context.listener()).receiveClientAbilities(ClientAbilities.fromPayload(payload));
            if (Goo.LOGGER.isDebugEnabled()) {
                Goo.LOGGER.debug(LOG_SYNCED, payload.entries().size());
            }
        });
    }

    /**
     * Returns the abilities available for a goo type on the client.
     *
     * @param type the goo type
     * @return immutable list, empty if none synced
     */
    public static List<ClientAbility> getAbilitiesForType(ResourceKey<GooTypeDefinition> type) {
        return ClientAbilities.current().forType(type);
    }

    /**
     * Returns the synced ability an id names.
     *
     * @param abilityId the ability resource id string
     * @return the ability, or null when none synced under that id
     */
    public static @Nullable ClientAbility findAbility(String abilityId) {
        return ClientAbilities.current().find(abilityId);
    }

    /**
     * Returns true if the goo type has any synced abilities.
     *
     * @param type the goo type
     * @return true if at least one ability is available
     */
    public static boolean hasAbilities(ResourceKey<GooTypeDefinition> type) {
        return !getAbilitiesForType(type).isEmpty();
    }

    /**
     * A lightweight client-side ability descriptor.
     *
     * @param id          the ability resource identifier
     * @param displayName the translation key
     * @param icon        the icon texture path override (empty for convention path)
     * @param order       the sort order
     * @param tags        categorical tags for targeting and display
     * @param behaviors   the ability's step program, whose params the marker's renderers read
     * @param cost        the mB a throw costs, the same at every stack count
     * @param delivery    how the ability leaves the glove
     * @param badge       the target kind the radial marks on the icon
     * @param requires    the items the player must know before its radial petal unlocks
     * @param area        the area the glove draws while right click is held
     * @param indicator   when the ability's indicator shows
     * @param consumes    the items a throw takes, one of each, beside its goo cost
     * @param upkeep      the mB a held self + brew effect pays each tick it stands
     *                    (decision self-effects-trickle-until-ended)
     * @param distancePrice what a blink adds to the cost for its trip
     *                    (decision blink-lands-safely-costed-by-distance)
     * @param onPrism     the steps a landing on a prism runs, empty for none
     *                    (decision on-prism-reaction-wears-the-prism-badge)
     * @param pose        the arm pose the glove arm takes in place of the swing
     *                    (decision ability-json-names-its-arm-pose)
     * @param description the translation key for what the ability does, which the radial's panel shows
     *                    (decision ability-json-carries-a-description)
     * @param costMechanics the format key explaining how a zero-cost ability charges, empty for none
     *                    (decision zero-cost-field-formats-the-cost-mechanics)
     * @param costFormula the short formula a dynamically priced ability's petal reads, empty for none
     *                    (decision dynamic-costs-keep-the-mb-quantity)
     */
    public record ClientAbility(Identifier id, String displayName, String icon,
                                int order, List<String> tags,
                                List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                                List<Identifier> requires, AbilityArea area, IndicatorShowing indicator,
                                List<Identifier> consumes, int upkeep, DistancePrice distancePrice,
                                List<Step> onPrism, ArmPose pose, String description,
                                Optional<String> costMechanics, Optional<String> costFormula) {

        /**
         * A client ability naming no cost formula.
         *
         * @param id            the ability resource identifier
         * @param displayName   the translation key
         * @param icon          the icon texture path override
         * @param order         the sort order
         * @param tags          categorical tags
         * @param behaviors     the ability's step program
         * @param cost          the mB a throw costs
         * @param delivery      how the ability leaves the glove
         * @param badge         the target kind the radial marks on the icon
         * @param requires      the items the player must know before its radial petal unlocks
         * @param area          the area the glove draws while right click is held
         * @param indicator     when the ability's indicator shows
         * @param consumes      the items a throw takes, one of each
         * @param upkeep        the mB a held self + brew effect pays each tick it stands
         * @param distancePrice what a blink adds to the cost for its trip
         * @param onPrism       the steps a landing on a prism runs, empty for none
         * @param pose          the arm pose the glove arm takes in place of the swing
         * @param description   the translation key for what the ability does
         * @param costMechanics the format key explaining how a zero-cost ability charges, empty for none
         */
        public ClientAbility(Identifier id, String displayName, String icon, int order, List<String> tags,
                             List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                             List<Identifier> requires, AbilityArea area, IndicatorShowing indicator,
                             List<Identifier> consumes, int upkeep, DistancePrice distancePrice,
                             List<Step> onPrism, ArmPose pose, String description, Optional<String> costMechanics) {
            this(id, displayName, icon, order, tags, behaviors, cost, delivery, badge, requires, area, indicator,
                    consumes, upkeep, distancePrice, onPrism, pose, description, costMechanics, Optional.empty());
        }

        private static final int TICKS_PER_SECOND = 20;
        private static final String PER_SECOND = "/s";
        private static final String COUNT_SEPARATOR = " | ";
        private static final String NO_COUNT = "";
        private static final String ABILITY_ICON_PREFIX = "textures/goo/ability/";
        private static final String ICON_SUFFIX = ".png";
        private static final char NAMESPACE_SEPARATOR = ':';
        private static final Identifier SPIRE_BLOCK = Identifier.withDefaultNamespace("stone");

        /**
         * A client ability naming no cost mechanics.
         *
         * @param id            the ability resource identifier
         * @param displayName   the translation key
         * @param icon          the icon texture path override
         * @param order         the sort order
         * @param tags          categorical tags
         * @param behaviors     the ability's step program
         * @param cost          the mB a throw costs
         * @param delivery      how the ability leaves the glove
         * @param badge         the target kind the radial marks on the icon
         * @param requires      the items the player must know before its radial petal unlocks
         * @param area          the area the glove draws while right click is held
         * @param indicator     when the ability's indicator shows
         * @param consumes      the items a throw takes, one of each
         * @param upkeep        the mB a held self + brew effect pays each tick it stands
         * @param distancePrice what a blink adds to the cost for its trip
         * @param onPrism       the steps a landing on a prism runs, empty for none
         * @param pose          the arm pose the glove arm takes in place of the swing
         * @param description   the translation key for what the ability does
         */
        public ClientAbility(Identifier id, String displayName, String icon, int order, List<String> tags,
                             List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                             List<Identifier> requires, AbilityArea area, IndicatorShowing indicator,
                             List<Identifier> consumes, int upkeep, DistancePrice distancePrice,
                             List<Step> onPrism, ArmPose pose, String description) {
            this(id, displayName, icon, order, tags, behaviors, cost, delivery, badge, requires, area, indicator,
                    consumes, upkeep, distancePrice, onPrism, pose, description, Optional.empty());
        }

        /**
         * A client ability naming no description, which reads its displayName key plus ".description".
         *
         * @param id            the ability resource identifier
         * @param displayName   the translation key
         * @param icon          the icon texture path override
         * @param order         the sort order
         * @param tags          categorical tags
         * @param behaviors     the ability's step program
         * @param cost          the mB a throw costs
         * @param delivery      how the ability leaves the glove
         * @param badge         the target kind the radial marks on the icon
         * @param requires      the items the player must know before its radial petal unlocks
         * @param area          the area the glove draws while right click is held
         * @param indicator     when the ability's indicator shows
         * @param consumes      the items a throw takes, one of each
         * @param upkeep        the mB a held self + brew effect pays each tick it stands
         * @param distancePrice what a blink adds to the cost for its trip
         * @param onPrism       the steps a landing on a prism runs, empty for none
         * @param pose          the arm pose the glove arm takes in place of the swing
         */
        public ClientAbility(Identifier id, String displayName, String icon, int order, List<String> tags,
                             List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                             List<Identifier> requires, AbilityArea area, IndicatorShowing indicator,
                             List<Identifier> consumes, int upkeep, DistancePrice distancePrice,
                             List<Step> onPrism, ArmPose pose) {
            this(id, displayName, icon, order, tags, behaviors, cost, delivery, badge, requires, area, indicator,
                    consumes, upkeep, distancePrice, onPrism, pose, AbilityDefinition.defaultDescription(displayName));
        }

        /**
         * A client ability naming no arm pose, which takes its delivery kind's default.
         *
         * @param id            the ability resource identifier
         * @param displayName   the translation key
         * @param icon          the icon texture path override
         * @param order         the sort order
         * @param tags          categorical tags
         * @param behaviors     the ability's step program
         * @param cost          the mB a throw costs
         * @param delivery      how the ability leaves the glove
         * @param badge         the target kind the radial marks on the icon
         * @param requires      the items the player must know before its radial petal unlocks
         * @param area          the area the glove draws while right click is held
         * @param indicator     when the ability's indicator shows
         * @param consumes      the items a throw takes, one of each
         * @param upkeep        the mB a held self + brew effect pays each tick it stands
         * @param distancePrice what a blink adds to the cost for its trip
         * @param onPrism       the steps a landing on a prism runs, empty for none
         */
        public ClientAbility(Identifier id, String displayName, String icon, int order, List<String> tags,
                             List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                             List<Identifier> requires, AbilityArea area, IndicatorShowing indicator,
                             List<Identifier> consumes, int upkeep, DistancePrice distancePrice,
                             List<Step> onPrism) {
            this(id, displayName, icon, order, tags, behaviors, cost, delivery, badge, requires, area, indicator,
                    consumes, upkeep, distancePrice, onPrism, ArmPose.defaultFor(delivery.kind()));
        }

        /**
         * A client ability reacting to no prism itself.
         *
         * @param id            the ability resource identifier
         * @param displayName   the translation key
         * @param icon          the icon texture path override
         * @param order         the sort order
         * @param tags          categorical tags
         * @param behaviors     the ability's step program
         * @param cost          the mB a throw costs
         * @param delivery      how the ability leaves the glove
         * @param badge         the target kind the radial marks on the icon
         * @param requires      the items the player must know before its radial petal unlocks
         * @param area          the area the glove draws while right click is held
         * @param indicator     when the ability's indicator shows
         * @param consumes      the items a throw takes, one of each
         * @param upkeep        the mB a held self + brew effect pays each tick it stands
         * @param distancePrice what a blink adds to the cost for its trip
         */
        public ClientAbility(Identifier id, String displayName, String icon, int order, List<String> tags,
                             List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                             List<Identifier> requires, AbilityArea area, IndicatorShowing indicator,
                             List<Identifier> consumes, int upkeep, DistancePrice distancePrice) {
            this(id, displayName, icon, order, tags, behaviors, cost, delivery, badge, requires, area, indicator,
                    consumes, upkeep, distancePrice, List.of());
        }

        /**
         * A client ability whose cost reads no trip.
         *
         * @param id          the ability resource identifier
         * @param displayName the translation key
         * @param icon        the icon texture path override
         * @param order       the sort order
         * @param tags        categorical tags
         * @param behaviors   the ability's step program
         * @param cost        the mB a throw costs
         * @param delivery    how the ability leaves the glove
         * @param badge       the target kind the radial marks on the icon
         * @param requires    the items the player must know before its radial petal unlocks
         * @param area        the area the glove draws while right click is held
         * @param indicator   when the ability's indicator shows
         * @param consumes    the items a throw takes, one of each
         * @param upkeep      the mB a held self + brew effect pays each tick it stands
         */
        public ClientAbility(Identifier id, String displayName, String icon, int order, List<String> tags,
                             List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                             List<Identifier> requires, AbilityArea area, IndicatorShowing indicator,
                             List<Identifier> consumes, int upkeep) {
            this(id, displayName, icon, order, tags, behaviors, cost, delivery, badge, requires, area, indicator,
                    consumes, upkeep, DistancePrice.NONE);
        }

        /**
         * A client ability paying no upkeep.
         *
         * @param id          the ability resource identifier
         * @param displayName the translation key
         * @param icon        the icon texture path override
         * @param order       the sort order
         * @param tags        categorical tags
         * @param behaviors   the ability's step program
         * @param cost        the mB a throw costs
         * @param delivery    how the ability leaves the glove
         * @param badge       the target kind the radial marks on the icon
         * @param requires    the items the player must know before its radial petal unlocks
         * @param area        the area the glove draws while right click is held
         * @param indicator   when the ability's indicator shows
         * @param consumes    the items a throw takes, one of each
         */
        public ClientAbility(Identifier id, String displayName, String icon, int order, List<String> tags,
                             List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                             List<Identifier> requires, AbilityArea area, IndicatorShowing indicator,
                             List<Identifier> consumes) {
            this(id, displayName, icon, order, tags, behaviors, cost, delivery, badge, requires, area, indicator,
                    consumes, AbilityDefinition.NO_UPKEEP);
        }

        /**
         * A client ability consuming no item beside its goo cost.
         *
         * @param id          the ability resource identifier
         * @param displayName the translation key
         * @param icon        the icon texture path override
         * @param order       the sort order
         * @param tags        categorical tags
         * @param behaviors   the ability's step program
         * @param cost        the mB a throw costs
         * @param delivery    how the ability leaves the glove
         * @param badge       the target kind the radial marks on the icon
         * @param requires    the items the player must know before its radial petal unlocks
         * @param area        the area the glove draws while right click is held
         * @param indicator   when the ability's indicator shows
         */
        public ClientAbility(Identifier id, String displayName, String icon, int order, List<String> tags,
                             List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                             List<Identifier> requires, AbilityArea area, IndicatorShowing indicator) {
            this(id, displayName, icon, order, tags, behaviors, cost, delivery, badge, requires, area, indicator,
                    List.of());
        }

        /**
         * A client ability declaring no area.
         *
         * @param id          the ability resource identifier
         * @param displayName the translation key
         * @param icon        the icon texture path override
         * @param order       the sort order
         * @param tags        categorical tags
         * @param behaviors   the ability's step program
         * @param cost        the mB a throw costs
         * @param delivery    how the ability leaves the glove
         * @param badge       the target kind the radial marks on the icon
         * @param requires    the items the player must know before its radial petal unlocks
         */
        public ClientAbility(Identifier id, String displayName, String icon, int order, List<String> tags,
                             List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                             List<Identifier> requires) {
            this(id, displayName, icon, order, tags, behaviors, cost, delivery, badge, requires, AbilityArea.NONE);
        }

        /**
         * A client ability whose indicator shows while right click is held.
         *
         * @param id          the ability resource identifier
         * @param displayName the translation key
         * @param icon        the icon texture path override
         * @param order       the sort order
         * @param tags        categorical tags
         * @param behaviors   the ability's step program
         * @param cost        the mB a throw costs
         * @param delivery    how the ability leaves the glove
         * @param badge       the target kind the radial marks on the icon
         * @param requires    the items the player must know before its radial petal unlocks
         * @param area        the area the glove draws while right click is held
         */
        public ClientAbility(Identifier id, String displayName, String icon, int order, List<String> tags,
                             List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                             List<Identifier> requires, AbilityArea area) {
            this(id, displayName, icon, order, tags, behaviors, cost, delivery, badge, requires, area,
                    IndicatorShowing.HELD);
        }

        /**
         * Builds the client descriptor from a synced entry.
         *
         * @param entry the synced entry
         * @return the client ability
         */
        public static ClientAbility fromEntry(AbilitySyncPayload.Entry entry) {
            return new ClientAbility(Identifier.tryParse(entry.abilityId()), entry.displayName(), entry.icon(),
                    entry.order(), entry.tags(), entry.behaviors(),
                    entry.cost(), entry.delivery(), entry.badge(), entry.requires(), entry.area(),
                    entry.indicator(), entry.consumes(), entry.upkeep(), entry.distancePrice(), entry.onPrism(),
                    entry.pose(), entry.description(), entry.costMechanics(), entry.costFormula());
        }

        /**
         * The ability's icon texture, which the wheel and the crosshair panel draw: the explicit
         * override where the JSON names one, otherwise the convention path.
         *
         * @return the icon texture identifier
         */
        public Identifier iconTexture() {
            String override = icon();
            if (!override.isEmpty()) {
                // diagnose-then-fix-radial-icon-id: a namespaced icon keeps its namespace rather than taking a second goo:
                return override.indexOf(NAMESPACE_SEPARATOR) >= 0 ? Identifier.parse(override)
                        : Identifier.fromNamespaceAndPath(Goo.MODID, override);
            }
            return Identifier.fromNamespaceAndPath(Goo.MODID, ABILITY_ICON_PREFIX + id.getPath() + ICON_SUFFIX);
        }

        /**
         * Whether the ability carries an on_prism reaction of its own, which
         * the wheel marks with the prism badge beside its own.
         * on-prism-reaction-wears-the-prism-badge
         *
         * @return true when a landing on a prism runs the ability's own reaction
         */
        public boolean reactsToPrisms() {
            return !onPrism.isEmpty();
        }

        /**
         * The program this ability runs as a prism's combo, as the server's
         * PrismCombos.comboSteps reads it: a prism ability's own behaviors,
         * or another ability's on_prism reaction.
         * thrown-hosts-absorb-their-prism-combos
         *
         * @return the combo's program
         */
        public List<Step> comboSteps() {
            return badge == AbilityBadge.PRISM ? behaviors : onPrism;
        }

        /**
         * Whether the player knows every item this ability requires, which
         * unlocks its radial petal (decision locked-petal-stays-on-the-wheel).
         *
         * @param known the items the player knows
         * @return true when no required item is unknown
         */
        public boolean isKnownTo(KnownItems known) {
            return known.containsAll(requires);
        }

        /**
         * What the ability costs as the wheel and the crosshair panel read it:
         * a held effect's upkeep a second, as "20/s", and every other
         * ability's one-shot cost.
         * self-effects-trickle-until-ended
         *
         * @return the cost, formatted
         */
        public String costLabel() {
            return costLabel(Optional.empty());
        }

        /**
         * What the ability costs for a trip: a held effect's upkeep a second, a
         * fixed-cost stream's or channel's cost a second, and every other
         * ability's one-shot cost plus what the trip adds.
         * blink-lands-safely-costed-by-distance
         *
         * @param trip the blink's trip, empty for an ability making none
         * @return the cost, formatted
         */
        public String costLabel(Optional<BlinkLanding> trip) {
            if (upkeep > 0) {
                return GooFormat.formatAmount(upkeepPerSecond()) + PER_SECOND;
            }
            // panel-and-petals-count-uses-or-seconds: a stream or channel reads its cost a second
            if (!pricedDynamically() && paysWhileHeld()) {
                return GooFormat.formatAmount((long) cost * TICKS_PER_SECOND / delivery.ticksPerCharge()) + PER_SECOND;
            }
            return GooFormat.formatAmount(BlinkAim.tripCost(distancePrice, cost, trip));
        }

        /**
         * Whether the ability's price moves with the target, the trip, the drag or the stone it
         * plans, so no fixed cost counts its uses: one naming a cost formula.
         * dynamic-costs-keep-the-mb-quantity
         *
         * @return true for a dynamically priced ability
         */
        public boolean pricedDynamically() {
            return costFormula.isPresent();
        }

        /**
         * Whether a fixed price counts the ability's uses or seconds.
         *
         * @return true for a fixed-cost ability with a price above 0
         */
        public boolean countsUses() {
            return !pricedDynamically() && price() > 0;
        }

        /**
         * Whether the ability pays as it is held, so the holdings sustain it for seconds: a brew's
         * upkeep, or a stream's or channel's cost each ticksPerCharge.
         *
         * @return true for a brew, a stream or a channel
         */
        public boolean paysWhileHeld() {
            return upkeep > 0 || HeldRoute.runsWhileHeld(delivery, badge);
        }

        /**
         * Whether the ability is the glove's Extender, whose own upkeep a standing Extender
         * never halves (decision extender-multiplies-the-next-self-duration).
         *
         * @return true for an ability running the extender step
         */
        public boolean extendsOthers() {
            return behaviors.stream().anyMatch(ExtenderStep.class::isInstance);
        }

        /**
         * What the petal reads with nothing standing on the ability's type.
         *
         * @param holdings    the mB the player holds of the ability's type across the inventory
         * @param reagentSets the whole sets of the ability's consumed items the inventory holds
         * @return the petal's cost line
         */
        public String petalLabel(int holdings, int reagentSets) {
            return petalLabel(holdings, reagentSets, StandingUpkeep.NONE);
        }

        /**
         * What the petal reads: the shorthand cost, then the uses or time the holdings afford
         * beside the upkeep standing on the type, or a dynamically priced ability's formula.
         * panel-and-petals-count-uses-or-seconds
         * dynamic-costs-keep-the-mb-quantity
         * time-counts-net-standing-upkeep
         *
         * @param holdings    the mB the player holds of the ability's type across the inventory
         * @param reagentSets the whole sets of the ability's consumed items the inventory holds
         * @param standing    the upkeep already standing on the ability's type
         * @return "1K | x3" for a one-shot, "1K/s | 12s" for a stream, channel or brew, "200 + 100/block" for a blink
         */
        public String petalLabel(int holdings, int reagentSets, StandingUpkeep standing) {
            return costFormula.orElseGet(() -> costLabel()
                    + AffordedCount.reckon(this, holdings, reagentSets, standing)
                    .map(count -> COUNT_SEPARATOR + count.label()).orElse(NO_COUNT));
        }

        /**
         * Whether the petal dims: the holdings afford no use beside the upkeep standing on the
         * type, or a dynamically priced ability's base price exceeds them.
         *
         * @param holdings    the mB the player holds of the ability's type across the inventory
         * @param reagentSets the whole sets of the ability's consumed items the inventory holds
         * @param standing    the upkeep already standing on the ability's type
         * @return true when the petal reads unaffordable
         */
        public boolean dimsAt(int holdings, int reagentSets, StandingUpkeep standing) {
            return AffordedCount.reckon(this, holdings, reagentSets, standing).map(AffordedCount::affordsNone)
                    .orElse(price() > holdings);
        }

        /**
         * The holdings the ability needs to read affordable: a second of a
         * held effect's upkeep, or another ability's one-shot cost.
         *
         * @return the amount in mB
         */
        public int price() {
            return upkeep > 0 ? upkeepPerSecond() : cost;
        }

        private int upkeepPerSecond() {
            return upkeep * TICKS_PER_SECOND;
        }

        /**
         * How the ability charges, its cost-mechanics format string filled from its own
         * numbers, so the text stays true when a number changes; a format the lookup
         * cannot fill reads as written.
         * zero-cost-field-formats-the-cost-mechanics
         *
         * @param lookup the lang and goo values the text fills from
         * @return the text, or empty for an ability naming no cost mechanics
         */
        public Optional<String> costMechanics(CostMechanicsLookup lookup) {
            return costMechanics.map(lookup::translate)
                    .map(format -> fillCostMechanics(format, costMechanicsArguments(lookup)));
        }

        private static String fillCostMechanics(String format, List<String> arguments) {
            try {
                return String.format(Locale.ROOT, format, arguments.toArray());
            } catch (IllegalFormatException unfillable) {
                return format;
            }
        }

        /**
         * The numbers a cost-mechanics format fills from, in the order its kind reads them:
         * a brew its upkeep a second; a blink its per-block price, its wall price and its
         * oculus percent; a Spire stone's goo value per block; any other none.
         *
         * @param lookup the goo values the numbers read from
         * @return the arguments, formatted
         */
        private List<String> costMechanicsArguments(CostMechanicsLookup lookup) {
            if (upkeep > 0) {
                return List.of(GooFormat.formatAmount(upkeepPerSecond()));
            }
            if (distancePrice.perBlock() > 0 || distancePrice.wallCost() > 0) {
                return List.of(GooFormat.formatAmount(distancePrice.perBlock()),
                        GooFormat.formatAmount(distancePrice.wallCost()),
                        Integer.toString(distancePrice.nodePercent()));
            }
            if (behaviors.stream().anyMatch(SpireStep.class::isInstance)) {
                return List.of(GooFormat.formatMixed(lookup.valueOf(SPIRE_BLOCK)));
            }
            return List.of();
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
    }
}
