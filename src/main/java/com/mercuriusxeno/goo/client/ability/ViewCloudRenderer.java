package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.Profiler;

/**
 * A cloud renderer of a view's own: the game's cloud renderer keeps one mesh
 * built around one camera, so a view drawing its clouds with the frame's
 * renderer would rebuild that mesh around the view's camera and leave the
 * frame's clouds jumping between the two. Each view draws its clouds with
 * one of these instead, loaded with the same cloud texture.
 * portals-link-by-looking-through-portal-vision
 */
final class ViewCloudRenderer extends CloudRenderer {

    private ViewCloudRenderer() {
    }

    /**
     * A cloud renderer with the cloud texture loaded, as the game loads its own.
     *
     * @return the renderer
     */
    static ViewCloudRenderer loaded() {
        ViewCloudRenderer renderer = new ViewCloudRenderer();
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        renderer.apply(renderer.prepare(resources, Profiler.get()), resources, Profiler.get());
        return renderer;
    }
}
