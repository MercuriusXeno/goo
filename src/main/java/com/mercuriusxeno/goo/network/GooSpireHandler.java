package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.SpireFootprint;
import com.mercuriusxeno.goo.ability.SpireLift;
import com.mercuriusxeno.goo.ability.program.FootprintHost;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.SpireStep;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * Server side of a Spire's submit: the footprint is checked against the caps
 * and the throw range, planned against the ground it stands on, its rise
 * lowered to what the caster's goo pays for, and lifted, charging the goo
 * value of the refill it places. The client held the choreography; the
 * server trusts none of it.
 * decision spire-rips-walls-and-platforms
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
     * Lifts a Spire for a player: refused whole for a glove-less player, an
     * unknown type, an ability the player may not use or that is not cast by
     * its footprint, a missing reagent, a footprint past the caps or the
     * throw range, or ground the goo cannot lift a block of.
     *
     * @param player  the casting player
     * @param payload the submit
     */
    public static void cast(ServerPlayer player, GooSpirePayload payload) {
        ResourceKey<GooTypeDefinition> gooType = GooTypes.known(payload.gooTypeId());
        if (gooType == null || !withinReach(player, payload.footprint())) {
            return;
        }
        AbilityDefinition ability = GooThrowHandler.usableAbility(player, payload.abilityId(), gooType);
        Optional<TagKey<Block>> lifts = ability == null ? Optional.empty() : SpireStep.liftsOf(ability.behaviors());
        if (castable(player, ability) && lifts.isPresent()) {
            lift(player, payload, gooType, ability, lifts.get());
        }
    }

    /**
     * Whether the submit is one the player can make: a glove held, the
     * footprint within its caps, and both corners within the throw range of
     * the player's eye.
     *
     * @param player    the casting player
     * @param footprint the footprint as submitted
     * @return true for a submit in reach
     */
    private static boolean withinReach(ServerPlayer player, SpireFootprint footprint) {
        return GooThrowHandler.validateGlove(player) && footprint.withinCaps()
                && inRange(player, footprint.corner()) && inRange(player, footprint.opposite());
    }

    private static boolean inRange(ServerPlayer player, BlockPos cell) {
        return player.getEyePosition().distanceTo(Vec3.atCenterOf(cell)) <= GooThrowHandler.MAX_RANGE;
    }

    private static boolean castable(ServerPlayer player, @Nullable AbilityDefinition ability) {
        return ability != null && ability.hasTag(AbilityTags.FOOTPRINT)
                && GooThrowHandler.holdsReagents(player, ability);
    }

    /**
     * Plans the lift at the highest rise the goo pays for, charges its refill
     * and runs the program on the planned ground.
     *
     * @param player  the casting player
     * @param payload the submit
     * @param gooType the ability's goo type
     * @param ability the ability
     * @param lifts   the blocks the ability may lift
     */
    private static void lift(ServerPlayer player, GooSpirePayload payload, ResourceKey<GooTypeDefinition> gooType,
                             AbilityDefinition ability, TagKey<Block> lifts) {
        ServerLevel level = player.level();
        int holdings = GooSourceScanner.aggregateAvailable(player).getOrDefault(gooType, 0);
        Optional<SpireLift> plan = SpireLift.affordable(level, payload.footprint(), lifts, GooValues.of(level),
                gooType, holdings);
        if (plan.isEmpty() || !GooSourceScanner.hasEnough(player, gooType, plan.get().cost())) {
            return;
        }
        GooSourceScanner.deplete(player, gooType, plan.get().cost());
        GooThrowHandler.consumeReagents(player, payload.abilityId(), gooType);
        try {
            ProgramBehavior.forHost(ability.behaviors(), HostKind.FOOTPRINT).tick(new FootprintHost(level, plan.get()));
        } catch (ProgramLoadException e) {
            Goo.LOGGER.error(LOG_PROGRAM_REFUSED, ability.id(), e.getMessage());
        }
    }
}
