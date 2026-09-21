package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import java.util.Set;

/**
 * The seam a step program reaches its world through (decision
 * host-agnostic-runtime). The runtime holds a host, never a level or a
 * block entity, so the same program runs on the marker block, the struck
 * entity or the potion holder. Reads answer through {@link Variables} by
 * name and through the typed accessors; world actions are host methods,
 * so a test drives a program against a mock with no level behind it.
 *
 * <p>Each method belongs to a {@link HostCapability}. A host implements
 * the methods of the capabilities its {@link HostKind} provides and
 * refuses the rest; {@link ProgramBehavior#forHost} keeps a program from
 * ever reaching a refused method by checking every step's needs against
 * the kind at load.
 */
public interface StepHost extends Variables {

    /**
     * Returns which kind of host this is, which names its capabilities
     * and variables.
     *
     * @return the host kind
     */
    HostKind kind();

    /**
     * Returns the block the program acts from: the marker block, or the
     * block the struck entity stands in.
     *
     * @return the anchor position
     */
    BlockPos position();

    /**
     * Returns the face the marker was placed on; the blast direction is
     * its opposite. Capability {@link HostCapability#PLACED_FACE}.
     *
     * @return the placed face
     */
    Direction placedFace();

    /**
     * Returns the blobs stacked on the host, live. Capability
     * {@link HostCapability#STACKS}.
     *
     * @return the stack count
     */
    int stackCount();

    /**
     * Spends one stacked blob. Capability {@link HostCapability#STACKS}.
     */
    void decrementStack();

    /**
     * Detonates at the anchor's center. Capability
     * {@link HostCapability#EXPLODE}.
     *
     * @param power the explosion power
     * @param mode  how blocks are treated
     */
    void explode(float power, ExplosionMode mode);

    /**
     * Scans the volume around the anchor for an entity every filter keeps.
     * Capability {@link HostCapability#ENTITY_SCAN}.
     *
     * @param shape   the volume shape
     * @param radius  the volume radius in blocks
     * @param filters the filters an entity must pass
     * @return true when at least one entity is in the volume
     */
    boolean anyEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters);

    /**
     * Hurts the host's target. Capability {@link HostCapability#TARGET}.
     *
     * @param amount the damage
     * @param source the damage source
     */
    void damageTarget(float amount, DamageKind source);

    /**
     * Adds a status effect to the host's target. Capability
     * {@link HostCapability#TARGET}.
     *
     * @param effect    the status effect id
     * @param duration  the duration in ticks
     * @param amplifier the amplifier
     * @param visible   whether the effect shows particles and an icon
     */
    void applyPotion(Identifier effect, int duration, int amplifier, boolean visible);
}
