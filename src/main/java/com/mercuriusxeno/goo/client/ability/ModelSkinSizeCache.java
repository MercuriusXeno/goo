package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.model.geom.ModelPart;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Each drawn model's skin size, read once per model root and shared by every
 * layer drawing a skin-space shader over it. A root a resource reload replaces
 * drops out with its model; ModelPart keeps identity equality, so the weak map
 * keys on the root itself.
 * Decisions diagnose-and-correct-petrify-stone-stretching-off-native-texels,
 * diagnose-and-correct-vines-and-ailment-overlay-assuming-a-square-skin.
 */
final class ModelSkinSizeCache {

    private static final Map<ModelPart, ModelSkinSize> SIZE_BY_ROOT = Collections.synchronizedMap(new WeakHashMap<>());

    private ModelSkinSizeCache() {
    }

    /**
     * The model's skin size, read off its cubes the first time the root is drawn.
     *
     * @param root the model's root part
     * @return the skin size
     */
    static ModelSkinSize sizeOf(ModelPart root) {
        return SIZE_BY_ROOT.computeIfAbsent(root, ModelSkinSize::of);
    }
}
