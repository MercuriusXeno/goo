package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.TravelForm;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.item.GooGloveItem;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * How far the lifted palm blob has become its ability's travel form in the
 * hand: Dart's blob turns into the javelin as the press lifts it off the
 * palm, so the thrown goo entity leaves the hand already a dart. Only a
 * delivery whose form morphs in the hand does this; every other ability's
 * blob stays a cuboid.
 * shriek-bloom-portal-mitosis-and-dart-still-throw-a-blob
 */
public final class PalmJavelin {

    private PalmJavelin() {
    }

    /**
     * The share of the travel form the palm blob shows at a share lifted:
     * none on the palm, the whole form fully lifted, and none for a delivery
     * whose form does not morph in the hand.
     *
     * @param delivery  the selected ability's delivery, or null for none
     * @param liftShare the share the blob has lifted off the palm, 0 to 1
     * @return the share of the form, 0 the cuboid to 1 the form whole
     */
    public static float morphAt(@Nullable Delivery delivery, float liftShare) {
        if (delivery == null || !delivery.morphsInHand()) {
            return 0f;
        }
        return Math.clamp(liftShare, 0f, 1f);
    }

    /**
     * The travel form a delivery's palm blob becomes.
     *
     * @param delivery the selected ability's delivery, or null for none
     * @return its form, the blob for none
     */
    static TravelForm formOf(@Nullable Delivery delivery) {
        return delivery == null ? TravelForm.BLOB : delivery.form();
    }

    /**
     * The delivery of the ability a glove has selected, as this client holds it.
     *
     * @param glove the glove
     * @return the delivery, or null where the glove selects no ability this client holds
     */
    static @Nullable Delivery selectedDelivery(ItemStack glove) {
        var selection = GooGloveItem.getSelection(glove);
        AbilitySyncHandler.ClientAbility ability = selection == null ? null
                : AbilitySyncHandler.findAbility(selection.abilityId());
        return ability == null ? null : ability.delivery();
    }
}
