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
 * magic is held out in front of the player, starting a little below their
 * line of sight with a wide mouth so they look down along it and see its
 * shape, and meeting their line of sight at the bore's reach, narrowing
 * toward a point, never wider than a block. At each depth it is as
 * wide as the vortex the bore breaks there, hidden by what stands in front
 * of it and never by the blocks it breaks. {@link BoreDrillPass} marches it
 * as Drain's vortex is marched: a churning, sparse, patchy swirl of earthy
 * dust in rock's tones, its cone defined by a rifled shell, ragged and
 * blocky.
 * decision bore-shows-a-big-drill-from-the-glove
 * decision bore-breaks-what-is-in-the-vortex
 * decision bore-vortex-with-a-worldspace-shake
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BoreDrill {

    /** The volume's section count, unread by the march, which needs none. */
    private static final int RINGS = 1;
    /** How many ticks the vortex takes to pour out to its full length. */
    static final double EMERGE_TICKS = 5;
    /** How far ahead of the eye, in blocks along the look, the held vortex starts. */
    static final double HELD_AHEAD = 1.0;
    /** How far below the line of sight, in blocks, the held vortex starts. */
    static final double HELD_BELOW = 0.35;
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
    private static final Vec3 WORLD_SOUTH = new Vec3(0, 0, 1);
    private static final double NEARLY_UPRIGHT = 0.999;

    private BoreDrill() {
    }

    /**
     * The vortex at its full length, held out in front: from a point ahead
     * of the eye and a little below the line of sight to the bore's reach on
     * the line of sight, as wide at each end as the vortex the bore breaks
     * is at that depth, between the bore step's near and far radii.
     * decision bore-shows-a-big-drill-from-the-glove
     * decision bore-breaks-what-is-in-the-vortex
     *
     * @param eye        the eye, camera relative
     * @param look       the look's unit vector
     * @param yaw        the look's yaw in degrees
     * @param reach      the bore's reach from the eye
     * @param nearRadius the broken vortex's radius at the eye, the bore step's JSON number
     * @param farRadius  the broken vortex's radius at the reach, the bore step's JSON number
     * @return the vortex, its apex where it is held
     */
    static ConeSections.Volume volume(Vec3 eye, Vec3 look, float yaw, double reach, double nearRadius,
                                      double farRadius) {
        Vec3 held = eye.add(look.scale(HELD_AHEAD)).add(below(look).scale(HELD_BELOW));
        Vec3 toFar = eye.add(look.scale(reach)).subtract(held);
        double length = toFar.length();
        double heldRadius = nearRadius + (farRadius - nearRadius) * Math.clamp(HELD_AHEAD / reach, 0, 1);
        return new ConeSections.Volume(held, toFar.normalize(), yaw, 0, length, RINGS,
                distance -> heldRadius + (farRadius - heldRadius) * Math.clamp(distance / length, 0, 1));
    }

    /**
     * The unit direction below the line of sight: square to the look, in
     * its upright plane, toward the ground.
     *
     * @param look the look's unit vector
     * @return the direction
     */
    static Vec3 below(Vec3 look) {
        Vec3 up = Math.abs(look.dot(WORLD_UP)) > NEARLY_UPRIGHT ? WORLD_SOUTH : WORLD_UP;
        return look.scale(look.dot(up)).subtract(up).normalize();
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
        ConeSections.Volume full = volume(eyeSeenFrom(mc, player, partialTick, camera),
                player.getViewVector(partialTick), player.getViewYRot(partialTick), bore.delivery().range(),
                step.vortexNearRadius(), step.vortexFarRadius());
        BoreDrillPass.draw(emerged(full, emergedShare(GloveUseTracker.channelTicks() + partialTick)), camera,
                event.getModelViewMatrix());
    }

    /**
     * Where the vortex's eye stands, camera relative: the camera itself in
     * first person, which eases down as the player sneaks, so the vortex
     * eases with the view rather than snapping to the pose; the player's eye
     * in any other view.
     *
     * @param mc          the client
     * @param player      the local player
     * @param partialTick the frame's partial tick
     * @param camera      the camera's world position
     * @return the eye, camera relative
     */
    private static Vec3 eyeSeenFrom(Minecraft mc, LocalPlayer player, float partialTick, Vec3 camera) {
        return mc.options.getCameraType().isFirstPerson() ? Vec3.ZERO
                : player.getEyePosition(partialTick).subtract(camera);
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
