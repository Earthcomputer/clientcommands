package net.earthcomputer.clientcommands.util;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.earthcomputer.clientcommands.ClientCommands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomSupport;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class DebugRandom extends LegacyRandomSource {
    static final Logger LOGGER = LogUtils.getLogger();

    public static final EntityType<?> DEBUG_ENTITY_TYPE;
    static {
        String debugEntityType = System.getProperty("clientcommands.debugEntityRng");
        if (debugEntityType == null) {
            DEBUG_ENTITY_TYPE = null;
        } else {
            DEBUG_ENTITY_TYPE = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(debugEntityType));
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
        super(RandomSupport.generateUniqueSeed());
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
            NbtIo.writeUnnamedTagWithFallback(firstTick ? new CompoundTag() : entity.saveWithoutId(new CompoundTag()), nbtStream);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    public void writeToFile() {
        try {
            this.nbtStream.close();
            Path debugDir = ClientCommands.configDir.resolve("debug");
            Files.createDirectories(debugDir);
            try (DataOutputStream dataOutput = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(debugDir.resolve(this.entity.getStringUUID() + ".dat"))))) {
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
            LOGGER.info("Written debug random for " + this.entity.getStringUUID() + " to file");
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
    @Nullable
    private List<RandomCall> selectedTick = null;
    private int selectedStackTrace = 0;
    private final IntSet usedStackTraces = new IntOpenHashSet();

    private final JList<List<RandomCall>> randomCallsList;
    private final JList<RandomCall> callsInTickList;
    private final JList<String> allStackTraceList;

    DebugRandomSourcePanel(List<List<RandomCall>> randomCalls) {
        this.randomCalls = randomCalls;

        for (List<RandomCall> randomCallsThisTick : randomCalls) {
            for (RandomCall randomCall : randomCallsThisTick) {
                usedStackTraces.add(randomCall.stackTrace());
            }
        }

        setPreferredSize(new Dimension(1280, 720));
        setLayout(new BorderLayout());

        randomCallsList = new JList<>(new Vector<>(randomCalls));
        randomCallsList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(index + ": " + value.size());
            setupSelectionUI(list, label, isSelected, cellHasFocus);
            if (isSameAsSelectedTick(value)) {
                label.setForeground(Color.GREEN);
            } else if (containsSelectedStackTrace(value)) {
                label.setForeground(Color.RED);
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
            if (selectedTick != null && index >= 0 && index < selectedTick.size()) {
                setSelectedStackTrace(selectedTick.get(index).stackTrace());
            }
        });

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Ticks", new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(randomCallsList), new JScrollPane(callsInTickList)));

        allStackTraceList = new JList<>(new Vector<>(DebugRandom.stackTraceById));
        allStackTraceList.addListSelectionListener(e -> {
            setSelectedStackTrace(allStackTraceList.getSelectedIndex());
        });
        allStackTraceList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            if (!usedStackTraces.contains(index)) {
                return new JPanel();
            }
            JTextArea textArea = new JTextArea(value);
            textArea.setEditable(false);
            setupSelectionUI(list, textArea, isSelected, cellHasFocus);
            if (index == selectedStackTrace) {
                textArea.setBackground(Color.YELLOW);
            }
            return textArea;
        });
        tabbedPane.addTab("All traces", new JScrollPane(allStackTraceList));

        add(tabbedPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        JButton dumpStackTraceButton = new JButton("Dump stack trace");
        dumpStackTraceButton.addActionListener(e -> {
            if (selectedStackTrace >= 0 && selectedStackTrace < DebugRandom.stackTraceById.size()) {
                DebugRandom.LOGGER.info(DebugRandom.stackTraceById.get(selectedStackTrace));
            }
        });
        bottomPanel.add(dumpStackTraceButton);
        JButton dumpStackTracesWithQuantitiesButton = new JButton("Dump unique stack traces with quantities");
        dumpStackTracesWithQuantitiesButton.addActionListener(e -> {
            if (selectedTick == null) {
                return;
            }

            LinkedHashMap<RandomCall, MutableInt> map = new LinkedHashMap<>();
            for (RandomCall call : selectedTick) {
                map.computeIfAbsent(call, k -> new MutableInt(1)).add(1);
            }
            DebugRandom.LOGGER.info(String.join("\n\n", map.entrySet().stream().map(entry -> String.format("[x%d] %s", entry.getValue().getValue(), DebugRandom.stackTraceById.get(entry.getKey().stackTrace()))).toArray(String[]::new)));
        });
        bottomPanel.add(dumpStackTracesWithQuantitiesButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void setSelectedStackTrace(int selectedStackTrace) {
        this.selectedStackTrace = selectedStackTrace;
        if (randomCallsList != null) {
            randomCallsList.repaint();
        }
        if (callsInTickList != null) {
            callsInTickList.repaint();
        }
        if (allStackTraceList != null) {
            allStackTraceList.repaint();
        }
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
        if (selectedTick == null) {
            return false;
        }
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

record RandomCall(int stackTrace, CompoundTag nbt) {
}
