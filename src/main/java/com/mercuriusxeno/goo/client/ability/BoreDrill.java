package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.BorePoint;
import com.mercuriusxeno.goo.ability.program.BoreStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Bore's drill: while right click holds a bore stream, a vortex of earth
 * magic extends from Bore's blob, which lifts by the glove and eases forward
 * to hold the drill 2 blocks long at its angle off the sight line
 * ({@link BoreBlobPlace}), to its point, which floats along the crosshair to
 * the aimed block: where the sight meets the first breakable block within
 * reach, the face of a solid block outside the tag, or the end of reach. It
 * starts at the blob with a wide mouth and narrows to the point, never wider
 * than a block, as wide at its point as what Bore breaks, and follows the
 * point deeper as blocks break. The drill and the break each read the aim,
 * so where Bore breaks matches where the drill draws because the two are in
 * sync. It is clipped by the blocks it meets, and {@link BoreContactSpray}
 * spits their particles at its point. {@link BoreDrillPass} marches it as
 * Drain's vortex is marched: a churning, sparse, patchy swirl of earthy dust
 * in rock's tones, its cone defined by a rifled shell, ragged.
 * decision bore-shows-a-big-drill-from-the-glove
 * decision bore-blob-lifts-by-the-glove-and-the-drill-extends-from-that-hand
 * decision bore-drill-is-two-blocks-held-at-an-angle-off-the-sight-line
 * decision bore-drill-point-floats-along-the-crosshair-to-the-aimed-block
 * decision bore-breaks-what-is-in-the-vortex
 * decision bore-vortex-with-a-worldspace-shake
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BoreDrill {

    /** The volume's section count, unread by the march, which needs none. */
    private static final int RINGS = 1;
    /** How many ticks the vortex takes to pour out to its full length. */
    static final double EMERGE_TICKS = 5;
    /** The drill's radius at its wide mouth, in blocks, under the half block that keeps it narrower than a block. */
    static final double MOUTH_RADIUS = 0.35;
    /** Ticks the vortex takes to build up to full once the bore starts. */
    static final double FADE_IN_TICKS = 6;
    /** Ticks the vortex takes to disperse once the bore ends. */
    static final double FADE_OUT_TICKS = 10;

    /** How much of the vortex shows, 0 to 1, and the game time it was last drawn at. */
    private static double presence;
    private static double lastDrawn = Double.NaN;
    /** The bore last held, which the vortex keeps drawing from while it disperses. */
    private static @Nullable BoreStep lastStep;
    private static double lastReach;

    private BoreDrill() {
    }

    /**
     * How much of the vortex shows after some ticks: building up over the
     * fade in while the bore runs, dispersing over the fade out once it ends,
     * so it never switches on or off at once.
     *
     * @param presence how much showed before, 0 to 1
     * @param held     whether the bore runs
     * @param ticks    the ticks since, with the partial tick
     * @return how much shows now, 0 to 1
     */
    static double presenceAfter(double presence, boolean held, double ticks) {
        double moved = held ? presence + ticks / FADE_IN_TICKS : presence - ticks / FADE_OUT_TICKS;
        return Math.clamp(moved, 0, 1);
    }

    /**
     * The drill at its full length: from its mouth at the lifted blob to its
     * point, narrowing from its wide mouth to the point radius the bore
     * breaks with, so its point is as wide as what Bore breaks.
     * decision bore-blob-lifts-by-the-glove-and-the-drill-extends-from-that-hand
     * decision bore-breaks-what-is-in-the-vortex
     *
     * @param mouth       the lifted blob's place, where the drill starts
     * @param point       the drill's point, {@link #pointOf}'s, in the same space as the mouth
     * @param yaw         the look's yaw in degrees
     * @param pointRadius the radius of the point the bore breaks at, the bore step's JSON number
     * @return the drill, its apex at the mouth
     */
    static ConeSections.Volume volume(Vec3 mouth, Vec3 point, float yaw, double pointRadius) {
        Vec3 toPoint = point.subtract(mouth);
        double length = toPoint.length();
        return new ConeSections.Volume(mouth, toPoint.normalize(), yaw, 0, length, RINGS,
                distance -> MOUTH_RADIUS + (pointRadius - MOUTH_RADIUS) * Math.clamp(distance / length, 0, 1));
    }

    /**
     * Where the drill's point stands: where the sight meets the first
     * breakable block within reach, the point Bore breaks at; where the sight
     * first meets a solid block Bore cannot break, at that block's face, so
     * the drill never runs through it; or the end of reach where it meets
     * nothing.
     * decision bore-drill-point-floats-along-the-crosshair-to-the-aimed-block
     *
     * @param sight     the sight the bore aims along, {@link CameraEye#of}'s
     * @param reach     the bore's reach along the sight
     * @param air       whether a cell holds air
     * @param breakable whether a cell holds a block the bore breaks
     * @return the point, in world space
     */
    static Vec3 pointOf(CameraEye.Sight sight, double reach, Predicate<BlockPos> air,
                        Predicate<BlockPos> breakable) {
        return BoreStep.stopOf(sight.eye(), sight.eye().add(sight.look().scale(reach)), air, breakable);
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
        if (player == null || mc.level == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        boolean held = holdsBore(player);
        updatePresence(held, mc.level.getGameTime() + partialTick);
        if (presence <= 0 || lastStep == null) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        ConeSections.Volume full = volume(CastOrigin.ofLocal(mc).subtract(camera),
                pointAhead(mc.level, CameraEye.of(mc, player, partialTick), lastStep, lastReach).subtract(camera),
                player.getViewYRot(partialTick), lastStep.pointRadius());
        double poured = held ? emergedShare(GloveUseTracker.channelTicks() + partialTick) : 1;
        BoreDrillPass.draw(emerged(full, poured), presence, event.getModelViewMatrix());
    }

    /**
     * Whether the local player runs a bore now, remembering its step and
     * reach so the vortex can keep drawing from them while it disperses.
     *
     * @param player the local player
     * @return true while a bore runs
     */
    private static boolean holdsBore(LocalPlayer player) {
        ClientAbility bore = runningBore(player);
        BoreStep step = bore == null ? null : stepOf(bore).orElse(null);
        if (step == null) {
            return false;
        }
        lastStep = step;
        // bore-reaches-as-far-as-your-block-reach
        lastReach = bore.delivery().reachingTo(player.blockInteractionRange()).range();
        return true;
    }

    private static void updatePresence(boolean held, double now) {
        double elapsed = Double.isNaN(lastDrawn) ? 0 : Math.max(0, now - lastDrawn);
        lastDrawn = now;
        presence = presenceAfter(presence, held, elapsed);
    }

    /**
     * Where the drill's point stands in a level along a sight: the sight of
     * {@link CameraEye#of}, the camera itself in first person, which eases
     * down as the player sneaks, so the point eases with the view rather than
     * snapping to the pose. The ground shake runs Bore's zone from the same
     * sight, so it shakes the blocks at the point.
     * decision diagnose-and-correct-eye-aimed-visuals-jumping-on-sneak
     *
     * @param level the client level
     * @param sight the sight the bore aims along
     * @param bore  the bore, whose tag names what it breaks
     * @param reach the bore's reach
     * @return the point, in world space
     */
    static Vec3 pointAhead(Level level, CameraEye.Sight sight, BoreStep bore, double reach) {
        return pointOf(sight, reach, airIn(level), breakableIn(level, bore));
    }

    /**
     * The breakable block a bore's point lands on in a level along a sight.
     *
     * @param level the client level
     * @param sight the sight the bore aims along
     * @param bore  the bore, whose tag names what it breaks
     * @param reach the bore's reach
     * @return the point and its block, empty where the sight meets no breakable block within reach
     */
    static Optional<BorePoint> landingAhead(Level level, CameraEye.Sight sight, BoreStep bore, double reach) {
        return BoreStep.pointOf(sight.eye(), sight.eye().add(sight.look().scale(reach)), airIn(level),
                breakableIn(level, bore));
    }

    private static Predicate<BlockPos> airIn(Level level) {
        return cell -> level.getBlockState(cell).isAir();
    }

    private static Predicate<BlockPos> breakableIn(Level level, BoreStep bore) {
        return cell -> level.getBlockState(cell).is(bore.breaks());
    }

    static Optional<BoreStep> stepOf(ClientAbility bore) {
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
