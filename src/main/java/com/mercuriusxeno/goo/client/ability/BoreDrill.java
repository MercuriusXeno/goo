package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.BoreDrillShape;
import com.mercuriusxeno.goo.ability.program.BorePoint;
import com.mercuriusxeno.goo.ability.program.BoreStep;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.block.ability.MortarCellBlock;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.item.GooCasters;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Bore's drill: while right click holds a bore stream, a vortex of earth
 * magic extends from Bore's blob, which lifts by the glove and eases forward
 * to hold the drill at its angle off the sight line ({@link BoreBlobPlace}),
 * through its cross section, which floats along the crosshair to the aimed
 * block at the depth the tube of rays sets, to its tip just past it in the
 * block. It is the cone the break reads ({@link BoreDrillShape}), set once
 * in the JSON: as wide at the blob as its width, tapering to a true point,
 * never wider than a block, so where it crosses the crosshair's plane it is
 * the ellipse Bore breaks with, and it follows the cross section deeper as
 * blocks break. The drill and the break each read the aim and the drill's
 * dimensions, so the two are in sync. It is clipped by the blocks it meets,
 * and {@link BoreContactSpray} spits their particles across its cross section. {@link BoreDrillPass} marches it as
 * Drain's vortex is marched: a churning, sparse, patchy swirl of earthy dust
 * in rock's tones, its cone defined by a rifled shell, ragged.
 * decision bore-shows-a-big-drill-from-the-glove
 * decision bore-blob-lifts-by-the-glove-and-the-drill-extends-from-that-hand
 * decision bore-drill-is-two-blocks-held-at-an-angle-off-the-sight-line
 * decision bore-drill-dimensions-drive-the-visuals-and-the-break
 * decision bore-cross-section-is-where-the-angled-drill-meets-the-look-plane
 * decision bore-drill-point-floats-along-the-crosshair-to-the-aimed-block
 * decision bore-breaks-what-is-in-the-vortex
 * decision bore-vortex-with-a-worldspace-shake
 */
public final class BoreDrill {

    /** The volume's section count, unread by the march, which needs none. */
    private static final int RINGS = 1;
    /** How many ticks the vortex takes to pour out to its full length. */
    static final double EMERGE_TICKS = 5;
    /** Ticks the vortex takes to build up to full once the bore starts. */
    static final double FADE_IN_TICKS = 6;
    /** Ticks the vortex takes to disperse once the bore ends. */
    static final double FADE_OUT_TICKS = 10;
    /** Half the vortex's length, where its middle stands for the sort. */
    private static final double HALF = 0.5;

    /** How much of the vortex shows, 0 to 1, and the game time it was last drawn at. */
    private static double presence;
    private static double lastDrawn = Double.NaN;
    /** The ability the drill is registered under in {@link ConeVisuals}. */
    static final String BORE_ID = "goo:rock_bore";

    /** The cone poured through this frame, the glove's or a nozzle's, taken by the frame's draw. */
    private static @Nullable SprayCone poured;
    /** The bore last poured, which the vortex keeps drawing from as it disperses. */
    private static @Nullable PouredBore lastPoured;

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
     * The drill: the cone the break reads, from its mouth at the blob through
     * the cross section's center on the crosshair's plane to its tip, the
     * shape's past length beyond, tapering to a true point at the shape's
     * slope, so its section on the plane is the ellipse Bore breaks with and a
     * drill shortened for a near block keeps that section.
     * decision bore-drill-dimensions-drive-the-visuals-and-the-break
     * decision bore-cross-section-is-where-the-angled-drill-meets-the-look-plane
     * decision bore-blob-lifts-by-the-glove-and-the-drill-extends-from-that-hand
     *
     * @param mouth the blob's place, where the drill starts
     * @param point the cross section's center, {@link #pointOf}'s, in the same space as the mouth
     * @param shape the drill cone, the bore step's
     * @param yaw   the look's yaw in degrees
     * @return the drill, its apex at the mouth
     */
    static ConeSections.Volume volume(Vec3 mouth, Vec3 point, BoreDrillShape shape, float yaw) {
        Vec3 toTip = tipOf(mouth, point, shape).subtract(mouth);
        double length = toTip.length();
        return new ConeSections.Volume(mouth, toTip.normalize(), yaw, 0, length, RINGS,
                distance -> shape.taper() * Math.max(0, length - distance));
    }

