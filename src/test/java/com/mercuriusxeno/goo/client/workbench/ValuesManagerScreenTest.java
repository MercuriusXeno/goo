package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the values manager through its seams with no Screen: the title row's layout through its static seam, where
 * the screen's margin is 8, its button height 14 and its button gap 2, and the open's requests through
 * {@link ManagerReads} over real panes and a fake request line.
 */
class ValuesManagerScreenTest {
    private static final String MODEL = """
            {"path": "base_values.json", "members": [
              {"key": "stick", "keySeparator": null, "kind": "VALUE", "text": "\\"4\\"", "entries": [],
               "leading": null, "trailing": null, "closingGap": null}]}
            """;
    private static final String SAVED = MODEL.replace("\"text\": \"\\\"4\\\"\"", "\"text\": \"5\"");
    private static final Map<String, String> CANNED = Map.of(WorkbenchOperations.MODEL, MODEL);

    /**
     * A press on an open dropdown's choice, over the pane's search box, picks the choice and never reaches the
     * screen's widgets, so the box takes no focus (decision open-dropdown-intercepts-clicks-before-the-pane-behind-it).
     */
    @Test
    void openDropdownTakesTheClickOverTheSearchBox() {
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> call.getArgument(0));
        BuilderOverlays overlays = new BuilderOverlays(actions, mock(ItemIcons.class));
        overlays.init(DrawnFrame.font());
        overlays.show(true);
        List<String> picked = new ArrayList<>();
        overlays.openDropdown(new BuilderOverlays.Anchor(20, 20), SearchDropdown.over(List.of(),
                List.of("oak", "birch"), false), choice -> picked.add(choice.id()));
        DrawnFrame frame = new DrawnFrame();
        overlays.draw(frame.graphics, DrawnFrame.font(), frame.targets);
        DrawnFrame.Text birch = frame.drawn().stream().filter(text -> text.text().contains("birch")).findFirst()
                .orElseThrow(() -> new AssertionError(frame.texts().toString()));
        EditBox search = new EditBox(DrawnFrame.font(), birch.x() - 10, birch.y() - 4, 200, 16,
                Component.literal("Search prefabs"));
        MouseButtonEvent press = new MouseButtonEvent(birch.x() + 1, birch.y() + 1,
                new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
        assertTrue(search.isMouseOver(press.x(), press.y()), "the search box stands under the choice");
        List<MouseButtonEvent> reachedWidgets = new ArrayList<>();
        assertTrue(ValuesManagerScreen.routePress(frame.targets, press, event -> reachedWidgets.add(event), () -> { }));
        assertEquals(List.of("birch"), picked);
        assertTrue(reachedWidgets.isEmpty(), "the press reached the widgets behind the dropdown");
    }

    /**
     * The whole screen's frame on the Items tab with a draft standing, drawn onto mocked graphics through
     * {@link ValuesManagerScreen#extractRenderState}, each draw read back with the stratum it landed on: the count of
     * nextStratum calls before it (decision dropdowns-draw-on-one-stratum-above-everything-but-tooltip-and-dialog).
     */
    @Nested
    class NothingDrawsOverAnOpenList {
        private static final String DRAFT_BADGE = "draft";
        private static final String TITLE = "Values manager";
        private static final String STATUS = "Reading the draft.";
        private static final String TEXT = "text:";
        private static final String FILL = "fill:";
        private static final String TOOLTIP = "tooltip";
        private final Minecraft minecraft = mock(Minecraft.class, Mockito.RETURNS_DEEP_STUBS);
        // A focused box tells the game, so the game's instance answers for the whole test.
        private final MockedStatic<Minecraft> game = Mockito.mockStatic(Minecraft.class);
        private final ValuesManagerScreen screen;

