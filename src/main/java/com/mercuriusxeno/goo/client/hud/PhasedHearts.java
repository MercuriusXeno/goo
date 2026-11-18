package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.xeno.EldritchEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

/**
 * A phased player's own hearts: each normal heart draws as vanilla's heart
 * in blue and see-through over its empty container, in place of the red, so
 * a phased player can tell in first person. Poisoned, withered and frozen
 * hearts keep their own look (GuiPhasedHeartsMixin).
 * phased-things-draw-as-a-ghostly-image
 */
public final class PhasedHearts {

    private static final Identifier FULL = Identifier.fromNamespaceAndPath(Goo.MODID, "hud/heart/phased_full");
    private static final Identifier HALF = Identifier.fromNamespaceAndPath(Goo.MODID, "hud/heart/phased_half");

    private PhasedHearts() {
    }

    /**
     * Whether the local player's hearts draw phased.
     *
     * @return true while the local player stands out of phase
     */
    public static boolean drawPhased() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && EldritchEvents.isOutOfPhase(player);
    }

    /**
     * The phased heart's sprite.
     *
     * @param half whether the heart is a half heart
     * @return the whole or half phased heart
     */
    public static Identifier sprite(boolean half) {
        return half ? HALF : FULL;
    }
}
