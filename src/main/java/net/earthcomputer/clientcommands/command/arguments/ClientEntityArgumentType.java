package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.earthcomputer.clientcommands.command.ClientEntitySelector;
import net.minecraft.command.CommandSource;
import net.minecraft.command.EntitySelectorOptions;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.command.FloatRangeArgument;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.predicate.NumberRange;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.tag.EntityTypeTags;
import net.minecraft.tag.Tag;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import java.util.regex.Pattern;

public class ClientEntityArgumentType implements ArgumentType<ClientEntitySelector> {

    private static final Collection<String> EXAMPLES = Arrays.asList("Player", "0123", "@e", "@e[type=foo]");

    private final boolean singleTarget;

    private ClientEntityArgumentType(boolean singleTarget) {
        this.singleTarget = singleTarget;
    }

    public static ClientEntityArgumentType entity() {
        return new ClientEntityArgumentType(true);
    }

    public static ClientEntityArgumentType entities() {
        return new ClientEntityArgumentType(false);
    }

    public static ClientEntitySelector getEntitySelector(CommandContext<ServerCommandSource> context, String arg) {
        return context.getArgument(arg, ClientEntitySelector.class);
    }

    public static Entity getEntity(CommandContext<ServerCommandSource> context, String arg) throws CommandSyntaxException {
        return context.getArgument(arg, ClientEntitySelector.class).getEntity(context.getSource());
    }

    public static List<Entity> getEntities(CommandContext<ServerCommandSource> context, String arg) {
        return context.getArgument(arg, ClientEntitySelector.class).getEntities(context.getSource());
    }

