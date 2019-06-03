package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.earthcomputer.clientcommands.command.ClientEntitySelector;
import net.minecraft.command.EntitySelectorOptions;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.command.FloatRange;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.network.chat.BaseComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.Identifier;
import net.minecraft.util.NumberRange;
import net.minecraft.util.TagHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

public class ClientEntityArgumentType implements ArgumentType<ClientEntitySelector> {

    private static final Collection<String> EXAMPLES = Arrays.asList("Player", "0123", "@e", "@e[type=foo]");

    private ClientEntityArgumentType() {
    }

    public static ClientEntityArgumentType entities() {
        return new ClientEntityArgumentType();
    }

    public static List<Entity> getEntities(CommandContext<ServerCommandSource> context, String arg) {
        return context.getArgument(arg, ClientEntitySelector.class).getEntities(context.getSource());
    }

    @Override
    public ClientEntitySelector parse(StringReader reader) throws CommandSyntaxException {
        return new Parser(reader).parse();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof CommandSource) {
            StringReader reader = new StringReader(builder.getInput());
            reader.setCursor(builder.getStart());
            CommandSource source = (CommandSource) context.getSource();
            Parser parser = new Parser(reader);

            try {
                parser.parse();
            } catch (CommandSyntaxException ignore) {}

            return parser.listSuggestions(builder, b -> CommandSource.suggestMatching(source.getPlayerNames(), b));
        } else {
            return Suggestions.empty();
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    private static class Parser {
        private static final BiConsumer<Vec3d, List<Entity>> UNSORTED = (origin, list) -> {};
        private static final BiConsumer<Vec3d, List<Entity>> NEAREST = (origin, list) -> list.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(origin)));
        private static final BiConsumer<Vec3d, List<Entity>> FURTHEST = (origin, list) -> list.sort(Comparator.comparingDouble(entity -> -entity.squaredDistanceTo(origin)));
        private static final BiConsumer<Vec3d, List<Entity>> RANDOM = (origin, list) -> Collections.shuffle(list);

        private final StringReader reader;
        private BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> suggestor;
        private boolean playersOnly = false;
        private boolean playersOnlyForced = false;
        private BiPredicate<Vec3d, Entity> filter = (origin, entity) -> true;
        private BiConsumer<Vec3d, List<Entity>> sorter = UNSORTED;
        private int count = Integer.MAX_VALUE;
        private boolean senderOnly = false;
        private Double originX = null;
        private Double originY = null;
        private Double originZ = null;
        private Double boxX = null;
        private Double boxY = null;
        private Double boxZ = null;

        private boolean hasName = false;
        private boolean hasDistance = false;
        private boolean hasXRotation = false;
        private boolean hasYRotation = false;
        private boolean hasLimit = false;
        private boolean hasSort = false;
        private boolean hasType = false;

        Parser(StringReader reader) {
            this.reader = reader;
        }

        ClientEntitySelector parse() throws CommandSyntaxException {
            suggestor = this::suggestStart;

            if (reader.canRead() && reader.peek() == '@') {
                parseAtSelector();
            } else {
                parsePlayerName();
            }

            if (boxX != null || boxY != null || boxZ != null) {
                boolean xNeg = boxX != null && boxX < 0;
                boolean yNeg = boxY != null && boxY < 0;
                boolean zNeg = boxZ != null && boxZ < 0;
                double xMin = xNeg ? boxX : 0;
                double yMin = yNeg ? boxY : 0;
                double zMin = zNeg ? boxZ : 0;
                double xMax = (xNeg || boxX == null ? 0 : boxX) + 1;
                double yMax = (yNeg || boxY == null ? 0 : boxY) + 1;
                double zMax = (zNeg || boxZ == null ? 0 : boxZ) + 1;
                addFilter((origin, entity) -> entity.x - origin.x >= xMin && entity.x - origin.x < xMax
                        && entity.y - origin.y >= yMin && entity.y - origin.y < yMax
                        && entity.z - origin.z >= zMin && entity.z - origin.z < zMax);
            }
            if (playersOnly || playersOnlyForced) {
                addFilter((origin, entity) -> entity instanceof PlayerEntity);
            }
            return new ClientEntitySelector(filter, sorter, count, senderOnly, originX, originY, originZ);
        }

        void parsePlayerName() throws CommandSyntaxException {
            if (reader.canRead()) {
                suggestor = (builder, playerNameSuggestor) -> {
                    playerNameSuggestor.accept(builder);
                    return builder.buildFuture();
                };
            }

            int start = reader.getCursor();
            String playerName = reader.readString();
            if (playerName.isEmpty() || playerName.length() > 16) {
                reader.setCursor(start);
                throw EntitySelectorReader.INVALID_ENTITY_EXCEPTION.createWithContext(reader);
            }

            playersOnlyForced = true;
            filter = (origin, entity) -> ((PlayerEntity) entity).getGameProfile().getName().equals(playerName);
            count = 1;
        }

