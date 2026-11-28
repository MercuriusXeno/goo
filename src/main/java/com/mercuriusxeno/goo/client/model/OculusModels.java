package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import java.util.ArrayList;
import java.util.List;

/**
 * The oculus's eye and lid models, baked standalone for the oculus block's renderer. The lids draw one pixel row at
 * a time, each row its own model sampling its own row of the lid's hide, so the hide shades from row to row and the
 * seam rides each lid's leading edge.
 * decision oculus-prism-becomes-a-hovering-eye
 * decision oculus-eyelid-is-shaded-hide-with-a-seam
 */
public final class OculusModels {

    /** The lid's pixel rows, top to bottom as they stand shut: the upper lid's, then the lower lid's. */
    public static final int LID_ROWS = 7;
    private static final String EYE_NAME = "oculus_eye";
    private static final String LID_ROW_PREFIX = "oculus_lid_";
    private static final String MODEL_FOLDER = "block/";
    private static final String NAMESPACE_SEPARATOR = ":";
    private static final StandaloneModelKey<QuadCollection> EYE = keyFor(EYE_NAME);
    private static final List<StandaloneModelKey<QuadCollection>> LID_ROW_KEYS = lidRowKeys();

    private OculusModels() {
    }

    /**
     * A standalone model key named for one model file.
     *
     * @param name the model's file name under block/
     * @return the key
     */
    private static StandaloneModelKey<QuadCollection> keyFor(String name) {
        return new StandaloneModelKey<>(new ModelDebugName() {
            @Override
            public String debugName() {
                return Goo.MODID + NAMESPACE_SEPARATOR + name;
            }
        });
    }

    private static List<StandaloneModelKey<QuadCollection>> lidRowKeys() {
        List<StandaloneModelKey<QuadCollection>> keys = new ArrayList<>();
        for (int row = 0; row < LID_ROWS; row++) {
            keys.add(keyFor(lidRowName(row)));
        }
        return List.copyOf(keys);
    }

    /**
     * The model file name of one lid row.
     *
     * @param row the row, 0 the upper lid's brow to LID_ROWS - 1 the lower lid's bottom
     * @return the file name under block/
     */
    public static String lidRowName(int row) {
        return LID_ROW_PREFIX + row;
    }

    /**
     * Registers the eye and lid row models.
     *
     * @param event the standalone model registration event
     */
    public static void register(ModelEvent.RegisterStandalone event) {
        event.register(EYE, SimpleUnbakedStandaloneModel.quadCollection(
                Identifier.fromNamespaceAndPath(Goo.MODID, MODEL_FOLDER + EYE_NAME)));
        for (int row = 0; row < LID_ROWS; row++) {
            event.register(LID_ROW_KEYS.get(row), SimpleUnbakedStandaloneModel.quadCollection(
                    Identifier.fromNamespaceAndPath(Goo.MODID, MODEL_FOLDER + lidRowName(row))));
        }
    }

    /**
     * @return the eye's baked quads
     */
    public static QuadCollection eye() {
        return Minecraft.getInstance().getModelManager().getStandaloneModel(EYE);
    }

    /**
     * One lid row's baked quads, the row's base at the model's floor.
     *
     * @param row the row, 0 the upper lid's brow to LID_ROWS - 1 the lower lid's bottom
     * @return the row's quads
     */
    public static QuadCollection lidRow(int row) {
        return Minecraft.getInstance().getModelManager().getStandaloneModel(LID_ROW_KEYS.get(row));
    }
}
