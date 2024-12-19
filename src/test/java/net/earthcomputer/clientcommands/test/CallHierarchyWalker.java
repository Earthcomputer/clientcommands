package net.earthcomputer.clientcommands.test;

import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Recursively walks the call hierarchy starting from a particular field or method. Will continue walking if the method
 * is a synthetic or bridge method, or is specified explicitly via {@linkplain #recurseThrough}. Also supports filtering
 * calls by the type that the root field/method owner can be at runtime via {@linkplain #runtimeOwnerType(String)}.
 */
public abstract sealed class CallHierarchyWalker {
    final ReferencesFinder finder = ReferencesFinder.getInstance();
    private final Set<ReferencesFinder.OwnerNameAndDesc> methodsToRecurseThrough = new HashSet<>();
    // the reference <- referenced-from edges that have already been visited, used to prevent infinite recursion
    private final Set<Pair<ReferencesFinder.OwnerNameAndDesc, ReferencesFinder.OwnerNameAndDesc>> visitedEdges = new HashSet<>();
    private String runtimeOwnerType = "java/lang/Object";

    public static CallHierarchyWalker fromField(String owner, String name, String desc) {
        return new Field(owner, name, desc);
    }

    public static CallHierarchyWalker fromMethod(String owner, String name, String desc) {
        return new Method(owner, name, desc);
    }

    public CallHierarchyWalker recurseThrough(String owner, String name, String desc) {
        methodsToRecurseThrough.add(new ReferencesFinder.OwnerNameAndDesc(owner, name, desc));
        return this;
    }

    public CallHierarchyWalker runtimeOwnerType(String ownerType) {
        runtimeOwnerType = ownerType;
        return this;
    }

    public abstract void walk(ReferenceConsumer referenceConsumer);

    void handleReferences(
        List<ReferencesFinder.OwnerNameAndDesc> callStack,
        ReferencesFinder.ReferencesSet references,
        // gets the owner type declared on the call itself, used for when there is no dataflow information
        Function<AbstractInsnNode, String> declaredOwnerTypeGetter,
        // gets the depth in the operand stack that the owner can be found on a particular call. Can be used to make the
        // "owner" a different parameter to a method than the first when walking up the call stack. This is used for a
        // kind of multi-method dataflow to more accurately figure out if the runtime owner type matches.
        ToIntFunction<AbstractInsnNode> ownerStackDepthGetter,
        // gets the list of call instructions given the containing class name and the method node
        BiFunction<String, MethodNode, List<? extends AbstractInsnNode>> callFinder,
        ReferenceConsumer referenceConsumer
    ) {
        for (var reference : references.sorted()) {
            if (!visitedEdges.add(Pair.of(reference, callStack.getLast()))) {
                continue;
            }

            MethodNode method = reference.resolveMethod(finder);

            Analyzer<BasicValue> typeAnalyzer = finder.createTypeAnalyzer(reference.owner());
            Frame<BasicValue>[] typeFrames;
            try {
                typeFrames = typeAnalyzer.analyze(reference.owner(), method);
            } catch (AnalyzerException e) {
                throw new AssertionError("Failed to analyze method", e);
            }

            Analyzer<SourceValue> sourceAnalyzer = new Analyzer<>(new SourceInterpreter());
            Frame<SourceValue>[] sourceFrames;
            try {
                sourceFrames = sourceAnalyzer.analyze(reference.owner(), method);
            } catch (AnalyzerException e) {
                throw new AssertionError("Failed to analyze method", e);
            }

            // the number of references that match the owner type
            int matchingReferenceCount = 0;

            // the param index from which the owner consistently dataflows from
            int ownerParamIndex = -1;
            boolean consistentOwnerParamIndex = true;

            List<? extends AbstractInsnNode> calls = callFinder.apply(reference.owner(), method);
            for (AbstractInsnNode call : calls) {
                int ownerStackDepth = ownerStackDepthGetter.applyAsInt(call);
                if (ownerStackDepth != -1) {
                    String foundOwnerType;
                    // the call may not be in the instruction list if it's inside an invokedynamic
                    if (method.instructions.contains(call)) {
                        int insnIndex = method.instructions.indexOf(call);
                        Frame<BasicValue> typeFrame = typeFrames[insnIndex];
                        Frame<SourceValue> sourceFrame = sourceFrames[insnIndex];
                        if (typeFrame == null || sourceFrame == null) {
                            continue;
                        }

                        foundOwnerType = typeFrame.getStack(typeFrame.getStackSize() - 1 - ownerStackDepth).getType().getInternalName();

                        // find which parameter the owner dataflows from
                        if (consistentOwnerParamIndex) {
                            Set<AbstractInsnNode> ownerSourceInsns = sourceFrame.getStack(sourceFrame.getStackSize() - 1 - ownerStackDepth).insns;
                            for (AbstractInsnNode ownerSourceInsn : ownerSourceInsns) {
                                if (!(ownerSourceInsn instanceof VarInsnNode varInsn)) {
                                    consistentOwnerParamIndex = false;
                                    break;
                                }
                                if (ownerParamIndex != varInsn.var) {
                                    if (ownerParamIndex == -1) {
                                        ownerParamIndex = varInsn.var;
                                    } else {
                                        consistentOwnerParamIndex = false;
                                        break;
                                    }
                                }
                            }
                        }
                    } else {
                        // in the case of invokedynamic, dataflow would be too complicated, so we resort to using the owner type
                        // declared on the call
                        foundOwnerType = declaredOwnerTypeGetter.apply(call);
                    }

                    if (!finder.isAssignable(foundOwnerType, runtimeOwnerType) && !finder.isAssignable(runtimeOwnerType, foundOwnerType)) {
                        continue;
                    }
                }

                matchingReferenceCount++;
            }

            if (matchingReferenceCount == 0) {
                continue;
            }

            if ((method.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0 || methodsToRecurseThrough.contains(reference)) {
                // recursively find references to synthetic, bridge or explicitly listed methods

                if (!consistentOwnerParamIndex) {
                    ownerParamIndex = -1;
                } else if (ownerParamIndex != -1) {
                    // Convert index in the LVT to parameter index (not the same because of longs and doubles) for ownerParamIndex.
                    // Also ensure our variable is actually a parameter
                    int paramIndex = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
                    if (ownerParamIndex >= paramIndex) {
                        for (Type argumentType : Type.getArgumentTypes(method.desc)) {
                            if (ownerParamIndex == paramIndex) {
                                break;
                            }
                            if (argumentType.getSize() > 1) {
                                ownerParamIndex -= argumentType.getSize() - 1;
                            }
                            paramIndex++;
                        }
                        if (ownerParamIndex >= paramIndex) {
                            ownerParamIndex = -1;
                        }
                    }
                }

                ReferencesFinder.ReferencesSet recursiveReferences = finder.findMethodReferences(reference.owner(), reference.name(), reference.desc());
                if (!recursiveReferences.isEmpty()) {
                    List<ReferencesFinder.OwnerNameAndDesc> newCallStack = new ArrayList<>(callStack.size() + 1);
                    newCallStack.addAll(callStack);
                    newCallStack.add(reference);

                    int argumentCountWithThis = Type.getArgumentCount(method.desc);
                    if ((method.access & Opcodes.ACC_STATIC) == 0) {
                        argumentCountWithThis++;
                    }
                    int ownerStackDepth = ownerParamIndex == -1 ? -1 : argumentCountWithThis - 1 - ownerParamIndex;
                    boolean ownerIsThis = (method.access & Opcodes.ACC_STATIC) == 0 && ownerParamIndex == 0;

                    handleReferences(
                        newCallStack,
                        recursiveReferences,
                        insn -> ownerIsThis ? ((MethodInsnNode) insn).owner : runtimeOwnerType,
                        insn -> ownerStackDepth,
                        (containingClass, methodNode) -> finder.findCallsToMethodInMethod(containingClass, methodNode, reference.owner(), reference.name(), reference.desc()),
                        referenceConsumer
                    );

                    continue;
                }
            }

            for (int i = 0; i < matchingReferenceCount; i++) {
                referenceConsumer.accept(reference, callStack);
            }
        }
    }

    private static final class Field extends CallHierarchyWalker {
        private final String owner;
        private final String name;
        private final String desc;

        private Field(String owner, String name, String desc) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }

        @Override
        public void walk(ReferenceConsumer referenceConsumer) {
            handleReferences(
                List.of(new ReferencesFinder.OwnerNameAndDesc(owner, name, desc)),
                finder.findFieldReferences(owner, name, desc),
                insn -> ((FieldInsnNode) insn).owner,
                insn -> switch (insn.getOpcode()) {
                    case Opcodes.GETFIELD -> 0;
                    case Opcodes.PUTFIELD -> 1;
                    default -> -1;
                },
                (containingClass, method) -> finder.findCallsToFieldInMethod(method, owner, name, desc),
                referenceConsumer
            );
        }
    }

    private static final class Method extends CallHierarchyWalker {
        private final String owner;
        private final String name;
        private final String desc;

        private Method(String owner, String name, String desc) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }

        @Override
        public void walk(ReferenceConsumer referenceConsumer) {
            int argumentCount = Type.getArgumentCount(desc);
            handleReferences(
                List.of(new ReferencesFinder.OwnerNameAndDesc(owner, name, desc)),
                finder.findMethodReferences(owner, name, desc),
                insn -> ((MethodInsnNode) insn).owner,
                insn -> insn.getOpcode() == Opcodes.INVOKESTATIC ? -1 : argumentCount,
                (containingClass, method) -> finder.findCallsToMethodInMethod(containingClass, method, owner, name, desc),
                referenceConsumer
            );
        }
    }

    @FunctionalInterface
    public interface ReferenceConsumer {
        /**
         * Consume a reference. The call stack does not contain the reference, and is ordered from the field/method you
         * started walking from first, to the thing directly referenced by the reference last.
         */
        void accept(ReferencesFinder.OwnerNameAndDesc reference, List<ReferencesFinder.OwnerNameAndDesc> callStack);
    }
}
