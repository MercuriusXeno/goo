package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.network.KnifeRainSling;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Where a caster's lifted palm blob rests, chosen by the selected ability's
 * data: on the center line ahead of the eye, settled below the crosshair so
 * it never covers it, for an ability tagged centered, and at the steady
 * point by the glove that Goop and Slick use for every other ability. Each
 * point moves and turns with the caster's view and never bobs with the hand.
 * centered-abilities-float-the-blob-below-the-crosshair
 */
public final class BlobRest {

    /** How far ahead of the eye the centered blob rests, along the look. */
    static final double CENTER_AHEAD = 0.7;
    /** How far below the look the centered blob rests, clear of the crosshair. */
    static final double CENTER_BELOW = 0.15;
    /** A quarter turn, in degrees. */
    private static final float QUARTER_TURN = 90f;

    private BlobRest() {
    }

    /**
     * The rest point in a view.
     *
     * @param eye        the viewer's eye
     * @param xRot       the view's pitch in degrees, negative looking up
     * @param yRot       the view's yaw in degrees
     * @param gloveRight whether the glove is in the right hand
     * @param centered   whether the selected ability is tagged centered
     * @return the rest point
     */
    static Vec3 inView(Vec3 eye, float xRot, float yRot, boolean gloveRight, boolean centered) {
        if (!centered) {
            return SpraySource.inView(eye, xRot, yRot, gloveRight);
        }
        Vec3 ahead = Vec3.directionFromRotation(xRot, yRot);
        Vec3 up = Vec3.directionFromRotation(xRot - QUARTER_TURN, yRot);
        return eye.add(ahead.scale(CENTER_AHEAD)).subtract(up.scale(CENTER_BELOW));
    }

    /**
     * A caster's rest point this frame: in the camera's view for the local
     * player in first person, in the caster's own view otherwise, and the
     * glove the server sent for a caster this client does not hold.
     *
     * @param casterId    the caster's entity id
     * @param sentGlove   the glove the held tick came with
     * @param partialTick the frame's partial tick
     * @return the rest point
     */
    public static Vec3 of(int casterId, Vec3 sentGlove, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(casterId) instanceof Player caster)) {
            return sentGlove;
        }
        boolean gloveRight = KnifeRainSling.gloveOnTheRight(caster);
        boolean centered = selectsCentered(caster);
        if (caster == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Camera camera = mc.gameRenderer.getMainCamera();
            return inView(camera.position(), camera.xRot(), camera.yRot(), gloveRight, centered);
        }
        return inView(caster.getEyePosition(partialTick), caster.getViewXRot(partialTick),
                caster.getViewYRot(partialTick), gloveRight, centered);
    }

    /**
     * Whether the ability a caster's glove selects is tagged centered.
     *
     * @param caster the caster
     * @return true for a centered selection, false with none or no synced copy
     */
    private static boolean selectsCentered(Player caster) {
        ItemStack main = caster.getMainHandItem();
        ItemStack glove = main.getItem() instanceof GooGloveItem ? main : caster.getOffhandItem();
        GloveSelection selection = glove.getItem() instanceof GooGloveItem ? GooGloveItem.getSelection(glove) : null;
        ClientAbility ability = selection == null ? null : AbilitySyncHandler.findAbility(selection.abilityId());
        return ability != null && ability.hasTag(AbilityTags.CENTERED);
    }
}
