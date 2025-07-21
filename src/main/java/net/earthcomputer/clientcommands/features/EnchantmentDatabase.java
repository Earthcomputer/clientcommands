package net.earthcomputer.clientcommands.features;

import com.google.common.base.Preconditions;
import com.google.common.collect.Comparators;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Stream;

public final class EnchantmentDatabase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Codec<IntList> INT_LIST_CODEC = Codec.INT.listOf().xmap(IntArrayList::new, Function.identity());

    public static final EnchantmentDatabase INSTANCE = loadBuiltinDatabase();

    private static final int ALGORITHM_CURRENT = 0;
    private static final int ALGORITHM_114 = 1;

    private final Pool<EnchantmentData> enchantmentPool;
    private final Pool<ItemType> itemTypePool;

    // Keys are sorted by enchantment (index into item's applicableEnchantments NOT enchantmentPool), note this makes searches where the order of enchantments matters impossible.
    // Values are maps from item type (index into itemTypePool) to maps from bookshelf-slot key to frequency (number of times seen out of all 2^32 possible enchantment seeds).
    private final Map<List<EnchantmentWithLevel>, Int2ObjectMap<Int2IntMap>> resultStats;
    private final Map<List<EnchantmentWithLevel>, Int2ObjectMap<Int2IntMap>> subsetStats;

    public EnchantmentDatabase() {
        this(new Pool<>(), new Pool<>(), new HashMap<>(), new HashMap<>());
    }

    private EnchantmentDatabase(
        Pool<EnchantmentData> enchantmentPool,
        Pool<ItemType> itemTypePool,
        Map<List<EnchantmentWithLevel>, Int2ObjectMap<Int2IntMap>> resultStats,
        Map<List<EnchantmentWithLevel>, Int2ObjectMap<Int2IntMap>> subsetStats
    ) {
        this.enchantmentPool = enchantmentPool;
        this.itemTypePool = itemTypePool;
        this.resultStats = resultStats;
        this.subsetStats = subsetStats;
    }

    @Nullable
    public Integer getSeedsWithOutcome(HolderLookup.Provider registries, ItemStack item, List<EnchantmentInstance> enchantments, boolean enchantingTable, boolean exact, int bookshelves, int slot) {
        if (item.is(Items.ENCHANTED_BOOK)) {
            item = item.transmuteCopy(Items.BOOK);
        }

        ItemType itemType = itemTypeFromItem(item, enchantingTable, registries, false);
        if (itemType == null) {
            return null;
        }

        Integer itemIndex = itemTypePool.getIndex(itemType);
        if (itemIndex == null) {
            return null;
        }

        List<Holder<Enchantment>> applicableEnchantments = getApplicableEnchantments(item, enchantingTable, registries);
        if (applicableEnchantments == null) {
            return null;
        }

        Object2IntMap<Holder<Enchantment>> enchantmentToIndex = new Object2IntOpenHashMap<>();
        enchantmentToIndex.defaultReturnValue(-1);
        for (int i = 0; i < applicableEnchantments.size(); i++) {
            enchantmentToIndex.put(applicableEnchantments.get(i), i);
        }

        List<EnchantmentWithLevel> enchantmentWithLevels = new ArrayList<>(enchantments.size());
        for (EnchantmentInstance enchantment : enchantments) {
            int enchantmentIndex = enchantmentToIndex.getInt(enchantment.enchantment());
            if (enchantmentIndex == -1) {
                return 0;
            }
            enchantmentWithLevels.add(new EnchantmentWithLevel(enchantmentIndex, enchantment.level()));
        }

        int bookshelfAndSlot = getBookshelfSlotKey(bookshelves, slot);
        int result = 0;

        Int2ObjectMap<Int2IntMap> stats = resultStats.get(enchantmentWithLevels);
        if (stats != null) {
            Int2IntMap itemTypeStats = stats.get(itemIndex.intValue());
            if (itemTypeStats != null) {
                result += itemTypeStats.get(bookshelfAndSlot);
            }
        }

        if (!exact) {
            stats = subsetStats.get(enchantmentWithLevels);
            if (stats != null) {
                Int2IntMap itemTypeStats = stats.get(itemIndex.intValue());
                if (itemTypeStats != null) {
                    result += itemTypeStats.get(bookshelfAndSlot);
                }
            }
        }

        return result;
    }

    public static List<List<Holder<Item>>> groupItems(HolderLookup.Provider registries) {
        EnchantmentDatabase tempDatabase = new EnchantmentDatabase();

        Map<ItemType, List<Holder<Item>>> itemsByType = new LinkedHashMap<>();
        registries.lookupOrThrow(Registries.ITEM).listElements().forEach(item -> {
            ItemType type = tempDatabase.itemTypeFromItem(new ItemStack(item), true, registries, true);
            if (type != null) {
                itemsByType.computeIfAbsent(type, t -> new ArrayList<>()).add(item);
            }
        });

        return new ArrayList<>(itemsByType.values());
    }

    private static EnchantmentDatabase loadBuiltinDatabase() {
        try {
            Path databasePath = FabricLoader.getInstance().getModContainer("clientcommands").orElseThrow()
                .findPath("enchantment_database.nbt").orElseThrow();
            CompoundTag nbt = NbtIo.readCompressed(databasePath, NbtAccounter.unlimitedHeap());
            return fromNbt(nbt);
        } catch (Throwable e) {
            LOGGER.error("Exception reading built-in enchantment database", e);
            return new EnchantmentDatabase();
        }
    }

    public static EnchantmentDatabase fromNbt(CompoundTag nbt) {
        Pool<EnchantmentData> enchantmentPool = nbt.read("enchantment_pool", Pool.codec(EnchantmentData.CODEC)).orElseGet(Pool::new);
        Pool<ItemType> itemTypePool = nbt.read("item_type_pool", Pool.codec(ItemType.CODEC)).orElseGet(Pool::new);
        var resultStats = nbt.getByteArray("result_stats").flatMap(EnchantmentDatabase::resultStatsFromNbt).orElseGet(HashMap::new);
        var subsetStats = nbt.getByteArray("subset_stats").flatMap(EnchantmentDatabase::resultStatsFromNbt).orElseGet(HashMap::new);
        return new EnchantmentDatabase(enchantmentPool, itemTypePool, resultStats, subsetStats);
    }

    private static Optional<Map<List<EnchantmentWithLevel>, Int2ObjectMap<Int2IntMap>>> resultStatsFromNbt(byte[] nbt) {
        ByteArrayInputStream bais = new ByteArrayInputStream(nbt);

        try {
            int numResultStats = readVarInt(bais);
            Map<List<EnchantmentWithLevel>, Int2ObjectMap<Int2IntMap>> resultStats = HashMap.newHashMap(numResultStats);
            for (int i = 0; i < numResultStats; i++) {
                int numEnchantments = readVarInt(bais);
                List<EnchantmentWithLevel> enchantments = new ArrayList<>(numEnchantments);
                for (int j = 0; j < numEnchantments; j++) {
                    enchantments.add(new EnchantmentWithLevel(readVarInt(bais), readVarInt(bais)));
                }

                int numStats = readVarInt(bais);
                Int2ObjectMap<Int2IntMap> stats = new Int2ObjectOpenHashMap<>(numStats);
                for (int j = 0; j < numStats; j++) {
                    int itemType = readVarInt(bais);
                    int numItemTypeStats = readVarInt(bais);
                    Int2IntMap itemTypeStats = new Int2IntOpenHashMap(numItemTypeStats);
                    for (int k = 0; k < numItemTypeStats; k++) {
                        itemTypeStats.put(readVarInt(bais), readVarInt(bais));
                    }
                    stats.put(itemType, itemTypeStats);
                }

                resultStats.put(enchantments, stats);
            }

            return Optional.of(resultStats);
        } catch (IOException e) {
            LOGGER.error("Failed to read enchantments from nbt: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static int readVarInt(ByteArrayInputStream bais) throws IOException {
        int value = 0;
        int position = 0;

        while (true) {
            if (position >= 35) {
                throw new IOException("VarInt is too long");
            }

            int currentByte = bais.read();
            if (currentByte == -1) {
                throw new IOException("Unexpected end of stream while reading VarInt");
            }

            value |= (currentByte & 0x7f) << position;

            if ((currentByte & 0x80) == 0) {
                break;
            }

            position += 7;
        }

        return value;
    }

    public CompoundTag toNbt() {
        CompoundTag result = new CompoundTag();
        result.store("enchantment_pool", Pool.codec(EnchantmentData.CODEC), enchantmentPool);
        result.store("item_type_pool", Pool.codec(ItemType.CODEC), itemTypePool);
        result.put("result_stats", resultStatsToNbt(resultStats));
        result.put("subset_stats", resultStatsToNbt(subsetStats));
        return result;
    }

    private static ByteArrayTag resultStatsToNbt(Map<List<EnchantmentWithLevel>, Int2ObjectMap<Int2IntMap>> resultStats) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeVarInt(baos, resultStats.size());
        resultStats.forEach((enchantments, stats) -> {
            writeVarInt(baos, enchantments.size());
            for (EnchantmentWithLevel ench : enchantments) {
                writeVarInt(baos, ench.enchantment());
                writeVarInt(baos, ench.level());
            }

            writeVarInt(baos, stats.size());
            stats.forEach((itemType, itemTypeStats) -> {
                writeVarInt(baos, itemType);
                writeVarInt(baos, itemTypeStats.size());
                itemTypeStats.forEach((bookshelvesAndSlot, frequency) -> {
                    writeVarInt(baos, bookshelvesAndSlot);
                    writeVarInt(baos, frequency);
                });
            });
        });

        return new ByteArrayTag(baos.toByteArray());
    }

    private static void writeVarInt(ByteArrayOutputStream baos, int value) {
        while ((value & ~0x7f) != 0) {
            baos.write((value & 0x7f) | 0x80);
            value >>>= 7;
        }
        baos.write(value & 0x7f);
    }

    private static int currentEnchantmentAlgorithm() {
        int protocolVersion = MultiVersionCompat.INSTANCE.getProtocolVersion();
        if (protocolVersion >= MultiVersionCompat.V1_14 && protocolVersion <= MultiVersionCompat.V1_14_2) {
            return ALGORITHM_114;
        }

        return ALGORITHM_CURRENT;
    }

    private static EnchantmentData enchantmentDataFromEnchantment(Holder<Enchantment> ench) {
        return new EnchantmentData(
            ench.value().getWeight(),
            ench.value().getMaxLevel(),
            ench.value().definition().minCost(),
            ench.value().definition().maxCost()
        );
    }

    @Nullable
    private ItemType itemTypeFromItem(ItemStack item, boolean enchantingTable, HolderLookup.Provider registries, boolean create) {
        Enchantable enchantable = item.get(DataComponents.ENCHANTABLE);
        if (enchantable == null) {
            return null;
        }

        List<Holder<Enchantment>> enchantments = getApplicableEnchantments(item, enchantingTable, registries);
        if (enchantments == null) {
            return null;
        }

        Reference2IntMap<Holder<Enchantment>> enchantmentIndexes = new Reference2IntOpenHashMap<>();
        enchantmentIndexes.defaultReturnValue(-1);
        for (int i = 0; i < enchantments.size(); i++) {
            enchantmentIndexes.put(enchantments.get(i), i);
        }

        IntList applicableEnchantments = new IntArrayList(enchantments.size());
        List<IntList> exclusiveSets = new ArrayList<>();
        for (Holder<Enchantment> enchantment : enchantments) {
            EnchantmentData data = enchantmentDataFromEnchantment(enchantment);
            Integer dataId = enchantmentPool.getIndex(data, create);
            if (dataId == null) {
                return null;
            }
            applicableEnchantments.add(dataId.intValue());

            // don't worry about duplicates in exclusive sets or sorting them, the ItemType constructor handles that
            HolderSet<Enchantment> exclusiveSet = enchantment.value().exclusiveSet();
            if (exclusiveSet.size() != 0) {
                IntList exclusiveEnchantments = new IntArrayList();
                exclusiveEnchantments.add(enchantmentIndexes.getInt(enchantment));
                for (Holder<Enchantment> exclusiveEnchantment : exclusiveSet) {
                    int exclusiveId = enchantmentIndexes.getInt(exclusiveEnchantment);
                    if (exclusiveId != -1) {
                        exclusiveEnchantments.add(exclusiveId);
                    }
                }
                exclusiveSets.add(exclusiveEnchantments);
            }
        }

        return new ItemType(
            currentEnchantmentAlgorithm(),
            enchantable.value() / 4 * 4, // enchantability is only ever divided by 4, so treat enchantability that's the same when divided by 4 as the same item
            applicableEnchantments,
            exclusiveSets
        );
    }

    @Nullable
    private static List<Holder<Enchantment>> getApplicableEnchantments(ItemStack item, boolean enchantingTable, HolderLookup.Provider registries) {
        var enchantmentRegistry = registries.lookupOrThrow(Registries.ENCHANTMENT);

        Stream<Holder<Enchantment>> allEnchantments;
        if (enchantingTable) {
            var enchantingTableTag = enchantmentRegistry.get(EnchantmentTags.IN_ENCHANTING_TABLE);
            if (enchantingTableTag.isEmpty()) {
                return null;
            }
            allEnchantments = enchantingTableTag.get().stream();
        } else {
            allEnchantments = enchantmentRegistry.listElements().map(ench -> ench);
        }

        List<Holder<Enchantment>> enchantments = allEnchantments.filter(ench -> ench.value().isPrimaryItem(item) || item.is(Items.BOOK)).toList();
        if (enchantments.isEmpty()) {
            return null;
        }
        return enchantments;
    }

    public void merge(EnchantmentDatabase other) {
        mergeResultStats(other, resultStats, other.resultStats);
        mergeResultStats(other, subsetStats, other.subsetStats);
    }

    private void mergeResultStats(
        EnchantmentDatabase otherDatabase,
        Map<List<EnchantmentWithLevel>, Int2ObjectMap<Int2IntMap>> thisResultStats,
        Map<List<EnchantmentWithLevel>, Int2ObjectMap<Int2IntMap>> otherResultStats
    ) {
        otherResultStats.forEach((enchantments, otherStats) -> {
            Int2ObjectMap<Int2IntMap> stats = thisResultStats.computeIfAbsent(enchantments, k -> new Int2ObjectOpenHashMap<>());
            otherStats.forEach((otherItemType, otherItemTypeStats) -> {
                int itemType = itemTypePool.getOrCreateIndex(otherDatabase.itemTypePool.get(otherItemType).remap(otherDatabase, this));
                Int2IntMap itemTypeStats = stats.computeIfAbsent(itemType, k -> new Int2IntOpenHashMap());
                otherItemTypeStats.forEach((bookshelvesAndSlot, frequency) -> itemTypeStats.mergeInt(bookshelvesAndSlot, frequency, Integer::sum));
            });
        });
    }

    private static int getBookshelfSlotKey(int bookshelves, int slot) {
        return (slot << 4) | bookshelves;
    }

    public void addFromItems(HolderLookup.Provider registries, Stream<Holder<Item>> items) {
        int threads = Mth.smallestEncompassingPowerOfTwo(Math.max(1, Integer.getInteger("clientcommands.enchantmentDatabaseThreads", 16)));

        List<Holder<Enchantment>> enchantingTableEnchantments = registries.lookupOrThrow(Registries.ENCHANTMENT)
            .get(EnchantmentTags.IN_ENCHANTING_TABLE)
            .orElseThrow(() -> new IllegalStateException("No enchanting table tag"))
            .stream()
            .toList();

        EnchantmentDatabase[] databases = new EnchantmentDatabase[threads];
        for (int i = 0; i < threads; i++) {
            databases[i] = new EnchantmentDatabase();
        }

        try (ExecutorService threadPool = Executors.newFixedThreadPool(threads)) {
            items.forEach(item -> {
                ItemStack stack = new ItemStack(item);
                if (!stack.has(DataComponents.ENCHANTABLE)) {
                    return;
                }

                LOGGER.info("Creating database for item {}", item.unwrapKey().map(key -> key.location().toString()).orElse("<unknown>"));

                ItemType itemType = itemTypeFromItem(stack, true, registries, false);
                if (itemType != null && itemTypePool.contains(itemType)) {
                    LOGGER.info("Already found this item in the database");
                    return;
                }

                itemType = databases[0].itemTypeFromItem(stack, true, registries, false);
                if (itemType != null && databases[0].itemTypePool.contains(itemType)) {
                    LOGGER.info("Already computed for this item type");
                    return;
                }

                for (int bookshelves = 0; bookshelves <= 15; bookshelves++) {
                    LOGGER.info("Bookshelves: {}", bookshelves);

                    List<CompletableFuture<Void>> futures = new ArrayList<>(threads);
                    for (int threadId = 0; threadId < threads; threadId++) {
                        ItemType threadItemType = databases[threadId].itemTypeFromItem(stack, true, registries, true);
                        if (threadItemType == null) {
                            continue;
                        }
                        int threadItemTypeId = databases[threadId].itemTypePool.getOrCreateIndex(threadItemType);

                        Reference2IntMap<Holder<Enchantment>> threadEnchantmentToIndexMap = new Reference2IntOpenHashMap<>();
                        threadEnchantmentToIndexMap.defaultReturnValue(-1);
                        int index = 0;
                        for (Holder<Enchantment> ench : enchantingTableEnchantments) {
                            if (ench.value().isPrimaryItem(stack) || stack.is(Items.BOOK)) {
                                threadEnchantmentToIndexMap.put(ench, index++);
                            }
                        }

                        int bookshelves_f = bookshelves;
                        int threadId_f = threadId;
                        futures.add(CompletableFuture.runAsync(() -> {
                            RandomSource rand = RandomSource.create();
                            int enchantmentSeed = threadId_f;
                            do {
                                if ((enchantmentSeed & 0x01ffffff) == 0) {
                                    LOGGER.info("{}%", (double) Integer.toUnsignedLong(enchantmentSeed) * (100 * 0x1.0p-32));
                                }

                                ItemStack enchantedStack = new ItemStack(item);
                                rand.setSeed(enchantmentSeed);
                                int[] costs = new int[3];
                                for (int slot = 0; slot < 3; slot++) {
                                    costs[slot] = EnchantmentHelper.getEnchantmentCost(rand, slot, bookshelves_f, enchantedStack);
                                    if (costs[slot] < slot + 1) {
                                        costs[slot] = 0;
                                    }
                                }

                                for (int slot = 0; slot < 3; slot++) {
                                    if (costs[slot] == 0) {
                                        continue;
                                    }

                                    rand.setSeed(enchantmentSeed + slot);
                                    List<EnchantmentInstance> enchantments = EnchantmentHelper.selectEnchantment(rand, enchantedStack, costs[slot], enchantingTableEnchantments.stream());
                                    if (enchantedStack.is(Items.BOOK) && enchantments.size() > 1) {
                                        enchantments.remove(rand.nextInt(enchantments.size()));
                                    }

                                    List<EnchantmentWithLevel> enchantmentsWithLevels = new ArrayList<>(enchantments.size());
                                    for (EnchantmentInstance enchantment : enchantments) {
                                        int enchantmentIndex = threadEnchantmentToIndexMap.getInt(enchantment.enchantment());
                                        if (enchantmentIndex == -1) {
                                            throw new IllegalStateException("Inapplicable enchantment with id " + enchantment.enchantment().unwrapKey().map(key -> key.location().toString()).orElse("<unknown>"));
                                        }
                                        enchantmentsWithLevels.add(new EnchantmentWithLevel(enchantmentIndex, enchantment.level()));
                                    }
                                    enchantmentsWithLevels.sort(Comparator.comparingInt(EnchantmentWithLevel::enchantment));

                                    databases[threadId_f].resultStats.computeIfAbsent(enchantmentsWithLevels, k -> new Int2ObjectOpenHashMap<>())
                                        .computeIfAbsent(threadItemTypeId, k -> new Int2IntOpenHashMap())
                                        .mergeInt(getBookshelfSlotKey(bookshelves_f, slot), 1, Integer::sum);
                                }

                                enchantmentSeed += threads;
                            } while (enchantmentSeed != threadId_f);
                        }, threadPool));
                    }

                    for (CompletableFuture<Void> future : futures) {
                        future.join();
                    }
                }
            });
        }

        for (EnchantmentDatabase database : databases) {
            merge(database);
        }
    }

    public void populateSubsets() {
        subsetStats.clear();
        resultStats.forEach((enchantments, stats) -> {
            for (int mask = 1, max = (1 << enchantments.size()) - 1; mask < max; mask++) {
                List<EnchantmentWithLevel> subset = new ArrayList<>(Integer.bitCount(mask));
                for (int i = 0; i < enchantments.size(); i++) {
                    if ((mask & (1 << i)) != 0) {
                        subset.add(enchantments.get(i));
                    }
                }

                Int2ObjectMap<Int2IntMap> statsToMergeInto = subsetStats.computeIfAbsent(subset, k -> new Int2ObjectOpenHashMap<>());
                stats.forEach((itemType, itemTypeStats) -> {
                    Int2IntMap itemTypeStatsToMergeInto = statsToMergeInto.computeIfAbsent(itemType, k -> new Int2IntOpenHashMap());
                    itemTypeStats.forEach((bookshelvesAndSlot, frequency) -> itemTypeStatsToMergeInto.mergeInt(bookshelvesAndSlot, frequency, Integer::sum));
                });
            }
        });
    }

    private static final class Pool<T> {
        private final List<T> pool;
        private final Object2IntMap<T> indexes = new Object2IntOpenHashMap<>();

        public Pool() {
            this(new ArrayList<>());
        }

        public Pool(List<T> pool) {
            this.pool = pool;
            this.indexes.defaultReturnValue(-1);
            for (int i = 0; i < pool.size(); i++) {
                this.indexes.put(pool.get(i), i);
            }
            Preconditions.checkArgument(indexes.size() == pool.size(), "Pool has non-unique elements");
        }

        public static <T> Codec<Pool<T>> codec(Codec<T> elementCodec) {
            return Codec.list(elementCodec).comapFlatMap(list -> {
                try {
                    return DataResult.success(new Pool<>(new ArrayList<>(list)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(e::getMessage);
                }
            }, pool -> pool.pool);
        }

        public T get(int index) {
            return pool.get(index);
        }

        @Nullable
        public Integer getIndex(T value, boolean create) {
            return create ? Integer.valueOf(getOrCreateIndex(value)) : getIndex(value);
        }

        @Nullable
        public Integer getIndex(T value) {
            int index = indexes.getInt(value);
            return index == -1 ? null : index;
        }

        public int getOrCreateIndex(T value) {
            int index = indexes.getInt(value);
            if (index == -1) {
                index = pool.size();
                pool.add(value);
                indexes.put(value, index);
            }
            return index;
        }

        public boolean contains(T value) {
            return indexes.containsKey(value);
        }
    }

    private record EnchantmentData(int weight, int maxLevel, Enchantment.Cost minCost, Enchantment.Cost maxCost) {
        static final Codec<EnchantmentData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.POSITIVE_INT.fieldOf("weight").forGetter(EnchantmentData::weight),
            ExtraCodecs.POSITIVE_INT.fieldOf("max_level").forGetter(EnchantmentData::maxLevel),
            Enchantment.Cost.CODEC.fieldOf("min_cost").forGetter(EnchantmentData::minCost),
            Enchantment.Cost.CODEC.fieldOf("max_cost").forGetter(EnchantmentData::maxCost)
        ).apply(instance, EnchantmentData::new));
    }

    // applicableEnchantments is a list of indexes into enchantmentPool
    // exclusiveSets is a list of lists of indexes into applicableEnchantments (NOT enchantmentPool)
    private record ItemType(int enchantmentAlgorithm, int enchantability, IntList applicableEnchantments, List<IntList> exclusiveSets) {
        public static final Codec<ItemType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("enchantment_algorithm").forGetter(ItemType::enchantmentAlgorithm),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("enchantability").forGetter(ItemType::enchantability),
            INT_LIST_CODEC
                .validate(list -> list.isEmpty() ? DataResult.error(() -> "No applicable enchantments") : DataResult.success(list))
                .fieldOf("applicable_enchantments")
                .forGetter(ItemType::applicableEnchantments),
            INT_LIST_CODEC.listOf().fieldOf("exclusive_sets").forGetter(ItemType::exclusiveSets)
        ).apply(instance, ItemType::new));

        ItemType {
            exclusiveSets = new ArrayList<>(exclusiveSets);
            for (IntList list : exclusiveSets) {
                list.sort(Integer::compare);
                removeDuplicates(list);
            }
            exclusiveSets.sort(Comparators.lexicographical(Integer::compare));
            removeDuplicates(exclusiveSets);
            if (!exclusiveSets.isEmpty() && exclusiveSets.getFirst().isEmpty()) {
                exclusiveSets.removeFirst();
            }
        }

        // Assumes the list is sorted
        private static void removeDuplicates(IntList list) {
            for (int i = list.size() - 1; i >= 1; i--) {
                if (list.getInt(i) == list.getInt(i - 1)) {
                    list.removeInt(i);
                }
            }
        }

        // Assumes the list is sorted
        private static <T> void removeDuplicates(List<T> list) {
            for (int i = list.size() - 1; i >= 1; i--) {
                if (list.get(i).equals(list.get(i - 1))) {
                    list.remove(i);
                }
            }
        }

        private ItemType remap(EnchantmentDatabase oldDatabase, EnchantmentDatabase newDatabase) {
            IntList newApplicableEnchantments = new IntArrayList();
            for (int i = 0; i < applicableEnchantments.size(); i++) {
                newApplicableEnchantments.add(newDatabase.enchantmentPool.getOrCreateIndex(oldDatabase.enchantmentPool.get(applicableEnchantments.getInt(i))));
            }
            return new ItemType(enchantmentAlgorithm, enchantability, newApplicableEnchantments, exclusiveSets);
        }
    }

    // enchantment is an index into the item's applicableEnchantments list (NOT enchantmentPool)
    private record EnchantmentWithLevel(int enchantment, int level) {
    }
}
