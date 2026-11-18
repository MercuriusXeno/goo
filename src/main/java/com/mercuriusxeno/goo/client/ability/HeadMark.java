package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import java.util.Optional;

/**
 * The icon floating over a marked mob's head: Charm's dark purple heart or
 * Lead's red clenched fist. A mob both charmed and led wears the heart,
 * the charm being the one that turns it.
 * charm-glisten-and-icon-over-the-head
 * rallied-mobs-wear-a-red-clenched-fist
 */
public enum HeadMark {
    /** Charm's dark purple heart. */
    CHARMED_HEART("textures/entity/charmed_heart.png"),
    /** Lead's red clenched fist. */
    LEAD_FIST("textures/entity/lead_fist.png");

    /** The texture's path, held as a string so the enum carries no unserializable instance field. */
    private final String texturePath;

    HeadMark(String texturePath) {
        this.texturePath = texturePath;
    }

    /** @return the texture the icon draws */
    public Identifier texture() {
        return Identifier.fromNamespaceAndPath(Goo.MODID, texturePath);
    }

    /**
     * The mark a mob wears over its head.
     *
     * @param charmed whether the mob holds a charm
     * @param led whether the mob holds a lead
     * @return the mark, empty for a mob holding neither
     */
    static Optional<HeadMark> of(boolean charmed, boolean led) {
        if (charmed) {
            return Optional.of(CHARMED_HEART);
        }
        return led ? Optional.of(LEAD_FIST) : Optional.empty();
    }
}
