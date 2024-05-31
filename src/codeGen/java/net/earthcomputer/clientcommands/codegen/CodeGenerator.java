package net.earthcomputer.clientcommands.codegen;

import com.seedfinding.latticg.generator.ClassGenerator;
import com.seedfinding.latticg.reversal.Program;
import com.seedfinding.latticg.reversal.ProgramBuilder;
import com.seedfinding.latticg.reversal.calltype.java.JavaCalls;
import com.seedfinding.latticg.util.LCG;
import net.earthcomputer.clientcommands.features.CCrackRng;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CodeGenerator {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("clientcommands-codegen <destinationDir>");
            return;
        }

        Path destDir = Path.of(args[0]);
        genPlayerLattiCG(destDir);
        genVillagerLattiCG(destDir);
    }

    private static void genVillagerLattiCG(Path destDir) throws IOException {
        ProgramBuilder program = Program.builder(LCG.JAVA);

        program.skip(-2);
        program.add(JavaCalls.nextLong());

        writeLattiCGClass(program.build(), "net.earthcomputer.clientcommands.features.CrackVillagerRngGen", destDir);
    }

    private static void genPlayerLattiCG(Path destDir) throws IOException {
        ProgramBuilder program = Program.builder(LCG.JAVA);
        program.skip(-CCrackRng.NUM_THROWS * 4);
        for (int i = 0; i < CCrackRng.NUM_THROWS; i++) {
            program.skip(1);
            program.add(JavaCalls.nextFloat().ranged(CCrackRng.MAX_ERROR * 2));
            program.skip(2);
        }

        writeLattiCGClass(program.build(), "net.earthcomputer.clientcommands.features.CCrackRngGen", destDir);
    }

    private static void writeLattiCGClass(Program program, String fqName, Path destDir) throws IOException {
        int dotIndex = fqName.lastIndexOf('.');
        assert dotIndex >= 0;
        String packageName = fqName.substring(0, dotIndex);
        String className = fqName.substring(dotIndex + 1);

        String generatedClass = new ClassGenerator(packageName, className, program).generate();
        Files.writeString(destDir.resolve(fqName.replace('.', '/') + ".java"), generatedClass);
    }
}
