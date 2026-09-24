package com.mercuriusxeno.goo.tools.architecture;

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads goo's compiled classes through the JDK ClassFile API into the scan
 * model every architecture document is drawn from (decision
 * one-command-regenerates-architectural-documentation). The mod gains no
 * dependency: {@code java.lang.classfile} ships with the toolchain.
 */
public final class ClassScanner {

    private static final String CLASS_SUFFIX = ".class";
    private static final String PACKAGE_INFO = "package-info";
    private static final String MODULE_INFO = "module-info";
    private static final String JAVA_SUFFIX = ".java";
    private static final String STATIC_INITIALIZER = "<clinit>";
    private static final char NESTED_SEPARATOR = '$';
    private static final char INTERNAL_SEPARATOR = '/';
    private static final char QUALIFIED_SEPARATOR = '.';
    private static final Pattern GOO_INTERNAL_NAME = Pattern.compile("com/mercuriusxeno/goo/[A-Za-z0-9_/$]+");

    private final Path classesRoot;
    private final String sourceRoot;

    /**
     * A scanner over one class output directory.
     *
     * @param classesRoot the directory javac wrote the classes under
     * @param sourceRoot  the source root relative to the repository, forward slashes, such as {@code src/main/java}
     */
    public ClassScanner(Path classesRoot, String sourceRoot) {
        this.classesRoot = classesRoot;
        this.sourceRoot = sourceRoot;
    }

    /**
     * Reads every class under the root and folds nested classes into their top-level type.
     *
     * @return one scanned type per top-level class, ordered by qualified name
     * @throws IOException when a class file cannot be read
     */
    public List<ScannedType> scan() throws IOException {
        Map<String, List<ClassModel>> byTopLevel = new TreeMap<>();
        for (ClassModel model : readClassModels()) {
            String internalName = model.thisClass().asInternalName();
            byTopLevel.computeIfAbsent(topLevelOf(internalName), key -> new ArrayList<>()).add(model);
        }
        Set<String> known = byTopLevel.keySet();
        return byTopLevel.entrySet().stream()
                .map(entry -> toType(entry.getKey(), entry.getValue(), known))
                .sorted(Comparator.comparing(ScannedType::qualifiedName))
                .toList();
    }

    /**
     * Parses every class file under the root, skipping package and module descriptors.
     *
     * @return the parsed class models
     * @throws IOException when the directory cannot be walked or a class file cannot be read
     */
    private List<ClassModel> readClassModels() throws IOException {
        List<Path> classFiles;
        try (Stream<Path> paths = Files.walk(classesRoot)) {
            classFiles = paths.filter(ClassScanner::isTypeClassFile).toList();
        }
        List<ClassModel> models = new ArrayList<>(classFiles.size());
        for (Path classFile : classFiles) {
            models.add(ClassFile.of().parse(Files.readAllBytes(classFile)));
        }
        return models;
    }

