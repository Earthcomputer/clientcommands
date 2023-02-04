package net.earthcomputer.clientcommands.features;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.earthcomputer.clientcommands.ClientCommands;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.RandomSeed;
import org.slf4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class DebugRandom extends CheckedRandom {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final EntityType<?> DEBUG_ENTITY_TYPE;
    static {
        String debugEntityType = System.getProperty("clientcommands.debugEntityRng");
        if (debugEntityType == null) {
            DEBUG_ENTITY_TYPE = null;
        } else {
            DEBUG_ENTITY_TYPE = Registries.ENTITY_TYPE.get(new Identifier(debugEntityType));
        }
    }

    private static final Object2IntMap<String> stackTraceIds = new Object2IntOpenHashMap<>();
    static final List<String> stackTraceById = new ArrayList<>();

    private final Entity entity;
    private boolean firstTick = true;

    private final List<IntList> stackTraces = new ArrayList<>();
    private IntList stackTracesThisTick = new IntArrayList();

    private final ByteArrayOutputStream gzippedNbt = new ByteArrayOutputStream();
    private final DataOutputStream nbtStream;

    public DebugRandom(Entity entity) {
        super(RandomSeed.getSeed());
        this.entity = entity;
        this.stackTraces.add(this.stackTracesThisTick);
        try {
            this.nbtStream = new DataOutputStream(new GZIPOutputStream(gzippedNbt));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public int next(int bits) {
        StringWriter sw = new StringWriter();
        new Throwable("Stack trace").printStackTrace(new PrintWriter(sw));
        int stackTrace = stackTraceIds.computeIfAbsent(sw.toString(), (String st) -> {
            stackTraceById.add(st);
            return stackTraceIds.size();
        });
        handleStackTrace(stackTrace);

        return super.next(bits);
    }

    public void tick() {
        this.stackTraces.add(this.stackTracesThisTick = new IntArrayList());
        firstTick = false;
    }

    private void handleStackTrace(int stackTrace) {
        this.stackTracesThisTick.add(stackTrace);
        try {
            NbtIo.write(firstTick ? new NbtCompound() : entity.writeNbt(new NbtCompound()), nbtStream);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    public void writeToFile() {
        try {
            this.nbtStream.close();
            Path debugDir = ClientCommands.configDir.resolve("debug");
            Files.createDirectories(debugDir);
            try (DataOutputStream dataOutput = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(debugDir.resolve(this.entity.getUuidAsString() + ".dat"))))) {
                dataOutput.writeInt(stackTraceById.size());
                for (String st : stackTraceById) {
                    dataOutput.writeUTF(st);
                }
                dataOutput.writeInt(stackTraces.size());
                for (IntList stackTracesThisTick : stackTraces) {
                    dataOutput.writeInt(stackTracesThisTick.size());
                    for (int i = 0; i < stackTracesThisTick.size(); i++) {
                        dataOutput.writeInt(stackTracesThisTick.getInt(i));
                    }
                }
                dataOutput.write(this.gzippedNbt.toByteArray());
            }
            LOGGER.info("Written debug random for " + this.entity.getUuidAsString() + " to file");
        } catch (IOException e) {
            LOGGER.error("Error saving debug source to file", e);
        }
    }

    public static void main(String[] args) {
        JFileChooser fileChooser = new JFileChooser("config/clientcommands/debug");
        if (fileChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = fileChooser.getSelectedFile().toPath();

        List<List<RandomCall>> randomCalls;

        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))) {
            int numStackTraces = in.readInt();
            for (int i = 0; i < numStackTraces; i++) {
                String stackTrace = in.readUTF();
                stackTraceById.add(stackTrace);
                stackTraceIds.put(stackTrace, i);
            }

            int numTicks = in.readInt();
            List<IntList> stackTraces = new ArrayList<>(numTicks);
            for (int tick = 0; tick < numTicks; tick++) {
                int numStackTracesThisTick = in.readInt();
                IntList stackTracesThisTick = new IntArrayList(numStackTracesThisTick);
                for (int i = 0; i < numStackTracesThisTick; i++) {
                    stackTracesThisTick.add(in.readInt());
                }
                stackTraces.add(stackTracesThisTick);
            }

            randomCalls = new ArrayList<>(numTicks);

            byte[] remainingBytes = in.readAllBytes();
            try (DataInputStream in2 = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(remainingBytes)))) {
                for (IntList stackTracesThisTick : stackTraces) {
                    List<RandomCall> callsThisTick = new ArrayList<>(stackTracesThisTick.size());
                    for (int j = 0; j < stackTracesThisTick.size(); j++) {
                        callsThisTick.add(new RandomCall(stackTracesThisTick.getInt(j), NbtIo.read(in2)));
                    }
                    randomCalls.add(callsThisTick);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read file", e);
            return;
        }

        JFrame frame = new JFrame("Debug Entity RNG");
        frame.add(new DebugRandomSourcePanel(randomCalls));
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }
}

