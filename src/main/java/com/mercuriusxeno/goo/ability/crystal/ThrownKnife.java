package com.mercuriusxeno.goo.ability.crystal;

import com.mercuriusxeno.goo.ability.TravelForm;
import com.mercuriusxeno.goo.throwing.ProjectileFlight;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown knife or javelin, shared by the server that strikes with it and
 * the client that draws it: the one formed last leaves its hover slot first,
 * along the thrower's look at its delivery's throw speed, a knife 1.5 blocks
 * a tick and a javelin 2.5, and drops like an arrow, seeking no mob.
 * decision knives-and-javelins-drop-like-an-arrow
 * decision knives-do-not-seek-you-just-throw-them
 * decision dart-does-not-seek-or-highlight
 */
public final class ThrownKnife {

    /** The most ticks a knife or javelin flies before it is spent. */
    public static final int MOST_FLIGHT_TICKS = 100;
    /** How far the crosshair's ray reaches for a block or mob to rest on, in blocks. */
    public static final double CROSSHAIR_REACH = 64;

    private ThrownKnife() {
    }

    /**
     * Where the one a throw takes leaves from: the hover slot of the one
     * formed last, over the cube at the steady point by the glove.
     * decision knives-hover-above-the-blob-cube-over-the-hand
     *
     * @param eye        the thrower's eye
     * @param xRot       the thrower's view pitch in degrees
     * @param yRot       the thrower's view yaw in degrees
     * @param gloveRight whether the glove is in the right hand
     * @param held       the count held as the throw leaves, at least 1
     * @param javelins   whether the stack is Dart's javelins rather than Knives' knives
     * @return the point
     */
    public static Vec3 launchPointOf(Vec3 eye, float xRot, float yRot, boolean gloveRight, int held,
                                     boolean javelins) {
        return StackHover.pointOf(StackHover.cubeCenterInView(eye, xRot, yRot, gloveRight, javelins), xRot, yRot,
                StackHover.slotOf(held - 1, held, javelins));
    }

    /**
     * The velocity a throw leaves its slot with: heading from the slot at the
     * point the thrower's crosshair rests on, so it flies to the crosshair
     * rather than beside it.
     * decision knives-hover-above-the-blob-cube-over-the-hand
     *
     * @param slot      where it leaves
     * @param crosshair the point the crosshair rests on
     * @param speed     the delivery's throw speed, blocks per tick
     * @return the velocity, blocks per tick
     */
    public static Vec3 launchVelocityToward(Vec3 slot, Vec3 crosshair, double speed) {
        return ProjectileFlight.launch(crosshair.subtract(slot), speed);
    }

    /**
     * Where a player's crosshair rests: the first block or mob the look meets
     * within the crosshair's reach, or the point that far along the look where
     * it meets none. Each hovering knife and javelin points at it and each
     * throw heads at it.
     * decision knives-hover-above-the-blob-cube-over-the-hand
     *
     * @param player  the player
     * @param partial the partial tick, 1 on the server
     * @return the point
     */
    public static Vec3 crosshairPointOf(Player player, float partial) {
        Vec3 eye = player.getEyePosition(partial);
        Vec3 reach = player.getViewVector(partial).scale(CROSSHAIR_REACH);
        Vec3 end = player.level().clip(new ClipContext(eye, eye.add(reach), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getLocation();
        EntityHitResult mob = ProjectileUtil.getEntityHitResult(player, eye, end,
                player.getBoundingBox().expandTowards(reach).inflate(1), entity -> !entity.isSpectator()
                        && entity.isPickable(), eye.distanceToSqr(end));
        return mob == null ? end : mob.getLocation();
    }

    /**
     * The heading from a slot at the crosshair's point, which a hovering one
     * points along and a thrown one leaves along.
     *
     * @param slot      the slot
     * @param crosshair the point the crosshair rests on
     * @return the unit heading
     */
    public static Vec3 headingFrom(Vec3 slot, Vec3 crosshair) {
        return crosshair.subtract(slot).normalize();
    }

    /**
     * The point a knife or javelin draws from, its butt or tail on its slot
     * and its length running forward along its heading: a knife's point, which
     * a knife draws from, and a javelin's middle, which a javelin draws about.
     *
     * @param slot     the slot its butt or tail stands on
     * @param heading  its unit heading
     * @param javelins whether it is Dart's javelin rather than a knife
     * @param scale    its size, 1 whole
     * @return the point it draws from
     */
    public static Vec3 drawnFromOf(Vec3 slot, Vec3 heading, boolean javelins, float scale) {
        double lead = javelins ? TravelForm.DART.fins().sweep() : StackHover.KNIFE_LENGTH;
        return slot.add(heading.scale(lead * scale));
    }
}