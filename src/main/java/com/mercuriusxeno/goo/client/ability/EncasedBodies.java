package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.witch.WitchModel;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * The body an encasement draws over a mob whose model holds a part its skin
 * leaves bare. The base pass cuts a bare part away by its skin's alpha, while
 * the encasement lays its block texture on every face it is handed, so it
 * draws such a part as a solid box no one sees on the mob; a mob holding one
 * is encased over its own copy of the model with the part hidden. Every other
 * mob is encased over the model its renderer draws.
 * diagnose-and-correct-the-floating-hat-brim-box
 */
public final class EncasedBodies {

    private static final String HAT = "hat";
    /** The rim VillagerModel's hat carries, which WitchModel's hat inherits and witch.png leaves bare. */
    private static final String HAT_RIM = "hat_rim";

    private EncasedBodies() {
    }

    /**
     * The body an encasement draws over a mob type.
     *
     * @param type   the mob type
     * @param models the entity model set
     * @return the body, or null where the encasement draws the renderer's own model
     */
    public static @Nullable EntityModel<?> of(EntityType<?> type, EntityModelSet models) {
        if (type == EntityType.WITCH) {
            return withBareRimHidden(new WitchModel(models.bakeLayer(ModelLayers.WITCH)));
        }
        return null;
    }

    /**
     * A witch's model with its hat's bare rim hidden: WitchModel replaces
     * VillagerModel's hat, and PartDefinition.addOrReplaceChild carries the
     * replaced hat's children onto it, so the witch's hat holds the villager's
     * sixteen-wide rim, whose texels witch.png leaves clear.
     *
     * @param witch a witch model of the encasement's own
     * @return the same model, its hat drawing only the parts the skin covers
     */
    static WitchModel withBareRimHidden(WitchModel witch) {
        witch.getHead().getChild(HAT).getChild(HAT_RIM).visible = false;
        return witch;
    }

    /**
     * The cubes of a model its skin draws, for a fill laid over the posed
     * model, the aimed mob's ripple among them: every cube but a part the
     * skin leaves bare, which would fill as a box no one sees on the mob.
     *
     * @param model the model the mob's renderer draws, posed
     * @return its cubes, a witch's bare hat rim left out
     */
    static List<ModelRay.CubeBox> drawnCubes(Model<?> model) {
        List<ModelRay.CubeBox> cubes = ModelRay.cubesOf(model.root());
        if (!(model instanceof WitchModel)) {
            return cubes;
        }
        // diagnose-and-correct-the-floating-hat-brim-box
        return cubes.stream().filter(cube -> !cube.partPath().endsWith(HAT_RIM)).toList();
    }
}
