package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What a hasted player wears, read the same way by the glove's Haste and the
 * aeon brew: the aeon brew effect alone, with its icon and no particles, and
 * speed and mining speed above the player's base
 * (decision haste-stacks-speed-under-the-golden-overlay).
 */
final class HasteChecks {

    private static final String SHOULD_WEAR_ONLY_THE_BREW =
            "A hasted player should wear the aeon brew alone, iconed and without particles; wears %s";

    private HasteChecks() {
    }

    /**
     * Names what is wrong with the effects a hasted player wears.
     *
     * @param player the hasted player
     * @return empty when the aeon brew is its one effect, shown with its icon and no particles
     */
    static String onlyTheAeonBrew(ServerPlayer player) {
        Holder<MobEffect> brew = GooMobEffects.BREW_EFFECTS.get(GooTypes.AEON);
        MobEffectInstance worn = player.getEffect(brew);
        boolean alone = player.getActiveEffects().size() == 1;
        if (worn != null && alone && worn.showIcon() && !worn.isVisible()) {
            return "";
        }
        return String.format(SHOULD_WEAR_ONLY_THE_BREW, player.getActiveEffects());
    }

    /**
     * Whether the player moves and mines faster than its base.
     *
     * @param player the player
     * @return true when both speeds stand above their base
     */
    static boolean hasted(ServerPlayer player) {
        return aboveBase(player, Attributes.MOVEMENT_SPEED) && aboveBase(player, Attributes.BLOCK_BREAK_SPEED);
    }

    private static boolean aboveBase(ServerPlayer player, Holder<Attribute> attribute) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance != null && instance.getValue() > instance.getBaseValue();
    }
}