    /**
     * The drill drawn from a cone: its round volume, the cross section's center it runs through, and how far its
     * section stretches across and up and down against that round volume.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param volume      the round drill, the cone the break reads
     * @param point       the cross section's center
     * @param acrossScale how many times the round drill's width its section stands across
     * @param upDownScale how many times the round drill's width its section stands up and down
     */
    record Drill(ConeSections.Volume volume, Vec3 point, double acrossScale, double upDownScale) {

        /**
         * Half the drill's width across a distance along it.
         *
         * @param distance blocks from its mouth along it
         * @return the half width in blocks
         */
        double halfWidthAcrossAt(double distance) {
            return volume.radiusAt().applyAsDouble(distance) * acrossScale;
        }

        /**
         * Half the drill's height up and down a distance along it.
         *
         * @param distance blocks from its mouth along it
         * @return the half height in blocks
         */
        double halfHeightAt(double distance) {
            return volume.radiusAt().applyAsDouble(distance) * upDownScale;
        }

        /**
         * The drill part way poured out, its section stretched as the full drill's is.
         *
         * @param share how far it has poured out, 0 to 1
         * @return the drill
         */
        Drill emerged(double share) {
            return new Drill(BoreDrill.emerged(volume, share), point, acrossScale, upDownScale);
        }

        /**
         * The drill laid out about a camera.
         *
         * @param camera the camera's place, in world space
         * @return the drill, camera relative
         */
        Drill about(Vec3 camera) {
            ConeSections.Volume relative = new ConeSections.Volume(volume.apex().subtract(camera), volume.axis(),
                    volume.yaw(), volume.near(), volume.range(), volume.sections(), volume.radiusAt());
            return new Drill(relative, point.subtract(camera), acrossScale, upDownScale);
        }
    }

    /**
     * A bore poured through a cone.
     *
     * @param step       its bore step, whose drill and tag the vortex draws
     * @param cone       the cone it was poured through
     * @param ownDegrees the cone angle of Bore's own delivery, the round cone the poured shape is scaled against
     */
    private record PouredBore(BoreStep step, SprayCone cone, double ownDegrees) {
    }

    /**
     * What a drill aims with besides its cone: the drill's own dimensions, the look's yaw, the glove's hand, and
     * the blocks along the sight.
     *
     * @param shape      the drill cone, the bore step's
     * @param yaw        the look's yaw in degrees
     * @param gloveRight whether the glove is in the right hand
     * @param air        whether a cell holds air
     * @param breakable  whether a cell holds a block the bore breaks
     */
    record DrillAim(BoreDrillShape shape, float yaw, boolean gloveRight, Predicate<BlockPos> air,
                    Predicate<BlockPos> breakable) {
    }

    /**
     * What the blocks along a bore's sight hold, as the drill in force reads them.
     *
     * @param air          whether a cell holds air
     * @param mortarLayers the layers of Mortar a cell holds
     * @param breakable    whether a cell holds a block the bore breaks
     */
    record SightBlocks(Predicate<BlockPos> air, ToIntFunction<BlockPos> mortarLayers, Predicate<BlockPos> breakable) {

        /**
         * The blocks of a level as a bore reads them.
         *
         * @param level the client level
         * @param bore  the bore, whose tag names what it breaks
         * @return the blocks
         */
        static SightBlocks in(Level level, BoreStep bore) {
            return new SightBlocks(airIn(level), cell -> MortarCellBlock.layersAt(level, cell),
                    breakableIn(level, bore));
        }
    }

    /**
     * The drill in force along a sight: the giga drill while the sight
     * crosses Mortar, Bore's own otherwise, as the server's break reads it.
     * decision boring-through-rock-goo-gives-a-giga-drill-that-drains-it
     * decision bore-drill-dimensions-drive-the-visuals-and-the-break
     *
     * @param sight      the sight the bore aims along
     * @param reach      the bore's reach along the sight
     * @param bore       the bore step
     * @param gloveRight whether the glove is in the right hand
     * @param blocks     the blocks along the sight
     * @return the drill in force
     */
    static BoreStep.DrillInForce drillAlong(CameraEye.Sight sight, double reach, BoreStep bore, boolean gloveRight,
                                            SightBlocks blocks) {
        ChannelAim aim = new ChannelAim(sight.eye().add(sight.look().scale(reach)), null, 0);
        return BoreStep.drillInForce(sight.eye(), bore.shape(), bore.giga(), gloveRight, aim, blocks.air(),
                blocks.mortarLayers(), blocks.breakable());
    }

