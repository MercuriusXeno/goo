package com.mercuriusxeno.goo;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side settings, read from goo-client.toml, so the nether hole's
 * style and lens switch without a rebuild (decision one-disc-mesh-config-lens).
 */
public final class GooClientConfig {

    /** The body the nether black hole draws with. */
    public enum NetherHoleShape {
        /** A UV sphere occluder with a fresnel corona. */
        SPHERE,
        /** A cube occluder with an edge glow. */
        CUBE
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<NetherHoleShape> NETHER_HOLE_SHAPE;
    public static final ModConfigSpec.BooleanValue SHOW_NETHER_LENS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Nether black hole visual");
        builder.push("netherHole");

        NETHER_HOLE_SHAPE = builder
            .comment("The shape the nether black hole draws with: SPHERE (default) or CUBE (opt-in).")
            .defineEnum("shape", NetherHoleShape.SPHERE);

        SHOW_NETHER_LENS = builder
            .comment(
                "When true, a screen-space lens warps the view around the nether black hole.",
                "When false (default), no lens work runs.")
            .define("lensEnabled", false);

        builder.pop();

        SPEC = builder.build();
    }

    private GooClientConfig() {
    }
}
