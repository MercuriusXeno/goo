package com.mercuriusxeno.goo.client;

import java.util.Map;
import static java.util.Map.entry;

/**
 * The stays-on-parity list: each goo pipeline that blends in the level and draws on its parity path rather than
 * through an OIT set of its own, by its location under goo:pipeline/, with the reason it stays. A pipeline drawn
 * immediately never reaches the OIT phases, so it counts by its entry here, never by a set it would not use.
 * decision goo-translucency-draws-through-order-independent-transparency
 */
final class GooOitStays {

    static final String FAR_TO_NEAR =
            "drawn far to near on the sorting path, until the task on this thread that moves it lands";
    static final String THROUGH_BLOCKS = "drawn through blocks under a standing decision";
    static final String DECAL = "a decal on an opaque face";
    static final String WRITES_DEPTH = "writes its own depth";
    static final String WITH_THE_SKY = "drawn with the sky";
    static final String POST_EFFECT = "a post effect";

    /** Each staying pipeline's reason. */
    static final Map<String, String> REASONS = Map.ofEntries(
            entry("block_mingle_translucent", FAR_TO_NEAR),
            entry("bore_drill", FAR_TO_NEAR),
            entry("crystal_explosion", FAR_TO_NEAR),
            entry("crystal_explosion_through_blocks", THROUGH_BLOCKS),
            entry("dowsing_glow", THROUGH_BLOCKS),
            entry("dowsing_ore", THROUGH_BLOCKS),
            entry("fire_field", WRITES_DEPTH),
            entry("frost_explosion_through_blocks", THROUGH_BLOCKS),
            entry("fungus_glow", THROUGH_BLOCKS),
            entry("fungus_xray", THROUGH_BLOCKS),
            entry("glow_explosion", FAR_TO_NEAR),
            entry("goo_fluid", WRITES_DEPTH),
            entry("goo_portal_halo", FAR_TO_NEAR),
            entry("goo_portal_idle", FAR_TO_NEAR),
            entry("goo_portal_swirl_band", FAR_TO_NEAR),
            entry("goo_portal_view", FAR_TO_NEAR),
            entry("goo_ripple_edge", FAR_TO_NEAR),
            entry("goo_soft_outline", FAR_TO_NEAR),
            entry("laser_scorch", DECAL),
            entry("laser_scorch_glow", DECAL),
            entry("leaf_ghost", FAR_TO_NEAR),
            entry("leaf_ghost_through_blocks", THROUGH_BLOCKS),
            entry("lunar_moon", THROUGH_BLOCKS),
            entry("lunar_planet", WITH_THE_SKY),
            entry("lunar_sky_veil", WITH_THE_SKY),
            entry("lunar_trip_planet", THROUGH_BLOCKS),
            entry("metal_explosion", FAR_TO_NEAR),
            entry("metal_explosion_through_blocks", THROUGH_BLOCKS),
            entry("meteor_scorch", DECAL),
            entry("miasma_vortex", FAR_TO_NEAR),
            entry("pocket_sky", WITH_THE_SKY),
            entry("portable_hole_carve", WRITES_DEPTH),
            entry("portable_hole_mouth", FAR_TO_NEAR),
            entry("portable_hole_pit", WRITES_DEPTH),
            entry("rock_explosion_through_blocks", THROUGH_BLOCKS),
            entry("room_copy_translucent", FAR_TO_NEAR),
            entry("scry_faces", THROUGH_BLOCKS),
            entry("sculker_glass", POST_EFFECT),
            entry("spikes_steel", WRITES_DEPTH),
            entry("spore_shell", FAR_TO_NEAR),
            entry("spore_shell_through_blocks", THROUGH_BLOCKS),
            entry("unstable_explosion", FAR_TO_NEAR),
            entry("unstable_explosion_through_blocks", THROUGH_BLOCKS));

    private GooOitStays() {
    }
}