    /**
     * The drill's aim along a sight: the drill in force, its passable cells
     * and the blocks it breaks.
     *
     * @param sight      the sight the bore aims along
     * @param reach      the bore's reach along the sight
     * @param bore       the bore step
     * @param yaw        the look's yaw in degrees
     * @param gloveRight whether the glove is in the right hand
     * @param blocks     the blocks along the sight
     * @return the aim
     */
    static DrillAim aimAlong(CameraEye.Sight sight, double reach, BoreStep bore, float yaw, boolean gloveRight,
                             SightBlocks blocks) {
        BoreStep.DrillInForce drill = drillAlong(sight, reach, bore, gloveRight, blocks);
        return new DrillAim(drill.shape(), yaw, gloveRight, drill.passable(), blocks.breakable());
    }

    /**
     * The drill drawn from the cone it is handed: from the cone's apex, its cross section out along the sight to
     * the cone's reach, and its section scaled across and up and down by the cone's shape against the round cone
     * of Bore's own degrees, so Bore's own cone draws the drill the break reads.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param cone       the cone the drill is poured through
     * @param ownDegrees the cone angle of Bore's own delivery
     * @param sight      the sight the bore aims along
     * @param aim        the drill's dimensions, the yaw, the hand and the blocks
     * @return the drill
     */
    static Drill drillThrough(SprayCone cone, double ownDegrees, CameraEye.Sight sight, DrillAim aim) {
        Vec3 point = pointOf(sight, cone.reach(), aim.shape(), aim.gloveRight(), aim.air(), aim.breakable());
        double ownTan = Math.tan(Math.toRadians(ownDegrees * HALF));
        return new Drill(volume(cone.apex(), point, aim.shape(), aim.yaw()), point,
                Math.tan(Math.toRadians(cone.shape().sideDegrees() * HALF)) / ownTan,
                Math.tan(Math.toRadians(cone.shape().liftDegrees() * HALF)) / ownTan);
    }

    /**
     * Where the drill's tip stands: the shape's past length beyond the cross
     * section's center, on the line from the blob through it, inside the block.
     * decision bore-cross-section-is-where-the-angled-drill-meets-the-look-plane
     *
     * @param mouth the blob's place
     * @param point the cross section's center
     * @param shape the drill cone
     * @return the tip
     */
    static Vec3 tipOf(Vec3 mouth, Vec3 point, BoreDrillShape shape) {
        return point.add(point.subtract(mouth).normalize().scale(shape.pastLength()));
    }

