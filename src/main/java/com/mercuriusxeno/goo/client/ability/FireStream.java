package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.FlamesStep;
import com.mercuriusxeno.goo.client.ability.FireField.Jet;
import com.mercuriusxeno.goo.client.ability.FireField.Wall;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
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
 * Flames' fire stream: while right click holds Flames, a pixelated jet of
 * flame pours from the glove down its cone, drawn by {@link FirePass} as
 * one field of voxels. A block in the look cuts the jet off where it stands,
 * the flames keeping their size, and the fire it displaces bounces off the
 * face, damped. flames-pixelated-fire-stream
 * <p>
 * The operator's ruling on the jet's life: the fire front travels out from the
 * glove over a quarter second, and on release the jet burns away from the
 * glove outward over the same quarter second.
 * <p>
 * A jet pinned by {@code /goodebug flames} burns in place, held and released
 * on a loop, so the operator can walk around it and watch it from any side.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class FireStream {

    /** Ticks the fire front takes to travel the stream's whole range, and the burn-away takes on release. */
    static final double TRAVEL_TICKS = com.mercuriusxeno.goo.ability.program.FlameShape.TRAVEL_TICKS;
    /** A pinned jet's loop: held this many ticks, then released until the loop's end. */
    static final double PINNED_HELD_TICKS = 40;
    static final double PINNED_LOOP_TICKS = 60;
    /** blaze_flames.json's reach and cone, for a pinned jet when no Flames is synced. */
    private static final double DEFAULT_RANGE = 6;
    private static final double DEFAULT_CONE_DEGREES = 20;
    private static final String FLAMES_ID = "goo:blaze_flames";
    private static final double HALF = 0.5;
    /** Below this squared length a direction has no way. */
    private static final double NO_WAY = 1e-6;

    /** The game time the current hold began, or NaN while no hold runs. */
    private static double heldSince = Double.NaN;
    /** The game time the last hold ended, or NaN while a hold runs or nothing burns. */
    private static double releasedAt = Double.NaN;
    /** The jet {@code /goodebug flames} pinned, or null. */
    private static @Nullable PinnedJet pinned;

    private FireStream() {
    }

    /**
     * A jet pinned in the world: its frame and the block it hits in world
     * coordinates, and the game time its loop began.
     *
     * @param hand     the glove's world position
     * @param axis     the jet's unit vector
     * @param side     the unit direction across the jet to its side
     * @param range    the stream's reach
     * @param tan      the tangent of half the cone's angle
     * @param wall     where the jet hits a block, the hit point in world coordinates, or null
     * @param pinnedAt the game time the loop began
     */
    private record PinnedJet(Vec3 hand, Vec3 axis, Vec3 side, double range, double tan, @Nullable Wall wall,
                             double pinnedAt) {
    }

    /**
     * Pins a looping jet at the glove, down the look, as Flames would throw it now.
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
        double range = flames == null ? DEFAULT_RANGE : flames.delivery().range();
        double cone = flames == null ? DEFAULT_CONE_DEGREES : flames.delivery().coneDegrees();
        Vec3 hand = GloveAim.handPosition(mc.gameRenderer.getMainCamera());
        Aim aim = aimAtCrosshair(player, hand, 1f, range);
        Vec3 side = ConeSections.sectionSide(aim.axis(), player.getViewYRot(1f));
        pinned = new PinnedJet(hand, aim.axis(), side, range, Math.tan(Math.toRadians(cone * HALF)), aim.wall(),
                player.level().getGameTime());
        return true;
    }

    /** Takes the pinned jet away. */
    public static void unpin() {
        pinned = null;
    }

    /**
     * Draws the held jet, and the pinned one, after the translucent blocks.
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
        List<Burning> burning = new ArrayList<>();
        Burning held = heldJet(player, now, partialTick, camera);
        if (held != null) {
            burning.add(held);
        }
        Burning pinnedJet = pinnedJet(now, camera);
        if (pinnedJet != null) {
            burning.add(pinnedJet);
        }
        for (HeaterFlames.Heater heater : HeaterFlames.burning()) {
            Burning heaterJet = heaterJet(player, heater, now, camera);
            if (heaterJet != null) {
                burning.add(heaterJet);
            }
        }
        if (burning.isEmpty()) {
            return;
        }
        List<FireField> fields = new ArrayList<>();
        for (Burning jet : burning) {
            FireField field = new FireField(jet.jet(), jet.span(), jet.wall());
            if (field.burns()) {
                fields.add(field);
            }
        }
        FirePass.draw(fields, camera, event.getModelViewMatrix());
    }

    /**
     * A jet burning this frame: its frame, its burning stretch and the block it hits, camera relative.
     *
     * @param jet  the jet's frame
     * @param span the burning stretch
     * @param wall where the jet hits a block, or null
     */
    private record Burning(Jet jet, JetSpan span, @Nullable Wall wall) {
    }

    private static @Nullable Burning heldJet(LocalPlayer player, double now, float partialTick, Vec3 camera) {
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
        Minecraft mc = Minecraft.getInstance();
        Vec3 hand = GloveAim.handPosition(mc.gameRenderer.getMainCamera());
        Aim aim = aimAtCrosshair(player, hand, partialTick, range);
        Vec3 side = ConeSections.sectionSide(aim.axis(), player.getViewYRot(partialTick));
        double tan = Math.tan(Math.toRadians(flames.delivery().coneDegrees() * HALF));
        return new Burning(new Jet(hand.subtract(camera), aim.axis(), side, tan), span,
                cameraRelative(aim.wall(), camera));
    }

    /**
     * A heater prism's fire this frame: Flames' own, from the prism's tip the
     * way its server last pointed it, bouncing off the block its line meets
     * within Flames' reach. heater-prism-flames-nearby-creatures
     *
     * @param player the local player, whose level the line is traced in
     * @param heater the heater
     * @param now    the game time with the partial tick
     * @param camera the camera's world position
     * @return the heater's fire, or null once it has burned away
     */
    private static @Nullable Burning heaterJet(LocalPlayer player, HeaterFlames.Heater heater, double now,
                                               Vec3 camera) {
        JetSpan span = HeaterFlames.spanOf(heater, now, DEFAULT_RANGE);
        if (span == null) {
            return null;
        }
        Vec3 tip = heater.tip();
        Vec3 axis = heater.axis();
        BlockHitResult hit = player.level().clip(new ClipContext(tip, tip.add(axis.scale(DEFAULT_RANGE)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Wall wall = hit.getType() == HitResult.Type.BLOCK
                ? new Wall(hit.getLocation().distanceTo(tip), hit.getLocation(), hit.getDirection()) : null;
        Vec3 side = new Vec3(-axis.z, 0, axis.x);
        side = side.lengthSqr() > NO_WAY ? side.normalize() : new Vec3(1, 0, 0);
        return new Burning(new Jet(tip.subtract(camera), axis, side, Math.tan(Math.toRadians(DEFAULT_CONE_DEGREES
                * HALF))), span, cameraRelative(wall, camera));
    }

    private static @Nullable Burning pinnedJet(double now, Vec3 camera) {
        PinnedJet jet = pinned;
        if (jet == null) {
            return null;
        }
        double intoLoop = (now - jet.pinnedAt()) % PINNED_LOOP_TICKS;
        double released = intoLoop < PINNED_HELD_TICKS ? Double.NaN : intoLoop - PINNED_HELD_TICKS;
        JetSpan span = jetSpan(intoLoop, released, jet.range());
        if (span.tail() >= span.front()) {
            return null;
        }
        return new Burning(new Jet(jet.hand().subtract(camera), jet.axis(), jet.side(),
                jet.tan()), span, cameraRelative(jet.wall(), camera));
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
        if (Double.isNaN(heldSince)) {
            return null;
        }
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
     * as the server's FlamesStep does, and the jet pours from the glove
     * toward that point, so the fire bounces off the face the player looks at.
     *
     * @param axis the jet's unit vector, from the glove
     * @param wall where the crosshair meets a block within the reach, in world coordinates, or null
     */
    private record Aim(Vec3 axis, @Nullable Wall wall) {
    }

    /**
     * Aims the jet from the glove at the crosshair's block within the reach.
     *
     * @param player      the local player
     * @param hand        the glove's world position
     * @param partialTick the frame's partial tick
     * @param range       the stream's reach
     * @return the aim
     */
    private static Aim aimAtCrosshair(LocalPlayer player, Vec3 hand, float partialTick, double range) {
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(look.scale(range)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return new Aim(look, null);
        }
        Vec3 toHit = hit.getLocation().subtract(hand);
        Direction face = hit.getDirection();
        return new Aim(toHit.normalize(), new Wall(toHit.length(), hit.getLocation(), face));
    }

    private static @Nullable Wall cameraRelative(@Nullable Wall wall, Vec3 camera) {
        return wall == null ? null : new Wall(wall.distance(), wall.at().subtract(camera), wall.face());
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