        NothingDrawsOverAnOpenList() throws ReflectiveOperationException {
            when(minecraft.getWindow().getGuiScale()).thenReturn(2);
            game.when(Minecraft::getInstance).thenReturn(minecraft);
            screen = new ValuesManagerScreen(new ManagerConnection(part -> { }),
                    new ListedEntrySource(List.of(), List.of()));
            Field font = Screen.class.getDeclaredField("font");
            font.setAccessible(true);
            font.set(screen, DrawnFrame.font());
            screen.width = DrawnFrame.SCREEN_WIDTH;
            screen.height = DrawnFrame.SCREEN_HEIGHT;
            Method init = ValuesManagerScreen.class.getDeclaredMethod("init");
            init.setAccessible(true);
            init.invoke(screen);
            ManagerDraft draft = field(screen, "draft");
            draft.readEdited("{\"path\": \"base_values.json\", \"members\": [], \"draft\": {\"differs\": true}}");
        }

        @AfterEach
        void releaseTheGame() {
            game.close();
        }

        @SuppressWarnings("unchecked")
        private static <T> T field(Object owner, String name) throws ReflectiveOperationException {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(owner);
        }

        // The Items tab's builder stands open with its overlays, as a click on a builder control leaves it.
        private BuilderOverlays itemsOverlays() throws ReflectiveOperationException {
            ExpressionBuilderPane builder = field(field(screen, "valuesPane"), "builder");
            builder.open("", typed -> { }, () -> { });
            return field(builder, "overlays");
        }

        // Each draw the frame made, as "text:" and its string, "fill:" and its color or "tooltip", with its stratum.
        private record Drawn(String what, int stratum) {
        }

        private List<Drawn> frame(int mouseX, int mouseY) {
            GuiGraphicsExtractor graphics = new DrawnFrame().graphics;
            screen.extractRenderState(graphics, mouseX, mouseY, 0);
            List<Drawn> drawn = new ArrayList<>();
            int stratum = 0;
            for (Invocation call : Mockito.mockingDetails(graphics).getInvocations()) {
                String method = call.getMethod().getName();
                if (method.equals("nextStratum")) {
                    stratum++;
                } else if (method.equals("text")) {
                    Object text = call.getArgument(1);
                    drawn.add(new Drawn(TEXT + (text instanceof Component styled ? styled.getString() : text),
                            stratum));
                } else if (method.equals("fill") && call.getArguments().length == 5) {
                    drawn.add(new Drawn(FILL + call.<Integer>getArgument(4), stratum));
                } else if (method.equals(TOOLTIP)) {
                    drawn.add(new Drawn(TOOLTIP, stratum));
                }
            }
            return drawn;
        }

        private static List<Integer> strataOf(List<Drawn> drawn, String what) {
            List<Integer> strata = drawn.stream().filter(each -> each.what().equals(what)).map(Drawn::stratum)
                    .toList();
            assertFalse(strata.isEmpty(), what + " not drawn in " + drawn);
            return strata;
        }

        // The badge, the title and the status line each draw on a stratum below the open list's popup frame.
        private static void assertChromeUnder(List<Drawn> drawn) {
            int list = strataOf(drawn, FILL + Palette.POPUP_FILL).stream().min(Integer::compare).orElseThrow();
            for (String chrome : List.of(DRAFT_BADGE, TITLE, STATUS)) {
                int stratum = strataOf(drawn, TEXT + chrome).stream().max(Integer::compare).orElseThrow();
                assertTrue(stratum < list, "\"" + chrome + "\" draws on stratum " + stratum
                        + ", the open list's popup on " + list);
            }
        }

        /**
         * With a dropdown open on the Items tab, the gold "draft" badge, the title and the status line draw on a
         * stratum below the dropdown's, and the dropdown's below the draft dialog's and the tooltip's.
         */
        @Test
        void dropdownDrawsOverTheChromeAndUnderTheDialogAndTheTooltip() throws ReflectiveOperationException {
            itemsOverlays().openDropdown(new BuilderOverlays.Anchor(20, 60), SearchDropdown.over(List.of(),
                    List.of("oak", "birch"), false), choice -> { });
            DraftConfirm confirm = field(screen, "draftConfirm");
            confirm.askRevert("base_values.json");
            ExplainerMode explainer = field(screen, "explainer");
            explainer.toggle();
            ButtonPainter.Box items = ManagerChrome.tabBox(DrawnFrame.font(), ManagerTab.VALUES);
            List<Drawn> drawn = frame(items.left() + 1, items.top() + 1);
            assertChromeUnder(drawn);
            // A choice the list names as a prefab draws its $ mark.
            int dropdown = strataOf(drawn, TEXT + "$birch").stream().max(Integer::compare).orElseThrow();
            int dialog = strataOf(drawn, FILL + Dialog.DIM).stream().min(Integer::compare).orElseThrow();
            int tooltip = strataOf(drawn, TOOLTIP).getFirst();
            assertTrue(dropdown < dialog && dropdown < tooltip, "the dropdown on " + dropdown + ", the dialog on "
                    + dialog + ", the tooltip on " + tooltip);
        }

