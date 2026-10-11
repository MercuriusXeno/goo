package com.mercuriusxeno.goo.ability;

/**
 * The poses an ability holds the glove arm in on the player's model, in
 * place of the swing: a slinging charge winds the glove hand up to the
 * opposite shoulder while it is held, and flings it outward on release.
 * The order is synced, so a pose is appended last.
 * decision shards-sling-then-morph-to-flechettes
 */
public enum ArmPoseKind {
    /** The arm hangs as vanilla poses it. */
    NONE,
    /** The glove hand drawn across to the opposite shoulder, a charge being held. */
    WIND_UP,
    /** The glove arm sweeping out from the opposite shoulder to the glove side, a charge let go. */
    FLING
}
