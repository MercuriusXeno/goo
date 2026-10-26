package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.workbench.checks.CheckResult;
import com.mercuriusxeno.goo.workbench.checks.ValuesChecks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Every workbench read and write over the draft beside base_values.json, the inventory and the dumps, each taking
 * the posted text and answering a reply, so the loopback page and the in-game manager answer alike
 * (decision goo-workbench-opens-the-in-game-manager-screen).
 */
public final class WorkbenchOperations {
    /** Reads the draft's model, seeding the draft first where none stands. */
    public static final String MODEL = "model";
    /** Saves the posted model as the draft. */
    public static final String SAVE_MODEL = "model/save";
    /** Reads the inventory, dumping it first where none stands. */
    public static final String INVENTORY = "inventory";
    /** Resolves the posted model's constants. */
    public static final String EVALUATE_CONSTANTS = "constants/evaluate";
    /** Renames a constant of the posted model. */
    public static final String RENAME_CONSTANT = "constants/rename";
    /** Deletes a constant of the posted model with its cascade. */
    public static final String DELETE_CONSTANT = "constants/delete";
    /** Answers a constant's hover card. */
    public static final String CONSTANT_HOVER = "constants/hover";
    /** Describes the posted model's groups. */
    public static final String DESCRIBE_GROUPS = "groups/describe";
    /** Applies one change to a group of the posted model. */
    public static final String APPLY_TO_GROUP = "groups/apply";
    /** Reads the dumps the last run left. */
    public static final String STANDING_DUMPS = "dumps";
    /** Saves the posted model as the draft and runs the dumps over it, its progress line by line. */
    public static final String RUN_DUMPS = "dumps/run";
    /** Reads the audit's sections. */
    public static final String AUDIT_SECTIONS = "audit";
    /** Resolves every entry of the posted model. */
    public static final String RESOLVE_VALUES = "values/resolve";
    /** Lists the items the posted model leaves uncovered. */
    public static final String UNCOVERED_VALUES = "values/uncovered";
    /** Answers the Values table of the posted item's family. */
    public static final String FAMILY = "values/family";
    /** Answers, for each item recipes value and no entry does, the recipe inputs it is derived from. */
    public static final String DERIVED_FROM = "values/derived-from";
    /** Answers the posted item's hover card. */
    public static final String HOVER = "hover";
    /** Describes the posted model's processes. */
    public static final String PROCESSES = "processes";
    /** Renames the draft over the live file. */
    public static final String PROMOTE = "promote";
    /** Copies the live file back over the draft. */
    public static final String DISCARD = "discard";

    private static final int BAD_REQUEST = 400;
    private static final int NOT_FOUND = 404;
    private static final int CONFLICT = 409;
    private static final int SERVER_ERROR = 500;
    private static final String SECTIONS = "sections";
    private static final String GROUPS = "groups";
    private static final String FAILING = "failing";
    private static final String EXIT_CODE = "exitCode";
    private static final String DURATION = "durationSeconds";
    private static final double NANOS_PER_SECOND = 1e9;
    // The exit code a run reports when it was interrupted or could not start, since the runner gave none.
    private static final int RUN_FAILED_EXIT = -1;
    private static final String NO_DRAFT_AT = "No draft stands to promote at ";
    private static final String NO_LIVE_FILE_AT = "No live file stands to discard back to at ";
    private static final String NO_INVENTORY_WRITTEN = "The inventory dump wrote no ";
    private static final String NO_CONSTANT_NAMED = "No constant is named $";
    private static final String NO_OPERATION_NAMED = "No workbench operation is named ";
    private static final String EXCEPTION_MESSAGE_SEPARATOR = ": ";

