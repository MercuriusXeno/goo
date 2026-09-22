package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.BlobModelSize;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Select item model property keyed by the GOO_TYPE component: answers the
 * blob sprite the stack's type names for one model size (decision
 * type-named-textures). The item model's cases map each bundled sprite to
 * its model; a type naming no stitched sprite answers null, which selects
 * the fallback grey base tinted by the type's highlight color.
 *
 * @param size the blob model size whose sprite is answered
 */
public record BlobTextureProperty(BlobModelSize size) implements SelectItemModelProperty<Identifier> {

    /**
     * Property type the item model JSON names as {@code goo:blob_texture}.
     */
    public static final SelectItemModelProperty.Type<BlobTextureProperty, Identifier> TYPE =
        SelectItemModelProperty.Type.create(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                BlobModelSize.CODEC.fieldOf("size").forGetter(BlobTextureProperty::size)
            ).apply(instance, BlobTextureProperty::new)),
            Identifier.CODEC);

    @Override
    public @Nullable Identifier get(@NonNull ItemStack itemStack, @Nullable ClientLevel level,
                                    @Nullable LivingEntity owner, int seed,
                                    @NonNull ItemDisplayContext displayContext) {
        ResourceKey<GooTypeDefinition> key = itemStack.get(GooDataComponents.GOO_TYPE.get());
        GooTypeDefinition definition = key == null ? null : ClientGooTypes.definition(key);
        GooTypeSprites.TypeSprite sprite = GooTypeSprites.blob(
            definition == null ? null : definition.textures(), size, BlobTextureProperty::isItemSpriteStitched);
        return sprite.tinted() ? null : sprite.sprite();
    }

    @Override
    public SelectItemModelProperty.@NonNull Type<BlobTextureProperty, Identifier> type() {
        return TYPE;
    }

    @Override
    public @NonNull Codec<Identifier> valueCodec() {
        return Identifier.CODEC;
    }

    private static boolean isItemSpriteStitched(Identifier spriteId) {
        TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS);
        return atlas.getSprite(spriteId) != atlas.missingSprite();
    }
}
