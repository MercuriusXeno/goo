package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The target a goo render type's immediate draws land on, as 26.2's render type output target named it: the main
 * target unless the type names a target of its own, and the main target too while that target is not yet made.
 * Minecraft 26.3 removed the output target from the render type, so goo keeps the few types drawing elsewhere here.
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
public final class DrawTarget {

    private static final Map<RenderType, Supplier<@Nullable RenderTarget>> OWN_TARGETS = new ConcurrentHashMap<>();

    private DrawTarget() {
    }

    /**
     * Names the target a render type's draws land on.
     *
     * @param type   the render type
     * @param target the target, or null while it is not yet made
     * @return the render type
     */
    public static RenderType drawingInto(RenderType type, Supplier<@Nullable RenderTarget> target) {
        OWN_TARGETS.put(type, target);
        return type;
    }

    /**
     * @param type the render type
     * @return the target of its own the type draws into, or null where it draws onto the main target
     */
    public static @Nullable RenderTarget ownTargetOf(RenderType type) {
        Supplier<@Nullable RenderTarget> own = OWN_TARGETS.get(type);
        RenderTarget target = own == null ? null : own.get();
        return target == null || target == main() ? null : target;
    }

    private static RenderTarget main() {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget();
    }
}
