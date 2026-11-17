package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.resources.Identifier;
import java.util.List;

/**
 * An ability as the radial offers it: the synced ability, and every item it
 * requires with whether the player has learned it. While any is unlearned,
 * the ability stays on the wheel as a locked petal the key release refuses,
 * listing every required item and crossing off the learned ones.
 * decision locked-petal-stays-on-the-wheel
 * decision locked-petal-lists-the-unlearned-items
 * End in the End, for a player who came by no End gate, is grayed out: its
 * petal takes the locked petal's dimmed look, no panel draws for it and the
 * key release refuses it.
 * decision end-is-grayed-out-in-the-end-unless-you-came-by-the-ability
 *
 * @param ability   the synced ability
 * @param required  every item the ability requires, in the order it names them
 * @param grayedOut whether the ability stands grayed out where the player stands
 */
public record OfferedAbility(ClientAbility ability, List<RequiredItem> required, boolean grayedOut) {

    /**
     * One item an ability requires, and whether the player has learned it.
     *
     * @param item    the item's id
     * @param learned true once the player knows the item
     */
    public record RequiredItem(Identifier item, boolean learned) {
    }

    /**
     * Freezes the required items.
     *
     * @param ability   the synced ability
     * @param required  every item the ability requires
     * @param grayedOut whether the ability stands grayed out where the player stands
     */
    public OfferedAbility {
        required = List.copyOf(required);
    }

    /**
     * An ability standing available wherever the player stands.
     *
     * @param ability  the synced ability
     * @param required every item the ability requires
     */
    public OfferedAbility(ClientAbility ability, List<RequiredItem> required) {
        this(ability, required, false);
    }

    /**
     * Whether the player still lacks an item the ability requires.
     *
     * @return true while any required item is unlearned
     */
    public boolean locked() {
        return required.stream().anyMatch(item -> !item.learned());
    }

    /**
     * Whether the radial refuses the ability: locked, or grayed out where the player stands.
     *
     * @return true where the key release refuses it and no panel draws for it
     */
    public boolean refused() {
        return locked() || grayedOut;
    }
}
