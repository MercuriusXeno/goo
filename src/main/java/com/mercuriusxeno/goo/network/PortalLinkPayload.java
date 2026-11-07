package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.PortalChoice;
import com.mercuriusxeno.goo.ability.quantum.QuantumPortals;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.type.GooTypes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import java.util.Optional;

/**
 * Client-to-server payload: while choosing, the player right clicked one of
 * their distant portals, linking it to the one they chose from.
 * portals-link-by-looking-through-portal-vision
 *
 * @param target the portal right clicked as the partner
 */
public record PortalLinkPayload(Portal target) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<PortalLinkPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "portal_link"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, PortalLinkPayload> STREAM_CODEC = StreamCodec.composite(
            Portal.STREAM_CODEC, PortalLinkPayload::target,
            PortalLinkPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handles the payload on the server thread, for a player holding the glove.
     *
     * @param payload the payload
     * @param context the network context
     */
    public static void handle(PortalLinkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && GooThrowHandler.validateGlove(player)) {
                linkPaid(player, payload.target());
            }
        });
    }

    /**
     * Links the portal a player is choosing from to another by hand, for the
     * goo a Portal throw costs; a player who cannot pay is refused, choosing
     * held and the pair standing as it was.
     *
     * @param player the player
     * @param target the portal picked as the partner
     * @return true where the link was paid for and landed
     */
    public static boolean linkPaid(ServerPlayer player, Portal target) {
        ServerLevel level = player.level();
        QuantumPortals portals = QuantumPortals.get(level);
        Optional<Portal> from = portals.choosing(player.getUUID());
        boolean lands = from.isPresent() && !from.get().equals(target)
                && portals.portalsOf(player.getUUID()).contains(target)
                && from.get().block().dimension().equals(target.block().dimension());
        AbilityDefinition portal = AbilityRegistry.of(level).getAbility(PORTAL_ABILITY);
        if (!lands || portal == null
                || !AbilityCharges.charge(player, GooTypes.QUANTUM, portal.cost().exactIn(GooValues.of(level)))) {
            return false;
        }
        PortalChoice.link(player, target);
        return true;
    }

    /** The Portal ability, whose throw's cost a link by hand costs. */
    private static final Identifier PORTAL_ABILITY = Identifier.fromNamespaceAndPath(Goo.MODID, "quantum_portal");
}
