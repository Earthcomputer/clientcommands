accessWidener v2 named

# c2c
accessible field net/minecraft/client/multiplayer/AccountProfileKeyPairManager keyPair Ljava/util/concurrent/CompletableFuture;
# accessible method net/minecraft/network/protocol/ProtocolInfoBuilder protocolUnbound (Lnet/minecraft/network/ConnectionProtocol;Lnet/minecraft/network/protocol/PacketFlow;Ljava/util/function/Consumer;)Lnet/minecraft/network/ProtocolInfo$Unbound;

# Command Handling
accessible field net/minecraft/client/gui/Gui overlayMessageTime I
accessible field net/minecraft/client/gui/components/CommandSuggestions ARGUMENT_STYLES Ljava/util/List;
accessible field net/minecraft/network/chat/HoverEvent$Action legacyCodec Lcom/mojang/serialization/MapCodec;
accessible method net/minecraft/network/chat/HoverEvent <init> (Lnet/minecraft/network/chat/HoverEvent$TypedHoverEvent;)V
accessible class net/minecraft/network/chat/HoverEvent$TypedHoverEvent

# Data Query Handler
accessible field net/minecraft/client/DebugQueryHandler transactionId I

# cfinditem
accessible field net/minecraft/world/inventory/AbstractContainerMenu menuType Lnet/minecraft/world/inventory/MenuType;
accessible method net/minecraft/world/level/block/ShulkerBoxBlock canOpen (Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/ShulkerBoxBlockEntity;)Z

# cfish
accessible method net/minecraft/world/entity/projectile/FishingHook canHitEntity (Lnet/minecraft/world/entity/Entity;)Z

# chat
accessible method net/minecraft/client/Minecraft openChatScreen (Ljava/lang/String;)V

# ckit
extendable method net/minecraft/client/gui/screens/inventory/EffectRenderingInventoryScreen renderEffects (Lnet/minecraft/client/gui/GuiGraphics;II)V

# clisten
accessible field net/minecraft/network/Connection channel Lio/netty/channel/Channel;
accessible field net/minecraft/network/PacketDecoder protocolInfo Lnet/minecraft/network/ProtocolInfo;
accessible field net/minecraft/network/PacketEncoder protocolInfo Lnet/minecraft/network/ProtocolInfo;
accessible field net/minecraft/network/codec/IdDispatchCodec toId Lit/unimi/dsi/fastutil/objects/Object2IntMap;

# cpermissionlevel
accessible method net/minecraft/client/player/LocalPlayer getPermissionLevel ()I

# Game Options
accessible field net/minecraft/client/OptionInstance value Ljava/lang/Object;

# Length Extender
accessible field net/minecraft/client/gui/components/EditBox maxLength I

# Render Queue
accessible class net/minecraft/client/renderer/RenderType$CompositeState
accessible class net/minecraft/client/renderer/RenderStateShard$LineStateShard

# RNG Events
accessible field net/minecraft/world/entity/LivingEntity lastHurt F
accessible field net/minecraft/world/entity/decoration/ArmorStand invisible Z
accessible field net/minecraft/world/level/levelgen/LegacyRandomSource seed Ljava/util/concurrent/atomic/AtomicLong;

# cfourinarow
accessible method net/minecraft/client/gui/GuiGraphics innerBlit (Lnet/minecraft/resources/ResourceLocation;IIIIIFFFFFFFF)V