        /**
         * With an options list open on the Items tab, the badge, the title and the status line draw under it.
         */
        @Test
        void optionsListDrawsOverTheChrome() throws ReflectiveOperationException {
            itemsOverlays().openOptions(new BuilderOverlays.Anchor(20, 60), List.of(
                    new BuilderOverlays.Option("Sum", null, at -> { })));
            assertChromeUnder(frame(0, 0));
        }

        /**
         * With the goo type picker open on the Items tab, the badge, the title and the status line draw under it.
         */
        @Test
        void typePickerDrawsOverTheChrome() throws ReflectiveOperationException {
            itemsOverlays().openPicker(new BuilderOverlays.Anchor(20, 60), type -> { });
            assertChromeUnder(frame(0, 0));
        }
    }

    /**
     * With no dropdown open, a press on the search box reaches the screen's widgets, which focus it (decision
     * open-dropdown-intercepts-clicks-before-the-pane-behind-it).
     */
    @Test
    void searchBoxTakesTheClickWhenNoDropdownIsOpen() {
        MouseButtonEvent press = new MouseButtonEvent(30, 25, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
        List<MouseButtonEvent> reachedWidgets = new ArrayList<>();
        List<String> unfocused = new ArrayList<>();
        assertTrue(ValuesManagerScreen.routePress(new ClickTargets(), press, event -> reachedWidgets.add(event),
                () -> unfocused.add("unfocused")));
        assertEquals(List.of(press), reachedWidgets);
        assertTrue(unfocused.isEmpty(), "the widget that took the press keeps its focus");
    }

    /**
     * The X is a square ending at the right margin, the [?] a square one gap left of it and the topbar starts one gap
     * left of the [?] (decisions x-in-the-top-right-closes-the-manager,
     * question-button-toggles-explainer-mode-with-the-drag-mask).
     */
    @Test
    void closeXEndsAtTheRightMarginAndTheTopbarStandsLeftOfIt() {
        ManagerChrome.TitleRow row = ManagerChrome.titleRowOf(400);
        assertEquals(new ManagerChrome.TitleRow(378, 392, 362, 360), row);
    }

    /**
     * Escape with a text box focused empties it through its responder and drops its focus, leaving the manager open;
     * an Escape with no box focused falls through to the close (decision
     * escape-empties-the-box-and-never-closes-the-manager).
     */
    @Test
    void escapeEmptiesTheFocusedBoxAndOnlyAnUnfocusedEscapeCloses() {
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(List.of(new ManagerEntrySource.ItemName("minecraft:wax", "Wax"),
                new ManagerEntrySource.ItemName("minecraft:stick", "Stick")), List.of()));
        EditBox box = new EditBox(DrawnFrame.font(), 0, 0, 100, SearchRow.HEIGHT, Component.literal("Search items"));
        box.setResponder(values::search);
        box.setValue("wax");
        assertEquals(1, values.listed().size());
        List<String> unfocused = new ArrayList<>();
        assertTrue(ValuesManagerScreen.escapeEmpties(box, () -> unfocused.add("focus dropped")),
                "the focused box takes the Escape");
        assertEquals(List.of("", 2, List.of("focus dropped")), List.of(box.getValue(), values.listed().size(),
                unfocused));
        assertFalse(ValuesManagerScreen.escapeEmpties(null, () -> unfocused.add("again")),
                "with no box focused the Escape closes the manager");
        assertEquals(1, unfocused.size());
    }

