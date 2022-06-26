package net.earthcomputer.clientcommands.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.xpple.clientarguments.arguments.CGameProfileArgumentType;
import net.earthcomputer.clientcommands.interfaces.IProfileKeys;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.encryption.PlayerPublicKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import javax.crypto.Cipher;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static dev.xpple.clientarguments.arguments.CGameProfileArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class WhisperEncryptedCommand {
    private static boolean justSent = false;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cwe")
                .then(argument("player", gameProfile())
                    .then(argument("message", string())
                        .executes((ctx) ->
                            whisper(ctx.getSource(),
                                getCProfileArgument(ctx, "player"),
                                ctx.getArgument("message", String.class)
                            )
                        )
                    )
                ));
    }

    private static int whisper(FabricClientCommandSource source, Collection<GameProfile> profiles, String message) throws CommandSyntaxException {
        assert source.getClient().getNetworkHandler() != null;
        Optional<PlayerListEntry> entry = profiles.stream().findFirst().flatMap(gp ->
            source.getClient().getNetworkHandler().getPlayerList().stream().filter(e -> e.getProfile().getName().equalsIgnoreCase(gp.getName())).findFirst()
        );
        if (entry.isEmpty()) {
            source.sendError(Text.translatable("commands.cwhisperencrypted.playerNotFound"));
            return 0;
        }
        try {
            // step 2 encrypt the encrypted message using the public key of the recipient
            PlayerPublicKey ppk = entry.get().getPublicKeyData();
            if (ppk == null) {
                source.sendError(Text.translatable("commands.cwhisperencrypted.pubKeyNotFound"));
                return 0;
            }
            PublicKey publicKey = ppk.data().key();
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = message.getBytes(StandardCharsets.UTF_8);
            encrypted = Gzip.compress(encrypted);
            if (encrypted.length > 245) {
                source.sendError(Text.translatable("commands.cwhisperencrypted.messageTooLong"));
                return 0;
            }
            cipher.update(encrypted);
            encrypted = cipher.doFinal();
            String commandMessage = "w " + entry.get().getProfile().getName() + " CCENC:" + BaseUTF8.toUnicode(encrypted);
            if (commandMessage.length() >= 256) {
                source.sendError(Text.translatable("commands.cwhisperencrypted.messageTooLong"));
                return 0;
            }
            justSent = true;
            source.getClient().player.sendCommand(commandMessage);
        } catch (Exception e) {
            source.sendError(Text.translatable("commands.cplayerinfo.ioException"));
            e.printStackTrace();
            return 0;
        }
        return 1;
    }

    public static Text decryptTest(Text t) {
        if (t.getString().contains("CCENC:")) {
            JsonElement el = visit(Text.Serializer.toJsonTree(t));
            return Text.Serializer.fromJson(el);
        }
        return t;
    }

    public static JsonElement visit(JsonElement e) {
        if (e instanceof JsonPrimitive && ((JsonPrimitive) e).isString()) {
            String s = e.getAsString();
            if (s.startsWith("CCENC:")) {
                s = s.substring(6);
                try {
                    byte[] encrypted = BaseUTF8.fromUnicode(s);
                    // check size
                    encrypted = Arrays.copyOf(encrypted, 256);
                    Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                    Optional<PrivateKey> key = ((IProfileKeys) MinecraftClient.getInstance().getProfileKeys()).getPrivateKey();
                    if (key.isEmpty()) {
                        if (!justSent) {
                            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.translatable(
                                "commands.cwhisperencrypted.no_priv_key").formatted(Formatting.DARK_RED));
                        }
                        justSent = false;
                        return e;
                    }
                    cipher.init(Cipher.DECRYPT_MODE, key.get());
                    encrypted = cipher.doFinal(encrypted);
                    encrypted = Gzip.uncompress(encrypted);
                    String message = "ccenc: " + new String(encrypted, StandardCharsets.UTF_8);
                    return new JsonPrimitive(message);
                } catch (Exception ex) {
                    if (!justSent) {
                        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.translatable(
                            "commands.cplayerinfo.ioException").formatted(Formatting.DARK_RED));
                    }
                    justSent = false;
                    return e;
                }
            }
        } else {
            if (e instanceof JsonArray) {
                JsonArray a = (JsonArray) e;
                for (int i = 0; i < a.size(); i++) {
                    a.set(i, visit(a.get(i)));
                }
            } else if (e instanceof JsonObject) {
                JsonObject o = (JsonObject) e;
                for (String key : o.keySet()) {
                    o.add(key, visit(o.get(key)));
                }
            }
        }
        return e;
    }

    public static class Gzip {

        public static byte[] compress(byte[] str) {
            if (str == null || str.length == 0) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            GZIPOutputStream gzip;
            try {
                gzip = new GZIPOutputStream(out);
                gzip.write(str);
                gzip.close();
            } catch ( Exception e) {
                e.printStackTrace();
            }
            return out.toByteArray();
        }

        public static byte[] uncompress(byte[] bytes) {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            try {
                GZIPInputStream ungzip = new GZIPInputStream(in);
                byte[] buffer = new byte[256];
                int n;
                while ((n = ungzip.read(buffer)) >= 0) {
                    out.write(buffer, 0, n);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return out.toByteArray();
        }
    }

    public static class BaseUTF8 {
        public static int LOWER_BOUND = 34;
        public static int UPPER_BOUND = 0x10ffff - 0x800;

        private static int mapCodepoint(int codepoint) {
            if (codepoint < 0xd800) {
                return codepoint == 167 ? 32 : codepoint == 127 ? 33 : codepoint;
            } else {
                return codepoint + 0x800;
            }
        }

        private static int unmapCodepoint(int codepoint) {
            if (codepoint < 0xd800) {
                return codepoint == 32 ? 167 : codepoint == 33 ? 127 : codepoint;
            } else {
                return codepoint - 0x800;
            }
        }

        public static String toUnicode(byte[] b) {
            StringBuilder sb = new StringBuilder();
            int data = 0;
            int bitPtr = 0;
            for (byte b1 : b) {
                data |= Byte.toUnsignedInt(b1) << bitPtr;
                bitPtr += 8;
                if (bitPtr > 19) {
                    int codePoint = mapCodepoint((data & 0x0FFFFF) + LOWER_BOUND);
                    if (codePoint >= UPPER_BOUND) {
                        throw new RuntimeException("WHAT?");
                    }
                    data >>= 20;
                    sb.appendCodePoint(codePoint);
                }
                bitPtr %= 20;
            }
            if (bitPtr != 0) {
                int codePoint = mapCodepoint((data & 0x0FFFFF) + LOWER_BOUND);
                if (codePoint >= UPPER_BOUND) {
                    throw new RuntimeException("WHAT?");
                }
                sb.appendCodePoint(codePoint);
            }
            return sb.toString();
        }

        public static byte[] fromUnicode(String s) {
            int bitLength = s.codePointCount(0, s.length()) * 20;
            int dataLength = (bitLength + 7) / 8;
            byte[] data = new byte[dataLength];
            int dataPtr = 0;
            for (int i = 0; i < s.length();) {
                int codepoint = s.codePointAt(i);
                i += Character.charCount(codepoint);
                if (codepoint >= UPPER_BOUND) {
                    throw new RuntimeException("WHAT?");
                }
                codepoint = unmapCodepoint(codepoint);
                codepoint -= LOWER_BOUND;
                data[dataPtr / 8] |= codepoint << (dataPtr % 8);
                codepoint >>= 8 - (dataPtr % 8);
                data[dataPtr / 8 + 1] |= codepoint;
                data[dataPtr / 8 + 2] |= codepoint >> 8;
                dataPtr += 20;
            }
            return Arrays.copyOf(data, dataLength);
        }
    }
}
