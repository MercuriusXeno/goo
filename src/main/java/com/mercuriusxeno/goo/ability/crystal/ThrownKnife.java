package com.mercuriusxeno.goo.ability.crystal;

import com.mercuriusxeno.goo.ability.TravelForm;
import com.mercuriusxeno.goo.throwing.ProjectileFlight;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
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
    /** How far straight ahead along the look each knife and javelin aims, in blocks: a point on the horizon. */
    public static final double AIM_AHEAD = 64;
    /** A knives throw takes one of every two knives held. */
    private static final int HALVES = 2;

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
        return launchPointOf(eye, xRot, yRot, gloveRight, held - 1, held, javelins);
    }

    /**
     * Where one of a throw's volley leaves from: the hover slot of the one at an index.
     *
     * @param eye        the thrower's eye
     * @param xRot       the thrower's view pitch in degrees
     * @param yRot       the thrower's view yaw in degrees
     * @param gloveRight whether the glove is in the right hand
     * @param index      the index of the one leaving, 0 the first formed
     * @param held       the count held as the throw leaves, at least 1
     * @param javelins   whether the stack is Dart's javelins rather than Knives' knives
     * @return the point
     */
    public static Vec3 launchPointOf(Vec3 eye, float xRot, float yRot, boolean gloveRight, int index, int held,
                                     boolean javelins) {
        return StackHover.pointOf(StackHover.cubeCenterInView(eye, xRot, yRot, gloveRight), xRot, yRot,
                StackHover.slotOf(index, held, javelins));
    }

    /**
     * How many one throw takes from a stack: half the knives held, rounded
     * up, or one javelin.
     * decision knives-throw-one-per-two-ticks-while-left-click-is-held
     *
     * @param held     the count held as the throw leaves
     * @param javelins whether the stack is Dart's javelins rather than Knives' knives
     * @return the count thrown
     */
    public static int thrownAtOnce(int held, boolean javelins) {
        return javelins ? Math.min(1, held) : Mth.positiveCeilDiv(held, HALVES);
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

    /**
     * The point each knife and javelin aims at: a point far straight ahead
     * along the look, no block or mob in the way consulted, so every one faces
     * forward and a throw flies straight ahead from where it hovered.
     * decision knives-hover-above-the-blob-cube-over-the-hand
     *
     * @param player  the player
     * @param partial the partial tick, 1 on the server
     * @return the point
     */
    public static Vec3 aimPointOf(Player player, float partial) {
        return player.getEyePosition(partial).add(player.getViewVector(partial).scale(AIM_AHEAD));
    }
}