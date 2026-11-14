package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.MiasmaVortexStep;
import com.mercuriusxeno.goo.client.ClientGooTypes;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Drain's cast look: while right click holds a stream naming
 * {@link MiasmaVortexStep}, a filled, turning, rifled vortex of miasma fills
 * the cone the stream strikes, from the glove hand along the player's whole
 * look, redrawn every frame so it turns with the look, spawning nothing.
 * drain-casts-a-filled-tornadic-miasma-vortex-along-the-look
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class MiasmaVortex {

    /** Hex's wheel and bright colors, worn while the client holds no definition of the selected goo. */
    private static final MiasmaVortexPass.Colors HEX = new MiasmaVortexPass.Colors(0x5B4BA0, 0x8373C8);
    private static final VortexPresence PRESENCE = new VortexPresence();
    /** The ability last held, drawn on while its vortex thins away after the hold ends. */
    private static @Nullable ClientAbility lastHeld;
    private static MiasmaVortexPass.Colors lastColors = HEX;

    private MiasmaVortex() {
    }

    /**
     * Draws the vortex after the translucent blocks while it is held, and on
     * while it thins away after the hold ends.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
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
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 hand = CastOrigin.ofLocal(mc).subtract(camera.position());
        VortexCone vortex = VortexCone.along(hand, player.getViewVector(partialTick), drawn.delivery().range(),
                drawn.delivery().coneDegrees());
        MiasmaVortexPass.draw(vortex, lastColors, shown, event.getModelViewMatrix());
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
