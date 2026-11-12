package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.ConjuredDoor;

/**
 * The starfield cover's life on the client entering the pocket: it holds
 * from the moment the local caster's own door reaches their feet, through
 * the level change, until the room reads ready, and both the drawing and
 * the screen shown for the change read it. Another caster's door covers
 * nothing. A door reaching its caster's feet with no change following, an
 * entry the server refused, clears on its own after a short wait.
 * decision doorway-starfield-fills-the-view-until-the-room-is-ready
 */
public final class EntryCover {

    /** The cover the local player's entry holds. */
    public static final EntryCover LOCAL = new EntryCover();
    /** Client ticks the cover waits for the level change before letting go. */
    static final int WAITS_FOR_CHANGE = 20;

    private enum Stage {
        /** No cover stands. */
        CLEAR,
        /** The door enveloped the caster; the level change has not come. */
        AWAITING_CHANGE,
        /** The level changed; the room is not ready yet. */
        AWAITING_ROOM
    }

    private Stage stage = Stage.CLEAR;
    private int waitLeft;

    /**
     * Reads a door the server sent: the local caster's door on their feet
     * raises the cover.
     *
     * @param casterId the door's caster's entity id
     * @param localId  the local player's entity id
     * @param door     the door
     */
    public void doorSeen(int casterId, int localId, ConjuredDoor door) {
        if (casterId == localId && door.crossed() >= 1 && stage == Stage.CLEAR) {
            stage = Stage.AWAITING_CHANGE;
            waitLeft = WAITS_FOR_CHANGE;
        }
    }

    /**
     * Marks the level change the cover was waiting on, so it holds until the room is ready.
     */
    public void levelChanged() {
        if (stage != Stage.CLEAR) {
            stage = Stage.AWAITING_ROOM;
        }
    }

    /**
     * Lets the cover go, the room ready or the screen holding it gone.
     */
    public void clear() {
        stage = Stage.CLEAR;
    }

    /**
     * Counts one client tick toward letting go of a cover no change followed.
     */
    public void tick() {
        if (stage != Stage.AWAITING_CHANGE) {
            return;
        }
        waitLeft--;
        if (waitLeft <= 0) {
            stage = Stage.CLEAR;
        }
    }

    /**
     * @return true while the cover stands
     */
    public boolean covering() {
        return stage != Stage.CLEAR;
    }
}
