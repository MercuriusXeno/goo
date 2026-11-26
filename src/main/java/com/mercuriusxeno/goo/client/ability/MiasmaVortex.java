package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.MiasmaVortexStep;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
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
 * the cone the stream pours through, the glove's from the hand along the
 * player's whole look, redrawn every frame so it turns with the look, spawning nothing.
 * drain-casts-a-filled-tornadic-miasma-vortex-along-the-look
 * decision spray-abilities-render-procedurally-bound-to-their-cone
 */
public final class MiasmaVortex {

    private static final double HALF = 0.5;
    /** Hex's wheel and bright colors, worn while the client holds no definition of the selected goo. */
    private static final MiasmaVortexPass.Colors HEX = new MiasmaVortexPass.Colors(0x5B4BA0, 0x8373C8);
    private static final VortexPresence PRESENCE = new VortexPresence();

    /** The ability the vortex is registered under in {@link ConeVisuals}. */
    static final String DRAIN_ID = "goo:hex_drain";

    /** The cone poured through this frame, the glove's or a nozzle's, taken by the frame's draw. */
    private static @Nullable SprayCone poured;
    /** The cone last poured through, drawn on while its vortex thins away after the pour ends. */
    private static @Nullable SprayCone lastPoured;
    private static MiasmaVortexPass.Colors lastColors = HEX;

    private MiasmaVortex() {
    }

    /**
     * Pours the vortex through a cone this frame, the glove's or a nozzle's.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param cone the cone poured through, in world space
     */
    static void pour(SprayCone cone) {
        poured = cone;
    }

    /**
     * Hands the shared path the vortex while it is poured, and on while it thins
     * away after the pour ends, sorted by the middle of its cone.
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
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        pourTheGlovesVortex(mc, player, partialTick);
        SprayCone cone = poured;
        poured = null;
        if (cone != null) {
            lastPoured = cone;
        }
        VortexPresence.Shown shown = PRESENCE.update(cone != null, player.level().getGameTime() + partialTick);
        SprayCone drawn = lastPoured;
        if (drawn == null || shown.presence() <= 0) {
            return;
        }
        Camera camera = mc.gameRenderer.mainCamera();
        VortexCone vortex = VortexCone.along(drawn.apex().subtract(camera.position()), drawn.axis(), drawn.reach(),
                drawn.shape());
        draws.accept(sortedDrawOf(vortex, lastColors, shown, frame.modelView()));
    }

    /**
     * Pours the glove's vortex while right click holds it: the round cone of its delivery, from the hand along the
     * look this frame.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param mc          the client
     * @param player      the local player
     * @param partialTick the frame's partial tick
     */
    private static void pourTheGlovesVortex(Minecraft mc, LocalPlayer player, float partialTick) {
        ClientAbility held = heldVortex(player);
        if (held == null) {
            return;
        }
        lastColors = colorsOf(GloveAim.selectedGooType(player));
        ConeVisuals.pour(held.id().toString(), SprayCone.ofGlove(held.delivery(), CastOrigin.ofLocal(mc),
                player.getViewVector(partialTick)));
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
                && ability.behaviors().stream().anyMatch(MiasmaVortexStep.class::isInstance);
        return swirls && GloveUseTracker.runsHeld(player) ? ability : null;
    }

    private static MiasmaVortexPass.Colors colorsOf(@Nullable ResourceKey<GooTypeDefinition> gooType) {
        GooTypeDefinition definition = gooType == null ? null : ClientGooTypes.definition(gooType);
        return definition == null ? HEX : new MiasmaVortexPass.Colors(definition.wheel(), definition.bright());
    }
}
