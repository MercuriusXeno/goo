package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.SpireFootprint;
import com.mercuriusxeno.goo.ability.SpireGrowth;
import com.mercuriusxeno.goo.ability.ThrowRange;
import com.mercuriusxeno.goo.ability.program.FootprintHost;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Server side of a Spire's submit: the footprint is checked against the caps
 * and Spire's range, planned against the cells above it, its rise lowered
 * to what the caster's goo pays for, and grown, charging the ability's cost
 * per stone block grown. The client held the choreography; the server
 * trusts none of it.
 * decision spire-rips-walls-and-platforms
 * decision spire-grows-stone-out-of-the-top
 * decision spire-cost-field-prices-each-cubic-meter-of-stone-at-mundane-block
 */
public final class GooSpireHandler {

    private static final String LOG_PROGRAM_REFUSED = "Ability {} program refused for the footprint host: {}";

    private GooSpireHandler() {
    }

    /**
     * Handles the submit on the server thread.
     *
     * @param payload the submit
     * @param context the network context
     */
    public static void handle(GooSpirePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                cast(player, payload);
            }
        });
    }

    /**
     * Grows a Spire for a player: refused whole for a glove-less player, an
     * unknown type, an ability the player may not use or that is not cast by
     * its footprint, a missing reagent, a footprint past the caps or Spire's
     * range, or a footprint the goo cannot grow a stone block over.
     *
     * @param player  the casting player
     * @param payload the submit
     */
    public static void cast(ServerPlayer player, GooSpirePayload payload) {
        ResourceKey<GooTypeDefinition> gooType = GooTypes.known(payload.gooTypeId());
        if (gooType == null) {
            return;
        }
        AbilityDefinition ability = GooThrowHandler.usableAbility(player, payload.abilityId(), gooType);
        if (ability != null && withinReach(player, gooType, payload.footprint(), ThrowRange.of(ability))
                && castable(player, ability)) {
            grow(player, payload, gooType, ability);
        }
    }

    /**
     * Whether the submit is one the player can make: a caster held that casts the type, the
     * footprint within its caps, and both corners within Spire's range of
     * the player's eye.
     *
     * @param player    the casting player
     * @param gooType   the spire's goo type
     * @param footprint the footprint as submitted
     * @param range     Spire's range in blocks
     * @return true for a submit in reach
     */
    private static boolean withinReach(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
                                       SpireFootprint footprint, double range) {
        Vec3 eye = player.getEyePosition();
        return GooThrowHandler.validateCaster(player, gooType) && footprint.withinCaps()
                && inRange(eye, footprint.corner(), range) && inRange(eye, footprint.opposite(), range);
    }

    /**
     * Whether a footprint corner's cell stands within range of the eye.
     * decision each-ability-names-its-range-and-the-shared-throw-range-retires
     *
     * @param eye   the caster's eye
     * @param cell  the corner cell
     * @param range Spire's range in blocks
     * @return true for a cell in range
     */
    static boolean inRange(Vec3 eye, BlockPos cell, double range) {
        return eye.distanceTo(Vec3.atCenterOf(cell)) <= range;
    }

    private static boolean castable(ServerPlayer player, AbilityDefinition ability) {
        return ability.hasTag(AbilityTags.FOOTPRINT)
                && GooThrowHandler.holdsReagents(player, ability);
    }

    /**
     * Plans the growth at the highest rise the goo pays for, charges its stone
     * and runs the program on the planned footprint.
     *
     * @param player  the casting player
     * @param payload the submit
     * @param gooType the ability's goo type
     * @param ability the ability
     */
    private static void grow(ServerPlayer player, GooSpirePayload payload, ResourceKey<GooTypeDefinition> gooType,
                             AbilityDefinition ability) {
        ServerLevel level = player.level();
        int holdings = GooSourceScanner.aggregateAvailable(player).getOrDefault(gooType, 0);
        IRemainderLedger ledger = GooCapabilities.remainderLedgerOf(player);
        // spire-cost-field-prices-each-cubic-meter-of-stone-at-mundane-block
        Optional<SpireGrowth> plan = SpireGrowth.affordable(level, payload.footprint(),
                ability.cost().exactIn(GooValues.of(level)), gooType, ledger.remainders(), holdings);
        if (plan.isEmpty()) {
            return;
        }
        OptionalInt charged = plan.get().chargeThrough(ledger, gooType, holdings);
        if (charged.isEmpty()) {
            return;
        }
        GooSourceScanner.deplete(player, gooType, charged.getAsInt());
        GooThrowHandler.consumeReagents(player, payload.abilityId(), gooType);
        try {
            ProgramBehavior.forHost(ability.behaviors(), HostKind.FOOTPRINT).tick(new FootprintHost(level, plan.get()));
        } catch (ProgramLoadException e) {
            Goo.LOGGER.error(LOG_PROGRAM_REFUSED, ability.id(), e.getMessage());
        }
    }
}
