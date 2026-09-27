package com.mercuriusxeno.goo.item;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.util.Arrays;
import java.util.Set;

/**
 * Whether a block class defines its own right click: a class between it and BlockBehaviour
 * declares useItemOn or useWithoutItem. A plain canister click on such a block is the block's,
 * so the placement resolver places nothing there (decision preview-runs-the-placement-validator).
 */
final class OwnRightClick {

    private static final Set<String> RIGHT_CLICK_METHODS = Set.of("useItemOn", "useWithoutItem");

    private static final ClassValue<Boolean> DEFINED = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != BlockBehaviour.class; c = c.getSuperclass()) {
                if (Arrays.stream(c.getDeclaredMethods()).anyMatch(m -> RIGHT_CLICK_METHODS.contains(m.getName()))) {
                    return true;
                }
            }
            return false;
        }
    };

    private OwnRightClick() {
    }

    /**
     * @param blockClass the block's runtime class
     * @return true when the class, or a superclass below BlockBehaviour, declares a right click
     */
    static boolean definedBy(Class<? extends Block> blockClass) {
        return DEFINED.get(blockClass);
    }
}
