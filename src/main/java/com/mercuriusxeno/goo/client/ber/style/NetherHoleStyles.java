package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.GooClientConfig;
import com.mercuriusxeno.goo.GooClientConfig.NetherHoleShape;

/**
 * The {@link NetherHoleStyle} implementations and the client config that
 * picks between them and switches the lens (decision one-disc-mesh-config-lens).
 */
public final class NetherHoleStyles {

    /** A UV sphere occluder, a ray-sphere fresnel corona and the flat accretion disc. */
    public static final NetherHoleStyle SPHERE = new SphereHoleStyle();

    /** A cube occluder with a cube-edge glow in place of the corona, and the flat accretion disc. */
    public static final NetherHoleStyle CUBE = new CubeHoleStyle();

    private NetherHoleStyles() {}

    /**
     * Answers the style the client config names.
     *
     * @return the active style
     */
    public static NetherHoleStyle active() {
        return forShape(GooClientConfig.NETHER_HOLE_SHAPE.get());
    }

    /**
     * Answers the style that draws {@code shape}.
     *
     * @param shape the configured hole shape
     * @return the style for that shape
     */
    public static NetherHoleStyle forShape(NetherHoleShape shape) {
        return switch (shape) {
            case SPHERE -> SPHERE;
            case CUBE -> CUBE;
        };
    }

    /**
     * Answers whether the client config turns the screen-space lens on.
     *
     * @return true when the lens runs
     */
    public static boolean lensEnabled() {
        return GooClientConfig.SHOW_NETHER_LENS.get();
    }
}
