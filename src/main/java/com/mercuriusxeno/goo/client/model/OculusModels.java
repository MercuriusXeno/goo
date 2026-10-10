package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * The oculus's eye and lid models, baked standalone for the oculus prism's
 * style. Both stand in until the operator's own models replace the files.
 * Decision oculus-prism-becomes-a-hovering-eye.
 */
public final class OculusModels {

    private static final String EYE_NAME = "oculus_eye";
    private static final String LID_NAME = "oculus_lid";
    private static final String MODEL_FOLDER = "block/";
    private static final String NAMESPACE_SEPARATOR = ":";
    private static final StandaloneModelKey<QuadCollection> EYE = keyFor(EYE_NAME);
    private static final StandaloneModelKey<QuadCollection> LID = keyFor(LID_NAME);

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

    /**
     * Registers the eye and lid models.
     *
     * @param event the standalone model registration event
     */
    public static void register(ModelEvent.RegisterStandalone event) {
        event.register(EYE, SimpleUnbakedStandaloneModel.quadCollection(
                Identifier.fromNamespaceAndPath(Goo.MODID, MODEL_FOLDER + EYE_NAME)));
        event.register(LID, SimpleUnbakedStandaloneModel.quadCollection(
                Identifier.fromNamespaceAndPath(Goo.MODID, MODEL_FOLDER + LID_NAME)));
    }

    /**
     * @return the eye's baked quads
     */
    public static QuadCollection eye() {
        return Minecraft.getInstance().getModelManager().getStandaloneModel(EYE);
    }

    /**
     * @return a lid's baked quads, the lid's base at the model's floor
     */
    public static QuadCollection lid() {
        return Minecraft.getInstance().getModelManager().getStandaloneModel(LID);
    }
}