    /**
     * The topbar's X, [?], Save, Revert and Promote and the five tabs draw as the panes' flat 14 px button frames, the
     * active tab in the selected fill, and a press on Save reaches the save through the frame's click areas (decision
     * one-flat-painter-draws-every-button).
     */
    @Test
    void chromeDrawsFlatButtonsAndTheActiveTabSelected() {
        List<String> ran = new ArrayList<>();
        DrawnFrame frame = new DrawnFrame();
        ManagerChrome.draw(frame.graphics, DrawnFrame.font(), frame.targets, 400, ManagerTab.GROUPS,
                new ManagerChrome.Actions(() -> ran.add("close"), () -> ran.add("help"), () -> ran.add("save"),
                        () -> ran.add("revert"), () -> ran.add("promote"), tab -> ran.add(tab.name())));
        List<String> flat = new ArrayList<>(List.of("X", "?", "Save", "Revert", "Promote"));
        Arrays.stream(ManagerTab.values()).filter(each -> each != ManagerTab.GROUPS)
                .forEach(each -> flat.add(each.label()));
        for (String label : flat) {
            int[] box = frame.buttonAround(frame.find(label));
            assertEquals(ButtonPainter.HEIGHT, box[3] - box[1], label);
        }
        DrawnFrame.Text active = frame.find(ManagerTab.GROUPS.label());
        int[] selected = frame.fillsOf(Palette.SELECTED_FILL).stream().filter(fill -> fill[0] <= active.x()
                && active.x() < fill[2] && fill[1] <= active.y() && active.y() < fill[3]).findFirst()
                .orElseThrow(() -> new AssertionError("the active tab draws no selected fill"));
        assertEquals(ButtonPainter.HEIGHT, selected[3] - selected[1] + 2, "the selected tab's frame");
        frame.click("Save");
        frame.click(ManagerTab.AUDIT.label());
        assertEquals(List.of("save", ManagerTab.AUDIT.name()), ran);
    }

    /**
     * Explainer mode over five tabs laid 50 pixels apart on the tab row (decisions
     * question-button-toggles-explainer-mode-with-the-drag-mask, explainer-lines-are-the-operators-tab-descriptions).
     */
    @Nested
    class ExplainerModeOverTheTabs {
        private final ExplainerMode explainer = new ExplainerMode(ExplainerTargets.forTabs(tab ->
                new DragMask.Rect(8 + tab.ordinal() * 50, 20, 56 + tab.ordinal() * 50, 34)));

        private List<DragMask.Rect> fills(DrawnFrame frame, int color) {
            ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> rights = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> bottoms = ArgumentCaptor.forClass(Integer.class);
            Mockito.verify(frame.graphics, Mockito.atLeast(0)).fill(lefts.capture(),
                    tops.capture(), rights.capture(), bottoms.capture(), ArgumentMatchers.eq(color));
            List<DragMask.Rect> rects = new ArrayList<>();
            for (int index = 0; index < lefts.getAllValues().size(); index++) {
                rects.add(new DragMask.Rect(lefts.getAllValues().get(index), tops.getAllValues().get(index),
                        rights.getAllValues().get(index), bottoms.getAllValues().get(index)));
            }
            return rects;
        }

        private DrawnFrame frame() {
            DrawnFrame frame = new DrawnFrame();
            explainer.extract(frame.graphics, DrawnFrame.SCREEN_WIDTH, DrawnFrame.SCREEN_HEIGHT, frame.targets);
            return frame;
        }

        /**
         * One press of [?] shadows the screen and lights one hole over each tab.
         */
        @Test
        void pressDrawsTheMaskWithAHolePerTab() {
            explainer.toggle();
            DrawnFrame frame = frame();
            assertFalse(fills(frame, Palette.DRAG_SHADOW).isEmpty(), "the shadow covers the screen");
            List<DragMask.Rect> tabs = Arrays.stream(ManagerTab.values()).map(tab ->
                    new DragMask.Rect(8 + tab.ordinal() * 50, 20, 56 + tab.ordinal() * 50, 34)).toList();
            assertEquals(tabs, fills(frame, Palette.DRAG_HIGHLIGHT));
        }

