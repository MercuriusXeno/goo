package com.mercuriusxeno.goo.client.ber;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Carries one crucible's item entity into its melt head without a jump (decision
 * consume-at-rest-in-place). The client remembers the last pose it drew of an item
 * entity in the basin; when a head of that item appears soon after, the head's
 * first frame takes that pose and eases into the flat resting pose. The pose is
 * captured on the client because an item entity's bob offset is rolled per side.
 */
final class CrucibleHeadHandoff {

    /** Ticks the head takes to ease from the entity's pose to lying flat. */
    static final float EASE_TICKS = 8f;
    /** Ticks after the entity was last seen within which a new head takes its pose. */
    static final float HANDOFF_WINDOW_TICKS = 3f;
    /** The height ItemEntityRenderer lifts a model's bottom above the entity's feet. */
    static final float ENTITY_HOVER = 0.0625f;
    /** The height an entity's bob swings through, and the lift it swings about. */
    static final float ENTITY_BOB = 0.1f;
    /** The ticks per radian an entity's bob advances at. */
    private static final float BOB_TICKS_PER_RADIAN = 10f;
    /** The ticks per radian an entity spins at. */
    private static final float SPIN_TICKS_PER_RADIAN = 20f;
    /** Half, the share of a model's height below its center. */
    private static final float HALF = 0.5f;
    /** The tilt that lays an item's face up. */
    static final float FLAT_TILT_DEGREES = -90f;

    /**
     * An item model's pose in block-relative coords: its center, its turn about the
     * vertical, its tilt about X and its scale on the model's own extents.
     *
     * @param x     the model center's X
     * @param y     the model center's Y
     * @param z     the model center's Z
     * @param spin  the turn about the vertical, in radians
     * @param tilt  the tilt about X, in degrees, zero upright
     * @param scale the scale on the model's extents
     */
    record ItemPose(float x, float y, float z, float spin, float tilt, float scale) {
    }

    /** The item entity last seen in the basin, or null. */
    private @Nullable Identifier seenItem;
    private float seenX;
    private float seenY;
    private float seenZ;
    private float seenAge;
    private float seenBobOffset;
    private double seenAt;

    /** The head drawn last frame, or null while none was. */
    private @Nullable Identifier lastHead;
    /** The time the current head took the entity's pose, NaN while it lies at rest. */
    private double swapAt = Double.NaN;
    /** The entity's age at the swap, so its spin and bob carry on through the ease. */
    private float ageAtSwap;

    /**
     * Remembers the pose an item entity in the basin is drawn at this frame.
     *
     * @param item       the entity's item id
     * @param x          the entity's X relative to the block
     * @param y          the entity's feet Y relative to the block
     * @param z          the entity's Z relative to the block
     * @param ageInTicks the entity's client age with the partial tick
     * @param bobOffset  the entity's client bob offset
     * @param now        the game time with the partial tick
     */
    void seeEntity(Identifier item, double x, double y, double z, float ageInTicks, float bobOffset, double now) {
        seenItem = item;
        seenX = (float) x;
        seenY = (float) y;
        seenZ = (float) z;
        seenAge = ageInTicks;
        seenBobOffset = bobOffset;
        seenAt = now;
    }

    /**
     * The pose to draw the head at this frame: the entity's pose at the swap, easing
     * into the resting pose, or the resting pose once eased or with no entity seen.
     *
     * @param head     the head's item id, or null while no item dissolves
     * @param modelBox the head model's bounds, as the entity drew it
     * @param rest     the head's resting pose
     * @param now      the game time with the partial tick
     * @return the head's pose
     */
    ItemPose headPose(@Nullable Identifier head, AABB modelBox, ItemPose rest, double now) {
        if (head == null) {
            lastHead = null;
            swapAt = Double.NaN;
            return rest;
        }
        if (!head.equals(lastHead)) {
            startHandoff(head, now);
        }
        lastHead = head;
        if (Double.isNaN(swapAt)) {
            return rest;
        }
        float elapsed = (float) (now - swapAt);
        if (elapsed >= EASE_TICKS) {
            swapAt = Double.NaN;
            return rest;
        }
        ItemPose entity = entityPose(seenX, seenY, seenZ, ageAtSwap + elapsed, seenBobOffset, modelBox);
        return ease(entity, rest, elapsed / EASE_TICKS);
    }