    @Override
    public ClientEntitySelector parse(StringReader reader) throws CommandSyntaxException {
        final int start = reader.getCursor();
        ClientEntitySelector ret = new Parser(reader).parse();

        if (ret.getLimit() > 1 && singleTarget) {
            reader.setCursor(start);
            throw EntityArgumentType.TOO_MANY_ENTITIES_EXCEPTION.createWithContext(reader);
        }

        return ret;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof CommandSource source) {
            StringReader reader = new StringReader(builder.getInput());
            reader.setCursor(builder.getStart());
            Parser parser = new Parser(reader);

            try {
                parser.parse();
            } catch (CommandSyntaxException ignore) {}

            Collection<String> suggestions = source.getPlayerNames();
            suggestions.addAll(source.getEntitySuggestions());

            return parser.listSuggestions(builder, b -> CommandSource.suggestMatching(suggestions, b));
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
        private int limit = Integer.MAX_VALUE;
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
                parsePlayerNameOrUuid();
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
                addFilter((origin, entity) -> entity.getX() - origin.x >= xMin && entity.getX() - origin.x < xMax
                        && entity.getY() - origin.y >= yMin && entity.getY() - origin.y < yMax
                        && entity.getZ() - origin.z >= zMin && entity.getZ() - origin.z < zMax);
            }
            if (playersOnly || playersOnlyForced) {
                addFilter((origin, entity) -> entity instanceof PlayerEntity);
            }
            return new ClientEntitySelector(filter, sorter, limit, senderOnly, originX, originY, originZ);
        }

        void parsePlayerNameOrUuid() throws CommandSyntaxException {
            if (reader.canRead()) {
                int start = reader.getCursor();
                suggestor = (builder, playerNameSuggestor) -> {
                    SuggestionsBuilder newBuilder = builder.createOffset(start);
                    playerNameSuggestor.accept(newBuilder);
                    builder.add(newBuilder);
                    return builder.buildFuture();
                };
            }

            int start = reader.getCursor();
            String playerName = reader.readString();
            try {
                UUID uuid = UUID.fromString(playerName);
                filter = (origin, entity) -> entity.getUuid().equals(uuid);
                limit = 1;
                return;
            } catch (IllegalArgumentException ignore) {
                // we don't have a uuid, check player names
            }

            if (playerName.isEmpty() || playerName.length() > 16) {
                reader.setCursor(start);
                throw EntitySelectorReader.INVALID_ENTITY_EXCEPTION.createWithContext(reader);
            }

            playersOnlyForced = true;
            filter = (origin, entity) -> ((PlayerEntity) entity).getGameProfile().getName().equals(playerName);
            limit = 1;
        }

        void parseAtSelector() throws CommandSyntaxException {
            suggestor = (builder, playerNameSuggestor) -> suggestAtSelectors(builder.createOffset(builder.getStart() - 1), playerNameSuggestor);
            reader.skip();
            if (!reader.canRead())
                throw EntitySelectorReader.MISSING_EXCEPTION.createWithContext(reader);
            char type = reader.read();
            switch (type) {
                case 'p' -> {
                    playersOnly = true;
                    sorter = NEAREST;
                    limit = 1;
                    hasType = true;
                }
                case 'a' -> {
                    playersOnly = true;
                    sorter = UNSORTED;
                    limit = Integer.MAX_VALUE;
                    hasType = true;
                }
                case 'r' -> {
                    playersOnly = true;
                    sorter = RANDOM;
                    limit = 1;
                }
                case 'e' -> {
                    playersOnly = false;
                    sorter = UNSORTED;
                    limit = Integer.MAX_VALUE;
                }
                case 's' -> {
                    playersOnly = true;
                    sorter = UNSORTED;
                    limit = 1;
                    senderOnly = true;
                    addFilter((origin, entity) -> entity.isAlive());
                }
                default -> throw EntitySelectorReader.UNKNOWN_SELECTOR_EXCEPTION.createWithContext(reader, "@" + type);
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

        boolean readTagCharacter() {
            this.reader.skipWhitespace();
            if (this.reader.canRead() && this.reader.peek() == '#') {
                this.reader.skip();
                this.reader.skipWhitespace();
                return true;
            } else {
                return false;
            }
        }

        void addFilter(BiPredicate<Vec3d, Entity> filter) {
            final var prevFilter = this.filter;
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
            builder.suggest("@p", new TranslatableText("argument.entity.selector.nearestPlayer"));
            builder.suggest("@a", new TranslatableText("argument.entity.selector.allPlayers"));
            builder.suggest("@r", new TranslatableText("argument.entity.selector.randomPlayer"));
            builder.suggest("@s", new TranslatableText("argument.entity.selector.self"));
            builder.suggest("@e", new TranslatableText("argument.entity.selector.allEntities"));
            return builder.buildFuture();
        }

        private CompletableFuture<Suggestions> suggestOption(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> playerNameSuggestor) {
            String arg = builder.getRemaining().toLowerCase(Locale.ROOT);
            Option.options.forEach((name, opt) -> {
                if (opt.applicable(this) && name.toLowerCase(Locale.ROOT).startsWith(arg))
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
                        if (parser.reader.canRead() && parser.reader.peek() == '/') {
                            Pattern regex = RegexArgumentType.parseSlashyRegex(parser.reader);
                            parser.addFilter((origin, entity) -> regex.matcher(entity.getName().getString()).matches() != neg);
                        } else {
                            String name = parser.reader.readString();
                            parser.addFilter((origin, entity) -> entity.getName().getString().equals(name) != neg);
                        }
                        if (!neg)
                            parser.hasName = true;
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
                        parser.addFilter((origin, entity) -> range.testSqrt(entity.squaredDistanceTo(origin)));
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
                        FloatRangeArgument range = FloatRangeArgument.parse(parser.reader, true, MathHelper::wrapDegrees);
                        float min = range.getMin() == null ? 0 : range.getMin();
                        float max = range.getMax() == null ? 359 : range.getMax();
                        if (max < min)
                            parser.addFilter((origin, entity) -> entity.getPitch() >= min || entity.getPitch() <= max);
                        else
                            parser.addFilter((origin, entity) -> entity.getPitch() >= min && entity.getPitch() <= max);
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
                        FloatRangeArgument range = FloatRangeArgument.parse(parser.reader, true, MathHelper::wrapDegrees);
                        float min = range.getMin() == null ? 0 : range.getMin();
                        float max = range.getMax() == null ? 359 : range.getMax();
                        if (max < min)
                            parser.addFilter((origin, entity) -> entity.getYaw() >= min || entity.getYaw() <= max);
                        else
                            parser.addFilter((origin, entity) -> entity.getYaw() >= min && entity.getYaw() <= max);
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
                        parser.limit = limit;
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
                            case "nearest" -> parser.sorter = NEAREST;
                            case "furthest" -> parser.sorter = FURTHEST;
                            case "random" -> parser.sorter = RANDOM;
                            case "arbitrary" -> parser.sorter = UNSORTED;
                            default -> {
                                parser.reader.setCursor(cursor);
                                throw EntitySelectorOptions.IRREVERSIBLE_SORT_EXCEPTION.createWithContext(parser.reader, sort);
                            }
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
                            CommandSource.suggestIdentifiers(EntityTypeTags.getTagGroup().getTagIds(), builder, "!#");
                            if (!parser.hasType) {
                                CommandSource.suggestIdentifiers(Registry.ENTITY_TYPE.getIds(), builder);
                                CommandSource.suggestIdentifiers(EntityTypeTags.getTagGroup().getTagIds(), builder, String.valueOf('#'));
                            }
                            return builder.buildFuture();
                        };

                        int cursor = parser.reader.getCursor();
                        boolean neg = parser.readNegationCharacter();
                        final Identifier typeId;
                        if (parser.readTagCharacter()) {
                            typeId = Identifier.fromCommandInput(parser.reader);
                            parser.addFilter((origin, entity) -> {
                                try {
                                    Tag<EntityType<?>> tag = entity.getEntityWorld().getTagManager().getTag(Registry.ENTITY_TYPE_KEY, typeId, id -> new IllegalArgumentException());
                                    return tag.contains(entity.getType()) != neg;
                                } catch (IllegalArgumentException e) {
                                    return neg;
                                }
                            });
                        } else {
                            typeId = Identifier.fromCommandInput(parser.reader);
                            EntityType<?> type = Registry.ENTITY_TYPE.getOrEmpty(typeId).orElseThrow(() -> {
                                parser.reader.setCursor(cursor);
                                return EntitySelectorOptions.INVALID_TYPE_EXCEPTION.createWithContext(parser.reader, typeId);
                            });
                            parser.playersOnly = false;
                            if (!neg) {
                                parser.hasType = true;
                                if (type == EntityType.PLAYER) {
                                    parser.playersOnly = true;
                                }
                            }
                            parser.addFilter((origin, entity) -> (entity.getType() == type) != neg);
                        }
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
                        NbtCompound nbt = new StringNbtReader(parser.reader).parseCompound();
                        parser.addFilter((origin, entity) -> {
                            NbtCompound entityNbt = entity.writeNbt(new NbtCompound());
                            if (entity instanceof PlayerEntity) {
                                ItemStack heldItem = ((PlayerEntity) entity).getEquippedStack(EquipmentSlot.MAINHAND);
                                if (!heldItem.isEmpty())
                                    entityNbt.put("SelectedItem", heldItem.writeNbt(new NbtCompound()));
                            }
                            return NbtHelper.matches(nbt, entityNbt, true) != neg;
                        });
                    }

                    @Override
                    boolean applicable(Parser parser) {
                        return true;
                    }
                });
            }

            final Text desc;
            private Option(String desc) {
                this.desc = new TranslatableText(desc);
            }

            abstract void apply(Parser parser) throws CommandSyntaxException;
            abstract boolean applicable(Parser parser);
        }
    }
}