        void parseAtSelector() throws CommandSyntaxException {
            suggestor = (builder, playerNameSuggestor) -> suggestAtSelectors(builder.createOffset(builder.getStart() - 1), playerNameSuggestor);
            reader.skip();
            if (!reader.canRead())
                throw EntitySelectorReader.MISSING_EXCEPTION.createWithContext(reader);
            char type = reader.read();
            switch (type) {
                case 'p':
                    playersOnly = true;
                    sorter = NEAREST;
                    count = 1;
                    hasType = true;
                    break;
                case 'a':
                    playersOnly = true;
                    sorter = UNSORTED;
                    count = Integer.MAX_VALUE;
                    hasType = true;
                    break;
                case 'r':
                    playersOnly = true;
                    sorter = RANDOM;
                    count = 1;
                    break;
                case 'e':
                    playersOnly = false;
                    sorter = UNSORTED;
                    count = Integer.MAX_VALUE;
                    break;
                case 's':
                    playersOnly = true;
                    sorter = UNSORTED;
                    count = 1;
                    senderOnly = true;
                    addFilter((origin, entity) -> entity.isAlive());
                    break;
                default:
                    throw EntitySelectorReader.UNKNOWN_SELECTOR_EXCEPTION.createWithContext(reader, "@" + type);
            }

            suggestor = (builder, playerNameSuggest) -> {
                builder.suggest("[");
                return builder.buildFuture();
            };
            if (reader.canRead() && reader.peek() == '[') {
                reader.skip();
                reader.skipWhitespace();
                parseOptions();
            }
        }

        void parseOptions() throws CommandSyntaxException {
            suggestor = this::suggestOption;

            while (true) {
                int cursor = reader.getCursor();
                String optionName = reader.readString();
                Option option = Option.options.get(optionName);
                if (option == null) {
                    reader.setCursor(cursor);
                    throw EntitySelectorOptions.UNKNOWN_OPTION_EXCEPTION.createWithContext(reader, optionName);
                } else if (!option.applicable(this)) {
                    reader.setCursor(cursor);
                    throw EntitySelectorOptions.INAPPLICABLE_OPTION_EXCEPTION.createWithContext(reader, optionName);
                }

                reader.skipWhitespace();
                if (!reader.canRead() || reader.read() != '=') {
                    reader.setCursor(cursor);
                    throw EntitySelectorReader.VALUELESS_EXCEPTION.createWithContext(reader, optionName);
                }
                reader.skipWhitespace();

                suggestor = EntitySelectorReader.DEFAULT_SUGGESTION_PROVIDER;
                option.apply(this);

                reader.skipWhitespace();

                suggestor = (builder, playerNameSuggest) -> {
                    builder.suggest(",");
                    builder.suggest("]");
                    return builder.buildFuture();
                };

                if (!reader.canRead() || (reader.peek() != ',' && reader.peek() != ']')) {
                    throw EntitySelectorReader.UNTERMINATED_EXCEPTION.createWithContext(reader);
                }

                char delimiter = reader.read();
                if (delimiter == ',') {
                    suggestor = this::suggestOption;
                    reader.skipWhitespace();
                } else {
                    suggestor = EntitySelectorReader.DEFAULT_SUGGESTION_PROVIDER;
                    break;
                }
            }
        }

        boolean readNegationCharacter() {
            if (reader.canRead() && reader.peek() == '!') {
                reader.skip();
                reader.skipWhitespace();
                return true;
            } else {
                return false;
            }
        }

        void addFilter(BiPredicate<Vec3d, Entity> filter) {
            final BiPredicate<Vec3d, Entity> prevFilter = this.filter;
            this.filter = (origin, entity) -> filter.test(origin, entity) && prevFilter.test(origin, entity);
        }

        CompletableFuture<Suggestions> listSuggestions(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> playerNameSuggestor) {
            return suggestor.apply(builder.createOffset(reader.getCursor()), playerNameSuggestor);
        }

        private CompletableFuture<Suggestions> suggestStart(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> playerNameSuggestor) {
            playerNameSuggestor.accept(builder);
            suggestAtSelectors(builder, playerNameSuggestor);
            return builder.buildFuture();
        }

        private CompletableFuture<Suggestions> suggestAtSelectors(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> playerNameSuggestor) {
            builder.suggest("@p", new TranslatableComponent("argument.entity.selector.nearestPlayer"));
            builder.suggest("@a", new TranslatableComponent("argument.entity.selector.allPlayers"));
            builder.suggest("@r", new TranslatableComponent("argument.entity.selector.randomPlayer"));
            builder.suggest("@s", new TranslatableComponent("argument.entity.selector.self"));
            builder.suggest("@e", new TranslatableComponent("argument.entity.selector.allEntities"));
            return builder.buildFuture();
        }