    /**
     * Starts the ease when the new head is the item entity seen a moment ago, and
     * forgets the entity either way.
     *
     * @param head the new head's item id
     * @param now  the game time with the partial tick
     */
    private void startHandoff(Identifier head, double now) {
        boolean handedOff = now - seenAt <= HANDOFF_WINDOW_TICKS && head.equals(seenItem);
        swapAt = handedOff ? now : Double.NaN;
        ageAtSwap = seenAge + (float) (now - seenAt);
        seenItem = null;
    }

    /**
     * The pose ItemEntityRenderer draws an item entity at: upright, unscaled, its
     * model's bottom a hover and a bob above the feet, spun about the vertical.
     *
     * @param x          the entity's X relative to the block
     * @param y          the entity's feet Y relative to the block
     * @param z          the entity's Z relative to the block
     * @param ageInTicks the entity's age with the partial tick
     * @param bobOffset  the entity's bob offset
     * @param modelBox   the model's bounds in its ground display
     * @return the entity's pose
     */
    static ItemPose entityPose(float x, float y, float z, float ageInTicks, float bobOffset, AABB modelBox) {
        float bob = Mth.sin(ageInTicks / BOB_TICKS_PER_RADIAN + bobOffset) * ENTITY_BOB + ENTITY_BOB;
        float spin = ageInTicks / SPIN_TICKS_PER_RADIAN + bobOffset;
        float centerX = (float) modelBox.getCenter().x;
        float centerZ = (float) modelBox.getCenter().z;
        float cos = Mth.cos(spin);
        float sin = Mth.sin(spin);
        float bottomY = y + bob + ENTITY_HOVER;
        return new ItemPose(x + centerX * cos + centerZ * sin,
            bottomY + (float) modelBox.getYsize() * HALF,
            z - centerX * sin + centerZ * cos, spin, 0f, 1f);
    }

    /**
     * The head's resting pose: flat at its placement, scaled to its width.
     *
     * @param placement where the head lies and how wide
     * @param modelBox  the head model's bounds
     * @param minExtent the smallest extent a model is scaled by
     * @return the resting pose
     */
    static ItemPose restingPose(CrucibleItemLayout.ItemPlacement placement, AABB modelBox, double minExtent) {
        float scale = (float) (placement.size() / Math.max(Math.max(modelBox.getXsize(), modelBox.getYsize()),
            minExtent));
        return new ItemPose(placement.x(), placement.y(), placement.z(), 0f, FLAT_TILT_DEGREES, scale);
    }

    /**
     * Blends one pose into another on a smoothstep, turning the short way round.
     *
     * @param from     the pose at the start
     * @param to       the pose at the end
     * @param progress the share of the ease run, in [0, 1]
     * @return the blended pose
     */
    static ItemPose ease(ItemPose from, ItemPose to, float progress) {
        float t = Mth.clamp(progress, 0f, 1f);
        float eased = (float) Mth.smoothstep(t);
        float spinTo = from.spin() + Mth.wrapDegrees((to.spin() - from.spin()) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        return new ItemPose(Mth.lerp(eased, from.x(), to.x()), Mth.lerp(eased, from.y(), to.y()),
            Mth.lerp(eased, from.z(), to.z()), Mth.lerp(eased, from.spin(), spinTo),
            Mth.lerp(eased, from.tilt(), to.tilt()), Mth.lerp(eased, from.scale(), to.scale()));
    }
}
