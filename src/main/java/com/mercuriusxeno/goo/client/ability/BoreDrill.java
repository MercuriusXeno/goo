package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.BoreStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * Bore's vortex: while right click holds a bore stream, a vortex of earth
 * magic runs along the player's line of sight from the eye out to the
 * bore's reach, widening to an open far end never wider than a block, the
 * very vortex the bore breaks, hidden by what stands in front of it and
 * never by the blocks it breaks. {@link BoreDrillPass} marches it as Drain's
 * vortex is marched: a churning swirl of earthy dust in rock's tones, fine
 * streaks wound through it, ragged and blocky.
 * decision bore-shows-a-big-drill-from-the-glove
 * decision bore-breaks-what-is-in-the-vortex
 * decision bore-vortex-with-a-worldspace-shake
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BoreDrill {

    /** The volume's section count, unread by the march, which needs none. */
    private static final int RINGS = 1;
    /** How many ticks the vortex takes to pour out from the eye to its full length. */
    static final double EMERGE_TICKS = 5;

    private BoreDrill() {
    }

    /**
     * The vortex at its full length: a cone along the eye line from the eye
     * to the bore's reach, widening from the bore step's near radius to its
     * far radius, the vortex the bore breaks.
     * decision bore-breaks-what-is-in-the-vortex
     *
     * @param eye        the eye, camera relative
     * @param look       the look's unit vector
     * @param yaw        the look's yaw in degrees
     * @param reach      the bore's reach from the eye
     * @param nearRadius the vortex's radius at the eye, the bore step's JSON number
     * @param farRadius  the vortex's radius at the reach, the bore step's JSON number
     * @return the vortex, its apex at the eye
     */
    static ConeSections.Volume volume(Vec3 eye, Vec3 look, float yaw, double reach, double nearRadius,
                                      double farRadius) {
        return new ConeSections.Volume(eye, look, yaw, 0, reach, RINGS,
                distance -> nearRadius + (farRadius - nearRadius) * Math.clamp(distance / reach, 0, 1));
    }

    /**
     * The vortex part way poured out: as long as its share of its full
     * length, as wide at each distance as the full vortex is there.
     *
     * @param full  the vortex at its full length
     * @param share how far it has poured out, 0 to 1
     * @return the vortex
     */
    static ConeSections.Volume emerged(ConeSections.Volume full, double share) {
        return new ConeSections.Volume(full.apex(), full.axis(), full.yaw(), full.near(),
                full.range() * Math.clamp(share, 0, 1), full.sections(), full.radiusAt());
    }

    /**
     * How far the vortex has poured out, by how long the bore has run.
     *
     * @param ticks the ticks the bore has run, with the partial tick
     * @return the share of its full length, 0 to 1
     */
    static double emergedShare(double ticks) {
        return Math.clamp(ticks / EMERGE_TICKS, 0, 1);
    }

    /**
     * Draws the vortex after the translucent blocks while a bore runs, after
     * the ground shake has drawn its blocks, so the blocks it is breaking
     * never paint over it.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientAbility bore = player == null ? null : runningBore(player);
        BoreStep step = bore == null ? null : stepOf(bore).orElse(null);
        if (step == null || mc.level == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        ConeSections.Volume full = volume(player.getEyePosition(partialTick).subtract(camera),
                player.getViewVector(partialTick), player.getViewYRot(partialTick), bore.delivery().range(),
                step.vortexNearRadius(), step.vortexFarRadius());
        BoreDrillPass.draw(emerged(full, emergedShare(GloveUseTracker.channelTicks() + partialTick)), camera,
                event.getModelViewMatrix());
    }

    private static Optional<BoreStep> stepOf(ClientAbility bore) {
        return bore.behaviors().stream().filter(BoreStep.class::isInstance).map(BoreStep.class::cast).findFirst();
    }

    /**
     * The bore the local player's glove runs now.
     *
     * @param player the local player
     * @return the selected ability while right click holds it and it bores, otherwise null
     */
    static @Nullable ClientAbility runningBore(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        return bores(ability, GloveUseTracker.runsHeld(player)) ? ability : null;
    }

    /**
     * Whether a bore runs: the selected ability bores and right click holds it.
     *
     * @param ability the selected ability's synced copy, or null when none
     * @param useHeld whether right click holds a live press
     * @return true while the bore runs
     */
    static boolean bores(@Nullable ClientAbility ability, boolean useHeld) {
        return useHeld && ability != null && ability.behaviors().stream().anyMatch(BoreStep.class::isInstance);
    }
}
