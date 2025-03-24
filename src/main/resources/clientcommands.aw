accessWidener v2 named

# c2c
accessible field net/minecraft/client/multiplayer/AccountProfileKeyPairManager keyPair Ljava/util/concurrent/CompletableFuture;

# Command Handling
accessible field net/minecraft/client/gui/Gui overlayMessageTime I
accessible field net/minecraft/client/gui/components/CommandSuggestions ARGUMENT_STYLES Ljava/util/List;

# Data Query Handler
accessible field net/minecraft/client/DebugQueryHandler transactionId I

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

# chat
accessible method net/minecraft/client/Minecraft openChatScreen (Ljava/lang/String;)V

# ckit
accessible field net/minecraft/world/entity/LivingEntity equipment Lnet/minecraft/world/entity/EntityEquipment;

# clisten
accessible field net/minecraft/network/Connection channel Lio/netty/channel/Channel;
accessible field net/minecraft/network/PacketDecoder protocolInfo Lnet/minecraft/network/ProtocolInfo;
accessible field net/minecraft/network/PacketEncoder protocolInfo Lnet/minecraft/network/ProtocolInfo;
accessible field net/minecraft/network/codec/IdDispatchCodec toId Lit/unimi/dsi/fastutil/objects/Object2IntMap;

# cpermissionlevel
accessible method net/minecraft/client/player/LocalPlayer getPermissionLevel ()I

# cwaypoint
accessible field net/minecraft/server/MinecraftServer storageSource Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;
accessible method net/minecraft/client/renderer/GameRenderer getFov (Lnet/minecraft/client/Camera;FZ)F

# Game Options
accessible field net/minecraft/client/OptionInstance value Ljava/lang/Object;

# Length Extender
accessible field net/minecraft/client/gui/components/EditBox maxLength I

# Render Queue
accessible field net/minecraft/client/renderer/RenderPipelines LINES_SNIPPET Lcom/mojang/blaze3d/pipeline/RenderPipeline$Snippet;
accessible method net/minecraft/client/renderer/RenderPipelines register (Lcom/mojang/blaze3d/pipeline/RenderPipeline;)Lcom/mojang/blaze3d/pipeline/RenderPipeline;
accessible method net/minecraft/client/renderer/RenderType$CompositeState$CompositeStateBuilder createCompositeState (Z)Lnet/minecraft/client/renderer/RenderType$CompositeState;
accessible method net/minecraft/client/renderer/RenderType$CompositeState$CompositeStateBuilder setLayeringState (Lnet/minecraft/client/renderer/RenderStateShard$LayeringStateShard;)Lnet/minecraft/client/renderer/RenderType$CompositeState$CompositeStateBuilder;
accessible method net/minecraft/client/renderer/RenderType$CompositeState$CompositeStateBuilder setLineState (Lnet/minecraft/client/renderer/RenderStateShard$LineStateShard;)Lnet/minecraft/client/renderer/RenderType$CompositeState$CompositeStateBuilder;

# RNG Events
accessible method net/minecraft/world/entity/Entity isInvulnerableToBase (Lnet/minecraft/world/damagesource/DamageSource;)Z
accessible field net/minecraft/world/entity/LivingEntity lastHurt F
accessible field net/minecraft/world/entity/decoration/ArmorStand invisible Z
accessible field net/minecraft/world/level/levelgen/LegacyRandomSource seed Ljava/util/concurrent/atomic/AtomicLong;
