package com.mercuriusxeno.goo.ability.root;

import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.LivingEntity;

/**
 * Who Vines goo spares: a thing under Barkskin, the heart effect standing
 * on it, which no cell gives vines and whose held vines end as Barkskin
 * begins.
 * barkskin-makes-you-immune-to-vines
 */
public final class VineImmunity {

    private VineImmunity() {
    }

    /**
     * Whether the heart effect standing on a thing spares it from Vines.
     *
     * @param overlay the heart effect standing on the thing, {@link HeartOverlay#NONE} for none
     * @return true where Barkskin stands
     */
    public static boolean spares(HeartOverlay overlay) {
        return overlay.stands() && overlay.kind() == HeartKind.BARKSKIN;
    }

    /**
     * Whether Vines spares a living thing.
     *
     * @param living the thing
     * @return true where Barkskin stands on it
     */
    static boolean spares(LivingEntity living) {
        return living.hasData(GooAttachments.HEART_OVERLAY) && spares(living.getData(GooAttachments.HEART_OVERLAY));
    }
}
