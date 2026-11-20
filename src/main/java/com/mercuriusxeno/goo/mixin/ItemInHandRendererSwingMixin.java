package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.model.FirstPersonSwing;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records the carry and turn vanilla's swing lays on the first-person hand,
 * from the hand's pose on either side of it, and forgets them once that hand
 * is drawn, so the glove's lifted blob can take the swing back out.
 * decision blob-holds-its-angle-through-a-throws-swing
 * palm-blob-lifts-as-the-spray-source: the lifted blob stands above the palm as it rests unswung
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererSwingMixin {

    @Unique
    private final Matrix4f goo$beforeTheSwing = new Matrix4f();

    /**
     * Keeps the hand's pose before the swing carries and turns it.
     *
     * @param attack    how far through the swing
     * @param poseStack the hand's pose stack
     * @param invert    1 for the right arm, -1 for the left
     * @param arm       the arm swinging
     * @param ci        the callback
     */
    @Inject(method = "swingArm", at = @At("HEAD"))
    private void goo$keepThePoseBeforeTheSwing(float attack, PoseStack poseStack, int invert, HumanoidArm arm,
                                               CallbackInfo ci) {
        goo$beforeTheSwing.set(poseStack.last().pose());
    }

    /**
     * Records the swing's turn once it is laid on the hand.
     *
     * @param poseStack   the hand's pose stack
     * @param arm         the arm swinging
     * @param attackValue how far through the swing
     * @param ci          the callback
     */
    @Inject(method = "applyItemArmAttackTransform", at = @At("RETURN"))
    private void goo$recordTheSwing(PoseStack poseStack, HumanoidArm arm, float attackValue, CallbackInfo ci) {
        FirstPersonSwing.recordSwing(goo$beforeTheSwing, poseStack.last().pose());
    }

    /**
     * Forgets the last hand's swing as each hand's draw begins.
     *
     * @param ci the callback
     */
    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void goo$forgetTheLastHandsSwing(CallbackInfo ci) {
        FirstPersonSwing.forgetSwing();
    }

    /**
     * Forgets this hand's swing once it is drawn, so no other draw takes it.
     *
     * @param ci the callback
     */
    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void goo$forgetThisHandsSwing(CallbackInfo ci) {
        FirstPersonSwing.forgetSwing();
    }
}
