package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a caster's glove palm draws in third person, as the player model
 * draws it: the glove arm layer records each caster's palm from the arm's
 * drawn transform, and the lifted blob, Knives' and Dart's stack read it, so
 * the blob stands over the hand the model shows, upright or turned upside
 * down. A caster drawn before but not this frame keeps the palm last drawn,
 * carried with its position; one this client never drew stands its palm at
 * the body's hand (KnivesCharge.handAt), the world's up its up.
 * decision third-person-blob-stands-above-the-drawn-palm
 */
public final class DrawnPalm {

    /** The palm's face's normal in the glove's model: the hand body's north face. */
    private static final Vector3fc PALM_FACE_NORMAL = new Vector3f(0f, 0f, -1f);
    /** The model's up in its own pixels: entity models draw y down. */
    private static final Vector3fc MODEL_UP = new Vector3f(0f, -1f, 0f);
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);

    /** Each caster's drawn palms, by entity id and arm. */
    private static final Map<Integer, Map<HumanoidArm, Palm>> DRAWN = new ConcurrentHashMap<>();

    private DrawnPalm() {
    }

    /**
     * A drawn glove palm.
     *
     * @param place   where the resting palm blob's center draws
     * @param palmUp  the way the palm's face looks, a unit vector
     * @param modelUp the drawn model's up, from its feet toward its head, a unit vector
     */
    public record Palm(Vec3 place, Vec3 palmUp, Vec3 modelUp) {

        /**
         * The point a rise above the palm stands at, along the drawn model's up.
         *
         * @param rise how far above the palm, in blocks
         * @return the point
         */
        public Vec3 above(double rise) {
            return place.add(modelUp.scale(rise));
        }

        /**
         * The same palm moved by an offset.
         *
         * @param offset the offset
         * @return the moved palm
         */
        public Palm movedBy(Vec3 offset) {
            return new Palm(place.add(offset), palmUp, modelUp);
        }
    }

    /**
     * The palm as the model draws it: the palm point carried through the
     * glove arm's drawn transform and the glove's place on that arm, the
     * palm's face turned with it, and the model's up turned with the body.
     *
     * @param armToWorld  the arm part's drawn transform, onto the world's axes
     * @param bodyToWorld the body model's drawn transform, onto the world's axes
     * @param gloveOnArm  the glove's model onto the arm part: vanilla's item-in-hand offset and its display transform
     * @param palmInGlove the resting palm blob's center in the glove's model
     * @return the palm in the world's axes, about the transforms' origin
     */
    public static Palm of(Matrix4fc armToWorld, Matrix4fc bodyToWorld, Matrix4fc gloveOnArm, Vector3fc palmInGlove) {
        Matrix4f gloveToWorld = new Matrix4f(armToWorld).mul(gloveOnArm);
        Vector3f place = gloveToWorld.transformPosition(palmInGlove, new Vector3f());
        Vector3f palmUp = gloveToWorld.transformDirection(PALM_FACE_NORMAL, new Vector3f()).normalize();
        Vector3f modelUp = bodyToWorld.transformDirection(MODEL_UP, new Vector3f()).normalize();
        return new Palm(new Vec3(place), new Vec3(palmUp), new Vec3(modelUp));
    }

    /**
     * Records where a caster's glove palm drew this frame.
     *
     * @param casterId the caster's entity id
     * @param side     the arm holding the glove
     * @param casterAt where the caster's body drew this frame
     * @param palm     the palm in the world
     */
    public static void record(int casterId, HumanoidArm side, Vec3 casterAt, Palm palm) {
        DRAWN.computeIfAbsent(casterId, id -> new EnumMap<>(HumanoidArm.class))
                .put(side, palm.movedBy(casterAt.reverse()));
    }

    /**
     * Whether a caster draws in third person, its glove on the model's arm:
     * every caster but the local player in first person.
     *
     * @param mc     the client
     * @param caster the caster
     * @return true where the model's arm holds the glove
     */
    static boolean drawnInThirdPerson(Minecraft mc, Player caster) {
        return caster != mc.player || !mc.options.getCameraType().isFirstPerson();
    }

    /**
     * Where a caster's glove palm draws this frame: the palm last drawn,
     * carried with the caster, or the body's hand with the world's up for a
     * caster this client never drew.
     *
     * @param caster     the caster
     * @param gloveRight whether the glove is in the right hand
     * @param partial    the frame's partial tick
     * @return the palm in the world
     */
    static Palm of(Player caster, boolean gloveRight, float partial) {
        return drawnOf(caster.getId(), gloveRight ? HumanoidArm.RIGHT : HumanoidArm.LEFT)
                .map(palm -> palm.movedBy(caster.getPosition(partial)))
                .orElseGet(() -> new Palm(KnivesCharge.handAt(caster, gloveRight, partial), WORLD_UP, WORLD_UP));
    }

    private static Optional<Palm> drawnOf(int casterId, HumanoidArm side) {
        Map<HumanoidArm, Palm> palms = DRAWN.get(casterId);
        return palms == null ? Optional.empty() : Optional.ofNullable(palms.get(side));
    }

    /** Forgets every drawn palm, as a test's end does. */
    static void forgetEveryPalm() {
        DRAWN.clear();
    }
}
