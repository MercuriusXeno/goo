package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityTags;
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
import java.util.Optional;

/**
 * Where a caster's lifted palm blob rests, chosen by the selected ability's
 * data: on the center line ahead of the eye, settled below the crosshair so
 * it never covers it, for an ability tagged centered; at the glove rest for
 * Bore, which eases forward from there; and for every other ability at the
 * spray source Goop and Slick drip from, straight above where the hand rests.
 * Each point moves and turns with the caster's view and never bobs with the hand.
 * centered-abilities-float-the-blob-below-the-crosshair
 * palm-blob-lifts-as-the-spray-source
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

    /** The rests a lifted blob takes. */
    enum Rest {
        /** On the center line below the crosshair, for an ability tagged centered. */
        CENTER_LINE,
        /** At the glove rest, for Bore, whose drill extends from that hand. */
        BY_THE_GLOVE,
        /** Just over the palm, for Knives, whose knives rise from the hand and point up at the crosshair. */
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
            if (ability.hasTag(AbilityTags.CENTERED)) {
                return CENTER_LINE;
            }
            if (ability.delivery() != null && ability.delivery().formsKnives()) {
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
    static Vec3 inView(Vec3 eye, float xRot, float yRot, boolean gloveRight, Rest rest) {
        Vec3 ahead = Vec3.directionFromRotation(xRot, yRot);
        Vec3 up = Vec3.directionFromRotation(xRot - QUARTER_TURN, yRot);
        return switch (rest) {
            case CENTER_LINE -> eye.add(ahead.scale(CENTER_AHEAD)).subtract(up.scale(CENTER_BELOW));
            case BY_THE_GLOVE -> eye.add(ahead.scale(GloveRest.AHEAD))
                    .add(Vec3.directionFromRotation(0f, yRot + QUARTER_TURN)
                            .scale(gloveRight ? GloveRest.TO_THE_GLOVE_SIDE : -GloveRest.TO_THE_GLOVE_SIDE))
                    .subtract(up.scale(GloveRest.BELOW));
            case OVER_THE_PALM -> SpraySource.inView(eye, xRot, yRot, gloveRight, StackHover.KNIFE_RISE_ABOVE_THE_PALM);
            case ABOVE_THE_HAND -> SpraySource.inView(eye, xRot, yRot, gloveRight);
        };
    }

    /**
     * A caster's rest point this frame: in the camera's view for the local
     * player in first person, in the caster's own view otherwise, the spray
     * source above the hand where the selected ability lifts its blob there,
     * and the glove the server sent for a caster this client does not hold.
     *
     * @param casterId    the caster's entity id
     * @param sentGlove   the glove the held tick came with
     * @param partialTick the frame's partial tick
     * @return the rest point
     */
    public static Vec3 of(int casterId, Vec3 sentGlove, float partialTick) {
        return viewOf(casterId, partialTick).map(view -> {
            Rest rest = Rest.of(selectedAbility(view.caster()));
            if (rest == Rest.OVER_THE_PALM) {
                return SpraySource.of(casterId, sentGlove, partialTick, StackHover.KNIFE_RISE_ABOVE_THE_PALM);
            }
            return rest == Rest.ABOVE_THE_HAND ? SpraySource.of(casterId, sentGlove, partialTick)
                    : inView(view.eye(), view.xRot(), view.yRot(), GooCasters.casterOnTheRight(view.caster()),
                    rest);
        }).orElse(sentGlove);
    }

    /**
     * A caster's view this frame: the camera's for the local player in first
     * person, the caster's own otherwise.
     *
     * @param caster the caster
     * @param eye    the view's eye
     * @param xRot   the view's pitch in degrees
     * @param yRot   the view's yaw in degrees
     */
    private record View(Player caster, Vec3 eye, float xRot, float yRot) {
    }

    private static Optional<View> viewOf(int casterId, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(casterId) instanceof Player caster)) {
            return Optional.empty();
        }
        if (caster == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Camera camera = mc.gameRenderer.getMainCamera();
            return Optional.of(new View(caster, camera.position(), camera.xRot(), camera.yRot()));
        }
        return Optional.of(new View(caster, caster.getEyePosition(partialTick), caster.getViewXRot(partialTick),
                caster.getViewYRot(partialTick)));
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
