package com.mercuriusxeno.goo.item;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.EnumSet;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The click classification rule over every item kind a machine meets and the state of the
 * slot it aims at; the item kinds are named by what each item says of itself and whether it
 * carries a fluid handler, so no registry is touched.
 */
class GooInteractionTypeTest {

    /**
     * Each row: item kind, the type it names for itself, whether it carries a fluid handler,
     * whether the aimed slot holds a canister, and the classification expected.
     *
     * @return the theory's rows
     */
    static Stream<Arguments> itemKindsOverSlotStates() {
        return Stream.of(
                arguments("canister", GooInteractionType.CANISTER_INSERT, true, false,
                        GooInteractionType.CANISTER_INSERT),
                arguments("canister", GooInteractionType.CANISTER_INSERT, true, true,
                        GooInteractionType.CANISTER_PICKUP),
                arguments("omniblob", GooInteractionType.BLOB_INSERT, false, false,
                        GooInteractionType.BLOB_INSERT),
                arguments("omniblob", GooInteractionType.BLOB_INSERT, false, true,
                        GooInteractionType.BLOB_INSERT),
                arguments("tuner", GooInteractionType.TUNER_PASS, false, false,
                        GooInteractionType.TUNER_PASS),
                arguments("tuner", GooInteractionType.TUNER_PASS, false, true,
                        GooInteractionType.TUNER_PASS),
                arguments("gasket", GooInteractionType.GASKET_INSTALL, false, false,
                        GooInteractionType.GASKET_INSTALL),
                arguments("gasket", GooInteractionType.GASKET_INSTALL, false, true,
                        GooInteractionType.GASKET_INSTALL),
                arguments("bucket", null, true, false, GooInteractionType.FLUID_CONTAINER),
                arguments("bucket", null, true, true, GooInteractionType.FLUID_CONTAINER),
                arguments("stick", null, false, false, null),
                arguments("stick", null, false, true, null));
    }

    @ParameterizedTest(name = "{0}, slot filled {3} -> {4}")
    @MethodSource("itemKindsOverSlotStates")
    void classifiesEachItemKindAgainstTheAimedSlot(String itemKind,
            @Nullable GooInteractionType selfClassified, boolean fluidContainer,
            boolean targetSlotFilled, @Nullable GooInteractionType expected) {
        assertEquals(expected, GooInteractionType.resolve(selfClassified, fluidContainer, targetSlotFilled),
                itemKind);
    }

    @Test
    void onlyTheTunerAndTheGasketPassToTheirItem() {
        EnumSet<GooInteractionType> passing = EnumSet.noneOf(GooInteractionType.class);
        for (GooInteractionType type : GooInteractionType.values()) {
            if (type.passesToItem()) {
                passing.add(type);
            }
        }
        assertEquals(EnumSet.of(GooInteractionType.TUNER_PASS, GooInteractionType.GASKET_INSTALL), passing);
    }
}