class DebugRandomSourcePanel extends JPanel {
    private final List<List<RandomCall>> randomCalls;
    private List<RandomCall> selectedTick = Collections.emptyList();
    private int selectedStackTrace = 0;

    private final JList<List<RandomCall>> randomCallsList;
    private final JList<RandomCall> callsInTickList;

    DebugRandomSourcePanel(List<List<RandomCall>> randomCalls) {
        this.randomCalls = randomCalls;

        setPreferredSize(new Dimension(1280, 720));
        setLayout(new BorderLayout());

        randomCallsList = new JList<>(new Vector<>(randomCalls));
        randomCallsList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(index + ": " + value.size());
            setupSelectionUI(list, label, isSelected, cellHasFocus);
            if (isSameAsSelectedTick(value)) {
                label.setForeground(Color.GREEN);
            } else if (containsSelectedStackTrace(value)) {
                label.setForeground(Color.YELLOW);
            }
            return label;
        });
        randomCallsList.addListSelectionListener(e -> {
            int index = randomCallsList.getSelectedIndex();
            if (index >= 0 && index < this.randomCalls.size()) {
                setSelectedTick(this.randomCalls.get(index));
            }
        });

        callsInTickList = new JList<>();
        callsInTickList.setModel(new DefaultListModel<>());
        callsInTickList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JTextArea textArea = new JTextArea(value.nbt() + "\n" + DebugRandom.stackTraceById.get(value.stackTrace()));
            textArea.setEditable(false);
            setupSelectionUI(list, textArea, isSelected, cellHasFocus);
            if (value.stackTrace() == selectedStackTrace) {
                textArea.setBackground(Color.YELLOW);
            }
            return textArea;
        });
        callsInTickList.addListSelectionListener(e -> {
            int index = callsInTickList.getSelectedIndex();
            if (index >= 0 && index < selectedTick.size()) {
                selectedStackTrace = selectedTick.get(index).stackTrace();
                randomCallsList.repaint();
                callsInTickList.repaint();
            }
        });

        add(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(randomCallsList), new JScrollPane(callsInTickList)), BorderLayout.CENTER);
    }

    private void setSelectedTick(List<RandomCall> randomCalls) {
        selectedStackTrace = -1;
        selectedTick = randomCalls;
        randomCallsList.repaint();

        var model = (DefaultListModel<RandomCall>) callsInTickList.getModel();
        model.clear();
        model.addAll(randomCalls);
    }

    private boolean isSameAsSelectedTick(List<RandomCall> randomCalls) {
        if (randomCalls.size() != selectedTick.size()) {
            return false;
        }
        for (int i = 0; i < randomCalls.size(); i++) {
            if (randomCalls.get(i).stackTrace() != selectedTick.get(i).stackTrace()) {
                return false;
            }
        }
        return true;
    }

    private boolean containsSelectedStackTrace(List<RandomCall> randomCalls) {
        for (RandomCall randomCall : randomCalls) {
            if (randomCall.stackTrace() == selectedStackTrace) {
                return true;
            }
        }
        return false;
    }

    private void setupSelectionUI(JList<?> list, JComponent component, boolean isSelected, boolean cellHasFocus) {
        component.setBackground(list.getBackground());
        component.setForeground(list.getForeground());

        if (isSelected) {
            component.setBackground(list.getSelectionBackground());
            component.setForeground(list.getSelectionForeground());
        }
        if (cellHasFocus) {
            if (isSelected) {
                component.setBorder(UIManager.getBorder("List.focusSelectedCellHighlightBorder", component.getLocale()));
            }
            if (component.getBorder() == null) {
                component.setBorder(UIManager.getBorder("List.focusCellHighlightBorder", component.getLocale()));
            }
        }
    }
}

record RandomCall(int stackTrace, NbtCompound nbt) {
}
