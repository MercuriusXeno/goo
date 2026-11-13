package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mockito;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the Prefabs tab's search box at the pane's layout seam, with Font mocked and no Screen: where the box and the
 * list stand, when the box shows, and how the list's scroll bar follows the search (decisions
 * prefabs-tab-searches-its-list-by-name, every-tab-draws-a-scroll-bar).
 */
class VariablesPaneTest {
    private static final int PREFABS = 12;
    private static final GroupsPane.Bounds VIEW = new GroupsPane.Bounds(0, 20, 400, 120);

    private Font font;
    private GuiGraphicsExtractor graphics;
    private VariablesTab variables;
    private VariablesPane pane;

    private static String model() {
        String entries = IntStream.range(0, PREFABS).mapToObj(index -> """
                {"key": "prefab%d", "keySeparator": null, "kind": "VALUE", "text": "8", "entries": [],
                 "leading": null, "trailing": null, "closingGap": null}""".formatted(index))
                .collect(Collectors.joining(", "));
        return """
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [%s],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """.formatted(entries);
    }

    @BeforeEach
    void standThePane() {
        font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        when(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer(call -> call.getArgument(0));
        graphics = mock(GuiGraphicsExtractor.class);
        ManagerDraft draft = new ManagerDraft();
        draft.read(model());
        variables = new VariablesTab(draft);
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        ItemIcons icons = mock(ItemIcons.class);
        ExpressionBuilderPane builder = new ExpressionBuilderPane(actions, icons, List::of, List::of);
        pane = new VariablesPane(variables, draft, icons, actions, builder);
        pane.init(font);
        pane.show(true);
    }

    private void draw() {
        ScreenDraw.extract(pane, graphics, font, VIEW, new ClickTargets(), 0, 0);
    }

    private EditBox searchBox() {
        EditBox box = pane.searchBox();
        assertNotNull(box);
        return box;
    }

    /**
     * Consecutive prefabs stand one tighter pitch apart; it was 39 pixels for a number prefab used nowhere (decisions
     * prefab-lines-tighten-their-padding, prefab-head-is-name-pencil-and-value-then-used-by).
     */
    @Test
    void prefabRowsTakeTheTighterPitch() {
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, font, new GroupsPane.Bounds(0, 20, 400, 4000), frame.targets, 0, 0);
        List<Integer> tops = frame.topsOf(List.of("$prefab0", "$prefab1"));
        int pitch = tops.get(1) - tops.get(0);
        assertEquals(VariablesPane.UNUSED_PREFAB_PITCH, pitch);
        assertTrue(pitch - ExpressionLines.TEXT_LINE_HEIGHT < 39, "the pitch was 39 before the value's line");
    }

    /**
     * The box shows only while the Prefabs tab is the one shown.
     */
    @Test
    void searchBoxShowsWithTheTab() {
        pane.show(false);
        assertFalse(searchBox().visible);
        pane.show(true);
        assertTrue(searchBox().visible);
    }

    /**
     * With the list scrolled by the wheel, typing in the box returns the list to its top.
     */
    @Test
    void wheelNotchMovesTheListOneWheelStep() {
        // Decision one-wheel-step-and-one-dialog-primitive.
        draw();
        pane.scroll(-1);
        assertEquals(ScrollBar.WHEEL_STEP, pane.listBar().offset());
    }

