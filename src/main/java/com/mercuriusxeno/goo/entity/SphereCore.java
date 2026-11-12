package com.mercuriusxeno.goo.entity;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import java.util.Locale;

/**
 * The core a compression sphere draws, which tells who left it: the black
 * hole's jet black, or a large Blast's neon green.
 * large-blast-gathers-its-mined-drops-into-a-green-cored-sphere
 * black-hole-item-ball-draws-jet-black
 */
public enum SphereCore implements StringRepresentable {
    /** The black hole's sphere. */
    BLACK("textures/entity/compression_sphere.png"),
    /** A large Blast's sphere. */
    GREEN("textures/entity/compression_sphere_green.png");

    /** The name a black hole's sphere saved under before its core drew black. */
    private static final String SAVED_AS_MAROON = "maroon";
    private static final String NO_SUCH_CORE = "No sphere core is named ";

    /**
     * Codec writing the core by its lowercase name, reading a sphere saved as
     * maroon as the black hole's.
     */
    public static final Codec<SphereCore> CODEC = Codec.STRING.comapFlatMap(SphereCore::bySavedName,
            SphereCore::getSerializedName);

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
     * The core a synched ordinal names, the black hole's for one out of range.
     *
     * @param ordinal the ordinal the entity data carries
     * @return the core
     */
    public static SphereCore byOrdinal(int ordinal) {
        SphereCore[] cores = values();
        return ordinal >= 0 && ordinal < cores.length ? cores[ordinal] : BLACK;
    }

    /**
     * The core a saved name names, maroon reading as the black hole's.
     *
     * @param name the name the sphere saved its core under
     * @return the core, or an error for a name no core carries
     */
    private static DataResult<SphereCore> bySavedName(String name) {
        if (SAVED_AS_MAROON.equals(name)) {
            return DataResult.success(BLACK);
        }
        for (SphereCore core : values()) {
            if (core.getSerializedName().equals(name)) {
                return DataResult.success(core);
            }
        }
        return DataResult.error(() -> NO_SUCH_CORE + name);
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
