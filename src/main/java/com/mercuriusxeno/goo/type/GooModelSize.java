package com.mercuriusxeno.goo.type;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * The four goo item model sizes, each with the grey base sprite a type
 * naming no goo texture for that size renders tinted (decision
 * type-named-textures).
 */
public enum GooModelSize implements StringRepresentable {
    /**
     * The micro goo model.
     */
    TINY("tiny", "item/goo_tiny"),
    /**
     * The goo model.
     */
    SMALL("small", "item/goo_small"),
    /**
     * The kilogoo model.
     */
    BASE("base", "item/goo_base"),
    /**
     * The megagoo model.
     */
    LARGE("large", "item/goo_large");

    /**
     * Codec spelling a size by its lower-case name.
     */
    public static final Codec<GooModelSize> CODEC = StringRepresentable.fromEnum(GooModelSize::values);

    private final String serializedName;
    private final Identifier greyBase;

    GooModelSize(String serializedName, String greyBasePath) {
        this.serializedName = serializedName;
        this.greyBase = Identifier.fromNamespaceAndPath(GooTypes.NAMESPACE, greyBasePath);
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
