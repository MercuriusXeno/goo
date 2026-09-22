package com.mercuriusxeno.goo;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * The four blob item model sizes, each with the grey base sprite a type
 * naming no blob texture for that size renders tinted (decision
 * type-named-textures).
 */
public enum BlobModelSize implements StringRepresentable {
    /**
     * The micro omniblob model.
     */
    TINY("tiny", "item/goo_blob_tiny"),
    /**
     * The blob model.
     */
    SMALL("small", "item/goo_blob_small"),
    /**
     * The kiloblob model.
     */
    BASE("base", "item/goo_blob_base"),
    /**
     * The megablob model.
     */
    LARGE("large", "item/goo_blob_large");

    /**
     * Codec spelling a size by its lower-case name.
     */
    public static final Codec<BlobModelSize> CODEC = StringRepresentable.fromEnum(BlobModelSize::values);

    private final String serializedName;
    private final Identifier greyBase;

    BlobModelSize(String serializedName, String greyBasePath) {
        this.serializedName = serializedName;
        this.greyBase = Identifier.fromNamespaceAndPath(Goo.MODID, greyBasePath);
    }

    /**
     * @return the grey base sprite of this size, on the item atlas
     */
    public Identifier greyBase() {
        return greyBase;
    }

    @Override
    public @NonNull String getSerializedName() {
        return serializedName;
    }
}