    private final WorkbenchPaths paths;
    private final DraftSeed seed;
    private final InventoryDump dump;
    private final DumpRunner dumps;
    private final LiveApplier applier;
    // Guards the draft file alone: a dump run holds this object's own monitor for minutes, and a seed, Promote or
    // Discard must not wait on it.
    private final Object draftLock = new Object();
    private final Map<String, Operation> operations = Map.ofEntries(
            Map.entry(MODEL, posted -> modelReply(fileLayout())),
            Map.entry(SAVE_MODEL, this::saveModel),
            Map.entry(INVENTORY, posted -> inventory()),
            Map.entry(EVALUATE_CONSTANTS, this::evaluateConstants),
            Map.entry(RENAME_CONSTANT, this::renameConstant),
            Map.entry(DELETE_CONSTANT, this::deleteConstant),
            Map.entry(CONSTANT_HOVER, this::constantHover),
            Map.entry(DESCRIBE_GROUPS, this::describeGroups),
            Map.entry(APPLY_TO_GROUP, posted -> json(GroupsJson.applied(posted, fileLayout(),
                    GroupsJson.Inventory.read(dumpedInventory())))),
            Map.entry(STANDING_DUMPS, posted -> standingDumps()),
            Map.entry(AUDIT_SECTIONS, posted -> auditSections()),
            Map.entry(RESOLVE_VALUES, this::resolveValues),
            Map.entry(UNCOVERED_VALUES, posted -> json(ValuesJson.uncovered(postedModel(posted),
                    ValuesJson.inventory(dumpedInventory())))),
            Map.entry(FAMILY, posted -> json(Valuations.family(postedModel(posted),
                    ValuesJson.inventory(dumpedInventory()), ValuesJson.idFrom(posted)))),
            Map.entry(DERIVED_FROM, posted -> json(Valuations.derivedFrom(postedModel(posted),
                    ValuesJson.inventory(dumpedInventory())))),
            Map.entry(HOVER, posted -> json(ValuesJson.hover(postedModel(posted),
                    ValuesJson.inventory(dumpedInventory()), ValuesJson.idFrom(posted)))),
            Map.entry(PROCESSES, posted -> json(ProcessesJson.described(postedModel(posted)))),
            Map.entry(PROMOTE, this::promote),
            Map.entry(DISCARD, posted -> discard()));

    /**
     * @param paths the files the workbench reads and writes
     * @param seed  the text a draft starts from where no live file stands
     * @param dump  writes the inventory when none stands
     * @param dumps runs the audit and clique dumps
     */
    public WorkbenchOperations(WorkbenchPaths paths, DraftSeed seed, InventoryDump dump, DumpRunner dumps) {
        this(paths, seed, dump, dumps, LiveApplier.NONE);
    }

    /**
     * @param paths   the files the workbench reads and writes
     * @param seed    the text a draft starts from where no live file stands
     * @param dump    writes the inventory when none stands
     * @param dumps   runs the audit and clique dumps
     * @param applier applies the text a Save, a Promote or a Discard leaves standing to the running server
     */
    public WorkbenchOperations(WorkbenchPaths paths, DraftSeed seed, InventoryDump dump, DumpRunner dumps,
                               LiveApplier applier) {
        this.paths = paths;
        this.seed = seed;
        this.dump = dump;
        this.dumps = dumps;
        this.applier = applier;
    }

    @FunctionalInterface
    private interface Operation {
        WorkbenchReply answer(String posted) throws IOException, InterruptedException;
    }

    /**
     * Runs the named operation over the posted text; every failure answers as a refusal naming its cause, since an
     * unanswered request leaves the page or the manager waiting with no word of it.
     *
     * @param operation the operation's name, one of this class's constants
     * @param posted    the posted text, empty for a read
     * @return the operation's reply
     */
    public WorkbenchReply answer(String operation, String posted) {
        return answer(operation, posted, line -> { });
    }

