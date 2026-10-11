package com.mercuriusxeno.goo.client.entity;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.entity.FeedPile;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

/**
 * Draws Jelly's feed as a glob of amber jelly flecked with crumbs, a third of
 * a block wide, bobbing and spinning like a dropped item and faintly lit, so
 * it reads on the ground at dusk (decision feed-blob-feeds-and-draws-mobs).
 */
public final class FeedPileRenderer extends BobbingDiscRenderer<FeedPile> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Goo.MODID, "textures/entity/feed_pile.png");
    private static final float SIZE = 0.35f;
    private static final int GLOW_LEVELS = 3;

    /**
     * @param context the renderer context
     */
    public FeedPileRenderer(EntityRendererProvider.Context context) {
        super(context, TEXTURE, SIZE, GLOW_LEVELS);
    }
}