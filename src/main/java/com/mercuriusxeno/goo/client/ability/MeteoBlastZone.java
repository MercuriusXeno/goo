package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.program.MeteorStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import java.util.List;

/**
 * Meteo's blast zone, drawn at the aimed target while its charge holds: the
 * held dome's rings alone, rippling over the aimed surface out to the reach
 * the meteor's explosion cuts, depth tested and on the clock, with no sphere
 * shell, Meteo alone among the held ghosts departing from the whole sphere.
 * decision meteo-blast-zone-is-ground-rings-only
 * decision active-target-indicators-carry-depth-and-animation
 */
public final class MeteoBlastZone implements HeldGhostVisual {

    /** The one instance the held dome's registry holds. */
    public static final MeteoBlastZone INSTANCE = new MeteoBlastZone();

    private MeteoBlastZone() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.ASTRAL;
    }

    /**
     * Rings travelling out to the meteor's blast reach, falling back to the
     * area's size where the program calls no meteor of a fixed power.
     *
     * @param area      the ability's synced area
     * @param behaviors the ability's synced program
     * @return the ghost
     */
    @Override
    public HeldGhost ghost(AbilityArea area, List<Step> behaviors) {
        float reach = SyncedSteps.first(behaviors, MeteorStep.class)
                .filter(step -> step.power().variables().isEmpty())
                .map(step -> (float) step.reachAt(Variables.NONE)).orElse((float) area.size());
        return HeldGhost.outwardTo(reach);
    }

    /** No layer: the zone draws its rings alone. */
    @Override
    public List<HeldLayer> heldLayers() {
        return List.of();
    }
}
