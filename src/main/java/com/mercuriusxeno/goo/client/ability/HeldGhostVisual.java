package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import java.util.List;

/**
 * The ghost of a goo type's landing visual, drawn at the aim point while right
 * click is held: the landing's own dome at resting size through its own shader,
 * so a later change to the landing look carries into its ghost.
 * held-visual-ghosts-the-landing-in-two-passes
 */
public interface HeldGhostVisual {

    /**
     * @return the goo type whose landing this ghost draws
     */
    ResourceKey<GooTypeDefinition> gooType();

    /**
     * The ghost an ability of this type holds: its dome's radius and its rings.
     * By default the rings travel outward to the dome's radius, the area's size.
     *
     * @param area      the ability's synced area, a sphere
     * @param behaviors the ability's synced program
     * @return the ghost
     */
    default HeldGhost ghost(AbilityArea area, List<Step> behaviors) {
        return HeldGhost.outwardTo((float) area.size());
    }

    /**
     * @return the layers the ghost draws, in order, each in both passes
     */
    List<HeldLayer> heldLayers();
}
