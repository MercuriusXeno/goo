package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.ClientGateCarry;
import net.minecraft.world.entity.AbstractInterpolationHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionPath;
import org.jspecify.annotations.Nullable;
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
@Mixin(AbstractInterpolationHandler.class)
public abstract class InterpolationHandlerGateCarryMixin {

    @Shadow
    @Final
    protected Entity entity;

    /**
     * Sees the place the slide heads for from the body's drawn side.
     *
     * @param position the path to the place the server sent, or null where it sent none
     * @return the path to the place drawn
     */
    @ModifyVariable(method = "interpolateTo(Lnet/minecraft/world/entity/PositionPath;FFZ)Z", at = @At("HEAD"),
            argsOnly = true)
    private @Nullable PositionPath goo$seeTheTargetFromTheDrawnSide(@Nullable PositionPath position) {
        return position != null && carriedHere()
                ? PositionPath.of(ClientGateCarry.drawnSideOf(entity.getId(), position.endPosition())) : position;
    }

    /**
     * Sees the facing the slide turns to from the body's drawn side.
     *
     * @param yRot the facing the server sent
     * @return the facing drawn
     */
    @ModifyVariable(method = "interpolateTo(Lnet/minecraft/world/entity/PositionPath;FFZ)Z", at = @At("HEAD"),
            argsOnly = true, ordinal = 0)
    private float goo$seeTheFacingFromTheDrawnSide(float yRot) {
        return carriedHere() ? ClientGateCarry.drawnYawOf(entity.getId(), yRot) : yRot;
    }

    private boolean carriedHere() {
        return entity.level().isClientSide() && !ClientGateCarry.retargeting();
    }
}
