package com.mercuriusxeno.goo.client.ability;

import org.jspecify.annotations.Nullable;
import java.util.Map;

/**
 * The client's one registry of spray visuals, each under its ability's id, so whatever casts a spray, the glove or
 * a nozzle, hands the ability's visual the cone it pours through.
 * decision spray-abilities-render-procedurally-bound-to-their-cone
 * decision cone-style-abilities-are-reviewed-for-consistency
 */
public final class ConeVisuals {

    private static final String COLD_ID = "goo:frost_cold";
    private static final String GUST_ID = "goo:typhoon_gust";
    private static final String VACUUM_ID = "goo:void_vacuum";

    private static final Map<String, ConeVisual> VISUALS = Map.ofEntries(
            visual(FireStream.FLAMES_ID, cone -> {
                FireStream.pour(cone);
                HeatWaveEffect.pour(cone);
            }),
            visual(COLD_ID, cone -> WindLines.blowLocal(COLD_ID, cone)),
            visual(GUST_ID, cone -> WindLines.blowLocal(GUST_ID, cone)),
            visual(VACUUM_ID, cone -> WindLines.blowLocal(VACUUM_ID, cone)),
            visual(PetrifyFog.PETRIFY_ID, PetrifyFog::pour),
            visual(HealVisual.HEAL_ID, HealVisual::drawFogLocal),
            visual(MiasmaVortex.DRAIN_ID, MiasmaVortex::pour),
            visual(GrowthBreeze.GROWTH_ID, GrowthBreeze::pour),
            visual(SignalRings.TICK_ID, SignalRings::pour),
            visual(BoreDrill.BORE_ID, BoreDrill::pour),
            visual(GnatSeek.DECAY_ID, GnatSeek::pour));

    private ConeVisuals() {
    }

    private static Map.Entry<String, ConeVisual> visual(String abilityId, ConeVisual visual) {
        return Map.entry(abilityId, visual);
    }

    /**
     * The visual an ability draws from its cone.
     *
     * @param abilityId the ability's id
     * @return the visual, or null for an ability no cone visual draws
     */
    public static @Nullable ConeVisual visualFor(String abilityId) {
        return VISUALS.get(abilityId);
    }

    /**
     * Hands an ability's visual the cone it pours through this frame.
     *
     * @param abilityId the ability's id
     * @param cone      the cone poured through
     */
    public static void pour(String abilityId, SprayCone cone) {
        ConeVisual visual = visualFor(abilityId);
        if (visual != null) {
            visual.pour(cone);
        }
    }
}
