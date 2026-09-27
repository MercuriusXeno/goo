package com.mercuriusxeno.goo.item;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Whether a block class defines its own right click: a class between it and BlockBehaviour
 * declares useItemOn or useWithoutItem. A plain canister click on such a block is the block's,
 * so the placement resolver places nothing there (decision preview-runs-the-placement-validator).
 */
final class OwnRightClick {

    private static final String USE_ITEM_ON = "useItemOn";
    private static final String USE_WITHOUT_ITEM = "useWithoutItem";

    private static final ClassValue<Boolean> DEFINED = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != BlockBehaviour.class; c = c.getSuperclass()) {
                if (Arrays.stream(c.getDeclaredMethods()).anyMatch(OwnRightClick::isRightClick)) {
                    return true;
                }
            }
            return false;
        }
    };

    private OwnRightClick() {
    }

    private static boolean isRightClick(Method method) {
        return USE_ITEM_ON.equals(method.getName()) || USE_WITHOUT_ITEM.equals(method.getName());
    }

    /**
     * @param blockClass the block's runtime class
     * @return true when the class, or a superclass below BlockBehaviour, declares a right click
     */
    static boolean definedBy(Class<? extends Block> blockClass) {
        return DEFINED.get(blockClass);
    }
}
