package com.mercuriusxeno.goo.client.entity;

import com.mercuriusxeno.goo.entity.EldritchMonstrosity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Draws an Eldritch Monstrosity as a drowned-hued humanoid, scaled up; it
 * stands out of phase, so EldritchSight hides it from a plain viewer and
 * warps it for an eldritch one. It casts no shadow, which would betray it
 * to the plain.
 * monstrosities-harvest-under-quantum
 */
public class EldritchMonstrosityRenderer
        extends HumanoidMobRenderer<EldritchMonstrosity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/zombie/drowned.png");
    /** The monstrosity stands a third taller than the humanoid model it wears. */
    private static final float SCALE = 1.33f;

    /**
     * Creates the renderer.
     *
     * @param context the renderer provider context
     */
    public EldritchMonstrosityRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0f);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }

    @Override
    public void extractRenderState(EldritchMonstrosity monstrosity, HumanoidRenderState state, float partialTick) {
        super.extractRenderState(monstrosity, state, partialTick);
        state.scale = SCALE;
    }
}