    /**
     * Where the drill's cross section stands: on the sight line at the depth
     * the tube of rays sets, where Bore breaks; at a solid block Bore cannot
     * break, at its face, so the drill never runs through it; or the end of
     * reach where no ray meets a block.
     * decision bore-drill-point-floats-along-the-crosshair-to-the-aimed-block
     * decision bore-tube-rays-set-the-depth-of-the-cross-section
     *
     * @param sight      the sight the bore aims along, {@link CameraEye#of}'s
     * @param reach      the bore's reach along the sight
     * @param shape      the drill cone whose cross section sizes the tube
     * @param gloveRight whether the glove is in the right hand
     * @param air        whether a cell holds air
     * @param breakable  whether a cell holds a block the bore breaks
     * @return the point, in world space
     */
    static Vec3 pointOf(CameraEye.Sight sight, double reach, BoreDrillShape shape, boolean gloveRight,
                        Predicate<BlockPos> air, Predicate<BlockPos> breakable) {
        return BoreStep.stopOf(sight.eye(), sight.eye().add(sight.look().scale(reach)), shape, gloveRight, air,
                breakable);
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
     * Hands the shared path the vortex while a bore runs or while it
     * disperses, sorted by the middle of its cone. The ground shake draws its
     * blocks with the opaque blocks, before every far-to-near visual, so the
     * blocks the drill is breaking never paint over it; and the path draws it
     * after the clouds and weather, all farther than the vortex reaches, so
     * they sort behind its translucent dust, which writes no depth they could
     * test against.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision diagnose-and-correct-bore-behaving-weirdly-close-to-things
     *
     * @param frame the frame being drawn
     * @param draws where the vortex's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        // It drew only at AfterLevel, which a portal view's pass never posts; the path runs AfterLevel routes there.
        if (PortalView.drawing()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        pourTheGlovesDrill(mc, player, partialTick);
        boolean held = takePoured();
        updatePresence(held, mc.level.getGameTime() + partialTick);
        PouredBore drawn = lastPoured;
        if (presence <= 0 || drawn == null) {
            return;
        }
        draws.accept(sortedDrawOf(drillOf(mc, player, drawn, partialTick, held), presence, frame.modelView()));
    }

    /**
     * Pours the drill through a cone this frame, the glove's or a nozzle's.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param cone the cone poured through, in world space
     */
    static void pour(SprayCone cone) {
        poured = cone;
    }

    /**
     * Pours the glove's drill while right click holds a bore: the round cone of its delivery, from the blob along
     * the look this frame, out to Bore's own range whatever the caster's block reach.
     * operator ruling 2026-11-14: Bore drills only up to its own range, whatever the caster's block reach
     *
     * @param mc          the client
     * @param player      the local player
     * @param partialTick the frame's partial tick
     */
    private static void pourTheGlovesDrill(Minecraft mc, LocalPlayer player, float partialTick) {
        ClientAbility bore = runningBore(player);
        if (bore != null) {
            Delivery reaching = bore.delivery().reachingTo(player.blockInteractionRange());
            ConeVisuals.pour(bore.id().toString(), SprayCone.ofGlove(reaching, CastOrigin.ofLocal(mc),
                    player.getViewVector(partialTick)));
        }
    }

    /**
     * Takes the cone poured through this frame, if any.
     *
     * @return true where a cone was poured and the client holds Bore's synced copy
     */
    private static boolean takePoured() {
        SprayCone cone = poured;
        poured = null;
        return cone != null && takeBore(cone);
    }

    /**
     * Takes the cone the drill is poured through, remembering Bore's step and own cone angle with it so the
     * vortex can keep drawing from them while it disperses.
     *
     * @param cone the cone poured through
     * @return true where the client holds Bore's synced copy
     */
    private static boolean takeBore(SprayCone cone) {
        ClientAbility bore = AbilitySyncHandler.findAbility(BORE_ID);
        BoreStep step = bore == null ? null : stepOf(bore).orElse(null);
        if (step == null) {
            return false;
        }
        lastPoured = new PouredBore(step, cone, bore.delivery().coneDegrees());
        return true;
    }

    private static Drill drillOf(Minecraft mc, LocalPlayer player, PouredBore bore, float partialTick, boolean held) {
        BoreStep step = bore.step();
        CameraEye.Sight sight = CameraEye.of(mc, player, partialTick);
        Drill full = drillThrough(bore.cone(), bore.ownDegrees(), sight, aimAlong(sight, bore.cone().reach(), step,
                player.getViewYRot(partialTick), GooCasters.casterOnTheRight(player), SightBlocks.in(mc.level, step)));
        double share = held ? emergedShare(GloveUseTracker.channelTicks() + partialTick) : 1;
        return full.emerged(share).about(mc.gameRenderer.mainCamera().position());
    }

    // The vortex is laid out about the camera, so its middle's length is its distance from the camera.
    private static SortedDraw sortedDrawOf(Drill drill, double shown, Matrix4fc modelView) {
        ConeSections.Volume vortex = drill.volume();
        Vec3 middle = vortex.apex().add(vortex.axis().scale(vortex.range() * HALF));
        return new SortedDraw(middle.lengthSqr(), () -> BoreDrillPass.draw(drill, shown, modelView));
    }

    private static void updatePresence(boolean held, double now) {
        double elapsed = Double.isNaN(lastDrawn) ? 0 : Math.max(0, now - lastDrawn);
        lastDrawn = now;
        presence = presenceAfter(presence, held, elapsed);
    }

    /**
     * The breakable block a bore's cross section lands on in a level along a sight.
     *
     * @param level      the client level
     * @param sight      the sight the bore aims along
     * @param bore       the bore, whose tag names what it breaks and whose drill sizes the tube
     * @param gloveRight whether the glove is in the right hand
     * @param reach      the bore's reach
     * @return the cross section's center and a block it picks, empty where it picks none
     */
    static Optional<BorePoint> landingAhead(Level level, CameraEye.Sight sight, BoreStep bore, boolean gloveRight,
                                            double reach) {
        SightBlocks blocks = SightBlocks.in(level, bore);
        BoreStep.DrillInForce drill = drillAlong(sight, reach, bore, gloveRight, blocks);
        return BoreStep.pointOf(sight.eye(), sight.eye().add(sight.look().scale(reach)), drill.shape(), gloveRight,
                drill.passable(), blocks.breakable());
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
