package net.earthcomputer.clientcommands.test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.spongepowered.asm.util.asm.ASM;
import org.spongepowered.asm.util.asm.MixinVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class ReferencesFinder {
    private static ReferencesFinder instance;

    private ReferencesFinder() {
    }

    public static synchronized ReferencesFinder getInstance() {
        if (instance != null) {
            return instance;
        }

        instance = new ReferencesFinder();
        instance.buildIndex();
        return instance;
    }

    private final ModContainer minecraft = FabricLoader.getInstance()
        .getModContainer("minecraft")
        .orElseThrow(() -> new IllegalStateException("Mod \"minecraft\" has not been initialized"));
    private final Map<String, @Nullable ClassInfo> index = new HashMap<>();

    private void buildIndex() {
        // use an interner to save memory on all the duplicate strings we'll be seeing
        Interner<String> interner = Interners.newStrongInterner();

        for (Path jarFile : minecraft.getRootPaths()) {
            try (Stream<Path> files = Files.walk(jarFile)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    String className = file.toString();
                    if (className.endsWith(".class")) {
                        className = className.substring(0, className.length() - 6);
                        if (className.startsWith("/")) {
                            className = className.substring(1);
                        }

                        // prevent recursive reading of the same file
                        ClassInfo classInfo = getOrCreateClassInfo(interner, className);
                        if (classInfo == null) {
                            throw new AssertionError("Failed to get class info for class " + className);
                        }

                        try (InputStream in = Files.newInputStream(file)) {
                            ClassReader classReader = new ClassReader(in);
                            classReader.accept(new IndexerClassVisitor(interner), ClassReader.SKIP_FRAMES);
                        }
                    }
                }
            } catch (IOException e) {
                throw new AssertionError("Failed to build index", e);
            }
        }
    }

    private boolean getClassData(String className, ClassVisitor classVisitor, int readerFlags) {
        for (Path jarFile : minecraft.getRootPaths()) {
            try (InputStream in = Files.newInputStream(jarFile.resolve(className + ".class"))) {
                ClassReader reader = new ClassReader(in);
                reader.accept(classVisitor, readerFlags);
                return true;
            } catch (NoSuchFileException ignore) {
            } catch (IOException e) {
                throw new AssertionError("Failed to read class " + className, e);
            }
        }
        return false;
    }

    // should only be called while indexing!
    @Nullable
    private ClassInfo getOrCreateClassInfo(Interner<String> interner, String className) {
        if (index.containsKey(className)) {
            return index.get(className);
        }

        className = interner.intern(className);
        String[] superName = {null};
        String[][] interfaces = {null};
        boolean[] isInterface = {false};
        ImmutableMap.Builder<NameAndDesc, ReferencesSet> fields = ImmutableMap.builder();
        ImmutableMap.Builder<NameAndDesc, ReferencesSet> methods = ImmutableMap.builder();
        boolean foundClass = getClassData(className, new ClassVisitor(ASM.API_VERSION) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName1, String[] interfaces1) {
                superName[0] = interner.intern(superName1);
                interfaces[0] = interfaces1;
                isInterface[0] = (access & Opcodes.ACC_INTERFACE) != 0;
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                fields.put(new NameAndDesc(interner.intern(name), interner.intern(descriptor)), new ReferencesSet(access));
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                methods.put(new NameAndDesc(interner.intern(name), interner.intern(descriptor)), new ReferencesSet(access));
                return null;
            }
        }, ClassReader.SKIP_CODE);

        if (foundClass) {
            ClassInfo classInfo = new ClassInfo(
                className,
                superName[0],
                interfaces[0] == null ? List.of() : List.of(interfaces[0]),
                isInterface[0],
                fields.build(),
                methods.build()
            );
            index.put(className, classInfo);
            return classInfo;
        } else {
            index.put(className, null);
            return null;
        }
    }

    private static boolean doesMethodOverride(int parentAccess, ClassInfo parentOwner, String accessorClass) {
        // JVMS 21 §5.4.5
        if ((parentAccess & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0) {
            return true;
        } else if ((parentAccess & Opcodes.ACC_PRIVATE) == 0 && parentOwner.isSamePackage(accessorClass)) {
            return true;
        } else {
            return false;
        }
    }

    // finds the field as if referenced by field instruction, possibly from a superclass/superinterface from the specified owner
    @Nullable
    private static ReferencesSet resolveField(@Nullable ClassInfo owner, NameAndDesc nameAndDesc, boolean checkInterfaces, ClassResolver resolver) {
        while (owner != null) {
            ReferencesSet resolved = owner.fieldReferences.get(nameAndDesc);
            if (resolved != null) {
                return resolved;
            }

            if (checkInterfaces) {
                for (String interfaceName : owner.interfaces) {
                    ClassInfo interfaceInfo = resolver.resolve(interfaceName);
                    if (interfaceInfo != null) {
                        resolved = resolveField(interfaceInfo, nameAndDesc, true, resolver);
                        if (resolved != null) {
                            return resolved;
                        }
                    }
                }
            }

            owner = resolver.resolve(owner.superName);
        }
        return null;
    }

    // finds the method as if referenced by a nonvirtual method instruction, possibly from a superclass/superinterface from the specified owner
    // if the owner itself doesn't declare the method
    @Nullable
    private static ReferencesSet resolveNonVirtualMethod(@Nullable ClassInfo owner, NameAndDesc nameAndDesc, ClassResolver resolver) {
        while (owner != null) {
            ReferencesSet resolved = owner.methodReferences.get(nameAndDesc);
            if (resolved != null) {
                return resolved;
            }

            owner = resolver.resolve(owner.superName);
        }

        return null;
    }

    // finds the most abstract versions of the method as if referenced by a virtual method instruction, of which there could
    // be multiple in the case of multiple interface inheritance
    private static List<ReferencesSet> resolveVirtualMethod(String accessorClass, ClassInfo owner, NameAndDesc nameAndDesc, ClassResolver resolver) {
        ReferencesSet resolvedInClass = null;
        List<ReferencesSet> resolvedInInterfaces = new ArrayList<>();
        do {
            ReferencesSet resolved = owner.methodReferences.get(nameAndDesc);
            if (resolved != null && doesMethodOverride(resolved.access, owner, accessorClass)) {
                accessorClass = owner.name;
                resolvedInClass = resolved;
            }
            for (String interfaceName : owner.interfaces) {
                ClassInfo interfaceInfo = resolver.resolve(interfaceName);
                if (interfaceInfo != null) {
                    List<ReferencesSet> interfaceResolved = resolveVirtualMethod(accessorClass, interfaceInfo, nameAndDesc, resolver);
                    if (!interfaceResolved.isEmpty()) {
                        resolvedInInterfaces.addAll(interfaceResolved);
                        resolvedInClass = null;
                    }
                }
            }
            owner = resolver.resolve(owner.superName);
        } while (owner != null);
        if (resolvedInClass != null) {
            resolvedInInterfaces.addFirst(resolvedInClass);
        }
        return resolvedInInterfaces;
    }

    public ReferencesSet findFieldReferences(String owner, String name, String desc) {
        ClassInfo classInfo = index.get(owner);
        if (classInfo == null) {
            throw new IllegalArgumentException("owner class not found: " + owner);
        }
        ReferencesSet references = resolveField(classInfo, new NameAndDesc(name, desc), true, index::get);
        if (references == null) {
            throw new IllegalArgumentException("field not found: " + owner + " " + name + " " + desc);
        }
        return references;
    }

    public ReferencesSet findMethodReferences(String owner, String name, String desc) {
        ClassInfo classInfo = index.get(owner);
        if (classInfo == null) {
            throw new IllegalArgumentException("owner class not found: " + owner);
        }

        // we don't know whether the method is virtual or not yet, so find the non-virtual method first and then check
        // if it's non-virtual
        ReferencesSet nonVirtualReferences = resolveNonVirtualMethod(classInfo, new NameAndDesc(name, desc), index::get);
        if (nonVirtualReferences != null && (nonVirtualReferences.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) != 0) {
            return nonVirtualReferences;
        }

        List<ReferencesSet> virtualReferences = resolveVirtualMethod(owner, classInfo, new NameAndDesc(name, desc), index::get);
        return switch (virtualReferences.size()) {
            case 0 -> throw new IllegalArgumentException("method not found: " + owner + " " + name + " " + desc);
            case 1 -> virtualReferences.getFirst();
            default -> {
                ReferencesSet combined = new ReferencesSet(virtualReferences.getFirst().access);
                for (ReferencesSet virtualReference : virtualReferences) {
                    virtualReference.forEach(combined::add);
                }
                yield combined;
            }
        };
    }

    /**
     * Finds the references to the given field in the given method's instructions. The returned field instruction nodes
     * may not be contained in the method's instruction list if the field is referenced indirectly via an invokedynamic.
     */
    public List<FieldInsnNode> findCallsToFieldInMethod(MethodNode method, String fieldOwner, String fieldName, String fieldDesc) {
        List<FieldInsnNode> candidates = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            switch (insn) {
                case FieldInsnNode fieldInsn -> candidates.add(fieldInsn);
                case InvokeDynamicInsnNode indy -> {
                    for (Object bsmArg : indy.bsmArgs) {
                        addFakeFieldInsnsFromConstant(bsmArg, candidates);
                    }
                }
                default -> {
                }
            }
        }

        // Get the field we're expecting to see. We're not actually using its references here, only using the
        // ReferencesSet as a way of identifying the field.
        ReferencesSet expectedReferences = findFieldReferences(fieldOwner, fieldName, fieldDesc);

        List<FieldInsnNode> result = new ArrayList<>(1);
        for (FieldInsnNode fieldInsn : candidates) {
            if (!fieldInsn.name.equals(fieldName) || !fieldInsn.desc.equals(fieldDesc)) {
                continue;
            }
            ClassInfo ownerInfo = index.get(fieldInsn.owner);
            if (ownerInfo == null) {
                continue;
            }
            boolean isOurField = fieldInsn.owner.equals(fieldOwner);
            if (!isOurField) {
                // interfaces can only have static fields
                boolean checkInterfaces = fieldInsn.getOpcode() == Opcodes.GETSTATIC || fieldInsn.getOpcode() == Opcodes.PUTSTATIC;
                isOurField = resolveField(ownerInfo, new NameAndDesc(fieldName, fieldDesc), checkInterfaces, index::get) == expectedReferences;
            }
            if (isOurField) {
                result.add(fieldInsn);
            }
        }
        return result;
    }

    // add field references from indy and condy
    private static void addFakeFieldInsnsFromConstant(Object cst, List<FieldInsnNode> candidates) {
        switch (cst) {
            case Handle handle -> {
                if (handle.getTag() <= Opcodes.H_PUTSTATIC) {
                    int opcode = switch (handle.getTag()) {
                        case Opcodes.H_GETFIELD -> Opcodes.GETFIELD;
                        case Opcodes.H_GETSTATIC -> Opcodes.GETSTATIC;
                        case Opcodes.H_PUTFIELD -> Opcodes.PUTFIELD;
                        case Opcodes.H_PUTSTATIC -> Opcodes.PUTSTATIC;
                        default -> throw new AssertionError("Unexpected handle tag: " + handle.getTag());
                    };
                    candidates.add(new FieldInsnNode(opcode, handle.getOwner(), handle.getName(), handle.getDesc()));
                }
            }
            case ConstantDynamic condy -> {
                for (int i = 0; i < condy.getBootstrapMethodArgumentCount(); i++) {
                    addFakeFieldInsnsFromConstant(condy.getBootstrapMethodArgument(i), candidates);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Finds the references to a method in the given method's instructions. The returned method instruction nodes may
     * not be contained in the method's instruction list if the method is referenced indirectly via an invokedynamic.
     */
    public List<MethodInsnNode> findCallsToMethodInMethod(String containingClass, MethodNode method, String methodOwner, String methodName, String methodDesc) {
        List<MethodInsnNode> candidates = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            switch (insn) {
                case MethodInsnNode methodInsn -> candidates.add(methodInsn);
                case InvokeDynamicInsnNode indy -> {
                    for (Object bsmArg : indy.bsmArgs) {
                        addFakeMethodInsnsFromConstant(bsmArg, candidates);
                    }
                }
                default -> {
                }
            }
        }

        // Get the method we're expecting to see. We're not actually using its references here, only using the
        // ReferencesSet as a way of identifying the method.
        ReferencesSet expectedReferences = findMethodReferences(methodOwner, methodName, methodDesc);

        List<MethodInsnNode> result = new ArrayList<>(1);
        for (MethodInsnNode methodInsn : candidates) {
            if (!methodInsn.name.equals(methodName) || !methodInsn.desc.equals(methodDesc)) {
                continue;
            }
            ClassInfo ownerInfo = index.get(methodInsn.owner);
            if (ownerInfo == null) {
                continue;
            }
            boolean isOurMethod = methodInsn.owner.equals(methodOwner);
            if (!isOurMethod) {
                if (methodInsn.getOpcode() == Opcodes.INVOKESTATIC || methodInsn.getOpcode() == Opcodes.INVOKESPECIAL) {
                    isOurMethod = resolveNonVirtualMethod(ownerInfo, new NameAndDesc(methodName, methodDesc), index::get) == expectedReferences;
                } else {
                    isOurMethod = resolveVirtualMethod(containingClass, ownerInfo, new NameAndDesc(methodName, methodDesc), index::get).contains(expectedReferences);
                }
            }
            if (isOurMethod) {
                result.add(methodInsn);
            }
        }
        return result;
    }

    // add method references from indy and condy
    private static void addFakeMethodInsnsFromConstant(Object cst, List<MethodInsnNode> candidates) {
        switch (cst) {
            case Handle handle -> {
                if (handle.getTag() > Opcodes.H_PUTSTATIC) {
                    int opcode = switch (handle.getTag()) {
                        case Opcodes.H_INVOKEVIRTUAL -> Opcodes.INVOKEVIRTUAL;
                        case Opcodes.H_INVOKESTATIC -> Opcodes.INVOKESTATIC;
                        case Opcodes.H_INVOKESPECIAL, Opcodes.H_NEWINVOKESPECIAL -> Opcodes.INVOKESPECIAL;
                        case Opcodes.H_INVOKEINTERFACE -> Opcodes.INVOKEINTERFACE;
                        default -> throw new AssertionError("Unexpected handle tag: " + handle.getTag());
                    };
                    candidates.add(new MethodInsnNode(opcode, handle.getOwner(), handle.getName(), handle.getDesc(), handle.isInterface()));
                }
            }
            case ConstantDynamic condy -> {
                for (int i = 0; i < condy.getBootstrapMethodArgumentCount(); i++) {
                    addFakeMethodInsnsFromConstant(condy.getBootstrapMethodArgument(i), candidates);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Returns whether {@code rhs} is a subtype of {@code lhs}. Both {@code lhs} and {@code rhs} can be interfaces.
     */
    public boolean isAssignable(String lhs, String rhs) {
        if (lhs.equals(rhs)) {
            return true;
        }
        for (ClassInfo classInfo = index.get(rhs); classInfo != null; classInfo = index.get(classInfo.superName)) {
            if (lhs.equals(classInfo.superName)) {
                return true;
            }
            for (String interfaceName : classInfo.interfaces) {
                if (isAssignable(lhs, interfaceName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Creates an {@linkplain Analyzer} for the purpose of finding the types of local variables and values on the
     * operand stack.
     */
    public Analyzer<BasicValue> createTypeAnalyzer(String currentClass) {
        ClassInfo classInfo = index.get(currentClass);
        if (classInfo == null) {
            throw new IllegalArgumentException("Cannot find class " + currentClass);
        }
        return new Analyzer<>(
            new MixinVerifier(
                ASM.API_VERSION,
                Type.getObjectType(currentClass),
                Type.getObjectType(classInfo.superName),
                classInfo.interfaces.stream().map(Type::getObjectType).toList(),
                classInfo.isInterface
            )
        );
    }

    private class IndexerClassVisitor extends ClassVisitor {
        private final Interner<String> interner;
        private String className;

        IndexerClassVisitor(Interner<String> interner) {
            super(ASM.API_VERSION);
            this.interner = interner;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = interner.intern(name);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new IndexerMethodVisitor(interner, className, interner.intern(name), interner.intern(descriptor));
        }
    }

    private class IndexerMethodVisitor extends MethodVisitor {
        private final Interner<String> interner;
        private final OwnerNameAndDesc methodOwnerNameAndDesc;

        IndexerMethodVisitor(Interner<String> interner, String methodOwner, String methodName, String methodDesc) {
            super(ASM.API_VERSION);
            this.interner = interner;
            this.methodOwnerNameAndDesc = new OwnerNameAndDesc(methodOwner, methodName, methodDesc);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            ClassInfo ownerInfo = getOrCreateClassInfo(interner, owner);
            // interfaces can only have static fields
            boolean checkInterfaces = opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC;
            ReferencesSet references = resolveField(ownerInfo, new NameAndDesc(name, descriptor), checkInterfaces, className -> getOrCreateClassInfo(interner, className));
            if (references != null) {
                references.add(methodOwnerNameAndDesc);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESTATIC || opcode == Opcodes.INVOKESPECIAL) {
                ReferencesSet references = resolveNonVirtualMethod(getOrCreateClassInfo(interner, owner), new NameAndDesc(name, descriptor), className -> getOrCreateClassInfo(interner, className));
                if (references != null) {
                    references.add(methodOwnerNameAndDesc);
                }
            } else {
                ClassInfo ownerInfo = getOrCreateClassInfo(interner, owner);
                if (ownerInfo != null) {
                    for (ReferencesSet references : resolveVirtualMethod(methodOwnerNameAndDesc.owner, ownerInfo, new NameAndDesc(name, descriptor), className -> getOrCreateClassInfo(interner, className))) {
                        references.add(methodOwnerNameAndDesc);
                    }
                }
            }
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bsm, Object... bsmArgs) {
            for (Object bsmArg : bsmArgs) {
                handleConstant(bsmArg);
            }
        }

        private void handleConstant(Object cst) {
            switch (cst) {
                case Handle handle -> {
                    int opcode = switch (handle.getTag()) {
                        case Opcodes.H_GETFIELD -> Opcodes.GETFIELD;
                        case Opcodes.H_GETSTATIC -> Opcodes.GETSTATIC;
                        case Opcodes.H_PUTFIELD -> Opcodes.PUTFIELD;
                        case Opcodes.H_PUTSTATIC -> Opcodes.PUTSTATIC;
                        case Opcodes.H_INVOKEVIRTUAL -> Opcodes.INVOKEVIRTUAL;
                        case Opcodes.H_INVOKESTATIC -> Opcodes.INVOKESTATIC;
                        case Opcodes.H_INVOKESPECIAL, Opcodes.H_NEWINVOKESPECIAL -> Opcodes.INVOKESPECIAL;
                        case Opcodes.H_INVOKEINTERFACE -> Opcodes.INVOKEINTERFACE;
                        default -> throw new AssertionError("Unexpected handle tag: " + handle.getTag());
                    };
                    if (handle.getTag() <= Opcodes.H_PUTSTATIC) {
                        visitFieldInsn(opcode, handle.getOwner(), handle.getName(), handle.getDesc());
                    } else {
                        visitMethodInsn(opcode, handle.getOwner(), handle.getName(), handle.getDesc(), handle.isInterface());
                    }
                }
                case ConstantDynamic constantDynamic -> {
                    for (int i = 0; i < constantDynamic.getBootstrapMethodArgumentCount(); i++) {
                        handleConstant(constantDynamic.getBootstrapMethodArgument(i));
                    }
                }
                default -> {
                }
            }
        }
    }

    public record OwnerNameAndDesc(String owner, String name, String desc) implements Comparable<OwnerNameAndDesc> {
        /**
         * Reads the class file corresponding to {@linkplain #owner} and returns the {@linkplain MethodNode}
         * corresponding to {@linkplain #name} and {@linkplain #desc}.
         */
        public MethodNode resolveMethod(ReferencesFinder finder) {
            MethodNode[] result = {null};
            finder.getClassData(owner, new ClassVisitor(ASM.API_VERSION) {
                @Override
                public MethodVisitor visitMethod(int access, String name1, String desc1, String signature, String[] exceptions) {
                    return name1.equals(name) && desc1.equals(desc) ? result[0] = new MethodNode(access, name1, desc1, signature, exceptions) : null;
                }
            }, ClassReader.SKIP_FRAMES);
            if (result[0] == null) {
                throw new IllegalStateException("reference doesn't exist: " + owner + " " + name + " " + desc);
            }
            return result[0];
        }

        @Override
        public int compareTo(ReferencesFinder.OwnerNameAndDesc other) {
            return Comparator.comparing(OwnerNameAndDesc::owner)
                .thenComparing(OwnerNameAndDesc::name)
                .thenComparing(OwnerNameAndDesc::desc)
                .compare(this, other);
        }
    }

    private record NameAndDesc(String name, String desc) {
    }

    private record ClassInfo(
        String name,
        String superName,
        List<String> interfaces,
        boolean isInterface,
        Map<NameAndDesc, ReferencesSet> fieldReferences,
        Map<NameAndDesc, ReferencesSet> methodReferences
    ) {
        boolean isSamePackage(String other) {
            int thisSlashIndex = name.lastIndexOf('/');
            int otherSlashIndex = other.lastIndexOf('/');
            if (thisSlashIndex != otherSlashIndex) {
                return false;
            }
            return thisSlashIndex == -1 || name.regionMatches(0, other, 0, thisSlashIndex);
        }
    }

    @FunctionalInterface
    private interface ClassResolver {
        @Nullable
        ClassInfo resolve(String className);
    }

    public static class ReferencesSet implements Iterable<OwnerNameAndDesc> {
        private static final int HASH_SET_THRESHOLD = 5;
        private final int access;
        private Set<OwnerNameAndDesc> set = Set.of();

        private ReferencesSet(int access) {
            this.access = access;
        }

        public Iterable<OwnerNameAndDesc> sorted() {
            List<OwnerNameAndDesc> list = new ArrayList<>(set);
            list.sort(Comparator.naturalOrder());
            return list;
        }

        @Override
        public Iterator<OwnerNameAndDesc> iterator() {
            return set.iterator();
        }

        public boolean isEmpty() {
            return set.isEmpty();
        }

        public void add(OwnerNameAndDesc value) {
            if (set instanceof HashSet<OwnerNameAndDesc>) {
                set.add(value);
            } else if (!set.contains(value)) {
                if (set.size() == HASH_SET_THRESHOLD - 1) {
                    Set<OwnerNameAndDesc> oldSet = set;
                    set = new HashSet<>();
                    set.addAll(oldSet);
                    set.add(value);
                } else {
                    OwnerNameAndDesc[] elements = new OwnerNameAndDesc[set.size() + 1];
                    set.toArray(elements);
                    elements[elements.length - 1] = value;
                    set = Set.of(elements);
                }
            }
        }
    }
}
