package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.FlamesStep;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.ability.FireField.Jet;
import com.mercuriusxeno.goo.client.ability.FireField.Wall;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.throwing.ConeShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Flames' fire stream: while right click holds Flames, licks of flame and
 * embers pour from the glove down its cone, drawn by {@link FirePass} on the
 * world's 16-texel grid, and a block in the look cuts the fire off at its
 * face. flames-draws-as-particle-style-fire
 * <p>
 * The operator's ruling on the fire's life: the fire front travels out from
 * the glove over a quarter second, and on release the fire burns away from
 * the glove outward over the same quarter second.
 * <p>
 * A jet pinned by {@code /goodebug flames} burns in place, held and released
 * on a loop, so the operator can walk around it and watch it from any side.
 */
public final class FireStream {

    /** Ticks the fire front takes to travel the stream's whole range, and the burn-away takes on release. */
    static final double TRAVEL_TICKS = 5;
    /** A pinned jet's loop: held this many ticks, then released until the loop's end. */
    static final double PINNED_HELD_TICKS = 40;
    static final double PINNED_LOOP_TICKS = 60;
    /** blaze_flames.json's reach and cone, for a pinned jet when no Flames is synced, and for a heater prism. */
    static final double FLAMES_RANGE = 6;
    static final double FLAMES_CONE_DEGREES = 20;
    /** The ability the fire is registered under in {@link ConeVisuals}. */
    static final String FLAMES_ID = "goo:blaze_flames";
    private static final double HALF = 0.5;

    /** The game time the current hold began, or NaN while no hold runs. */
    private static double heldSince = Double.NaN;
    /** The game time the last hold ended, or NaN while a hold runs or nothing burns. */
    private static double releasedAt = Double.NaN;
    /** The cone Flames poured through this frame, taken by the frame's draw, or null once the pour stops. */
    private static @Nullable SprayCone poured;
    /** The cone the held fire last poured through, which it burns away down after the pour stops. */
    private static @Nullable SprayCone burning;
    /** The jet {@code /goodebug flames} pinned, or null. */
    private static @Nullable PinnedJet pinned;

    private FireStream() {
    }

    /**
     * A jet pinned in the world and the game time its loop began.
     *
     * @param cone     the cone it pours through, in world coordinates
     * @param wall     where the jet meets a block, the hit point in world coordinates, or null
     * @param pinnedAt the game time the loop began
     */
    private record PinnedJet(SprayCone cone, @Nullable Wall wall, double pinnedAt) {
    }

    /**
     * Hands the held fire the cone Flames pours through this frame, the glove's or a nozzle's.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param cone the cone poured through
     */
    static void pour(SprayCone cone) {
        poured = cone;
    }

