package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooItem;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.mockito.MockedStatic;
import java.lang.reflect.Field;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A client whose level holds one player with a glove in the right hand,
 * looking south and thirty degrees down, the local player in first person or
 * another player, the glove's selected goo type stubbed through
 * {@link GooGloveItem#getSelectedType}, each frame drawn at partial tick 0
 * with a 90 degree field of view. The right hand may hold raw goo in place of
 * the glove, its selection naming {@link #BARE_HAND_ABILITY} of its own type,
 * or nothing. Close it to restore the statics it swaps.
 */
public final class SprayingClient implements AutoCloseable {

    /** The spraying player's entity id. */
    public static final int CASTER = 7;
    /** The spraying player's eye. */
    public static final Vec3 EYE = new Vec3(3, 65.62, -2);
    /** The player's field of view setting, wider than the hand pass's 70 degrees. */
    public static final float WORLD_FOV = 90f;
    /** The eye's height above the feet. */
    private static final double EYE_HEIGHT = 1.62;
    /** The look's pitch, thirty degrees down. */
    public static final float PITCH = 30f;
    /** The look's yaw, a little west of south. */
    public static final float YAW = 20f;
    /** Where the server said the glove stood with the stream's tick. */
    public static final Vec3 SENT_GLOVE = new Vec3(3.3, 65.2, -1.4);
    /** The ability a bare hand's raw goo selects. */
    public static final String BARE_HAND_ABILITY = "goo:bare_hand_cast";

    /** What the caster's right hand holds. */
    private enum Holding {
        GLOVE, RAW_GOO, NOTHING
    }

    private final Minecraft minecraft;
    private final ClientLevel level;
    private final Camera camera;
    private final GameRenderer renderer;
    private final ItemStack glove;
    private final MockedStatic<Minecraft> client;
    private final MockedStatic<GooGloveItem> gloves;
    /** The goo stacks' statics, swapped only for a hand holding raw goo. */
    private final @Nullable MockedStatic<GooStacks> stacks;

    private SprayingClient(boolean local, Holding holding, @Nullable ResourceKey<GooTypeDefinition> selected) {
        minecraft = mock(Minecraft.class);
        level = mock(ClientLevel.class);
        minecraft.level = level;
        AbstractClientPlayer caster = local ? mock(LocalPlayer.class) : mock(RemotePlayer.class);
        when(level.getEntity(CASTER)).thenReturn(caster);
        when(level.players()).thenReturn(List.of(caster));
        when(caster.getId()).thenReturn(CASTER);
        when(caster.getEyePosition(anyFloat())).thenReturn(EYE);
        when(caster.getViewXRot(anyFloat())).thenReturn(PITCH);
        when(caster.getViewYRot(anyFloat())).thenReturn(YAW);
        when(caster.getMainArm()).thenReturn(HumanoidArm.RIGHT);
        when(caster.getPosition(anyFloat())).thenReturn(EYE.subtract(0, EYE_HEIGHT, 0));
        glove = mock(ItemStack.class);
        when(glove.getItem()).thenReturn(itemFor(holding));
        when(caster.getMainHandItem()).thenReturn(glove);
        when(caster.getOffhandItem()).thenReturn(mock(ItemStack.class));
        camera = mock(Camera.class);
        when(camera.position()).thenReturn(EYE);
        when(camera.xRot()).thenReturn(PITCH);
        when(camera.yRot()).thenReturn(YAW);
        when(camera.rotation()).thenReturn(new Quaternionf().rotationYXZ((float) Math.PI - (float) Math.toRadians(YAW),
                -(float) Math.toRadians(PITCH), 0f));
        when(camera.getFov()).thenReturn(WORLD_FOV);
        DeltaTracker frames = mock(DeltaTracker.class);
        when(frames.getGameTimeDeltaPartialTick(anyBoolean())).thenReturn(0f);
        when(minecraft.getDeltaTracker()).thenReturn(frames);
        renderer = mock(GameRenderer.class);
        when(renderer.mainCamera()).thenReturn(camera);
        setRenderState(renderer);
        Options options = mock(Options.class);
        when(options.getCameraType()).thenReturn(CameraType.FIRST_PERSON);
        setField("gameRenderer", renderer);
        setField("options", options);
        if (local) {
            minecraft.player = (LocalPlayer) caster;
        }
        client = mockStatic(Minecraft.class);
        client.when(Minecraft::getInstance).thenReturn(minecraft);
        gloves = mockStatic(GooGloveItem.class);
        gloves.when(() -> GooGloveItem.getSelectedType(glove)).thenReturn(selected);
        if (holding == Holding.RAW_GOO && selected != null) {
            gloves.when(() -> GooGloveItem.getSelection(glove))
                    .thenReturn(new GloveSelection(GooTypes.id(selected), BARE_HAND_ABILITY));
            stacks = mockStatic(GooStacks.class);
            stacks.when(() -> GooStacks.keyOf(glove)).thenReturn(selected);
        } else {
            stacks = null;
        }
    }

    private static Item itemFor(Holding holding) {
        return switch (holding) {
            case GLOVE -> mock(GooGloveItem.class);
            case RAW_GOO -> mock(GooItem.class);
            case NOTHING -> mock(Item.class);
        };
    }

    /**
     * A client drawing the local player spraying in first person.
     *
     * @param selected the glove's selected goo type
     * @return the client
     */
    public static SprayingClient local(ResourceKey<GooTypeDefinition> selected) {
        return new SprayingClient(true, Holding.GLOVE, selected);
    }

    /**
     * A client drawing another player spraying.
     *
     * @param selected the glove's selected goo type
     * @return the client
     */
    public static SprayingClient another(ResourceKey<GooTypeDefinition> selected) {
        return new SprayingClient(false, Holding.GLOVE, selected);
    }

    /**
     * A client drawing the local player in first person with no glove, raw
     * goo of a type in the right hand, its selection naming
     * {@link #BARE_HAND_ABILITY} of that type.
     *
     * @param held the raw goo's type
     * @return the client
     */
    public static SprayingClient bareHanded(ResourceKey<GooTypeDefinition> held) {
        return new SprayingClient(true, Holding.RAW_GOO, held);
    }

    /**
     * A client drawing the local player in first person holding neither a
     * glove nor raw goo.
     *
     * @return the client
     */
    public static SprayingClient emptyHanded() {
        return new SprayingClient(true, Holding.NOTHING, null);
    }

    /**
     * Sets the level's game time.
     *
     * @param gameTime the game time in ticks
     */
    public void at(long gameTime) {
        when(level.getGameTime()).thenReturn(gameTime);
    }

    /**
     * Sets the level's field of view, the hand pass keeping its own 70 degrees.
     *
     * @param fov the field of view, in degrees
     */
    public void viewedAt(float fov) {
        when(camera.getFov()).thenReturn(fov);
    }

    /**
     * Bobs each frame's view, as GameRenderer.bobView lays a walking bob on
     * the level's projection and the hand pass's pose alike.
     *
     * @param bob the bob, in view space
     */
    public void bobbing(Matrix4fc bob) {
        renderer.gameRenderState.optionsRenderState.bobView = true;
        doAnswer(call -> {
            call.<PoseStack>getArgument(1).last().pose().mul(bob);
            return null;
        }).when(renderer).bobView(any(), any());
    }

    /**
     * Draws the glove before the eyes as the hand pass does: the camera's turn
     * undone, the frame's bob, and the hand's own pose down to the palm.
     *
     * @param bob         the frame's bob, in view space
     * @param handToPalm  the hand's pose from the view to the palm, swing included
     * @return the glove's model onto the hand pass's camera-relative world axes, the palm at its origin
     */
    public Matrix4f gloveDrawnUnder(Matrix4fc bob, Matrix4fc handToPalm) {
        return new Matrix4f().rotation(camera.rotation()).mul(bob).mul(handToPalm);
    }

    /**
     * Where a point of the view stands in the hand pass's camera-relative world axes.
     *
     * @param view the point in view space
     * @return the point in the hand pass
     */
    public Vec3 inTheHandPass(Vector3fc view) {
        return new Vec3(new Vector3f(view).rotate(camera.rotation()));
    }

    /** @return the view's up in the hand pass's camera-relative world axes */
    public Vec3 viewUp() {
        return inTheHandPass(new Vector3f(0f, 1f, 0f));
    }

    /** @return the camera the level and the hand are drawn from */
    public Camera camera() {
        return camera;
    }

    /** @return the spraying player's glove */
    public ItemStack glove() {
        return glove;
    }

    private void setField(String name, Object value) {
        try {
            Field field = Minecraft.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(minecraft, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Hands a mocked renderer a frame's render state, which the view bob reads; the mock bobs nothing.
     *
     * @param renderer the mocked renderer
     */
    static void setRenderState(GameRenderer renderer) {
        try {
            Field field = GameRenderer.class.getDeclaredField("gameRenderState");
            field.setAccessible(true);
            field.set(renderer, new GameRenderState());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        if (stacks != null) {
            stacks.close();
        }
        gloves.close();
        client.close();
        LiftedPalmBlob.forgetEverySpray();
        SpraySource.forgetRestingPalm();
        DrawnPalm.forgetEveryPalm();
    }

    /**
     * Records the caster's right glove palm as the arm layer draws it this frame.
     *
     * @param palm the drawn palm, in the world
     */
    public void drawsThePalm(DrawnPalm.Palm palm) {
        DrawnPalm.record(CASTER, HumanoidArm.RIGHT, EYE.subtract(0, EYE_HEIGHT, 0), palm);
    }
}