        private CompletableFuture<Suggestions> suggestOption(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> playerNameSuggestor) {
            Option.options.forEach((name, opt) -> {
                if (opt.applicable(this))
                    builder.suggest(name + "=", opt.desc);
            });
            return builder.buildFuture();
        }

        private static abstract class Option {
            static Map<String, Option> options = new HashMap<>();
            static {
                options.put("name", new Option("argument.entity.options.name.description") {
                    @Override
                    void apply(Parser parser) throws CommandSyntaxException {
                        boolean neg = parser.readNegationCharacter();
                        String name = parser.reader.readString();
                        if (!neg)
                            parser.hasName = true;
                        parser.addFilter((origin, entity) -> entity.getName().getText().equals(name) != neg);
                    }

                    @Override
                    boolean applicable(Parser parser) {
                        return !parser.hasName;
                    }
                });
                options.put("distance", new Option("argument.entity.options.distance.description") {
                    @Override
                    void apply(Parser parser) throws CommandSyntaxException {
                        int cursor = parser.reader.getCursor();
                        NumberRange.FloatRange range = NumberRange.FloatRange.parse(parser.reader);
                        if ((range.getMin() != null && range.getMin() < 0) || (range.getMax() != null && range.getMax() < 0)) {
                            parser.reader.setCursor(cursor);
                            throw EntitySelectorOptions.NEGATIVE_DISTANCE_EXCEPTION.createWithContext(parser.reader);
                        }
                        parser.hasDistance = true;
                        parser.addFilter((origin, entity) -> range.matchesSquared(entity.squaredDistanceTo(origin)));
                    }

                    @Override
                    boolean applicable(Parser parser) {
                        return !parser.hasDistance;
                    }
                });
                options.put("x", new Option("argument.entity.options.x.description") {
                    @Override
                    void apply(Parser parser) throws CommandSyntaxException {
                        parser.originX = parser.reader.readDouble();
                    }

                    @Override
                    boolean applicable(Parser parser) {
                        return parser.originX == null;
                    }
                });
                options.put("y", new Option("argument.entity.options.y.description") {
                    @Override
                    void apply(Parser parser) throws CommandSyntaxException {
                        parser.originY = parser.reader.readDouble();
                    }

                    @Override
                    boolean applicable(Parser parser) {
                        return parser.originY == null;
                    }
                });
                options.put("z", new Option("argument.entity.options.z.description") {
                    @Override
                    void apply(Parser parser) throws CommandSyntaxException {
                        parser.originZ = parser.reader.readDouble();
                    }

                    @Override
                    boolean applicable(Parser parser) {
                        return parser.originZ == null;
                    }
                });
                options.put("dx", new Option("argument.entity.options.dx.description") {
                    void apply(Parser parser) throws CommandSyntaxException {
                        parser.boxX = parser.reader.readDouble();
                    }

                    @Override
                    boolean applicable(Parser parser) {
                        return parser.boxX == null;
                    }
                });
                options.put("dy", new Option("argument.entity.options.dy.description") {
                    void apply(Parser parser) throws CommandSyntaxException {
                        parser.boxY = parser.reader.readDouble();
                    }

                    @Override
                    boolean applicable(Parser parser) {
                        return parser.boxY == null;
                    }
                });
                options.put("dz", new Option("argument.entity.options.dz.description") {
                    void apply(Parser parser) throws CommandSyntaxException {
                        parser.boxZ = parser.reader.readDouble();
                    }

                    @Override
                    boolean applicable(Parser parser) {
                        return parser.boxZ == null;
                    }
                });
                options.put("x_rotation", new Option("argument.entity.options.x_rotation.description") {
                    @Override
                    void apply(Parser parser) throws CommandSyntaxException {
                        FloatRange range = FloatRange.parse(parser.reader, true, MathHelper::wrapDegrees);
                        float min = range.getMin() == null ? 0 : range.getMin();
                        float max = range.getMax() == null ? 359 : range.getMax();
                        if (max < min)
                            parser.addFilter((origin, entity) -> entity.pitch >= min || entity.pitch <= max);
                        else
                            parser.addFilter((origin, entity) -> entity.pitch >= min && entity.pitch <= max);
                        parser.hasXRotation = true;
                    }

                    @Override
                    boolean applicable(Parser parser) {
                        return !parser.hasXRotation;
                    }
                });
                options.put("y_rotation", new Option("argument.entity.options.y_rotation.description") {
                    @Override
                    void apply(Parser parser) throws CommandSyntaxException {
                        FloatRange range = FloatRange.parse(parser.reader, true, MathHelper::wrapDegrees);
                        float min = range.getMin() == null ? 0 : range.getMin();
                        float max = range.getMax() == null ? 359 : range.getMax();
                        if (max < min)
                            parser.addFilter((origin, entity) -> entity.yaw >= min || entity.yaw <= max);
                        else
                            parser.addFilter((origin, entity) -> entity.yaw >= min && entity.yaw <= max);
                        parser.hasYRotation = true;
                    }

                    @Override
                    boolean applicable(Parser parser) {
                        return !parser.hasYRotation;
                    }
                });
                options.put("limit", new Option("argument.entity.options.limit.description") {
                    @Override
                    void apply(Parser parser) throws CommandSyntaxException {
                        int cursor = parser.reader.getCursor();
                        int limit = parser.reader.readInt();
                        if (limit < 1) {
                            parser.reader.setCursor(cursor);
                            throw EntitySelectorOptions.TOO_SMALL_LEVEL_EXCEPTION.createWithContext(parser.reader);
                        }
                        parser.count = limit;
                        parser.hasLimit = true;
                    }

                    @Override
                    boolean applicable(Parser parser) {
                        return !parser.hasLimit;
                    }
                });
                options.put("sort", new Option("argument.entity.options.sort.description") {
                    @Override
                    void apply(Parser parser) throws CommandSyntaxException {
                        int cursor = parser.reader.getCursor();
                        String sort = parser.reader.readUnquotedString();
                        parser.suggestor = (builder, playerNameSuggest) -> CommandSource.suggestMatching(Arrays.asList("nearest", "furthest", "random", "arbitrary"), builder);
                        switch (sort) {
                            case "nearest":
                                parser.sorter = NEAREST;
                                break;
                            case "furthest":
                                parser.sorter = FURTHEST;
                                break;
                            case "random":
                                parser.sorter = RANDOM;
                                break;
                            case "arbitrary":
                                parser.sorter = UNSORTED;
                                break;
                            default:
                                parser.reader.setCursor(cursor);
                                throw EntitySelectorOptions.IRREVERSIBLE_SORT_EXCEPTION.createWithContext(parser.reader, sort);
                        }
                        parser.hasSort = true;
                    }

                    @Override
                    boolean applicable(Parser parser) {
                        return !parser.hasSort;
                    }
                });
                options.put("type", new Option("argument.entity.options.type.description") {
                    @Override
                    void apply(Parser parser) throws CommandSyntaxException {
                        parser.suggestor = (builder, playerNameSuggest) -> {
                            CommandSource.suggestIdentifiers(Registry.ENTITY_TYPE.getIds(), builder, "!");
                            if (!parser.hasType)
                                CommandSource.suggestIdentifiers(Registry.ENTITY_TYPE.getIds(), builder);
                            return builder.buildFuture();
                        };

                        int cursor = parser.reader.getCursor();
                        boolean neg = parser.readNegationCharacter();
                        Identifier typeId = Identifier.fromCommandInput(parser.reader);
                        EntityType<?> type = Registry.ENTITY_TYPE.getOrEmpty(typeId).orElseThrow(() -> {
                            parser.reader.setCursor(cursor);
                            return EntitySelectorOptions.INVALID_TYPE_EXCEPTION.createWithContext(parser.reader, typeId);
                        });
                        parser.playersOnly = false;
                        if (!neg) {
                            parser.hasType = true;
                            if (type == EntityType.PLAYER)
                                parser.playersOnly = true;
                        }
                        parser.addFilter((origin, entity) -> (entity.getType() == type) != neg);
                    }

                    @Override
                    boolean applicable(Parser parser) {
                        return !parser.hasType;
                    }
                });
                options.put("nbt", new Option("argument.entity.options.nbt.description") {
                    @Override
                    void apply(Parser parser) throws CommandSyntaxException {
                        boolean neg = parser.readNegationCharacter();
                        CompoundTag nbt = new StringNbtReader(parser.reader).parseCompoundTag();
                        parser.addFilter((origin, entity) -> {
                            CompoundTag entityNbt = entity.toTag(new CompoundTag());
                            if (entity instanceof PlayerEntity) {
                                ItemStack heldItem = ((PlayerEntity) entity).getEquippedStack(EquipmentSlot.MAINHAND);
                                if (!heldItem.isEmpty())
                                    entityNbt.put("SelectedItem", heldItem.toTag(new CompoundTag()));
                            }
                            return TagHelper.areTagsEqual(nbt, entityNbt, true) != neg;
                        });
                    }

                    @Override
                    boolean applicable(Parser parser) {
                        return true;
                    }
                });
            }

            final BaseComponent desc;
            private Option(String desc) {
                this.desc = new TranslatableComponent(desc);
            }

            abstract void apply(Parser parser) throws CommandSyntaxException;
            abstract boolean applicable(Parser parser);
        }
    }
}
