package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;

/**
 * Where a petal's fluid, its edge and its type's color come from: the
 * block atlas and the synced registry in play, a fake under test.
 */
interface PetalLook {

    /** Gap between the hub circle and the petals, as a fraction of the wheel's radius. */
    double HUB_GAP = 0.02;

    /** The block atlas's live fluid sprites and the type colors the client level holds. */
    PetalLook LIVE = new PetalLook() {
        @Override
        public FluidFace fluidFace(ResourceKey<GooTypeDefinition> type) {
            TextureAtlasSprite sprite = GooSubmitter.fluidSprite(type);
            AbstractTexture atlas = Minecraft.getInstance().getTextureManager().getTexture(sprite.atlasLocation());
            return new FluidFace(TextureSetup.singleTexture(atlas.getTextureView(), atlas.getSampler()),
                    new SpriteBox(sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1()),
                    GooSubmitter.fluidTint(type), ARGB.opaque(ClientGooTypes.edge(type)));
        }

        @Override
        public Identifier hubMask() {
            return RadialTextures.getHubTexture(RadialWheel.HUB_FRACTION - HUB_GAP);
        }

        @Override
        public ItemStack itemStack(Identifier item) {
            return BuiltInRegistries.ITEM.getValue(item).getDefaultInstance();
        }

        @Override
        public TextureSetup sprite(Identifier texture) {
            AbstractTexture loaded = Minecraft.getInstance().getTextureManager().getTexture(texture);
            return TextureSetup.singleTexture(loaded.getTextureView(), loaded.getSampler());
        }
    };

    /**
     * The texture a whole-file sprite, such as an ability icon or a badge,
     * draws from when it is cut at a petal's border.
     * decision icons-slide-in-from-behind-the-tip
     *
     * @param texture the sprite's texture file
     * @return the texture bound for drawing
     */
    TextureSetup sprite(Identifier texture);

    /**
     * What a type's petal draws its fill and edge with: the still fluid
     * sprite on the block atlas, which the atlas animates, under the fluid's
     * tint, inside an edge of the type's color.
     * decision petals-render-the-live-fluid
     *
     * @param type the petal's type
     * @return the fluid face
     */
    FluidFace fluidFace(ResourceKey<GooTypeDefinition> type);

    /**
     * The hub circle's mask.
     *
     * @return the mask texture
     */
    Identifier hubMask();

    /**
     * The stack a locked petal draws for one item it still needs, and whose
     * hover name names it.
     * decision locked-petal-lists-the-unlearned-items
     *
     * @param item the item's id
     * @return the item's default stack
     */
    ItemStack itemStack(Identifier item);

    /**
     * A sprite's rectangle on its atlas, in UV units.
     *
     * @param u0 the left edge
     * @param u1 the right edge
     * @param v0 the top edge
     * @param v1 the bottom edge
     */
    record SpriteBox(float u0, float u1, float v0, float v1) {

        /**
         * The atlas u at a fraction across the sprite.
         *
         * @param fraction 0 at the left edge, 1 at the right
         * @return the atlas u
         */
        float u(double fraction) {
            return (float) (u0 + (u1 - u0) * fraction);
        }

        /**
         * The atlas v at a fraction down the sprite.
         *
         * @param fraction 0 at the top edge, 1 at the bottom
         * @return the atlas v
         */
        float v(double fraction) {
            return (float) (v0 + (v1 - v0) * fraction);
        }
    }

    /**
     * A petal's fill and edge sources.
     *
     * @param texture   the atlas the sprite sits on
     * @param sprite    the sprite's rectangle on that atlas
     * @param tint      the ARGB tint the sprite renders under
     * @param edgeColor the opaque ARGB color of the petal's edge
     */
    record FluidFace(TextureSetup texture, SpriteBox sprite, int tint, int edgeColor) {
    }
}