    /**
     * Pins a looping jet at the glove, down the look, as Flames would pour it now.
     *
     * @return whether a jet was pinned: false with no player in a level
     */
    public static boolean pinAtGlove() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return false;
        }
        ClientAbility flames = AbilitySyncHandler.findAbility(FLAMES_ID);
        double range = flames == null ? FLAMES_RANGE : flames.delivery().range();
        double cone = flames == null ? FLAMES_CONE_DEGREES : flames.delivery().coneDegrees();
        Vec3 hand = CastOrigin.ofLocal(mc);
        Aim aim = aimAtCrosshair(player, hand, 1f, range);
        pinned = new PinnedJet(new SprayCone(hand, aim.axis(), ConeShape.round(cone), range), aim.wall(),
                player.level().getGameTime());
        return true;
    }

    /** Takes the pinned jet away. */
    public static void unpin() {
        pinned = null;
    }

    /**
     * Hands the shared path the held fire, the pinned one and every
     * heater's, sorted by the local player, whose hand the held fire leaves.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the fire's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = player.level().getGameTime() + partialTick;
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        pourTheGlovesFlames(player, partialTick);
        List<FireField> fields = new ArrayList<>();
        addBurning(fields, heldField(player, now, partialTick, camera));
        addBurning(fields, pinnedField(now, camera));
        for (HeaterFlames.Heater heater : HeaterFlames.burning()) {
            JetSpan span = HeaterFlames.spanOf(heater, now, FLAMES_RANGE);
            if (span != null) {
                Wall wall = wallAlong(player.level(), player, heater.tip(), heater.axis(), FLAMES_RANGE);
                addBurning(fields, HeaterFlames.fieldOf(heater, span, wall, camera));
            }
        }
        if (!fields.isEmpty()) {
            draws.accept(new SortedDraw(player.getEyePosition(partialTick).distanceToSqr(camera),
                    () -> FirePass.draw(fields, camera, frame.modelView())));
        }
    }

    private static void addBurning(List<FireField> fields, @Nullable FireField field) {
        if (field != null && field.burns()) {
            fields.add(field);
        }
    }

    /**
     * The glove's held Flames: while right click holds it, the glove pours the round cone of its delivery from the
     * cast origin toward what the crosshair points at, through Flames' visual in {@link ConeVisuals}.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param player      the local player
     * @param partialTick the frame's partial tick
     */
    private static void pourTheGlovesFlames(LocalPlayer player, float partialTick) {
        ClientAbility flames = selectedFlames(player);
        if (flames == null || !GloveUseTracker.runsHeld(player)) {
            return;
        }
        Vec3 hand = CastOrigin.ofLocal(Minecraft.getInstance());
        Aim aim = aimAtCrosshair(player, hand, partialTick, flames.delivery().range());
        ConeVisuals.pour(flames.id().toString(), SprayCone.ofGlove(flames.delivery(), hand, aim.axis()));
    }

    /**
     * The field a fire burns through a cone: from its apex, down its axis, its licks spread by the cone's two
     * widths out to its reach, cut off at the face the look meets.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param cone   the cone the fire pours through, in world coordinates
     * @param span   the burning stretch
     * @param wall   where the look meets a block, in world coordinates, or null
     * @param camera the camera's world position
     * @return the field, camera relative
     */
    static FireField fieldOf(SprayCone cone, JetSpan span, @Nullable Wall wall, Vec3 camera) {
        Jet jet = new Jet(cone.apex().subtract(camera), cone.axis(), FireField.sideOf(cone.axis()),
                halfTan(cone.shape().sideDegrees()), halfTan(cone.shape().liftDegrees()));
        Wall relative = wall == null ? null : new Wall(wall.distance(), wall.at().subtract(camera), wall.face());
        return new FireField(jet, span, cone.reach(), relative);
    }

    private static double halfTan(double degrees) {
        return Math.tan(Math.toRadians(degrees * HALF));
    }

    private static @Nullable FireField heldField(LocalPlayer player, double now, float partialTick, Vec3 camera) {
        SprayCone cone = poured;
        poured = null;
        if (cone != null) {
            burning = cone;
        }
        SprayCone fire = burning;
        JetSpan span = fire == null ? null : trackHold(cone != null, now, fire.reach());
        if (fire == null || span == null) {
            burning = null;
            return null;
        }
        Wall wall = aimAlong(CameraEye.of(Minecraft.getInstance(), player, partialTick), fire.apex(), fire.reach(),
                CameraEye.blocksSeenBy(player)).wall();
        return fieldOf(fire, span, wall, camera);
    }

    private static @Nullable FireField pinnedField(double now, Vec3 camera) {
        PinnedJet jet = pinned;
        if (jet == null) {
            return null;
        }
        double intoLoop = (now - jet.pinnedAt()) % PINNED_LOOP_TICKS;
        double released = intoLoop < PINNED_HELD_TICKS ? Double.NaN : intoLoop - PINNED_HELD_TICKS;
        return fieldOf(jet.cone(), jetSpan(intoLoop, released, jet.cone().reach()), jet.wall(), camera);
    }

    /**
     * The stretch of the jet that burns: from the tail, which burns away from
     * the glove after release, to the front, which travels out from the glove
     * after the press.
     *
     * @param tail  blocks from the glove the burning starts
     * @param front blocks from the glove the burning reaches
     */
    record JetSpan(double tail, double front) {
    }

    /**
     * Tracks the hold and answers the stretch of the jet that burns now.
     *
     * @param held  whether right click holds Flames this frame
     * @param now   the game time with the partial tick
     * @param range the stream's reach
     * @return the burning stretch, or null once nothing burns
     */
    private static @Nullable JetSpan trackHold(boolean held, double now, double range) {
        if (held) {
            if (Double.isNaN(heldSince) || !Double.isNaN(releasedAt)) {
                heldSince = now;
                releasedAt = Double.NaN;
            }
            return jetSpan(now - heldSince, Double.NaN, range);
        }
        return Double.isNaN(heldSince) ? null : trackRelease(now, range);
    }

    private static @Nullable JetSpan trackRelease(double now, double range) {
        if (Double.isNaN(releasedAt)) {
            releasedAt = now;
        }
        JetSpan span = jetSpan(now - heldSince, now - releasedAt, range);
        if (span.tail() >= span.front()) {
            heldSince = Double.NaN;
            releasedAt = Double.NaN;
            return null;
        }
        return span;
    }

    /**
     * The stretch of the jet that burns, a given time into the hold and after
     * the release: the front and the tail each travel the whole range in
     * {@link #TRAVEL_TICKS}.
     *
     * @param ticksHeld     ticks since the hold began
     * @param ticksReleased ticks since the release, or NaN while the hold runs
     * @param range         the stream's reach
     * @return the burning stretch
     */
    static JetSpan jetSpan(double ticksHeld, double ticksReleased, double range) {
        double front = Math.min(range, range * ticksHeld / TRAVEL_TICKS);
        double tail = Double.isNaN(ticksReleased) ? 0 : Math.min(range, range * ticksReleased / TRAVEL_TICKS);
        return new JetSpan(tail, front);
    }

    /**
     * The jet's aim: the operator's ruling, the fire aims at what the
     * crosshair points at. The look from the eye finds the block and face,
     * and the jet pours from the glove toward that point.
     *
     * @param axis the jet's unit vector, from the glove
     * @param wall where the crosshair meets a block within the reach, in world coordinates, or null
     */
    record Aim(Vec3 axis, @Nullable Wall wall) {
    }

    private static Aim aimAtCrosshair(LocalPlayer player, Vec3 hand, float partialTick, double range) {
        return aimAlong(CameraEye.of(Minecraft.getInstance(), player, partialTick), hand, range,
                CameraEye.blocksSeenBy(player));
    }

    /**
     * The jet's aim along a sight: the block and face the sight line meets
     * within the reach, the jet pouring from the glove toward that point.
     * decision diagnose-and-correct-eye-aimed-visuals-jumping-on-sneak
     *
     * @param sight  the eye and look the crosshair's line leaves from, {@link CameraEye#of}'s
     * @param hand   the glove's world position
     * @param range  the jet's reach
     * @param blocks the clip the line is traced against
     * @return the aim
     */
    static Aim aimAlong(CameraEye.Sight sight, Vec3 hand, double range, CameraEye.ViewClip blocks) {
        Vec3 look = sight.look();
        BlockHitResult hit = blocks.clip(sight.eye(), sight.eye().add(look.scale(range)));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return new Aim(look, null);
        }
        Vec3 toHit = hit.getLocation().subtract(hand);
        return new Aim(toHit.normalize(), new Wall(toHit.length(), hit.getLocation(), hit.getDirection()));
    }

    /**
     * Where a line from a spout meets a block within the reach.
     *
     * @param level  the level the line is traced in
     * @param viewer the entity the trace ignores
     * @param spout  where the line starts
     * @param axis   its unit vector
     * @param range  its reach
     * @return the wall, in world coordinates, or null in open air
     */
    static @Nullable Wall wallAlong(Level level, Entity viewer, Vec3 spout, Vec3 axis, double range) {
        BlockHitResult hit = level.clip(new ClipContext(spout, spout.add(axis.scale(range)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, viewer));
        return hit.getType() == HitResult.Type.BLOCK
                ? new Wall(hit.getLocation().distanceTo(spout), hit.getLocation(), hit.getDirection()) : null;
    }

    /**
     * The Flames the local player's glove has selected.
     *
     * @param player the local player
     * @return the ability, or null while the glove selects no Flames
     */
    private static @Nullable ClientAbility selectedFlames(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        boolean burns = ability != null && ability.behaviors().stream().anyMatch(FlamesStep.class::isInstance);
        return burns ? ability : null;
    }
}
