accessWidener v2 named

# c2c
accessible field net/minecraft/client/multiplayer/AccountProfileKeyPairManager keyPair Ljava/util/concurrent/CompletableFuture;

# cchess
accessible field net/minecraft/client/gui/components/AbstractButton TEXT_MARGIN I

# Command Handling
accessible field net/minecraft/client/gui/Gui overlayMessageTime I
accessible field net/minecraft/client/gui/components/CommandSuggestions ARGUMENT_STYLES Ljava/util/List;

# Data Query Handler
accessible field net/minecraft/client/DebugQueryHandler transactionId I

# CComponentUtil
accessible field net/minecraft/commands/arguments/coordinates/LocalCoordinates forwards D
accessible field net/minecraft/commands/arguments/coordinates/LocalCoordinates left D
accessible field net/minecraft/commands/arguments/coordinates/LocalCoordinates up D
accessible field net/minecraft/commands/arguments/coordinates/WorldCoordinates x Lnet/minecraft/commands/arguments/coordinates/WorldCoordinate;
accessible field net/minecraft/commands/arguments/coordinates/WorldCoordinates y Lnet/minecraft/commands/arguments/coordinates/WorldCoordinate;
accessible field net/minecraft/commands/arguments/coordinates/WorldCoordinates z Lnet/minecraft/commands/arguments/coordinates/WorldCoordinate;
accessible field net/minecraft/network/chat/contents/NbtContents compiledNbtPath Lnet/minecraft/commands/arguments/NbtPathArgument$NbtPath;

# cfinditem
accessible field net/minecraft/world/inventory/AbstractContainerMenu menuType Lnet/minecraft/world/inventory/MenuType;
accessible method net/minecraft/world/level/block/ShulkerBoxBlock canOpen (Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/ShulkerBoxBlockEntity;)Z

# cfish
accessible method net/minecraft/world/entity/projectile/FishingHook canHitEntity (Lnet/minecraft/world/entity/Entity;)Z

# cfps
accessible field net/minecraft/client/Minecraft virtualScreen Lnet/minecraft/client/renderer/VirtualScreen;
accessible field net/minecraft/client/renderer/VirtualScreen screenManager Lcom/mojang/blaze3d/platform/ScreenManager;
accessible field com/mojang/blaze3d/platform/ScreenManager monitors Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;

# cgive
accessible method net/minecraft/world/entity/player/Inventory addResource (ILnet/minecraft/world/item/ItemStack;)I
accessible method net/minecraft/world/entity/player/Inventory hasRemainingSpaceForItem (Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z

# ckit
accessible field net/minecraft/world/entity/LivingEntity equipment Lnet/minecraft/world/entity/EntityEquipment;

# clisten
accessible field net/minecraft/network/Connection channel Lio/netty/channel/Channel;
accessible field net/minecraft/network/PacketDecoder protocolInfo Lnet/minecraft/network/ProtocolInfo;
accessible field net/minecraft/network/PacketEncoder protocolInfo Lnet/minecraft/network/ProtocolInfo;
accessible field net/minecraft/network/codec/IdDispatchCodec toId Lit/unimi/dsi/fastutil/objects/Object2IntMap;

# cmap
accessible method net/minecraft/client/Screenshot getFile (Ljava/io/File;)Ljava/io/File;

# cpermissionlevel
#accessible method net/minecraft/client/player/LocalPlayer getPermissionLevel ()I

# cposteffect
accessible method net/minecraft/client/renderer/GameRenderer setPostEffect (Lnet/minecraft/resources/Identifier;)V
accessible field net/minecraft/client/renderer/ShaderManager compilationCache Lnet/minecraft/client/renderer/ShaderManager$CompilationCache;
accessible field net/minecraft/client/renderer/ShaderManager$CompilationCache configs Lnet/minecraft/client/renderer/ShaderManager$Configs;

# Game Options
accessible field net/minecraft/client/OptionInstance value Ljava/lang/Object;

# Length Extender
accessible field net/minecraft/client/gui/components/EditBox maxLength I

# Render Queue
accessible method net/minecraft/client/renderer/rendertype/RenderType create (Ljava/lang/String;Lnet/minecraft/client/renderer/rendertype/RenderSetup;)Lnet/minecraft/client/renderer/rendertype/RenderType;

# RNG Events
accessible method net/minecraft/world/entity/Entity isInvulnerableToBase (Lnet/minecraft/world/damagesource/DamageSource;)Z
accessible field net/minecraft/world/entity/LivingEntity lastHurt F
accessible field net/minecraft/world/entity/decoration/ArmorStand invisible Z
accessible field net/minecraft/world/level/levelgen/LegacyRandomSource seed Ljava/util/concurrent/atomic/AtomicLong;
