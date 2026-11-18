package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveButton;
import com.mercuriusxeno.goo.ability.ThrowRange;
import com.mercuriusxeno.goo.ability.program.PortalStep;
import com.mercuriusxeno.goo.network.CastDart;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import com.mercuriusxeno.goo.network.PalmBlobChargePayload;
import com.mercuriusxeno.goo.throwing.ThrowArc;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Portal shot as the server takes it: the button pressed names the
 * color, left click blue and right click orange, and the shot goes straight
 * along the shooter's look to the face it strikes, as Bulb's cast does,
 * wherever the client's aim fell. A shot the client aimed through a linked
 * gate places nothing, since no portal is shot through a portal. The shot then
 * casts as any throw does, paying and refusing alike, its color held for
 * the cast alone.
 * portal-takes-over-left-and-right-click-for-blue-and-orange
 * portal-has-no-bullet-drop-and-goes-where-you-point-like-bulb
 */
public final class PortalShots {

    /** The color of the shot each player is casting now, held only while the cast runs. */
    private static final Map<UUID, PortalColor> SHOOTING = new ConcurrentHashMap<>();

    private PortalShots() {
    }

    /**
     * Shoots Portal for a player by the button pressed; a shot naming an
     * ability that places no portals, aimed through a linked gate, or
     * striking nothing along the look, does nothing.
     *
     * @param player the shooting player
     * @param button the button pressed
     * @param shot   the cast the client aimed
     */
    public static void shoot(ServerPlayer player, GloveButton button, GooThrowPayload shot) {
        Optional<AbilityDefinition> portal = portalNamed(player, shot.abilityId());
        // operator UAT 2026-11-17: you aren't supposed to be able to shoot portals through portals
        if (portal.isEmpty() || shot.gate().isPresent()) {
            return;
        }
        PortalColor color = PortalColor.pressedBy(button);
        alongTheLook(player, shot, ThrowRange.of(portal.get())).ifPresent(cast -> {
            casting(player.getUUID(), color, () -> GooThrowHandler.execute(player, cast));
            // operator UAT 2026-11-17: the palm blob hops as it shoots, for the players watching too
            PalmBlobChargePayload.sendLift(player);
            if (opened(player, cast, color)) {
                dartTheShot(player, cast, color);
            }
        });
    }

    /**
     * Whether a shot opened its portal: the shooter's portal of its color now
     * covers the face it struck.
     *
     * @param player the shooting player
     * @param cast   the cast at the struck face
     * @param color  the shot's color
     * @return true where the portal opened
     */
    private static boolean opened(ServerPlayer player, GooThrowPayload cast, PortalColor color) {
        Direction face = GooThrowHandler.directionFromOrdinal(cast.targetFace());
        QuantumPortals portals = QuantumPortals.get(player.level());
        return face != null && portals.portalAt(GlobalPos.of(player.level().dimension(), cast.targetPos()), face)
                .filter(standing -> standing.color() == color
                        && portals.portalsOf(player.getUUID()).contains(standing))
                .isPresent();
    }

    /**
     * Darts the shot from the lifted palm blob to the face it struck, a small
     * fast Quantum blob in its portal's color, for the shooter and the players
     * watching.
     * operator UAT 2026-11-18: quantum goo's color is orange and blue; the shots fire orange on right
     *
     * @param player the shooting player
     * @param cast   the cast at the struck face
     * @param color  the shot's color
     */
    private static void dartTheShot(ServerPlayer player, GooThrowPayload cast, PortalColor color) {
        Vec3 palm = ThrowArc.clampToReach(player.getEyePosition(), cast.origin(),
                ThrowArc.HAND_REACH * player.getScale());
        EntityVisuals.sendToWatchers(player, CastDart.dart(palm, cast.targetPoint(), color.shotVisual()));
    }

    /**
     * Runs a player's Portal cast as a shot of one color, the color its
     * portal opens in, held only while the cast runs.
     *
     * @param caster the casting player
     * @param color  the shot's color
     * @param cast   the cast
     */
    public static void casting(UUID caster, PortalColor color, Runnable cast) {
        SHOOTING.put(caster, color);
        try {
            cast.run();
        } finally {
            SHOOTING.remove(caster);
        }
    }

    /**
     * A shot re-aimed at the block face the shooter's look strikes within
     * Portal's range.
     * each-ability-names-its-range-and-the-shared-throw-range-retires
     *
     * @param player the shooting player
     * @param shot   the cast the client aimed
     * @param range  Portal's range in blocks
     * @return the shot at the struck face, empty where the look strikes no block within the range
     */
    private static Optional<GooThrowPayload> alongTheLook(ServerPlayer player, GooThrowPayload shot, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK
                ? Optional.of(shot.struckAt(hit.getBlockPos(), hit.getDirection(), hit.getLocation()))
                : Optional.empty();
    }

    /**
     * The ability a shot names, where it places portals.
     *
     * @param player    the shooting player
     * @param abilityId the shot's ability id string
     * @return Portal, empty for an id naming no ability that places portals
     */
    private static Optional<AbilityDefinition> portalNamed(ServerPlayer player, String abilityId) {
        Identifier id = Identifier.tryParse(abilityId);
        AbilityDefinition ability = id == null ? null : AbilityRegistry.of(player.level()).getAbility(id);
        return Optional.ofNullable(ability).filter(named -> PortalStep.placesPortals(named.behaviors()));
    }

    /**
     * The color of the shot a player is casting now.
     *
     * @param caster the player, or null for a cast with no caster
     * @return the color, empty where no shot of theirs is casting
     */
    public static Optional<PortalColor> colorOf(@Nullable UUID caster) {
        return caster == null ? Optional.empty() : Optional.ofNullable(SHOOTING.get(caster));
    }
}
