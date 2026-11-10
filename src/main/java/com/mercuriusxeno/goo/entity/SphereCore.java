package com.mercuriusxeno.goo.entity;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import java.util.Locale;

/**
 * The core a compression sphere draws, which tells who left it: the black
 * hole's dark maroon, or a large Blast's neon green.
 * large-blast-gathers-its-mined-drops-into-a-green-cored-sphere
 */
public enum SphereCore implements StringRepresentable {
    /** The black hole's sphere. */
    MAROON("textures/entity/compression_sphere.png"),
    /** A large Blast's sphere. */
    GREEN("textures/entity/compression_sphere_green.png");

    /**
     * Codec writing the core by its lowercase name.
     */
    public static final Codec<SphereCore> CODEC = StringRepresentable.fromEnum(SphereCore::values);

    private final Identifier texture;

    SphereCore(String texturePath) {
        this.texture = Identifier.fromNamespaceAndPath(Goo.MODID, texturePath);
    }

    /**
     * @return the texture the sphere draws with this core
     */
    public Identifier texture() {
        return texture;
    }

    /**
     * The core a synched ordinal names, maroon for one out of range.
     *
     * @param ordinal the ordinal the entity data carries
     * @return the core
     */
    public static SphereCore byOrdinal(int ordinal) {
        SphereCore[] cores = values();
        return ordinal >= 0 && ordinal < cores.length ? cores[ordinal] : MAROON;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
