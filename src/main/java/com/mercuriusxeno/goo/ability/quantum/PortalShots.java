package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveButton;
import com.mercuriusxeno.goo.ability.program.PortalStep;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
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
 * gate keeps that aim, which the throw checks for itself. The shot then
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
     * ability that places no portals, or striking nothing along the look,
     * does nothing.
     *
     * @param player the shooting player
     * @param button the button pressed
     * @param shot   the cast the client aimed
     */
    public static void shoot(ServerPlayer player, GloveButton button, GooThrowPayload shot) {
        if (!placesPortals(player, shot.abilityId())) {
            return;
        }
        Optional<GooThrowPayload> aimed = shot.gate().isPresent() ? Optional.of(shot) : alongTheLook(player, shot);
        aimed.ifPresent(cast -> casting(player.getUUID(), PortalColor.pressedBy(button),
                () -> GooThrowHandler.execute(player, cast)));
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
     * the throw range.
     *
     * @param player the shooting player
     * @param shot   the cast the client aimed
     * @return the shot at the struck face, empty where the look strikes no block
     */
    private static Optional<GooThrowPayload> alongTheLook(ServerPlayer player, GooThrowPayload shot) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(GooThrowHandler.MAX_RANGE));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK
                ? Optional.of(shot.struckAt(hit.getBlockPos(), hit.getDirection(), hit.getLocation()))
                : Optional.empty();
    }

    /**
     * Whether an ability id names an ability that places portals.
     *
     * @param player    the shooting player
     * @param abilityId the shot's ability id string
     * @return true for Portal
     */
    private static boolean placesPortals(ServerPlayer player, String abilityId) {
        Identifier id = Identifier.tryParse(abilityId);
        AbilityDefinition ability = id == null ? null : AbilityRegistry.of(player.level()).getAbility(id);
        return ability != null && PortalStep.placesPortals(ability.behaviors());
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