    /**
     * Whether a path is a class file holding a type rather than a package or module descriptor.
     *
     * @param path the file to test
     * @return true for a type's class file
     */
    private static boolean isTypeClassFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(CLASS_SUFFIX)
                && !fileName.startsWith(PACKAGE_INFO)
                && !fileName.startsWith(MODULE_INFO);
    }

    /**
     * Builds the scanned type for one top-level class and the nested classes folded into it.
     *
     * @param topLevel the top-level internal name
     * @param models   the class models of the top-level class and its nested classes
     * @param known    every top-level internal name the scan holds
     * @return the scanned type
     */
    private ScannedType toType(String topLevel, List<ClassModel> models, Set<String> known) {
        ClassModel top = models.stream()
                .filter(model -> model.thisClass().asInternalName().equals(topLevel))
                .findFirst()
                .orElse(models.getFirst());
        Set<String> references = new TreeSet<>();
        int instanceofChecks = 0;
        for (ClassModel model : models) {
            references.addAll(referencedTopLevels(model, known));
            instanceofChecks += countInstanceofChecks(model);
        }
        references.remove(topLevel);
        int lastSlash = topLevel.lastIndexOf(INTERNAL_SEPARATOR);
        return new ScannedType(qualified(topLevel.substring(0, Math.max(lastSlash, 0))),
                topLevel.substring(lastSlash + 1), sourcePathOf(top, topLevel), declaredFields(top),
                declaredMethods(top), references.stream().map(ClassScanner::qualified)
                .collect(TreeSet::new, Set::add, Set::addAll), instanceofChecks);
    }

    /**
     * The top-level scanned types a class names anywhere in its constant pool:
     * class entries, descriptors and generic signatures alike.
     *
     * @param model the class to read
     * @param known every top-level internal name the scan holds
     * @return the known top-level internal names the class references
     */
    private static Set<String> referencedTopLevels(ClassModel model, Set<String> known) {
        Set<String> referenced = new TreeSet<>();
        for (PoolEntry entry : model.constantPool()) {
            if (entry instanceof Utf8Entry utf8) {
                Matcher matcher = GOO_INTERNAL_NAME.matcher(utf8.stringValue());
                while (matcher.find()) {
                    referenced.add(topLevelOf(matcher.group()));
                }
            }
        }
        referenced.retainAll(known);
        return referenced;
    }

    /**
     * Counts the {@code instanceof} instructions across a class's method bodies.
     *
     * @param model the class to read
     * @return how many instanceof instructions it holds
     */
    private static int countInstanceofChecks(ClassModel model) {
        return model.methods().stream()
                .flatMap(method -> method.code().stream())
                .mapToInt(ClassScanner::countInstanceofChecks)
                .sum();
    }

    /**
     * Counts the {@code instanceof} instructions in one method body.
     *
     * @param code the method body
     * @return how many instanceof instructions it holds
     */
    private static int countInstanceofChecks(CodeModel code) {
        return (int) code.elementStream()
                .filter(element -> element instanceof TypeCheckInstruction check
                        && check.opcode() == Opcode.INSTANCEOF)
                .count();
    }

    /**
     * The fields a class declares in source.
     *
     * @param model the class to read
     * @return its fields, compiler-synthesised ones left out
     */
    private static List<ScannedField> declaredFields(ClassModel model) {
        return model.fields().stream()
                .filter(field -> !field.flags().has(AccessFlag.SYNTHETIC))
                .map(ClassScanner::toField)
                .toList();
    }

    /**
     * Converts one field model.
     *
     * @param field the field model
     * @return the scanned field
     */
    private static ScannedField toField(FieldModel field) {
        return new ScannedField(field.fieldName().stringValue(), field.fieldType().stringValue(),
                field.flags().has(AccessFlag.STATIC));
    }

    /**
     * The methods and constructors a class declares in source.
     *
     * @param model the class to read
     * @return its methods, synthetic, bridge and static initialiser ones left out
     */
    private static List<ScannedMethod> declaredMethods(ClassModel model) {
        return model.methods().stream()
                .filter(ClassScanner::isDeclaredInSource)
                .map(ClassScanner::toMethod)
                .toList();
    }

    /**
     * Whether a method is one the source declares rather than one javac added.
     *
     * @param method the method model
     * @return true when neither synthetic, a bridge nor the static initialiser
     */
    private static boolean isDeclaredInSource(MethodModel method) {
        return !method.flags().has(AccessFlag.SYNTHETIC)
                && !method.flags().has(AccessFlag.BRIDGE)
                && !method.methodName().equalsString(STATIC_INITIALIZER);
    }

    /**
     * Converts one method model.
     *
     * @param method the method model
     * @return the scanned method
     */
    private static ScannedMethod toMethod(MethodModel method) {
        return new ScannedMethod(method.methodName().stringValue(), method.methodType().stringValue(),
                method.methodTypeSymbol().parameterCount(), method.flags().has(AccessFlag.STATIC));
    }

    /**
     * The source path of a top-level class, relative to the repository.
     *
     * @param top      the top-level class model
     * @param topLevel its internal name
     * @return the source root, the package path and the source file name joined by slashes
     */
    private String sourcePathOf(ClassModel top, String topLevel) {
        int lastSlash = topLevel.lastIndexOf(INTERNAL_SEPARATOR);
        String fileName = top.findAttribute(Attributes.sourceFile())
                .map(attribute -> attribute.sourceFile().stringValue())
                .orElse(topLevel.substring(lastSlash + 1) + JAVA_SUFFIX);
        return sourceRoot + INTERNAL_SEPARATOR + topLevel.substring(0, lastSlash + 1) + fileName;
    }

    /**
     * The top-level internal name a possibly nested internal name folds into.
     *
     * @param internalName an internal name, such as {@code a/b/Outer$Inner}
     * @return the name up to its first nested separator
     */
    private static String topLevelOf(String internalName) {
        int nested = internalName.indexOf(NESTED_SEPARATOR);
        return nested < 0 ? internalName : internalName.substring(0, nested);
    }

    /**
     * Converts an internal name to its dotted form.
     *
     * @param internalName a slash-separated name
     * @return the dot-separated name
     */
    private static String qualified(String internalName) {
        return internalName.replace(INTERNAL_SEPARATOR, QUALIFIED_SEPARATOR);
    }
}