    /**
     * The search box stands 14 tall at the list's top and stretches to the [+] at the row's right end, one gap short
     * of it, and a prefab's rename pencil stands one pencil gap past its name (decision
     * one-search-row-per-list-with-its-create-control-beside-it).
     */
    @Test
    void searchRowStretchesToItsCreateControlAndThePencilStandsOneGapPastTheName() {
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), VIEW, frame.targets, 0, 0);
        EditBox box = searchBox();
        int[] plus = frame.buttonAround(frame.drawn().stream().filter(text -> text.text().equals("+")
                && text.y() < VIEW.top() + SearchRow.HEIGHT).findFirst().orElseThrow());
        assertEquals(List.of(VIEW.top(), SearchRow.HEIGHT, plus[0] - SearchRow.GAP),
                List.of(box.getY(), box.getHeight(), box.getX() + box.getWidth()));
        assertEquals(VIEW.right() - ScrollBar.WIDTH - ScrollBar.GAP, plus[2], "the [+] at the row's right end");
        DrawnFrame.Text name = frame.findFirst("$prefab0");
        assertEquals(name.x() + DrawnFrame.font().width(name.text()) + ButtonPainter.PENCIL_GAP,
                frame.buttonAround(frame.findFirst("✎"))[0]);
    }

    @Test
    void typingReturnsTheListToItsTop() {
        draw();
        pane.scroll(-1);
        pane.scroll(-1);
        assertTrue(pane.listBar().offset() > 0);
        searchBox().setValue("prefab1");
        assertEquals(0, pane.listBar().offset());
    }

    /**
     * Unsearched, the whole draft overflows the view, so the thumb is shorter than its track; a search keeping one
     * prefab fits the view, so the bar measures that block alone and the thumb fills the track.
     */
    @Test
    void scrollBarMeasuresTheFilteredPrefabsAlone() {
        draw();
        int track = 50;
        assertTrue(pane.listBar().thumb(0, track).height() < track);
        searchBox().setValue("prefab11");
        assertEquals(List.of("prefab11"), variables.listed().stream().map(VariablesTab.ConstantBlock::name).toList());
        draw();
        assertEquals(track, pane.listBar().thumb(0, track).height());
    }

    // The tops of every fill the draw made at one color, in draw order.
    private List<Integer> fillTopsAt(int color) {
        ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
        verify(graphics, atLeastOnce()).fill(anyInt(), tops.capture(), anyInt(), anyInt(), eq(color));
        return tops.getAllValues();
    }

    /**
     * Scrolled to its top, the first prefab's Rename and Delete borders start at least one pixel row below the list's
     * clip top; each block draws its Rename then its Delete, so the first two border fills are the first block's.
     */
    @Test
    void firstPrefabButtonsPadBelowTheListTop() {
        draw();
        ArgumentCaptor<Integer> clipTop = ArgumentCaptor.forClass(Integer.class);
        verify(graphics).enableScissor(anyInt(), clipTop.capture(), anyInt(), anyInt());
        List<Integer> borders = fillTopsAt(Palette.BORDER);
        assertTrue(borders.get(0) > clipTop.getValue(), "Rename top " + borders.get(0) + " vs " + clipTop.getValue());
        assertTrue(borders.get(1) > clipTop.getValue(), "Delete top " + borders.get(1) + " vs " + clipTop.getValue());
    }

    /**
     * The second prefab's Rename and Delete borders start at least two rows below the separator above it, leaving an
     * empty row between them; each block draws its Rename, its Delete, then its value's frame and pencil.
     */
    @Test
    void laterPrefabButtonsPadBelowTheirSeparator() {
        draw();
        int separator = fillTopsAt(Palette.SEPARATOR).get(0);
        List<Integer> borders = fillTopsAt(Palette.BORDER);
        int bordersPerBlock = 4;
        int rename = borders.get(bordersPerBlock);
        int delete = borders.get(bordersPerBlock + 1);
        assertTrue(rename >= separator + 2, "Rename top " + rename + " vs " + separator);
        assertTrue(delete >= separator + 2, "Delete top " + delete + " vs " + separator);
    }

    /**
     * A prefab's head reads its name with a pencil just past it and a red x at the row's right end (the operator's
     * rulings).
     */
    @Test
    void headDrawsPencilByTheNameAndARedXAtTheEnd() {
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, font, TALL_VIEW, frame.targets, 0, 0);
        DrawnFrame.Text name = frame.drawn().stream().filter(text -> text.text().equals("$prefab0")).findFirst()
                .orElseThrow();
        DrawnFrame.Text pencil = frame.drawn().stream().filter(text -> text.text().equals("✎")).findFirst()
                .orElseThrow();
        int pastName = name.x() + "$prefab0".length() * 6;
        assertEquals(name.y(), pencil.y());
        assertTrue(pencil.x() > pastName && pencil.x() < pastName + 12, pencil + " vs " + name);
        DrawnFrame.Text cross = frame.drawn().stream().filter(text -> text.text().equals("✗")).findFirst()
                .orElseThrow();
        assertEquals(Palette.DANGER, cross.color());
        int listRight = TALL_VIEW.right() - ScrollBar.WIDTH - ScrollBar.GAP;
        assertEquals(listRight - 6 - ButtonPainter.PADDING, cross.x());
    }

    /**
     * Every tooltip hint a drawn Prefabs frame registers reads Rename or Delete
     * (decision values-manager-controls-carry-no-tooltip-but-rename-delete-and-names).
     */
    @Test
    void everyTooltipHintIsRenameOrDelete() {
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, font, TALL_VIEW, frame.targets, 0, 0);
        List<String> hints = frame.targets.hoverKeys().stream().map(BuilderOverlays::tooltipFor)
                .flatMap(Optional::stream).map(component -> component.getString()).distinct().toList();
        assertEquals(List.of("Rename", "Delete"), hints.stream().sorted(Comparator.reverseOrder()).toList());
    }

    private static final int GAME_LINE_HEIGHT = 9;

    // A small amount stands a pixel or three lower than a full-size label on its line (decision
    // goo-amounts-draw-smaller-and-a-pixel-lower-everywhere), so one line holds texts within half a line's height.
    private static boolean onLine(int labelY, int amountY) {
        return Math.abs(labelY - amountY) < ExpressionLines.TEXT_LINE_HEIGHT / 2;
    }
    private static final int SPRITE = 16;

    private static final GroupsPane.Bounds TALL_VIEW = new GroupsPane.Bounds(0, 20, 400, 6000);
    private static final GroupsPane.Bounds FITS_THE_EDITED_BLOCK = new GroupsPane.Bounds(0, 20, 400, 320);

    // A Prefabs pane over the twelve prefabs model() names, built fresh with no draw yet.
    private static VariablesPane twelvePrefabs(VariablesTab prefabs, ManagerDraft draft) {
        return twelvePrefabs(prefabs, draft, mock(ManagerActions.class));
    }

    private static VariablesPane twelvePrefabs(VariablesTab prefabs, ManagerDraft draft, ManagerActions actions) {
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane read = new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        read.init(DrawnFrame.font());
        read.show(true);
        return read;
    }

    // Each block's top inside the list's content once a follow lands on the named block, read off a view tall enough
    // to draw every block unscrolled.
    private static List<Integer> blockTopsFollowed(String name) {
        ManagerDraft draft = new ManagerDraft();
        draft.read(model());
        VariablesPane tall = twelvePrefabs(new VariablesTab(draft), draft);
        tall.jumpTo(name);
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(tall, frame.graphics, DrawnFrame.font(), TALL_VIEW, frame.targets, 0, 0);
        DrawnFrame settled = new DrawnFrame();
        ScreenDraw.extract(tall, settled.graphics, DrawnFrame.font(), TALL_VIEW, settled.targets, 0, 0);
        List<Integer> names = settled.topsOf(IntStream.range(0, PREFABS).mapToObj(index -> "$prefab" + index)
                .toList());
        return names.stream().map(y -> y - names.getFirst()).toList();
    }

    /**
     * A shift + left follow to a prefab well below the fold centers its block in the list, the block read-only
     * (decisions shift-follow-centers-the-target-in-its-scrolling-view, every-follow-lands-selected-and-read-only).
     */
    @Test
    void jumpCentersTheFollowedBlockInTheList() {
        int target = 6;
        List<Integer> tops = blockTopsFollowed("prefab" + target);
        DrawnFrame before = new DrawnFrame();
        ScreenDraw.extract(pane, before.graphics, DrawnFrame.font(), FITS_THE_EDITED_BLOCK, before.targets, 0, 0);
        pane.jumpTo("prefab" + target);
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), FITS_THE_EDITED_BLOCK, frame.targets, 0, 0);
        int[] list = frame.firstScissor();
        int blockHeight = tops.get(target + 1) - tops.get(target);
        assertTrue(blockHeight <= list[3] - list[1], "the followed block outgrows the list");
        assertEquals(tops.get(target) + blockHeight / 2 - (list[3] - list[1]) / 2, pane.listBar().offset());
        assertEquals(Optional.empty(), variables.editing());
    }

    /**
     * A follow lands on the prefab selected and read-only: a builder open over another prefab closes and the block
     * draws in the selected fill (decision every-follow-lands-selected-and-read-only).
     */
    @Test
    void jumpLandsTheBlockSelectedAndReadOnly() {
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        ManagerDraft draft = new ManagerDraft();
        draft.read(model());
        VariablesTab prefabs = new VariablesTab(draft);
        ItemIcons icons = mock(ItemIcons.class);
        ExpressionBuilderPane builder = new ExpressionBuilderPane(actions, icons, List::of, List::of);
        VariablesPane read = new VariablesPane(prefabs, draft, icons, actions, builder);
        prefabs.startEditing("prefab2");
        DrawnFrame editing = new DrawnFrame();
        ScreenDraw.extract(read, editing.graphics, DrawnFrame.font(), TALL_VIEW, editing.targets, 0, 0);
        assertTrue(builder.isOpen(), "the builder stood open over $prefab2");
        read.jumpTo("prefab6");
        assertFalse(builder.isOpen());
        DrawnFrame landed = new DrawnFrame();
        ScreenDraw.extract(read, landed.graphics, DrawnFrame.font(), TALL_VIEW, landed.targets, 0, 0);
        DrawnFrame.Text name = landed.find("$prefab6");
        List<int[]> fills = landed.fillsOf(Palette.SELECTED_FILL);
        assertTrue(fills.size() == 1 && fills.getFirst()[1] <= name.y() && name.y() < fills.getFirst()[3],
                fills.size() + " selected fills over " + name);
    }

    /**
     * The first frame after a follow already draws the block centered, where it settles, and glowing at age zero,
     * with the search empty (decision one-reveal-primitive-centers-before-the-first-draw).
     */
    @Test
    void jumpDrawsTheBlockCenteredAndGlowingOnTheFirstFrame() {
        searchBox().setValue("prefab1");
        DrawnFrame searched = new DrawnFrame();
        ScreenDraw.extract(pane, searched.graphics, DrawnFrame.font(), FITS_THE_EDITED_BLOCK, searched.targets, 0, 0);
        pane.jumpTo("prefab6");
        assertBlockRevealedOnTheFirstFrame(pane, "prefab6", FITS_THE_EDITED_BLOCK);
        assertEquals("", searchBox().getValue());
    }

    /**
     * Creating a prefab with "wax" in the search empties the search and draws the new block centered and glowing on
     * the next frame (decision create-reveals-the-new-row-like-a-follow).
     */
    @Test
    void createRevealsTheNewBlockCenteredAndGlowing() {
        searchBox().setValue("wax");
        DrawnFrame list = new DrawnFrame();
        ScreenDraw.extract(pane, list.graphics, DrawnFrame.font(), FITS_THE_EDITED_BLOCK, list.targets, 0, 0);
        list.click("+");
        DrawnFrame naming = new DrawnFrame();
        ScreenDraw.extract(pane, naming.graphics, DrawnFrame.font(), FITS_THE_EDITED_BLOCK, naming.targets, 0, 0);
        EditBox box = pane.nameBox();
        assertNotNull(box);
        box.setValue("zz_fresh");
        naming.click("✓");
        assertBlockRevealedOnTheFirstFrame(pane, "zz_fresh", FITS_THE_EDITED_BLOCK);
        assertEquals("", searchBox().getValue());
    }

    // The next frame draws the block where it settles, scrolled into the list, glowing at age zero over its name.
    private static void assertBlockRevealedOnTheFirstFrame(VariablesPane shown, String name, GroupsPane.Bounds view) {
        DrawnFrame first = new DrawnFrame();
        ScreenDraw.extract(shown, first.graphics, DrawnFrame.font(), view, first.targets, 0, 0);
        assertTrue(shown.listBar().offset() > 0, name + " scrolled to");
        DrawnFrame.Text drawn = first.find(PrefabNames.marked(name));
        DrawnFrame settled = new DrawnFrame();
        ScreenDraw.extract(shown, settled.graphics, DrawnFrame.font(), view, settled.targets, 0, 0);
        assertEquals(settled.find(PrefabNames.marked(name)).y(), drawn.y(), "drawn where it settles");
        List<int[]> glows = first.fillsOf(Palette.GLOW);
        assertTrue(glows.size() == 1 && glows.getFirst()[1] <= drawn.y() && drawn.y() < glows.getFirst()[3],
                glows.size() + " glows over " + drawn);
    }

    /**
     * A scalar prefab's name line reads its name, ✎, Used by with its links and the ✗ at one y in that order; the
     * next line reads Value: one indent in, the expression, and the evaluated amount in parentheses right of it; its
     * pencil opens the rename box in the name's place (decision
     * prefab-head-is-name-pencil-used-by-then-value-with-its-amount).
     */
    @Test
    void scalarHeadReadsNameUsedByThenValueWithItsAmount() {
        VariablesPane read = readByEgg(mock(ManagerActions.class));
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(read, frame.graphics, DrawnFrame.font(), VIEW, frame.targets, 0, 0);
        DrawnFrame.Text name = frame.find("$base");
        DrawnFrame.Text pencil = frame.findFirst("✎");
        DrawnFrame.Text usedBy = frame.find("Used by ");
        DrawnFrame.Text egg = linkText(frame, "$egg");
        DrawnFrame.Text cross = frame.find("✗");
        assertEquals(List.of(name.y(), name.y(), name.y(), name.y()),
                List.of(pencil.y(), usedBy.y(), egg.y(), cross.y()), frame.drawn().toString());
        assertTrue(name.x() < pencil.x() && pencil.x() < usedBy.x() && usedBy.x() < egg.x() && egg.x() < cross.x(),
                frame.drawn().toString());
        DrawnFrame.Text value = frame.find("Value:");
        assertEquals(name.x() + ControlFlow.INDENT, value.x());
        assertTrue(value.y() > name.y() && value.y() - name.y() < 2 * ButtonPainter.HEIGHT, value + " vs " + name);
        DrawnFrame.Text expression = frame.drawn().stream().filter(text -> text.text().equals("384")
                && text.x() > value.x()).findFirst().orElseThrow(() -> new AssertionError(frame.drawn().toString()));
        assertEquals(value.y(), expression.y(), frame.drawn().toString());
        assertTrue(value.x() < expression.x(), frame.drawn().toString());
        frame.clickFirst("✎");
        DrawnFrame renaming = new DrawnFrame();
        ScreenDraw.extract(read, renaming.graphics, DrawnFrame.font(), VIEW, renaming.targets, 0, 0);
        assertEquals(frame.count("✎") - 1, renaming.count("✎"), "the rename box stands in the pencil's place");
    }

    private static final String READ_PREFABS = """
            {"path": "base_values.json", "members": [
              {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                {"key": "wood", "keySeparator": null, "kind": "VALUE", "text": "8", "entries": [],
                 "leading": null, "trailing": null, "closingGap": null},
                {"key": "plank", "keySeparator": null, "kind": "VALUE", "text": "\\"$wood / 4\\"", "entries": [],
                 "leading": null, "trailing": null, "closingGap": null}],
               "leading": null, "trailing": null, "closingGap": null}]}
            """;
    // wood is named by the stick directly, by the prefab plank and by the group logs, and its cascade reaches the
    // terminus item cake, which no expression of its own names wood.
    private static final String WOOD_READ = """
            "name": "wood", "kind": "scalar", "value": "8", "error": null,
            "direct": ["minecraft:stick"], "usedBy": ["minecraft:stick", "minecraft:cake"],
            "readers": [{"id": "plank", "kind": "constant"}, {"id": "#logs", "kind": "group"},
                        {"id": "minecraft:stick", "kind": "item"}]""";
    private static final String READ_EVALUATED = """
            {"constants": [{%s},
              {"name": "plank", "kind": "scalar", "value": "2", "error": null, "direct": [], "usedBy": [],
               "readers": []}]}
            """.formatted(WOOD_READ);

    // A Prefabs pane over wood and plank, read by plank and the group logs.
    private static VariablesPane woodReadByPlankAndLogs(ManagerActions actions) {
        ManagerDraft draft = new ManagerDraft();
        draft.read(READ_PREFABS);
        VariablesTab prefabs = new VariablesTab(draft);
        prefabs.readEvaluated(READ_EVALUATED);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane read = new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        read.init(DrawnFrame.font());
        read.show(true);
        return read;
    }

    private static DrawnFrame.Text linkText(DrawnFrame frame, String label) {
        return frame.drawn().stream().filter(text -> text.text().equals(label) && text.color() == Palette.LINK)
                .reduce((first, last) -> last).orElseThrow(() -> new AssertionError(label + " in "
                        + frame.drawn()));
    }

    private static List<Integer> itemXs(DrawnFrame frame) {
        ArgumentCaptor<Integer> xs = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, Mockito.atLeast(0)).item(any(), xs.capture(), anyInt());
        return xs.getAllValues();
    }

    /**
     * wood's used-by run draws the stick's icon, then the prefab plank's chip and the group logs' chip in the link
     * color (decision
     * used-by-lists-direct-items-and-links-to-prefabs-and-groups).
     */
    @Test
    void usedByDrawsDirectItemsThenPrefabAndGroupLinks() {
        VariablesPane read = woodReadByPlankAndLogs(mock(ManagerActions.class));
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(read, frame.graphics, DrawnFrame.font(), TALL_VIEW, frame.targets, 0, 0);
        assertTrue(frame.targets.hoverKeys().contains(ItemIcons.hoverKey("minecraft:stick")));
        assertEquals(1, itemXs(frame).size(), "one item icon, the stick's");
        DrawnFrame.Text plank = linkText(frame, "$plank");
        DrawnFrame.Text logs = linkText(frame, "#logs");
        assertTrue(itemXs(frame).getFirst() < plank.x(), "stick before plank");
        assertTrue(plank.x() < logs.x(), plank + " before " + logs);
        assertEquals(plank.y(), logs.y());
    }

    /**
     * plank's read view and its builder key the $wood ref by one hover key, PrefabHovers.hoverKey("wood"), so both
     * show the prefab's tooltip (decision value-hover-reads-the-same-editing-and-read-only).
     */
    @Test
    void readViewRefHoversTheKeyTheBuildersRefHovers() {
        VariablesPane read = woodReadByPlankAndLogs(mock(ManagerActions.class));
        DrawnFrame list = new DrawnFrame();
        ScreenDraw.extract(read, list.graphics, DrawnFrame.font(), TALL_VIEW, list.targets, 0, 0);
        DrawnFrame.Text readRef = linkText(list, "$wood");
        assertEquals(Optional.of(PrefabHovers.hoverKey("wood")),
                list.targets.hovered(readRef.x() + 1, readRef.y() + 1));
        list.click("$wood");
        DrawnFrame editing = new DrawnFrame();
        ScreenDraw.extract(read, editing.graphics, DrawnFrame.font(), TALL_VIEW, editing.targets, 0, 0);
        DrawnFrame building = new DrawnFrame();
        ScreenDraw.extract(read, building.graphics, DrawnFrame.font(), TALL_VIEW, building.targets, 0, 0);
        DrawnFrame.Text builderRef = linkText(building, "$wood");
        assertEquals(Optional.of(PrefabHovers.hoverKey("wood")),
                building.targets.hovered(builderRef.x() + 1, builderRef.y() + 1));
    }

    /**
     * A typed prefab's name line carries Used by with the stick's icon and the ✗ at the line's right end; below it
     * Value: stands above each type row (decision prefab-head-is-name-pencil-used-by-then-value-with-its-amount).
     */
    @Test
    void typedHeadCarriesUsedByAndEachTypeRowItsOwnAmount() {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "wood", "keySeparator": null, "kind": "VALUE", "text": "{\\"leaf\\": 4, \\"glow\\": 6}",
                     "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        VariablesTab prefabs = new VariablesTab(draft);
        prefabs.readEvaluated("""
                {"constants": [{"name": "wood", "kind": "tree", "tree": {"leaf": "4", "glow": "6"}, "value": null,
                                "error": null, "direct": ["minecraft:stick"], "usedBy": [], "readers": []}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane read = new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(read, frame.graphics, DrawnFrame.font(), TALL_VIEW, frame.targets, 0, 0);
        DrawnFrame.Text name = frame.find("$wood");
        DrawnFrame.Text usedBy = frame.find("Used by ");
        DrawnFrame.Text cross = frame.find("✗");
        assertEquals(List.of(name.y(), name.y()), List.of(usedBy.y(), cross.y()), frame.drawn().toString());
        int listRight = TALL_VIEW.right() - ScrollBar.WIDTH - ScrollBar.GAP;
        assertEquals(listRight - 6 - ButtonPainter.PADDING, cross.x());
        ArgumentCaptor<Integer> itemXs = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics).item(any(), itemXs.capture(), anyInt());
        assertTrue(itemXs.getValue() > usedBy.x(), "the stick's icon follows Used by");
        DrawnFrame.Text value = frame.find("Value:");
        assertTrue(value.y() > name.y(), value + " below " + name);
        DrawnFrame.Text leafRow = frame.find("4");
        DrawnFrame.Text glowRow = frame.find("6");
        assertTrue(value.y() < leafRow.y() && leafRow.y() < glowRow.y(), frame.drawn().toString());
    }

    // The amounts a prefab's amount line draws, in its order, over the one prefab the draft and the evaluated answer
    // hold.
    private static List<String> amountLineOf(String expression, String evaluated) {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "beam", "keySeparator": null, "kind": "VALUE", "text": %s,
                     "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """.formatted(expression));
        VariablesTab prefabs = new VariablesTab(draft);
        prefabs.readEvaluated("{\"constants\": [{\"name\": \"beam\", " + evaluated + ", \"error\": null}]}");
        VariablesTab.ConstantBlock block = prefabs.blocks().getFirst();
        GooAmountGrid line = VariablesPane.amountLine(ExpressionLines.shownRuns(block.expression(), id -> id), block);
        DrawnFrame frame = new DrawnFrame();
        line.draw(frame.graphics, DrawnFrame.font(), 0, 0);
        return frame.placements().stream().filter(placed -> placed.scale() == Lettering.AMOUNT_SCALE)
                .map(DrawnFrame.Placed::text).toList();
    }

    /**
     * A prefab's amount line is a typed prefab's amounts highest first, a scalar expression's one amount, and none
     * for a plain number, whose amount is the number itself (decisions
     * prefab-head-is-name-pencil-used-by-then-value-with-its-amount,
     * value-lists-order-highest-first-ties-by-type-key).
     */
    @Test
    void anAmountLineReadsTypedAmountsHighestFirstAScalarsOneAmountAndNoneForAPlainNumber() {
        assertEquals(List.of(List.of("6", "4"), List.of("192"), List.of()), List.of(
                amountLineOf("\"{\\\"leaf\\\": 4, \\\"glow\\\": 6}\"",
                        "\"kind\": \"tree\", \"tree\": {\"leaf\": \"4\", \"glow\": \"6\"}, \"value\": null"),
                amountLineOf("\"\\\"$wood * 24\\\"\"", "\"kind\": \"scalar\", \"value\": \"192\""),
                amountLineOf("\"384\"", "\"kind\": \"scalar\", \"value\": \"384\"")));
    }

    private static final List<String> PREVIEWED_TYPES = List.of("rock", "yore", "opulent", "crystal");

    // A prefab summing two others whose preview reads four goo types, the screenshot's $mundane_trim.
    private static VariablesPane typedPreview() {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "trim", "keySeparator": null, "kind": "VALUE", "text": "\\"$diamond + $block\\"",
                     "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        VariablesTab prefabs = new VariablesTab(draft);
        prefabs.readEvaluated("""
                {"constants": [{"name": "trim", "kind": "tree", "tree": {"rock": "192", "yore": "336",
                                "opulent": "32256", "crystal": "96768"}, "value": null, "error": null,
                                "direct": [], "usedBy": [], "readers": []}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        ItemIcons icons = mock(ItemIcons.class);
        return new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
    }

    private static int blitsBetween(DrawnFrame frame, int top, int bottom) {
        ArgumentCaptor<Integer> ys = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeast(0)).blit(any(), any(), anyInt(), ys.capture(), anyFloat(), anyFloat(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        return (int) ys.getAllValues().stream().filter(y -> y >= top && y < bottom).count();
    }

    /**
     * A typed preview draws each goo type's icon where its name stood, the amount beside it (decision
     * prefab-head-is-name-pencil-used-by-then-value-with-its-amount).
     */
    @Test
    void typedPreviewDrawsIconsInPlaceOfTypeNames() {
        DrawnFrame frame = new DrawnFrame();
        typedPreview().extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 20, 2000, 400),
                frame.targets, 0, 0);
        DrawnFrame.Text value = frame.find("Value:");
        // The amount line lays its four icons in two rows (decision
        // prefab-head-is-name-pencil-used-by-then-value-with-its-amount), so the second row stands an icon line down.
        assertEquals(PREVIEWED_TYPES.size(), blitsBetween(frame, value.y() - ButtonPainter.HEIGHT,
                value.y() + ExpressionLines.ICON_LINE_HEIGHT + ButtonPainter.HEIGHT), frame.drawn().toString());
        assertTrue(frame.drawn().stream().anyMatch(text -> text.text().equals("96768") && onLine(text.y(), value.y())),
                frame.drawn().toString());
    }

    private static DrawnFrame.Text greenText(DrawnFrame frame, String label) {
        return frame.drawn().stream().filter(text -> text.text().equals(label) && text.color() == 0xFF80E080)
                .findFirst().orElseThrow(() -> new AssertionError(label + " in " + frame.drawn()));
    }

    private static List<Integer> fillTopsAt(DrawnFrame frame, int color) {
        ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeastOnce()).fill(anyInt(), tops.capture(), anyInt(), anyInt(), eq(color));
        return tops.getAllValues();
    }

    @org.junit.jupiter.api.Nested
    class RemovalMarking {
        private final ManagerActions actions = mock(ManagerActions.class);
        private final ManagerDraft marked = new ManagerDraft();
        private VariablesPane read;

        @BeforeEach
        void standTwelvePrefabs() {
            marked.read(model());
            read = twelvePrefabs(new VariablesTab(marked), marked, actions);
        }

        private DrawnFrame drawn(VariablesPane drawnPane) {
            DrawnFrame frame = new DrawnFrame();
            ScreenDraw.extract(drawnPane, frame.graphics, DrawnFrame.font(), TALL_VIEW, frame.targets, 0, 0);
            return frame;
        }

        /**
         * Clicking a prefab's ✗ marks it: the block still draws, a gold ↺ hinting Restore stands where the ✗ stood,
         * and the draft reads unsaved (decision
         * every-delete-marks-for-removal-and-a-gold-arrow-restores).
         */
        @Test
        void xMarksThePrefabAndAGoldArrowStandsInItsPlace() {
            DrawnFrame list = drawn(read);
            DrawnFrame.Text cross = list.find("✗");
            list.click("✗");
            DrawnFrame after = drawn(read);
            assertTrue(after.texts().contains("$prefab" + (PREFABS - 1)), after.texts().toString());
            DrawnFrame.Text arrow = after.drawn().stream().filter(text -> text.text().equals("↺")).findFirst()
                    .orElseThrow(() -> new AssertionError(after.texts().toString()));
            assertEquals(cross.x(), arrow.x());
            assertEquals(cross.y(), arrow.y());
            assertEquals(Palette.GOLD, arrow.color());
            assertEquals(Optional.of(BuilderOverlays.hintKey("Restore")),
                    after.targets.hovered(arrow.x() + 1, arrow.y() + 1));
            verify(actions).markUnsaved();
        }

        /**
         * Clicking the ↺ of a marked prefab clears its mark: the ✗ stands again and the save path runs its write
         * (decision every-delete-marks-for-removal-and-a-gold-arrow-restores).
         */
        @Test
        void arrowRestoresAndTheSaveRunsItsWrite() {
            drawn(read).click("✗");
            drawn(read).click("↺");
            DrawnFrame after = drawn(read);
            assertEquals(PREFABS, after.texts().stream().filter("✗"::equals).count(), after.texts().toString());
            assertTrue(marked.removals().marked(RemovalMarks.Tab.PREFABS).isEmpty());
            Runnable write = mock(Runnable.class);
            read.applyRemovals(write);
            verify(write).run();
        }
    }

    /**
     * A prefab with a cascade opens the confirm listing its readers, and the confirm's Delete marks the prefab
     * (decision every-delete-marks-for-removal-and-a-gold-arrow-restores).
     */
    @Test
    void confirmsDeleteMarksThePrefab() {
        VariablesPane read = readByEgg(mock(ManagerActions.class));
        DrawnFrame list = new DrawnFrame();
        ScreenDraw.extract(read, list.graphics, DrawnFrame.font(), VIEW, list.targets, 0, 0);
        list.click("✗");
        DrawnFrame confirm = new DrawnFrame();
        ScreenDraw.extract(read, confirm.graphics, DrawnFrame.font(), VIEW, confirm.targets, 0, 0);
        assertTrue(confirm.texts().contains("$egg"), confirm.texts().toString());
        confirm.click("Delete");
        DrawnFrame after = new DrawnFrame();
        ScreenDraw.extract(read, after.graphics, DrawnFrame.font(), VIEW, after.targets, 0, 0);
        assertTrue(after.texts().contains("↺"), after.texts().toString());
    }

    /**
     * The save path sends one delete naming each marked prefab before the draft write (decision
     * every-delete-marks-for-removal-and-a-gold-arrow-restores).
     */
    @Test
    void theSaveDeletesEachMarkedPrefabBeforeTheWrite() {
        ManagerActions actions = mock(ManagerActions.class);
        Mockito.doAnswer(call -> {
            call.<Consumer<String>>getArgument(3).accept("""
                    {"path": "base_values.json", "members": []}""");
            return null;
        }).when(actions).stream(anyString(), anyString(), any(), any(), any());
        ManagerDraft draft = new ManagerDraft();
        draft.read(model());
        VariablesPane read = twelvePrefabs(new VariablesTab(draft), draft, actions);
        draft.removals().mark(RemovalMarks.Tab.PREFABS, "prefab0");
        read.applyRemovals(() -> actions.request(WorkbenchOperations.SAVE_MODEL, "",
                reply -> { }));
        InOrder order = Mockito.inOrder(actions);
        order.verify(actions).stream(eq(WorkbenchOperations.DELETE_CONSTANT),
                ArgumentMatchers.contains("\"name\":\"prefab0\""), any(), any(), any());
        order.verify(actions).request(eq(WorkbenchOperations.SAVE_MODEL),
                anyString(), any());
    }

    /**
     * An open rename box takes a ✓ and a gold ↺ right of it on its row; ✓ sends the rename with the typed name, as
     * Enter does, and ↺ closes the box leaving the name, as Escape does (decisions
     * open-name-box-takes-a-done-checkmark, open-name-box-takes-a-gold-arrow-that-cancels).
     */
    @Test
    void renameBoxTakesADoneCheckAndANevermindArrow() {
        List<AbstractWidget> widgets = new ArrayList<>();
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> {
            widgets.add(call.getArgument(0));
            return call.getArgument(0);
        });
        ManagerDraft draft = new ManagerDraft();
        draft.read(model());
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane read = new VariablesPane(new VariablesTab(draft), draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        read.init(DrawnFrame.font());
        read.show(true);
        String last = "prefab" + (PREFABS - 1);
        DrawnFrame list = new DrawnFrame();
        ScreenDraw.extract(read, list.graphics, DrawnFrame.font(), TALL_VIEW, list.targets, 0, 0);
        clickRenameOf(list, last);
        DrawnFrame open = new DrawnFrame();
        ScreenDraw.extract(read, open.graphics, DrawnFrame.font(), TALL_VIEW, open.targets, 0, 0);
        EditBox box = widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(each -> each.visible && last.equals(each.getValue())).findFirst().orElseThrow();
        box.setValue("renamed");
        open.click("✓");
        verify(actions).request(eq(WorkbenchOperations.RENAME_CONSTANT),
                ArgumentMatchers.contains("\"to\":\"renamed\""), any());
        DrawnFrame again = new DrawnFrame();
        ScreenDraw.extract(read, again.graphics, DrawnFrame.font(), TALL_VIEW, again.targets, 0, 0);
        clickRenameOf(again, last);
        DrawnFrame reopened = new DrawnFrame();
        ScreenDraw.extract(read, reopened.graphics, DrawnFrame.font(), TALL_VIEW, reopened.targets, 0, 0);
        box.setValue("other");
        reopened.click("↺");
        assertFalse(box.visible);
        DrawnFrame closed = new DrawnFrame();
        ScreenDraw.extract(read, closed.graphics, DrawnFrame.font(), TALL_VIEW, closed.targets, 0, 0);
        assertTrue(closed.texts().contains("$" + last), closed.texts().toString());
    }

    // The rename pencil on a prefab's name line, apart from its value's pencil below.
    private static void clickRenameOf(DrawnFrame frame, String prefab) {
        DrawnFrame.Text name = frame.find("$" + prefab);
        DrawnFrame.Text pencil = frame.drawn().stream().filter(text -> text.text().equals("✎")
                && text.y() == name.y()).findFirst().orElseThrow(() -> new AssertionError(frame.drawn().toString()));
        assertTrue(frame.targets.click(pencil.x() + 1, pencil.y() + 1), pencil + " took no click");
    }

    /**
     * A ✓ and a gold ↺ stand right of the open box on its row.
     *
     * @param frame the frame drawn with the box open
     * @param box   the open box
     */
    static void assertNameBoxButtons(DrawnFrame frame, EditBox box) {
        DrawnFrame.Text done = frame.find("✓");
        DrawnFrame.Text arrow = frame.find("↺");
        int boxRight = box.getX() + box.getWidth();
        assertTrue(done.x() > boxRight && arrow.x() > boxRight, done + " " + arrow + " vs box right " + boxRight);
        assertTrue(Math.abs(done.y() - box.getY()) < ExpressionLines.TEXT_LINE_HEIGHT, done + " vs box " + box.getY());
        assertEquals(done.y(), arrow.y());
        assertEquals(Palette.GOLD, arrow.color());
        assertEquals(Optional.of(BuilderOverlays.hintKey("Done")),
                frame.targets.hovered(done.x() + 1, done.y() + 1));
        assertEquals(Optional.of(BuilderOverlays.hintKey("Nevermind")),
                frame.targets.hovered(arrow.x() + 1, arrow.y() + 1));
    }

    // A Prefabs pane over one prefab, base, that the prefab egg reads.
    private static VariablesPane readByEgg(ManagerActions actions) {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "base", "keySeparator": null, "kind": "VALUE", "text": "384", "entries": [],
                     "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        VariablesTab prefabs = new VariablesTab(draft);
        prefabs.readEvaluated("""
                {"constants": [{"name": "base", "kind": "scalar", "value": "384", "error": null, "usedBy": [],
                                "readers": [{"id": "egg", "kind": "constant"}]}]}
                """);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane read = new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        read.init(DrawnFrame.font());
        read.show(true);
        return read;
    }

    /**
     * Each change the builder writes over a prefab lands in the draft and evaluates it afresh, so the value the block
     * shows is the value the builder holds.
     */
    @Test
    void eachBuilderChangeEvaluatesThePrefabsAfresh() {
        ManagerActions actions = mock(ManagerActions.class);
        ManagerDraft draft = new ManagerDraft();
        draft.read(model());
        VariablesTab prefabs = new VariablesTab(draft);
        twelvePrefabs(prefabs, draft, actions).writeBuilt("prefab0", "\"$base / 8\"");
        assertEquals(Optional.of("\"$base / 8\""), prefabs.expressionOf("prefab0").map(ManagerDraft::shownText));
        verify(actions).draftChanged();
    }

    /**
     * The [+] right of the search box opens a focused name box; Enter on a free name lists the new prefab and opens
     * its builder (decision goo-workbench-opens-the-in-game-manager-screen: the page's Variables pane added
     * constants).
     */
    @Test
    void plusRightOfTheSearchNamesANewPrefabAndOpensItsBuilder() {
        List<AbstractWidget> widgets = new ArrayList<>();
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> {
            widgets.add(call.getArgument(0));
            return call.getArgument(0);
        });
        ManagerDraft draft = new ManagerDraft();
        draft.read(model());
        VariablesTab prefabs = new VariablesTab(draft);
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane read = new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        read.init(DrawnFrame.font());
        read.show(true);
        DrawnFrame list = new DrawnFrame();
        ScreenDraw.extract(read, list.graphics, DrawnFrame.font(), VIEW, list.targets, 0, 0);
        DrawnFrame.Text plus = list.find("+");
        assertTrue(plus.x() > VIEW.left() + 180, plus + " stands right of the search box");
        list.click("+");
        DrawnFrame naming = new DrawnFrame();
        ScreenDraw.extract(read, naming.graphics, DrawnFrame.font(), VIEW, naming.targets, 0, 0);
        EditBox box = widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(each -> each.visible && each != read.searchBox()).findFirst()
                .orElseThrow(() -> new AssertionError("no name box stood open"));
        verify(actions).focus(box);
        GroupsPaneTest.GroupCards.focus(box);
        box.setValue("fresh_prefab");
        assertTrue(read.keyPressed(GLFW.GLFW_KEY_ENTER));
        assertEquals(Optional.of("fresh_prefab"), prefabs.editing());
        verify(actions).draftChanged();
        DrawnFrame opened = new DrawnFrame();
        ScreenDraw.extract(read, opened.graphics, DrawnFrame.font(), VIEW, opened.targets, 0, 0);
        DrawnFrame settled = new DrawnFrame();
        ScreenDraw.extract(read, settled.graphics, DrawnFrame.font(), VIEW, settled.targets, 0, 0);
        assertTrue(settled.texts().contains("$fresh_prefab"), settled.texts().toString());
        assertTrue(settled.texts().stream().anyMatch(text -> text.endsWith(ControlFlow.dropdownLabel(""))),
                settled.texts().toString());
    }

    /**
     * A prefab being edited draws its builder's gray panel wholly below its Used by item icons, so the panel never
     * covers an icon's bottom row; it covered one row before.
     */
    @Test
    void builderPanelStandsBelowTheUsedByIcons() {
        variables.readEvaluated("""
                {"constants": [{"name": "prefab0", "kind": "scalar", "value": "8",
                                "direct": ["minecraft:apple", "minecraft:bread"]}]}
                """);
        variables.startEditing("prefab0");
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), TALL_VIEW, frame.targets, 0, 0);
        ArgumentCaptor<Integer> itemTops = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeastOnce()).item(any(), anyInt(), itemTops.capture());
        int panelTop = fillTopsAt(frame, Palette.PANEL_FILL).getFirst();
        for (int top : itemTops.getAllValues()) {
            assertTrue(top + SPRITE <= panelTop, "icon " + top + ".." + (top + SPRITE) + " vs panel top " + panelTop);
        }
    }
}
