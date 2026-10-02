package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.animal.sheep.SheepFurModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * The outer shells some mobs wear over their body, drawn by layers of their
 * own: a sheep's wool, a slime's outer gel. A splat on such a mob is cast
 * against and painted on the shell while it shows, so the shell never buries
 * it; every other mob's splat sits on its body.
 * Decision shader-coat-on-every-mob-landing.
 */
public final class MobShells {

    private MobShells() {
    }

    /**
     * Picks the shell a mob shows this frame.
     */
    @FunctionalInterface
    public interface Shell {

        /**
         * @param state the mob's render state
         * @return the shell model it shows, or null where it shows none
         */
        @Nullable EntityModel<?> shown(LivingEntityRenderState state);
    }

    /** The shell of a mob that wears none. */
    public static final Shell NONE = state -> null;

    /**
     * The shell a mob type wears, baked from the same layer definitions its
     * own shell layer draws, so the splat sits exactly on what is drawn.
     *
     * @param type   the mob type
     * @param models the entity model set
     * @return its shell, or NONE
     */
    public static Shell of(EntityType<?> type, EntityModelSet models) {
        if (type == EntityType.SHEEP) {
            SheepFurModel adultWool = new SheepFurModel(models.bakeLayer(ModelLayers.SHEEP_WOOL));
            SheepFurModel babyWool = new SheepFurModel(models.bakeLayer(ModelLayers.SHEEP_BABY_WOOL));
            return state -> woolShown(state, adultWool, babyWool);
        }
        if (type == EntityType.SLIME) {
            SlimeModel outerGel = new SlimeModel(models.bakeLayer(ModelLayers.SLIME_OUTER));
            return state -> outerGel;
        }
        return NONE;
    }

    /**
     * The wool a sheep shows, as SheepWoolLayer draws it: its age's wool
     * until it is sheared, then none.
     *
     * @param state     the sheep's render state
     * @param adultWool the adult wool model
     * @param babyWool  the baby wool model
     * @return the wool it shows, or null once sheared
     */
    static @Nullable EntityModel<?> woolShown(LivingEntityRenderState state, EntityModel<?> adultWool,
            EntityModel<?> babyWool) {
        if (!(state instanceof SheepRenderState sheep) || sheep.isSheared) {
            return null;
        }
        return sheep.isBaby ? babyWool : adultWool;
    }
}
