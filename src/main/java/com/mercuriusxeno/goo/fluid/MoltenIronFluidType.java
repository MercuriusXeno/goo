package com.mercuriusxeno.goo.fluid;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Molten iron's fluid type: a living thing moves through it as through lava,
 * slowed, sinking and climbing out by vanilla's own lava travel. Minecraft
 * 26.2 moves an entity as through lava only within NeoForge's lava type,
 * where 26.1 moved it so in every fluid tagged lava; molten iron, a fluid
 * type of its own, answers that travel itself.
 * decision lunar-matter-is-anorthosite-basalt-regolith-and-an-iron-core
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
public final class MoltenIronFluidType extends FluidType {

    /** The input's push a tick, as vanilla's lava travel takes it. */
    private static final float LAVA_INPUT_SPEED = 0.02F;
    /** The horizontal and vertical drag in fluid shallower than a jump, as in lava. */
    private static final double SHALLOW_HORIZONTAL_DRAG = 0.5;
    private static final double SHALLOW_VERTICAL_DRAG = 0.8F;
    /** The drag in fluid deeper than a jump, as in lava. */
    private static final double DEEP_DRAG = 0.5;
    /** Gravity's share a tick, as in lava. */
    private static final double GRAVITY_SHARE = 4.0;
    /** The rise a mob takes climbing out over a ledge, and the reach it climbs to, as out of lava. */
    private static final double CLIMB_OUT_SPEED = 0.3F;
    private static final double CLIMB_OUT_REACH = 0.6F;

    /**
     * @param properties the fluid type's properties
     */
    public MoltenIronFluidType(Properties properties) {
        super(properties);
    }

    @Override
    public boolean move(LivingEntity entity, Vec3 movementVector, double gravity) {
        boolean isFalling = entity.getDeltaMovement().y <= 0.0;
        double oldY = entity.getY();
        entity.moveRelative(LAVA_INPUT_SPEED, movementVector);
        entity.move(MoverType.SELF, entity.getDeltaMovement());
        if (entity.getFluidTypeHeight(this) <= entity.getFluidJumpThreshold()) {
            entity.setDeltaMovement(entity.getDeltaMovement()
                    .multiply(SHALLOW_HORIZONTAL_DRAG, SHALLOW_VERTICAL_DRAG, SHALLOW_HORIZONTAL_DRAG));
            entity.setDeltaMovement(entity.getFluidFallingAdjustedMovement(gravity, isFalling,
                    entity.getDeltaMovement()));
        } else {
            entity.setDeltaMovement(entity.getDeltaMovement().scale(DEEP_DRAG));
        }
        if (gravity != 0.0) {
            entity.setDeltaMovement(entity.getDeltaMovement().add(0.0, -gravity / GRAVITY_SHARE, 0.0));
        }
        Vec3 movement = entity.getDeltaMovement();
        if (entity.horizontalCollision
                && entity.isFree(movement.x, movement.y + CLIMB_OUT_REACH - entity.getY() + oldY, movement.z)) {
            entity.setDeltaMovement(movement.x, CLIMB_OUT_SPEED, movement.z);
        }
        return true;
    }
}
