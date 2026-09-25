package com.mercuriusxeno.goo.block;

import net.minecraft.world.level.block.state.properties.Property;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A block's state properties read from its compiled class on the classpath, so a unit test learns them
 * without initializing the block or BlockStateProperties, whose static init needs the Minecraft bootstrap
 * (decision diagnose-then-fix-test-cwd). The fields createBlockStateDefinition reads name the properties, and each
 * field's value is rebuilt by evaluating the ldc, getstatic and invokestatic run its static initializer assigns it
 * from; a field built any other way, such as an enum constant, is read from its loaded class instead.
 */
final class CompiledStateDefinition {

    private static final String STATE_DEFINITION_METHOD = "createBlockStateDefinition";
    private static final String STATIC_INITIALIZER = "<clinit>";
    private static final ClassLoader LOADER = CompiledStateDefinition.class.getClassLoader();

    private CompiledStateDefinition() {
    }

    /**
     * Each property the block's createBlockStateDefinition adds, by its serialized name, with its value names.
     *
     * @param block the block class, never initialized here
     * @return the properties in the order the block adds them
     */
    static Map<String, List<String>> of(Class<?> block) {
        Map<String, List<String>> properties = new LinkedHashMap<>();
        for (AbstractInsnNode insn : method(Type.getInternalName(block), STATE_DEFINITION_METHOD).instructions) {
            if (insn instanceof FieldInsnNode read && read.getOpcode() == Opcodes.GETSTATIC) {
                Property<?> property = (Property<?>) staticValue(read.owner, read.name);
                properties.put(property.getName(), valueNames(property));
            }
        }
        if (properties.isEmpty()) {
            throw new IllegalStateException(block.getName() + " adds no state property in " + STATE_DEFINITION_METHOD);
        }
        return properties;
    }

    private static Object staticValue(String owner, String field) {
        return evaluateInitializer(owner, field).orElseGet(() -> readLoaded(owner, field));
    }

    /** The value the run of instructions ending in the field's putstatic computes, when every one is evaluable. */
    private static Optional<Object> evaluateInitializer(String owner, String field) {
        List<AbstractInsnNode> run = new ArrayList<>();
        for (AbstractInsnNode insn : method(owner, STATIC_INITIALIZER).instructions) {
            if (insn instanceof FieldInsnNode put && put.getOpcode() == Opcodes.PUTSTATIC) {
                if (put.owner.equals(owner) && put.name.equals(field)) {
                    return evaluate(run);
                }
                run.clear();
            } else if (insn.getOpcode() >= 0) {
                run.add(insn);
            }
        }
        throw new IllegalStateException(owner + "." + field + " has no static assignment");
    }

    private static Optional<Object> evaluate(List<AbstractInsnNode> run) {
        Deque<Object> stack = new ArrayDeque<>();
        for (AbstractInsnNode insn : run) {
            if (insn instanceof LdcInsnNode constant) {
                stack.push(constant.cst instanceof Type type ? loadClass(type) : constant.cst);
            } else if (insn instanceof FieldInsnNode read && read.getOpcode() == Opcodes.GETSTATIC) {
                stack.push(staticValue(read.owner, read.name));
            } else if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC) {
                stack.push(invoke(call, stack));
            } else {
                return Optional.empty();
            }
        }
        return stack.size() == 1 ? Optional.of(stack.pop()) : Optional.empty();
    }

    private static Object invoke(MethodInsnNode call, Deque<Object> stack) {
        Type[] parameters = Type.getArgumentTypes(call.desc);
        Class<?>[] parameterClasses = new Class<?>[parameters.length];
        Object[] arguments = new Object[parameters.length];
        for (int i = parameters.length - 1; i >= 0; i--) {
            parameterClasses[i] = loadClass(parameters[i]);
            arguments[i] = stack.pop();
        }
        try {
            Method method = loadClass(Type.getObjectType(call.owner)).getDeclaredMethod(call.name, parameterClasses);
            method.setAccessible(true);
            return method.invoke(null, arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot call " + call.owner + "." + call.name + call.desc, e);
        }
    }

    private static Object readLoaded(String owner, String field) {
        try {
            var declared = loadClass(Type.getObjectType(owner)).getDeclaredField(field);
            declared.setAccessible(true);
            return declared.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read " + owner + "." + field, e);
        }
    }

    private static Class<?> loadClass(Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN -> boolean.class;
            case Type.INT -> int.class;
            case Type.OBJECT, Type.ARRAY -> {
                try {
                    yield Class.forName(type.getSort() == Type.ARRAY
                            ? type.getDescriptor().replace('/', '.') : type.getClassName(), false, LOADER);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("class missing on classpath: " + type, e);
                }
            }
            default -> throw new IllegalStateException("parameter type this reader does not load: " + type);
        };
    }

    private static <T extends Comparable<T>> List<String> valueNames(Property<T> property) {
        return property.getPossibleValues().stream().map(property::getName).toList();
    }

    private static MethodNode method(String owner, String name) {
        return classNode(owner).methods.stream().filter(method -> method.name.equals(name)).findFirst()
                .orElseThrow(() -> new IllegalStateException(owner + " declares no " + name));
    }

    private static ClassNode classNode(String owner) {
        try (InputStream in = LOADER.getResourceAsStream(owner + ".class")) {
            if (in == null) {
                throw new IllegalStateException("class missing on classpath: " + owner);
            }
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + owner, e);
        }
    }
}
