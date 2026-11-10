package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import java.util.Optional;

/**
 * The icon floating over a marked mob's head: Charm's dark purple heart or
 * Rally's red clenched fist. A mob both charmed and rallied wears the heart,
 * the charm being the one that turns it.
 * charm-glisten-and-icon-over-the-head
 * rallied-mobs-wear-a-red-clenched-fist
 */
public enum HeadMark {
    /** Charm's dark purple heart. */
    CHARMED_HEART("textures/entity/charmed_heart.png"),
    /** Rally's red clenched fist. */
    RALLY_FIST("textures/entity/rally_fist.png");

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
     * @param rallied whether the mob holds a rally
     * @return the mark, empty for a mob holding neither
     */
    static Optional<HeadMark> of(boolean charmed, boolean rallied) {
        if (charmed) {
            return Optional.of(CHARMED_HEART);
        }
        return rallied ? Optional.of(RALLY_FIST) : Optional.empty();
    }
}
