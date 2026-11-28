package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.GloveRest;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.crystal.StackHover;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.item.GooCasters;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a caster's lifted palm blob rests, chosen by the selected ability's
 * data: at the glove rest for Bore, which eases forward from there; just over
 * the palm for Knives and Dart; and for every other ability at the spray
 * source Goop and Slick drip from, straight above where the hand rests, so
 * no ability's blob floats to the center line.
 * Each point moves and turns with the caster's view and never bobs with the hand.
 * That is first person's; a caster drawn in third person rests every
 * ability's blob above the palm the model draws.
 * decision no-ability-floats-its-blob-to-the-center-line
 * palm-blob-lifts-as-the-spray-source
 * decision third-person-blob-stands-above-the-drawn-palm
 */
public final class BlobRest {

    /** A quarter turn, in degrees. */
    private static final float QUARTER_TURN = 90f;

    private BlobRest() {
    }

    /** The rests a lifted blob takes. */
    public enum Rest {
        /** At the glove rest, for Bore, whose drill extends from that hand. */
        BY_THE_GLOVE,
        /** Just over the palm, for Knives and Dart, whose stacks stand over the hand. */
        OVER_THE_PALM,
        /** Straight above where the hand rests, for every other ability. */
        ABOVE_THE_HAND;

        /**
         * The rest an ability's blob lifts to.
         *
         * @param ability the synced copy of the selected ability, or null with none
         * @return the rest
         */
        static Rest of(@Nullable ClientAbility ability) {
            if (ability == null) {
                return ABOVE_THE_HAND;
            }
            if (ability.delivery() != null && ability.delivery().charge().stacks()) {
                return OVER_THE_PALM;
            }
            // decision bore-blob-lifts-by-the-glove-and-the-drill-extends-from-that-hand
            return BoreDrill.stepOf(ability).isPresent() ? BY_THE_GLOVE : ABOVE_THE_HAND;
        }
    }

    /**
     * The rest point in a view, at the default field of view.
     *
     * @param eye        the viewer's eye
     * @param xRot       the view's pitch in degrees, negative looking up
     * @param yRot       the view's yaw in degrees
     * @param gloveRight whether the glove is in the right hand
     * @param rest       the rest the selected ability lifts its blob to
     * @return the rest point
     */
    public static Vec3 inView(Vec3 eye, float xRot, float yRot, boolean gloveRight, Rest rest) {
        Vec3 ahead = Vec3.directionFromRotation(xRot, yRot);
        Vec3 up = Vec3.directionFromRotation(xRot - QUARTER_TURN, yRot);
        return switch (rest) {
            case BY_THE_GLOVE -> eye.add(ahead.scale(GloveRest.AHEAD))
                    .add(Vec3.directionFromRotation(0f, yRot + QUARTER_TURN)
                            .scale(gloveRight ? GloveRest.TO_THE_GLOVE_SIDE : -GloveRest.TO_THE_GLOVE_SIDE))
                    .subtract(up.scale(GloveRest.BELOW));
            case OVER_THE_PALM -> SpraySource.inView(eye, xRot, yRot, gloveRight, StackHover.KNIFE_RISE_ABOVE_THE_PALM);
            case ABOVE_THE_HAND -> SpraySource.inView(eye, xRot, yRot, gloveRight);
        };
    }

    /**
     * The rest point of a caster drawn in third person: above the drawn palm
     * along the drawn model's up, by the rise the rest names; Bore's blob
     * stands above the palm as every other ability's does.
     * decision third-person-blob-stands-above-the-drawn-palm
     *
     * @param palm the drawn palm
     * @param rest the rest the selected ability lifts its blob to
     * @return the rest point
     */
    static Vec3 aboveTheDrawnPalm(DrawnPalm.Palm palm, Rest rest) {
        return palm.above(rest == Rest.OVER_THE_PALM ? StackHover.KNIFE_RISE_ABOVE_THE_PALM
                : SpraySource.RISE_ABOVE_THE_PALM);
    }

    /**
     * A caster's rest point this frame: in the camera's view for the local
     * player in first person, above the drawn palm for a caster drawn in
     * third person, and the glove the server sent for a caster this client
     * does not hold.
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
        Rest rest = Rest.of(selectedAbility(caster));
        boolean gloveRight = GooCasters.casterOnTheRight(caster);
        if (DrawnPalm.drawnInThirdPerson(mc, caster)) {
            return aboveTheDrawnPalm(DrawnPalm.of(caster, gloveRight, partialTick), rest);
        }
        if (rest == Rest.OVER_THE_PALM) {
            return SpraySource.of(casterId, sentGlove, partialTick, StackHover.KNIFE_RISE_ABOVE_THE_PALM);
        }
        if (rest == Rest.ABOVE_THE_HAND) {
            return SpraySource.of(casterId, sentGlove, partialTick);
        }
        Camera camera = mc.gameRenderer.mainCamera();
        return inView(camera.position(), camera.xRot(), camera.yRot(), gloveRight, rest);
    }

    /**
     * The synced copy of the ability a caster's glove, or raw goo held bare
     * handed, selects.
     * decision raw-goo-items-work-bare-handed-for-the-held-type
     *
     * @param caster the caster
     * @return the ability, or null with no caster, no selection or no synced copy
     */
    static @Nullable ClientAbility selectedAbility(Player caster) {
        GloveSelection selection = GooCasters.heldSelection(caster);
        return selection == null ? null : AbilitySyncHandler.findAbility(selection.abilityId());
    }
}
