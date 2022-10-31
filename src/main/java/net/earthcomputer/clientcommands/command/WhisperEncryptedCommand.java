package net.earthcomputer.clientcommands.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
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
import java.io.IOException;
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

    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cwe.playerNotFound"));
    private static final SimpleCommandExceptionType PUBLIC_KEY_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cwe.publicKeyNotFound"));
    private static final SimpleCommandExceptionType MESSAGE_TOO_LONG_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cwe.messageTooLong"));
    private static final SimpleCommandExceptionType ENCRYPTION_FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cwe.encryptionFailed"));

    private static boolean justSent = false;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cwe")
                .then(argument("player", gameProfile())
                    .then(argument("message", string())
                        .executes((ctx) -> whisper(ctx.getSource(), getCProfileArgument(ctx, "player"), getString(ctx, "message"))))));
    }

    private static int whisper(FabricClientCommandSource source, Collection<GameProfile> profiles, String message) throws CommandSyntaxException {
        assert source.getClient().getNetworkHandler() != null;
        if (profiles.size() != 1) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }
        PlayerListEntry recipient = source.getClient().getNetworkHandler().getPlayerList().stream()
                .filter(p -> p.getProfile().getName().equalsIgnoreCase(profiles.iterator().next().getName()))
                .findFirst()
                .orElseThrow(PLAYER_NOT_FOUND_EXCEPTION::create);

        try {
            PlayerPublicKey ppk = recipient.getPublicKeyData();
            if (ppk == null) {
                throw PUBLIC_KEY_NOT_FOUND_EXCEPTION.create();
            }
            PublicKey publicKey = ppk.data().key();
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
            byte[] compressedBytes = Gzip.compress(bytes);
            if (compressedBytes.length > 245) {
                throw MESSAGE_TOO_LONG_EXCEPTION.create();
            }
            cipher.update(compressedBytes);
            compressedBytes = cipher.doFinal();
            String commandMessage = "w " + recipient.getProfile().getName() + " CCENC:" + BaseUTF8.toUnicode(compressedBytes);
            if (commandMessage.length() >= 256) {
                throw MESSAGE_TOO_LONG_EXCEPTION.create();
            }
            justSent = true;
            source.getPlayer().sendCommand(commandMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw ENCRYPTION_FAILED_EXCEPTION.create();
        }
        return Command.SINGLE_SUCCESS;
    }

    public static Text decryptTest(Text t) {
        JsonElement el = visit(Text.Serializer.toJsonTree(t));
        return Text.Serializer.fromJson(el);
    }

    private static JsonElement visit(JsonElement e) {
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
                            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.translatable("commands.cwe.privateKeyNotFound").formatted(Formatting.RED));
                        }
                        justSent = false;
                        return e;
                    }
                    cipher.init(Cipher.DECRYPT_MODE, key.get());
                    byte[] decrypted = cipher.doFinal(encrypted);
                    decrypted = Gzip.uncompress(decrypted);
                    String message = "ccenc: " + new String(decrypted, StandardCharsets.UTF_8);
                    return new JsonPrimitive(message);
                } catch (Exception ex) {
                    if (!justSent) {
                        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.translatable("commands.cwe.decryptionFailed").formatted(Formatting.RED));
                    }
                    justSent = false;
                    return e;
                }
            }
        } else {
            if (e instanceof JsonArray a) {
                for (int i = 0; i < a.size(); i++) {
                    a.set(i, visit(a.get(i)));
                }
            } else if (e instanceof JsonObject o) {
                for (String key : o.keySet()) {
                    o.add(key, visit(o.get(key)));
                }
            }
        }
        return e;
    }

    public static class Gzip {

        private static byte[] compress(byte[] str) {
            if (str == null || str.length == 0) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            GZIPOutputStream gzip;
            try {
                gzip = new GZIPOutputStream(out);
                gzip.write(str);
                gzip.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return out.toByteArray();
        }

        private static byte[] uncompress(byte[] bytes) {
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
            } catch (IOException e) {
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

        private static String toUnicode(byte[] b) {
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

        private static byte[] fromUnicode(String s) {
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