        /**
         * Hovering the Items tab in the mode opens a tooltip reading the Items paragraph, starting at its first
         * sentence.
         */
        @Test
        void hoveringItemsInTheModeShowsTheItemsParagraph() {
            explainer.toggle();
            DrawnFrame frame = frame();
            String key = frame.targets.hovered(20, 25).orElseThrow();
            String text = explainer.tooltipFor(key).orElseThrow().getString();
            assertTrue(text.startsWith("Search for items, view their explicit or derived values, and recipe ratios"
                    + " to help make decisions on values."), text);
        }

        /**
         * A span the paragraph sets between backticks draws styled with no backtick shown, the text around it plain.
         */
        @Test
        void backtickedSpanDrawsStyledWithoutItsBackticks() {
            explainer.toggle();
            String key = frame().targets.hovered(20, 25).orElseThrow();
            Component shown = explainer.tooltipFor(key).orElseThrow();
            assertFalse(shown.getString().contains("`"), shown.getString());
            List<String> styled = new ArrayList<>();
            shown.visit((style, piece) -> {
                if (ExplainerMode.CODE_STYLE.equals(style)) {
                    styled.add(piece);
                }
                return Optional.empty();
            }, Style.EMPTY);
            assertEquals(List.of("anchor items", "Anchor items"), styled);
        }

        /**
         * A click anywhere in the mode leaves it, the next frame drawing no shadow.
         */
        @Test
        void clickAnywhereLeavesTheMode() {
            explainer.toggle();
            assertTrue(frame().targets.click(300, 200));
            assertEquals(List.of(), fills(frame(), Palette.DRAG_SHADOW));
        }

        /**
         * A paragraph too tall at full size for either side of its tab draws shrunk, one that fits draws at full
         * size.
         */
        @Test
        void paragraphFittingNeitherSideShrinks() {
            GroupsPane.Bounds tab = new GroupsPane.Bounds(300, 20, 340, 34);
            assertEquals(List.of(ExplainerTooltip.SHRUNK, 1f), List.of(ExplainerTooltip.scaleFor(180, tab, 200),
                    ExplainerTooltip.scaleFor(150, tab, 200)));
        }

        /**
         * A second press of [?] draws no shadow and hovers no explainer text.
         */
        @Test
        void secondPressDrawsNoMask() {
            explainer.toggle();
            explainer.toggle();
            DrawnFrame frame = frame();
            assertEquals(List.of(), fills(frame, Palette.DRAG_SHADOW));
            assertEquals(Optional.empty(), frame.targets.hovered(20, 25));
        }
    }

