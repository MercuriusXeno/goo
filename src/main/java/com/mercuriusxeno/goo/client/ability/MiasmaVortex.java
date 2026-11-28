package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.MiasmaVortexStep;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.throwing.LookAim;
import com.mercuriusxeno.goo.throwing.VortexCone;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Drain's cast look: while right click holds a stream naming
 * {@link MiasmaVortexStep}, a filled, turning, rifled vortex of miasma fills
 * the cone the stream strikes, from the lifted blob at what the player's
 * look intersects, redrawn every frame so it turns with the look, spawning nothing.
 * drain-casts-a-filled-tornadic-miasma-vortex-along-the-look
 * ability-goes-where-the-look-intersects
 */
public final class MiasmaVortex {

    private static final double HALF = 0.5;
    /** Hex's wheel and bright colors, worn while the client holds no definition of the selected goo. */
    private static final MiasmaVortexPass.Colors HEX = new MiasmaVortexPass.Colors(0x5B4BA0, 0x8373C8);
    private static final VortexPresence PRESENCE = new VortexPresence();
    /** The ability last held, drawn on while its vortex thins away after the hold ends. */
    private static @Nullable ClientAbility lastHeld;
    private static MiasmaVortexPass.Colors lastColors = HEX;

    private MiasmaVortex() {
    }

    /**
     * Hands the shared path the vortex while it is held, and on while it thins
     * away after the hold ends, sorted by the middle of its cone.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the vortex's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        ClientAbility held = heldVortex(player);
        if (held != null) {
            lastHeld = held;
            lastColors = colorsOf(GloveAim.selectedGooType(player));
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        VortexPresence.Shown shown = PRESENCE.update(held != null, player.level().getGameTime() + partialTick);
        ClientAbility drawn = lastHeld;
        if (drawn == null || shown.presence() <= 0) {
            return;
        }
        Camera camera = mc.gameRenderer.mainCamera();
        // ability-goes-where-the-look-intersects
        VortexCone aimed = VortexCone.aimed(CastOrigin.ofLocal(mc), player.getEyePosition(partialTick),
                player.getViewVector(partialTick), drawn.delivery().range(), drawn.delivery().coneDegrees(),
                LookAim.blocksAndLivingSeenBy(player));
        VortexCone vortex = VortexCone.along(aimed.apex().subtract(camera.position()), aimed.axis(), aimed.length(),
                drawn.delivery().coneDegrees());
        draws.accept(sortedDrawOf(vortex, lastColors, shown, frame.modelView()));
    }

    private static SortedDraw sortedDrawOf(VortexCone vortex, MiasmaVortexPass.Colors colors,
                                           VortexPresence.Shown shown, Matrix4fc modelView) {
        Vec3 middle = vortex.apex().add(vortex.axis().scale(vortex.length() * HALF));
        return new SortedDraw(middle.lengthSqr(), () -> MiasmaVortexPass.draw(vortex, colors, shown, modelView));
    }

    /**
     * The ability the local player's glove holds while right click holds a
     * stream naming the vortex.
     *
     * @param player the local player
     * @return the ability, or null while no such stream is held
     */
    private static @Nullable ClientAbility heldVortex(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        boolean swirls = ability != null
                && MiasmaVortexStep.drawnIn(ability.behaviors());
        return swirls && GloveUseTracker.runsHeld(player) ? ability : null;
    }

    private static MiasmaVortexPass.Colors colorsOf(@Nullable ResourceKey<GooTypeDefinition> gooType) {
        GooTypeDefinition definition = gooType == null ? null : ClientGooTypes.definition(gooType);
        return definition == null ? HEX : new MiasmaVortexPass.Colors(definition.wheel(), definition.bright());
    }
}
