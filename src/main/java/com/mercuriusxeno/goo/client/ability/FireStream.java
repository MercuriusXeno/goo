package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.FlamesStep;
import com.mercuriusxeno.goo.client.ability.FireField.Jet;
import com.mercuriusxeno.goo.client.ability.FireField.Wall;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

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
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class FireStream {

    /** Ticks the fire front takes to travel the stream's whole range, and the burn-away takes on release. */
    static final double TRAVEL_TICKS = 5;
    /** A pinned jet's loop: held this many ticks, then released until the loop's end. */
    static final double PINNED_HELD_TICKS = 40;
    static final double PINNED_LOOP_TICKS = 60;
    /** blaze_flames.json's reach and cone, for a pinned jet when no Flames is synced, and for a heater prism. */
    static final double FLAMES_RANGE = 6;
    static final double FLAMES_CONE_DEGREES = 20;
    private static final String FLAMES_ID = "goo:blaze_flames";
    private static final double HALF = 0.5;

    /** The game time the current hold began, or NaN while no hold runs. */
    private static double heldSince = Double.NaN;
    /** The game time the last hold ended, or NaN while a hold runs or nothing burns. */
    private static double releasedAt = Double.NaN;
    /** The jet {@code /goodebug flames} pinned, or null. */
    private static @Nullable PinnedJet pinned;

    private FireStream() {
    }

    /**
     * A jet pinned in the world, in world coordinates, and the game time its loop began.
     *
     * @param hand     the glove's world position
     * @param axis     the jet's unit vector
     * @param range    the stream's reach
     * @param cone     the stream's cone, in degrees
     * @param wall     where the jet meets a block, the hit point in world coordinates, or null
     * @param pinnedAt the game time the loop began
     */
    private record PinnedJet(Vec3 hand, Vec3 axis, double range, double cone, @Nullable Wall wall,
                             double pinnedAt) {
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
        Vec3 hand = GloveAim.handPosition(mc.gameRenderer.getMainCamera());
        Aim aim = aimAtCrosshair(player, hand, 1f, range);
        pinned = new PinnedJet(hand, aim.axis(), range, cone, aim.wall(), player.level().getGameTime());
        return true;
    }

    /** Takes the pinned jet away. */
    public static void unpin() {
        pinned = null;
    }

    /**
     * Draws the held fire, the pinned one and every heater's, after the translucent blocks.
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
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = player.level().getGameTime() + partialTick;
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
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
        FirePass.draw(fields, camera, event.getModelViewMatrix());
    }

    private static void addBurning(List<FireField> fields, @Nullable FireField field) {
        if (field != null && field.burns()) {
            fields.add(field);
        }
    }

    /**
     * The field held Flames burns: from the glove, down its aim, at its
     * delivery's reach and cone, cut off at the face the look meets.
     *
     * @param delivery Flames' delivery
     * @param hand     the glove's world position
     * @param axis     the aim's unit vector from the glove
     * @param span     the burning stretch
     * @param wall     where the look meets a block, in world coordinates, or null
     * @param camera   the camera's world position
     * @return the field, camera relative
     */
    static FireField fieldOf(Delivery delivery, Vec3 hand, Vec3 axis, JetSpan span, @Nullable Wall wall,
                             Vec3 camera) {
        return fieldFrom(hand, axis, delivery.range(), delivery.coneDegrees(), span, wall, camera);
    }

    /**
     * A field from a spout in world coordinates, made camera relative.
     *
     * @param spout  where the fire pours from
     * @param axis   the unit vector it pours along
     * @param range  its reach in open air
     * @param cone   its cone, in degrees
     * @param span   the burning stretch
     * @param wall   where its line meets a block, in world coordinates, or null
     * @param camera the camera's world position
     * @return the field
     */
    static FireField fieldFrom(Vec3 spout, Vec3 axis, double range, double cone, JetSpan span, @Nullable Wall wall,
                               Vec3 camera) {
        Jet jet = new Jet(spout.subtract(camera), axis, FireField.sideOf(axis),
                Math.tan(Math.toRadians(cone * HALF)));
        Wall relative = wall == null ? null : new Wall(wall.distance(), wall.at().subtract(camera), wall.face());
        return new FireField(jet, span, range, relative);
    }

    private static @Nullable FireField heldField(LocalPlayer player, double now, float partialTick, Vec3 camera) {
        ClientAbility flames = selectedFlames(player);
        if (flames == null) {
            heldSince = Double.NaN;
            releasedAt = Double.NaN;
            return null;
        }
        double range = flames.delivery().range();
        JetSpan span = trackHold(GloveUseTracker.runsHeld(player), now, range);
        if (span == null) {
            return null;
        }
        Vec3 hand = GloveAim.handPosition(Minecraft.getInstance().gameRenderer.getMainCamera());
        Aim aim = aimAtCrosshair(player, hand, partialTick, range);
        return fieldOf(flames.delivery(), hand, aim.axis(), span, aim.wall(), camera);
    }

    private static @Nullable FireField pinnedField(double now, Vec3 camera) {
        PinnedJet jet = pinned;
        if (jet == null) {
            return null;
        }
        double intoLoop = (now - jet.pinnedAt()) % PINNED_LOOP_TICKS;
        double released = intoLoop < PINNED_HELD_TICKS ? Double.NaN : intoLoop - PINNED_HELD_TICKS;
        return fieldFrom(jet.hand(), jet.axis(), jet.range(), jet.cone(), jetSpan(intoLoop, released, jet.range()),
                jet.wall(), camera);
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
