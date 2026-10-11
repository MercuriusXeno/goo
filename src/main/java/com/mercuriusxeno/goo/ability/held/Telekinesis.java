package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.List;

/**
 * Kinetic's Telekinesis on a player: while its held effect stands, the
 * player's block and entity interaction ranges are raised by the step's
 * reach, so breaking, placing, attacking and interacting enact at the
 * extended distance, and a faint kinetic shimmer shows on the player's
 * model and arm. The modifiers are saved with the player and come off as
 * the held effect ends.
 * telekinesis-enacts-at-extended-reach
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class Telekinesis {

    /** Ticks between one shimmer refresh and the next, so a player who starts watching sees it within a second. */
    static final int SHIMMER_REFRESH_TICKS = 20;
    /** How long each refresh lasts: past the next refresh and its fade, so the shimmer never dips. */
    static final int SHIMMER_TICKS = SHIMMER_REFRESH_TICKS * 3;
    private static final int SHIMMER_CLEARED = 0;
    /** The ranges Telekinesis raises, each with its modifier's id. */
    private static final List<Reach> REACHES = List.of(
            new Reach(Attributes.BLOCK_INTERACTION_RANGE,
                    Identifier.fromNamespaceAndPath(Goo.MODID, "telekinesis_block_reach")),
            new Reach(Attributes.ENTITY_INTERACTION_RANGE,
                    Identifier.fromNamespaceAndPath(Goo.MODID, "telekinesis_entity_reach")));

    private Telekinesis() {
    }

    private record Reach(Holder<Attribute> attribute, Identifier modifierId) {
    }

    /**
     * Raises a player's interaction ranges by a reach and starts the shimmer;
     * laying it again replaces the earlier reach.
     *
     * @param target the player
     * @param reach  the blocks added to each range
     */
    public static void lay(LivingEntity target, double reach) {
        for (Reach range : REACHES) {
            AttributeInstance instance = target.getAttribute(range.attribute());
            if (instance != null) {
                instance.addOrReplacePermanentModifier(new AttributeModifier(range.modifierId(), reach,
                        AttributeModifier.Operation.ADD_VALUE));
            }
        }
        sendShimmer(target, SHIMMER_TICKS);
    }

    /**
     * Takes Telekinesis off a player as its held effect ends: both ranges
     * back to their own, the shimmer dropped on every client.
     *
     * @param player the player
     */
    public static void clear(ServerPlayer player) {
        for (Reach range : REACHES) {
            AttributeInstance instance = player.getAttribute(range.attribute());
            if (instance != null) {
                instance.removeModifier(range.modifierId());
            }
        }
        sendShimmer(player, SHIMMER_CLEARED);
    }

    /**
     * Refreshes the shimmer of each player whose held effects lay Telekinesis.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % SHIMMER_REFRESH_TICKS != 0
                || !player.hasData(GooAttachments.HELD_EFFECTS)) {
            return;
        }
        boolean reaching = player.getData(GooAttachments.HELD_EFFECTS).held().stream()
                .anyMatch(held -> held.lays().contains(LaidState.TELEKINESIS));
        if (reaching) {
            sendShimmer(player, SHIMMER_TICKS);
        }
    }

    private static void sendShimmer(LivingEntity target, int ticks) {
        EntityVisuals.sendToWatchers(target, new AilmentPayload(target.getId(), AilmentKind.TELEKINESIS, ticks));
    }
}
