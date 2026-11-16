package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.FormSounds;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import java.util.Optional;

/**
 * A turn the javelin a held throw's press forms takes in the hand.
 * decision dart-forms-on-right-click-and-throws-on-left-click
 */
public enum JavelinTurn {
    /** The palm blob begins forming a javelin: the press's first held tick, or the first after a throw. */
    FORMS,
    /** The javelin, whole, leaves the hand on the attack key. */
    THROWN,
    /** Releasing right click begins reverting the javelin, formed or forming, to the palm blob. */
    REVERTS;

    /**
     * The sound this turn plays: the form's shink as a javelin begins
     * forming, the revert's as one begins reverting, and none for a throw.
     * decision dart-shinks-as-it-forms-and-pitched-down-as-it-reverts
     *
     * @param sounds the sounds the selected delivery's form names
     * @return the sound, empty for none
     */
    public Optional<SoundCue> soundOf(FormSounds sounds) {
        return switch (this) {
            case FORMS -> sounds.form();
            case REVERTS -> sounds.revert();
            case THROWN -> Optional.empty();
        };
    }
}
