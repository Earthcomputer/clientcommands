package net.earthcomputer.clientcommands.features;

import com.mojang.logging.LogUtils;
import dev.xpple.simplewaypoints.SimpleWaypoints;
import net.earthcomputer.clientcommands.ClientCommands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Waypoints {
    private Waypoints() {
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void migrateWaypoints() {
        Path clientcommandsWaypointsPath = ClientCommands.CONFIG_DIR.resolve("waypoints.dat");
        if (!Files.isRegularFile(clientcommandsWaypointsPath)) {
            return; // nothing to migrate, or migration already done
        }

        LOGGER.info("clientcommands' waypoints.dat file detected, will be migrated to SimpleWaypoints");
        Path clientcommandsOldWaypointsPath = ClientCommands.CONFIG_DIR.resolve("waypoints.dat_old");
        boolean deleteOld;
        try {
            Files.copy(clientcommandsWaypointsPath, clientcommandsOldWaypointsPath, StandardCopyOption.REPLACE_EXISTING);
            deleteOld = true;
        } catch (IOException e) {
            LOGGER.error("Could not back up waypoints.dat file", e);
            deleteOld = false;
        }

        Path simplewaypointsWaypointsPath = SimpleWaypoints.MOD_CONFIG_PATH.resolve("waypoints.dat");
        if (Files.isRegularFile(simplewaypointsWaypointsPath)) {
            LOGGER.info("SimpleWaypoints' waypoints.dat file already exists, clientcommands' waypoints.dat file will be merged");
            try {
                CompoundTag clientcommandsCompoundTag = NbtIo.read(clientcommandsWaypointsPath);
                CompoundTag simplewaypointsCompoundTag = NbtIo.read(simplewaypointsWaypointsPath);
                if (clientcommandsCompoundTag == null || simplewaypointsCompoundTag == null) {
                    throw new IOException("Could not parse clientcommands' or SimpleWaypoints' waypoint.dat file");
                }
                simplewaypointsCompoundTag.merge(clientcommandsCompoundTag);
                NbtIo.write(simplewaypointsCompoundTag, simplewaypointsWaypointsPath);
            } catch (IOException e) {
                LOGGER.error("Could not merge waypoints.dat file", e);
            }
        } else {
            LOGGER.info("SimpleWaypoints' waypoints.dat does not exist yet, clientcommands' waypoints.dat file will be copied");
            try {
                Files.copy(clientcommandsWaypointsPath, simplewaypointsWaypointsPath);
            } catch (IOException e) {
                LOGGER.error("Could not migrate waypoints.dat file", e);
            }
        }

        if (deleteOld) {
            LOGGER.info("clientcommands's waypoint.dat will be deleted, waypoints.dat_old is kept");
            try {
                Files.delete(clientcommandsWaypointsPath);
            } catch (IOException e) {
                LOGGER.error("Could not delete waypoints.dat file during migration", e);
            }
        }
    }
}
