package net.earthcomputer.clientcommands.oldservergametest;

import com.google.gson.JsonObject;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// Mojmap names for 1.14.4+, SRG names before that
final class ServerMappings {
    private static final String FORGE_MAVEN = "https://maven.minecraftforge.net/de/oceanlabs/mcp/";

    // there are no MCP mappings for 1.9.1, but its obfuscation is the same as 1.9.2
    private static final Map<String, String> MCP_VERSION_OVERRIDES = Map.of("1.9.1", "1.9.2");

    private final ProtocolVersion version;
    // null if the server is unobfuscated
    private final @Nullable MappingTreeView tree;
    private final int namedNamespace;
    private final int runtimeNamespace;

    private ServerMappings(ProtocolVersion version, @Nullable MappingTreeView tree, String namedNamespace, String runtimeNamespace) {
        this.version = version;
        this.tree = tree;
        this.namedNamespace = tree == null ? MappingTreeView.NULL_NAMESPACE_ID : tree.getNamespaceId(namedNamespace);
        this.runtimeNamespace = tree == null ? MappingTreeView.NULL_NAMESPACE_ID : tree.getNamespaceId(runtimeNamespace);
    }

    static ServerMappings forVersion(ServerVersions.ServerVersion version, JsonObject metadata, Path versionDir) {
        ProtocolVersion protocolVersion = version.protocolVersion();
        if (protocolVersion.olderThanOrEqualTo(ProtocolVersion.v1_14_3)) {
            // SRG mappings map from the obfuscated names to the MCP class names and SRG field names
            return new ServerMappings(protocolVersion, loadMcp(version), "target", "source");
        } else if (protocolVersion.olderThanOrEqualTo(ProtocolVersion.v1_21_11)) {
            JsonObject serverMappings = metadata.getAsJsonObject("downloads").getAsJsonObject("server_mappings");
            Path mappingsFile = ServerVersions.downloadCached(
                URI.create(serverMappings.get("url").getAsString()),
                serverMappings.get("sha1").getAsString(),
                versionDir.resolve("server_mappings.txt")
            );
            MemoryMappingTree tree = new MemoryMappingTree();
            try {
                MappingReader.read(mappingsFile, MappingFormat.PROGUARD_FILE, tree);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            // ProGuard mappings map from the named names to the obfuscated names
            return new ServerMappings(protocolVersion, tree, "source", "target");
        } else {
            return new ServerMappings(protocolVersion, null, "", "");
        }
    }

    String mapClass(MappedClass clazz) {
        String name = clazz.names().get(version);
        if (tree == null) {
            return name;
        }
        MappingTreeView.ClassMappingView mapping = tree.getClass(toInternalName(name), namedNamespace);
        if (mapping == null) {
            throw new IllegalArgumentException("No mapping for class " + name + " in " + version);
        }
        return toBinaryName(mapping.getName(runtimeNamespace));
    }

    String mapField(MappedField field) {
        String name = field.names().get(version);
        if (tree == null) {
            return name;
        }
        String ownerName = field.owner().names().get(version);
        MappingTreeView.FieldMappingView mapping = tree.getField(toInternalName(ownerName), name, null, namedNamespace);
        if (mapping == null) {
            throw new IllegalArgumentException("No mapping for field " + ownerName + "." + name + " in " + version);
        }
        return mapping.getName(runtimeNamespace);
    }

    /**
     * Returns the runtime name and descriptor of the given method. The descriptor is null if the server is
     * unobfuscated, in which case the name is assumed to be unique.
     */
    RuntimeMethod mapMethod(MappedMethod method) {
        String name = method.names().get(version);
        if (tree == null) {
            return new RuntimeMethod(name, null);
        }
        String ownerName = method.owner().names().get(version);
        MappingTreeView.MethodMappingView mapping = tree.getMethod(toInternalName(ownerName), name, null, namedNamespace);
        if (mapping == null) {
            throw new IllegalArgumentException("No mapping for method " + ownerName + "." + name + " in " + version);
        }
        return new RuntimeMethod(mapping.getName(runtimeNamespace), mapping.getDesc(runtimeNamespace));
    }

    private static String toInternalName(String binaryName) {
        return binaryName.replace('.', '/');
    }

    private static String toBinaryName(String internalName) {
        return internalName.replace('/', '.');
    }

    private static Reader readZipEntry(Path zip, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(zip.toFile());
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            zipFile.close();
            throw new IOException("No " + entryName + " in " + zip);
        }
        return new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8) {
            @Override
            public void close() throws IOException {
                super.close();
                zipFile.close();
            }
        };
    }

    private static Path downloadFromForgeMaven(String path) {
        URI url = URI.create(FORGE_MAVEN + path);
        String sha1 = new String(ServerVersions.download(URI.create(url + ".sha1")), StandardCharsets.UTF_8).strip();
        return ServerVersions.downloadCached(url, sha1, ServerVersions.getCacheDir().resolve("mcp").resolve(path));
    }

    private static MemoryMappingTree loadMcp(ServerVersions.ServerVersion version) {
        String mcpVersion = MCP_VERSION_OVERRIDES.getOrDefault(version.id(), version.id());
        MemoryMappingTree tree = new MemoryMappingTree();
        try {
            // from 1.13, the SRG mappings are in MCPConfig rather than the old MCP format
            if (version.protocolVersion().newerThanOrEqualTo(ProtocolVersion.v1_13)) {
                Path configZip = downloadFromForgeMaven("mcp_config/" + mcpVersion + "/mcp_config-" + mcpVersion + ".zip");
                try (Reader reader = readZipEntry(configZip, "config/joined.tsrg")) {
                    MappingReader.read(reader, MappingFormat.TSRG_FILE, tree);
                }
            } else {
                Path srgZip = downloadFromForgeMaven("mcp/" + mcpVersion + "/mcp-" + mcpVersion + "-srg.zip");
                try (Reader reader = readZipEntry(srgZip, "joined.srg")) {
                    MappingReader.read(reader, MappingFormat.SRG_FILE, tree);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return tree;
    }

    record VersionedName(String name, List<OlderName> olderNames) {
        VersionedName(String name) {
            this(name, List.of());
        }

        VersionedName until(ProtocolVersion lastVersion, String olderName) {
            List<OlderName> newOlderNames = new ArrayList<>(olderNames);
            newOlderNames.add(new OlderName(lastVersion, olderName));
            return new VersionedName(name, List.copyOf(newOlderNames));
        }

        String get(ProtocolVersion version) {
            String result = name;
            for (OlderName olderName : olderNames) {
                if (version.olderThanOrEqualTo(olderName.lastVersion())) {
                    result = olderName.name();
                }
            }
            return result;
        }

        record OlderName(ProtocolVersion lastVersion, String name) {
        }
    }

    record MappedClass(VersionedName names) {
        static MappedClass of(String name) {
            return new MappedClass(new VersionedName(name));
        }

        MappedClass until(ProtocolVersion lastVersion, String olderName) {
            return new MappedClass(names.until(lastVersion, olderName));
        }
    }

    record MappedField(MappedClass owner, VersionedName names) {
        static MappedField of(MappedClass owner, String name) {
            return new MappedField(owner, new VersionedName(name));
        }

        MappedField until(ProtocolVersion lastVersion, String olderName) {
            return new MappedField(owner, names.until(lastVersion, olderName));
        }
    }

    record MappedMethod(MappedClass owner, VersionedName names) {
        static MappedMethod of(MappedClass owner, String name) {
            return new MappedMethod(owner, new VersionedName(name));
        }

        MappedMethod until(ProtocolVersion lastVersion, String olderName) {
            return new MappedMethod(owner, names.until(lastVersion, olderName));
        }
    }

    record RuntimeMethod(String name, @Nullable String descriptor) {
    }
}
