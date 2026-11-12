package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.ClientGateCarry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Slides a body the server carried through a linked gate, and the client
 * has not yet drawn through, toward each later place the server sends as
 * seen from the side of the gate it went into, so it is drawn on into that
 * gate rather than pulled behind it.
 * aim-and-shots-carry-through-linked-gates
 */
@Mixin(InterpolationHandler.class)
public abstract class InterpolationHandlerGateCarryMixin {

    @Shadow
    @Final
    private Entity entity;

    /**
     * Sees the place the slide heads for from the body's drawn side.
     *
     * @param position the place the server sent
     * @return the place drawn
     */
    @ModifyVariable(method = "interpolateTo", at = @At("HEAD"), argsOnly = true)
    private Vec3 goo$seeTheTargetFromTheDrawnSide(Vec3 position) {
        return carriedHere() ? ClientGateCarry.drawnSideOf(entity.getId(), position) : position;
    }

    /**
     * Sees the facing the slide turns to from the body's drawn side.
     *
     * @param yRot the facing the server sent
     * @return the facing drawn
     */
    @ModifyVariable(method = "interpolateTo", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float goo$seeTheFacingFromTheDrawnSide(float yRot) {
        return carriedHere() ? ClientGateCarry.drawnYawOf(entity.getId(), yRot) : yRot;
    }

    private boolean carriedHere() {
        return entity.level().isClientSide() && !ClientGateCarry.retargeting();
    }
}