    /**
     * Runs the named operation over the posted text, handing each line a dump run prints to progress before it
     * answers, as the Audit tab shows the run going (decision goo-workbench-opens-the-in-game-manager-screen).
     *
     * @param operation the operation's name, one of this class's constants
     * @param posted    the posted text, empty for a read
     * @param progress  takes each line a dump run prints
     * @return the operation's reply
     */
    public WorkbenchReply answer(String operation, String posted, Consumer<String> progress) {
        Operation chosen = RUN_DUMPS.equals(operation) ? text -> json(runDumps(text, progress).toString())
                : operations.get(operation);
        if (chosen == null) {
            return WorkbenchReply.of(NOT_FOUND, WorkbenchReply.TEXT, NO_OPERATION_NAMED + operation);
        }
        try {
            return chosen.answer(posted);
        } catch (IllegalArgumentException e) {
            return WorkbenchReply.of(BAD_REQUEST, WorkbenchReply.TEXT, String.valueOf(e.getMessage()));
        } catch (RuntimeException | LinkageError e) {
            return WorkbenchReply.of(SERVER_ERROR, WorkbenchReply.TEXT, e.getClass().getSimpleName()
                    + EXCEPTION_MESSAGE_SEPARATOR + e.getMessage());
        } catch (IOException e) {
            return WorkbenchReply.of(SERVER_ERROR, WorkbenchReply.TEXT, String.valueOf(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return WorkbenchReply.of(SERVER_ERROR, WorkbenchReply.TEXT, String.valueOf(e.getMessage()));
        }
    }

    /**
     * Saves the posted model as the draft, runs the dumps and hands each line the run prints to progress; one run
     * at a time, since the dumps write one pair of files (decision workbench-reruns-the-audit-headless).
     *
     * @param posted   the model to save
     * @param progress takes each line the run prints
     * @return the run's exit code, its duration and both dumps section by section
     * @throws IOException where the draft cannot be written
     */
    public synchronized JsonObject runDumps(String posted, Consumer<String> progress) throws IOException {
        String text = postedModel(posted).serialize();
        Files.writeString(paths.draft(), text);
        // A run saves the draft as Save does, so the world runs what the audit reads
        // (decision save-applies-the-draft-live-promote-renames-it).
        applier.apply(text);
        long start = System.nanoTime();
        int exit;
        try {
            exit = dumps.run(paths.draft(), progress);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exit = RUN_FAILED_EXIT;
        } catch (IOException e) {
            progress.accept(e.getMessage());
            exit = RUN_FAILED_EXIT;
        }
        JsonObject result = new JsonObject();
        result.addProperty(EXIT_CODE, exit);
        result.addProperty(DURATION, (System.nanoTime() - start) / NANOS_PER_SECOND);
        result.add(SECTIONS, DumpSections.read(paths.auditDump(), paths.cliqueDump()));
        return result;
    }

    /**
     * Ends the dump run in flight, as the page's Stop and a stopping game server do.
     */
    public void stopDumps() {
        dumps.stop();
    }

    private static WorkbenchReply json(String body) {
        return WorkbenchReply.of(WorkbenchReply.OK, WorkbenchReply.JSON, body);
    }

    /**
     * Writes the draft and applies it to the running server, so a player tests a value as they edit it
     * (decision save-applies-the-draft-live-promote-renames-it).
     *
     * @param posted the model to save
     * @return the saved model
     */
    private WorkbenchReply saveModel(String posted) throws IOException {
        BaseValuesModel saved = postedModel(posted);
        String text = saved.serialize();
        Files.writeString(paths.draft(), text);
        applier.apply(text);
        return modelReply(saved);
    }

    /**
     * Resolves the posted model's constants through the game's parser, each with its error and its readers.
     *
     * @param posted the model whose constants resolve
     * @return every constant with its value, its error and its readers
     */
    private WorkbenchReply evaluateConstants(String posted) throws IOException {
        return json(ConstantsJson.evaluated(postedModel(posted),
                GroupsJson.Inventory.read(paths.inventoryFile()).tags()));
    }

    /**
     * Answers the hover card of the constant the posted name holds in the posted model, not found where no constant
     * holds it (decision references-link-to-their-variable-and-hover-a-card).
     *
     * @param posted the model and the constant's name
     * @return the constant's hover card, or not found
     */
    private WorkbenchReply constantHover(String posted) throws IOException {
        String name = ConstantsJson.nameFrom(posted);
        return ConstantsJson.hovered(name, postedModel(posted), GroupsJson.Inventory.read(paths.inventoryFile()).tags())
                .map(WorkbenchOperations::json)
                .orElseGet(() -> WorkbenchReply.of(NOT_FOUND, WorkbenchReply.TEXT, NO_CONSTANT_NAMED + name));
    }

    private WorkbenchReply renameConstant(String posted) throws IOException {
        ConstantsJson.Rename rename = ConstantsJson.renameFrom(posted);
        return modelReply(postedModel(posted).renameConstant(rename.from(), rename.to()));
    }

    /**
     * Deletes a constant from the posted model with its cascade of readers and answers the model left
     * (decision deleting-a-variable-inlines-its-expression-into-each-reader).
     *
     * @param posted the model and the constant's name
     * @return the model left after the delete
     */
    private WorkbenchReply deleteConstant(String posted) throws IOException {
        return modelReply(postedModel(posted).deleteConstant(ConstantsJson.nameFrom(posted)));
    }

    private WorkbenchReply describeGroups(String posted) throws IOException, InterruptedException {
        JsonObject answer = new JsonObject();
        answer.add(GROUPS, GroupsJson.described(postedModel(posted), GroupsJson.Inventory.read(dumpedInventory())));
        return json(answer.toString());
    }

    /**
     * Answers the sections goo_audit.json carries, read afresh on every request so a rule added later shows with no
     * restart (decision each-check-is-one-rule-added-alone).
     *
     * @return the audit's sections, none where no audit stands
     */
    private WorkbenchReply auditSections() throws IOException {
        if (!Files.isRegularFile(paths.auditJson())) {
            JsonObject none = new JsonObject();
            none.add(SECTIONS, new JsonArray());
            return json(none.toString());
        }
        return new WorkbenchReply(WorkbenchReply.OK, WorkbenchReply.JSON, Files.readAllBytes(paths.auditJson()));
    }

    private WorkbenchReply standingDumps() throws IOException {
        JsonObject answer = new JsonObject();
        answer.add(SECTIONS, DumpSections.read(paths.auditDump(), paths.cliqueDump()));
        return json(answer.toString());
    }

    private WorkbenchReply resolveValues(String posted) throws IOException {
        BaseValuesModel model = postedModel(posted);
        return json(Files.isRegularFile(paths.inventoryFile())
                ? Valuations.resolved(model, ValuesJson.inventory(paths.inventoryFile()))
                : Valuations.resolved(model));
    }

    private BaseValuesModel postedModel(String posted) throws IOException {
        return fileLayout().withMembers(ModelJson.membersFrom(posted));
    }

    /**
     * @return the draft's model, the draft seeded first where none stands
     */
    private BaseValuesModel fileLayout() throws IOException {
        return BaseValuesModel.parse(Files.readString(seededDraft()));
    }

    /**
     * Seeds the draft from the live file where no draft stands, or from the seed where no live file stands either,
     * so every read and save works on the draft (decision workbench-authors-a-draft-and-promotes-by-rename).
     *
     * @return the draft's path
     */
    private Path seededDraft() throws IOException {
        Path draft = paths.draft();
        synchronized (draftLock) {
            if (Files.isRegularFile(draft)) {
                return draft;
            }
            Files.createDirectories(draft.getParent());
            if (Files.isRegularFile(paths.baseValues())) {
                Files.copy(paths.baseValues(), draft);
            } else {
                Files.write(draft, seed.read());
            }
        }
        return draft;
    }

    private boolean draftDiffers() throws IOException {
        Path draft = paths.draft();
        return Files.isRegularFile(draft)
                && (!Files.isRegularFile(paths.baseValues()) || Files.mismatch(draft, paths.baseValues()) >= 0);
    }

    private WorkbenchReply modelReply(BaseValuesModel model) throws IOException {
        return json(ModelJson.toJson(model, paths.baseValues().toString(), paths.draft().toString(), draftDiffers(),
                ChecksJson.toJson(checked(model))));
    }

    /**
     * Runs the type-source checks over a model, expanding #tag entries through the inventory where one stands
     * (decision type-source-checks-are-rules-that-gate-promote).
     *
     * @param model the model to check
     * @return every check's result
     */
    private List<CheckResult> checked(BaseValuesModel model) throws IOException {
        return Files.isRegularFile(paths.inventoryFile())
                ? ValuesChecks.run(model.serialize(), ValuesJson.inventory(paths.inventoryFile()).tagResolver())
                : ValuesChecks.run(model.serialize());
    }

    /**
     * Renames the draft over the live file, the one write the live file takes; a draft failing a type-source check
     * promotes only when the posted text acknowledges the failures (decisions
     * workbench-authors-a-draft-and-promotes-by-rename, type-source-checks-are-rules-that-gate-promote).
     *
     * @param posted the text acknowledging failing checks, or empty
     * @return the live model, or the refusal naming the failing checks
     */
    private WorkbenchReply promote(String posted) throws IOException {
        boolean acknowledged = ChecksJson.acknowledged(posted);
        synchronized (draftLock) {
            if (!Files.isRegularFile(paths.draft())) {
                return WorkbenchReply.of(CONFLICT, WorkbenchReply.TEXT, NO_DRAFT_AT + paths.draft());
            }
            List<CheckResult> failing = checked(BaseValuesModel.parse(Files.readString(paths.draft()))).stream()
                    .filter(result -> !result.passed()).toList();
            if (!failing.isEmpty() && !acknowledged) {
                JsonObject refusal = new JsonObject();
                refusal.add(FAILING, ChecksJson.toJson(failing));
                return WorkbenchReply.of(CONFLICT, WorkbenchReply.JSON, refusal.toString());
            }
            Files.move(paths.draft(), paths.baseValues(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        }
        String live = Files.readString(paths.baseValues());
        applier.apply(live);
        return modelReply(BaseValuesModel.parse(live));
    }

    /**
     * Copies the live file back over the draft, dropping every unpromoted change
     * (decision workbench-authors-a-draft-and-promotes-by-rename).
     *
     * @return the draft's model, or a conflict where no live file stands
     */
    private WorkbenchReply discard() throws IOException {
        synchronized (draftLock) {
            if (!Files.isRegularFile(paths.baseValues())) {
                return WorkbenchReply.of(CONFLICT, WorkbenchReply.TEXT, NO_LIVE_FILE_AT + paths.baseValues());
            }
            Files.copy(paths.baseValues(), paths.draft(), StandardCopyOption.REPLACE_EXISTING);
        }
        String live = Files.readString(paths.baseValues());
        applier.apply(live);
        return modelReply(BaseValuesModel.parse(live));
    }

    private WorkbenchReply inventory() throws IOException, InterruptedException {
        Path inventory = dumpedInventory();
        if (!Files.isRegularFile(inventory)) {
            return WorkbenchReply.of(SERVER_ERROR, WorkbenchReply.TEXT, NO_INVENTORY_WRITTEN + inventory);
        }
        return new WorkbenchReply(WorkbenchReply.OK, WorkbenchReply.JSON, Files.readAllBytes(inventory));
    }

    /**
     * Runs the dump once, however many requests ask while it runs.
     */
    private synchronized void dumpInventoryIfAbsent() throws IOException, InterruptedException {
        if (!Files.isRegularFile(paths.inventoryFile())) {
            dump.run();
        }
    }

    /**
     * A fresh tree holds no inventory, so every reader of it waits on the one dump rather than refusing while the
     * dump still writes.
     *
     * @return the inventory's path, dumped first where none stood
     */
    private Path dumpedInventory() throws IOException, InterruptedException {
        dumpInventoryIfAbsent();
        return paths.inventoryFile();
    }
}