    /**
     * A left-button press carrying shift runs the follow action of the area under it and leaves its left action
     * unrun, a plain left-button press runs the left one, and a right-button press runs neither (decision
     * shift-left-click-follows-plain-left-modifies).
     */
    @Test
    void pressReachesTheActionForItsGesture() {
        List<String> ran = new ArrayList<>();
        ClickTargets targets = new ClickTargets();
        targets.add(0, 0, 50, 10, () -> ran.add("modify"));
        targets.addFollow(0, 0, 50, 10, () -> ran.add("follow"), null);
        ValuesManagerScreen.clickTargets(targets,
                new MouseButtonEvent(5, 5, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOD_SHIFT)));
        assertEquals(List.of("follow"), ran);
        ValuesManagerScreen.clickTargets(targets,
                new MouseButtonEvent(5, 5, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)));
        assertEquals(List.of("follow", "modify"), ran);
        ValuesManagerScreen.clickTargets(targets,
                new MouseButtonEvent(5, 5, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 0)));
        assertEquals(List.of("follow", "modify"), ran);
    }

    /**
     * Two cursor positions over one hovered area place its tooltip at one point, the point the area's bounds anchor
     * (decision tooltips-anchor-below-their-element-and-grow-upward-when-short-of-room).
     */
    @Test
    void tooltipStaysPutAsTheCursorMovesOverOneElement() {
        ClickTargets targets = new ClickTargets();
        targets.addHover(40, 20, 50, 14, "stick");
        List<Vector2ic> points = new ArrayList<>();
        for (int mouseX : new int[] {42, 85}) {
            int mouseY = mouseX == 42 ? 22 : 31;
            points.add(ValuesManagerScreen.anchoredTooltip(targets, mouseX, mouseY).orElseThrow()
                    .positionTooltip(400, 300, mouseX, mouseY, 60, 30));
        }
        TooltipAnchor.Point anchored = TooltipAnchor.place(new GroupsPane.Bounds(40, 20, 90, 34), 60, 30, 400, 300);
        assertEquals(List.of(new Vector2i(anchored.x(), anchored.y()),
                new Vector2i(anchored.x(), anchored.y())), points);
    }

    @Nested
    class RightClickOnATextBox {
        private final Font font = mock(Font.class);
        private final List<String> responded = new ArrayList<>();

        {
            when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
            when(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer(call -> call.getArgument(0));
        }

        private EditBox box(int x, int y, String text) {
            EditBox box = new EditBox(font, x, y, 100, 12, Component.literal(""));
            box.setValue(text);
            box.setResponder(responded::add);
            return box;
        }

        private static MouseButtonEvent press(double x, double y, int button) {
            return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
        }

        /**
         * A right-button press inside a box holding "iron" empties it, its responder takes the empty text and the
         * press is consumed (decision right-click-empties-every-text-box-in-the-manager).
         */
        @Test
        void rightPressInsideABoxEmptiesItAndRunsItsResponder() {
            EditBox search = box(0, 0, "iron");
            responded.clear();
            boolean took = ValuesManagerScreen.emptyBoxUnder(List.of(search),
                    press(5, 5, GLFW.GLFW_MOUSE_BUTTON_RIGHT));
            assertTrue(took);
            assertEquals("", search.getValue());
            assertEquals(List.of(""), responded);
        }

        /**
         * Each of the manager's boxes, the Items search, the Prefabs search and rename, the New group name and the
         * builder's number and dropdown search, empties under its own right press and leaves the others whole.
         */
        @Test
        void rightPressEmptiesOnlyTheBoxUnderIt() {
            List<EditBox> boxes = List.of(box(0, 0, "items"), box(0, 20, "prefabs"), box(0, 40, "rename"),
                    box(0, 60, "group"), box(0, 80, "4"), box(0, 100, "dropdown"));
            for (int each = 0; each < boxes.size(); each++) {
                ValuesManagerScreen.emptyBoxUnder(boxes, press(5, each * 20 + 5, GLFW.GLFW_MOUSE_BUTTON_RIGHT));
                assertEquals("", boxes.get(each).getValue());
                for (int later = each + 1; later < boxes.size(); later++) {
                    assertFalse(boxes.get(later).getValue().isEmpty());
                }
            }
        }

        /**
         * A right press no box covers, a left press inside a box and a right press on a hidden box keep the text and
         * pass the press on.
         */
        @Test
        void pressOutsideABoxOrOfAnotherButtonKeepsTheText() {
            EditBox search = box(0, 0, "iron");
            EditBox hidden = box(0, 20, "iron");
            hidden.visible = false;
            List<EditBox> boxes = List.of(search, hidden);
            assertFalse(ValuesManagerScreen.emptyBoxUnder(boxes, press(300, 5, GLFW.GLFW_MOUSE_BUTTON_RIGHT)));
            assertFalse(ValuesManagerScreen.emptyBoxUnder(boxes, press(5, 5, GLFW.GLFW_MOUSE_BUTTON_LEFT)));
            assertFalse(ValuesManagerScreen.emptyBoxUnder(boxes, press(5, 25, GLFW.GLFW_MOUSE_BUTTON_RIGHT)));
            assertEquals("iron", search.getValue());
            assertEquals("iron", hidden.getValue());
        }
    }

    @Nested
    class Opening {

        /**
         * Opening the manager reads the model and then the draft's resolve, uncovered, derived-from, groups,
         * constants and processes, and asks the server for no inventory (decision
         * client-builds-items-and-tags-from-its-own-registries).
         */
        @Test
        void openReadsTheModelAndTheDraftAndNoInventory() {
            List<Posted> requested = new ArrayList<>();
            ManagerActions actions = recordingActions(requested);
            reads(actions, new ManagerDraft()).open(new ListedEntrySource(List.of(), List.of()), () -> { });
            assertEquals(List.of(WorkbenchOperations.MODEL, WorkbenchOperations.RESOLVE_VALUES,
                    WorkbenchOperations.UNCOVERED_VALUES, WorkbenchOperations.DERIVED_FROM,
                    WorkbenchOperations.DESCRIBE_GROUPS, WorkbenchOperations.EVALUATE_CONSTANTS,
                    WorkbenchOperations.PROCESSES), operations(requested));
        }
    }

    @Nested
    class Posting {
        private static final List<String> DRAFT_READS = List.of(WorkbenchOperations.RESOLVE_VALUES,
                WorkbenchOperations.UNCOVERED_VALUES, WorkbenchOperations.DERIVED_FROM,
                WorkbenchOperations.DESCRIBE_GROUPS, WorkbenchOperations.EVALUATE_CONSTANTS,
                WorkbenchOperations.PROCESSES);

        private final List<Posted> requested = new ArrayList<>();
        private final ManagerDraft draft = new ManagerDraft();
        private final ValuesTab values = new ValuesTab(draft);
        private final ManagerReads reads = reads(recordingActions(requested), draft, values);

        // The members each draft read posted, by key, one list per read, in the order DRAFT_READS names them.
        private List<List<String>> postedKeys() {
            List<Posted> drafted = requested.stream().filter(each -> DRAFT_READS.contains(each.operation())).toList();
            assertEquals(DRAFT_READS, drafted.stream().map(Posted::operation).toList());
            List<List<String>> keys = drafted.stream().map(each -> JsonParser.parseString(each.text())
                    .getAsJsonObject().getAsJsonArray("changed").asList().stream()
                    .map(member -> member.getAsJsonObject().get("key").getAsString()).toList()).toList();
            requested.clear();
            return keys;
        }

        /**
         * The open's draft reads post no member, the refreshes after one edit post that member alone, and the
         * refreshes after the save answered post no member again (decision requests-post-only-the-changed-members).
         */
        @Test
        void draftReadsPostOnlyTheMembersChangedSinceTheSave() {
            reads.open(new ListedEntrySource(List.of(), List.of()), () -> { });
            assertEquals(List.of(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()), postedKeys());
            values.readEntries(new ListedEntrySource(
                    List.of(new ManagerEntrySource.ItemName("minecraft:stick", "Stick")), List.of()));
            values.select("minecraft:stick");
            values.edit("5");
            reads.readAfresh();
            List<String> stick = List.of("stick");
            assertEquals(List.of(stick, stick, stick, stick, stick, stick), postedKeys());
            reads.save(() -> { });
            assertEquals(List.of(List.of("stick")), requested.stream()
                    .filter(each -> WorkbenchOperations.SAVE_MODEL.equals(each.operation()))
                    .map(each -> JsonParser.parseString(each.text()).getAsJsonObject().getAsJsonArray("changed")
                            .asList().stream().map(member -> member.getAsJsonObject().get("key").getAsString())
                            .toList()).toList());
            assertEquals(List.of(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()), postedKeys());
        }
    }

    /**
     * One request the fake line took.
     *
     * @param operation the operation requested
     * @param text      the text it posted
     */
    private record Posted(String operation, String text) {
    }

    private static List<String> operations(List<Posted> requested) {
        return requested.stream().map(Posted::operation).toList();
    }

    // Answers the model from the canned replies and a save with the model the save posted merged, as the server
    // does, and records every operation requested with its posted text.
    private static ManagerActions recordingActions(List<Posted> requested) {
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        doAnswer(call -> {
            String operation = call.getArgument(0);
            requested.add(new Posted(operation, call.getArgument(1)));
            String reply = WorkbenchOperations.SAVE_MODEL.equals(operation) ? SAVED : CANNED.get(operation);
            if (reply != null) {
                call.<Consumer<String>>getArgument(2).accept(reply);
            }
            return null;
        }).when(actions).request(anyString(), anyString(), any());
        return actions;
    }

    private static ManagerReads reads(ManagerActions actions, ManagerDraft draft) {
        ValuesTab values = new ValuesTab(draft);
        return reads(actions, draft, values);
    }

    private static ManagerReads reads(ManagerActions actions, ManagerDraft draft, ValuesTab values) {
        ItemIcons icons = mock(ItemIcons.class);
        GroupsPane groups = new GroupsPane(new GroupsTab(draft), values, icons, actions, builder(actions, icons));
        VariablesPane variables = new VariablesPane(new VariablesTab(draft), draft, icons, actions,
                builder(actions, icons));
        ProcessesPane processes = new ProcessesPane(new ProcessesTab(draft), draft, actions, icons, List::of);
        AbilitiesPane abilities = new AbilitiesPane(new AbilitiesTab(), actions, icons, builder(actions, icons),
                key -> key, ref -> java.util.Map.of(), java.util.Set::of);
        return new ManagerReads(actions, values, groups, variables, processes, abilities);
    }

    private static ExpressionBuilderPane builder(ManagerActions actions, ItemIcons icons) {
        return new ExpressionBuilderPane(actions, icons, List::of, List::of);
    }

    /**
     * The icons the screen builds hover each item's anchors as the Items tab reads them, so every icon ItemLinks
     * draws lists them (decision item-hover-lists-its-anchors).
     */
    @Test
    void itemIconsHoverTheAnchorsTheItemsTabReads() {
        ValuesTab values = new ValuesTab();
        values.readDerivedFrom("""
                {"items": {"minecraft:bronze_rod": {"inputs": [{"id": "minecraft:bronze_ingot", "count": 1}],
                  "output": 1, "anchors": ["minecraft:copper_ingot", "minecraft:tin_ingot"]}}}
                """);
        // The name reads the item registry, which a unit test leaves unbound.
        ItemIcons icons = Mockito.spy(ValuesManagerScreen.itemIconsOver(values));
        Mockito.doReturn("Bronze Rod").when(icons).nameOf(anyString());
        List<TooltipLine> lines = icons.tooltipFor(ItemIcons.hoverKey("minecraft:bronze_rod")).orElseThrow();
        assertEquals(new TooltipLine.Members(values.anchorsOf("minecraft:bronze_rod"), 0), lines.getLast());
        assertEquals(2, values.anchorsOf("minecraft:bronze_rod").size());
    }

    /**
     * Hovering the vs of an explicit row's would-be equivalency reads why the row shows it, on one line (decision
     * explicit-value-row-reads-vs-its-would-be-derivative).
     */
    @Test
    void vsHoverReadsWhyTheExplicitValueStands() {
        assertEquals(Optional.of(List.of(new TooltipLine.Text(Component.literal(
                "Using explicit value instead of derivative recipe shown here")))),
                ValuesManagerScreen.oneLineTooltip(ValuesPane.VS_HOVER_KEY));
    }

    /**
     * A run of goo amount lines reaches the tooltip as one grid element between the text lines around it (decision
     * tooltip-goo-lists-fill-ceil-sqrt-columns-aligned-on-the-widest-digits).
     */
    @Test
    void aRunOfAmountLinesFoldsIntoOneGrid() {
        TooltipLine.GooAmount metal = new TooltipLine.GooAmount("metal", "3");
        TooltipLine.GooAmount rock = new TooltipLine.GooAmount("rock", "1/2");
        var elements = ValuesManagerScreen.tooltipElements(List.of(new TooltipLine.Text(Component.literal("stick")),
                metal, rock, new TooltipLine.Text(Component.literal("after"))));
        assertEquals(3, elements.size(), elements.toString());
        assertEquals(Optional.of(new TooltipLine.GooAmounts(List.of(metal, rock))), elements.get(1).right());
    }
}
