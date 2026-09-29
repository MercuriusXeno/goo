package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.type.GooModelSize;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
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
 * goo sprite the stack's type names for one model size (decision
 * type-named-textures). The item model's cases map each bundled sprite to
 * its model; a type naming no stitched sprite answers null, which selects
 * the fallback grey base tinted by the type's highlight color.
 *
 * @param size the goo model size whose sprite is answered
 */
public record GooTextureProperty(GooModelSize size) implements SelectItemModelProperty<Identifier> {

    /**
     * Property type the item model JSON names as {@code goo:goo_texture}.
     */
    public static final SelectItemModelProperty.Type<GooTextureProperty, Identifier> TYPE =
        SelectItemModelProperty.Type.create(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                GooModelSize.CODEC.fieldOf("size").forGetter(GooTextureProperty::size)
            ).apply(instance, GooTextureProperty::new)),
            Identifier.CODEC);

    @Override
    public @Nullable Identifier get(@NonNull ItemStack itemStack, @Nullable ClientLevel level,
                                    @Nullable LivingEntity owner, int seed,
                                    @NonNull ItemDisplayContext displayContext) {
        ResourceKey<GooTypeDefinition> key = itemStack.get(GooDataComponents.GOO_TYPE.get());
        GooTypeDefinition definition = key == null ? null : ClientGooTypes.definition(key);
        GooTypeSprites.TypeSprite sprite = GooTypeSprites.goo(
            definition == null ? null : definition.textures(), size, GooTextureProperty::isItemSpriteStitched);
        return sprite.tinted() ? null : sprite.sprite();
    }

    @Override
    public SelectItemModelProperty.@NonNull Type<GooTextureProperty, Identifier> type() {
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
