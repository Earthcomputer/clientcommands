package net.earthcomputer.clientcommands.test;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.command.WaypointCommand;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public final class WaypointLoadingTest {
    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static CompoundTag parseSnbt(String snbt) {
        try {
            return new TagParser(new StringReader(snbt)).readStruct();
        } catch (CommandSyntaxException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testWaypointLoading() {
        CompoundTag waypointTag = parseSnbt("""
            {
                DataVersion: 4189,
                Waypoints: {
                    foo: {
                        testWaypoint: {
                           pos: [I; 1, 2, 3],
                           Dimension: "minecraft:overworld"
                        }
                    }
                }
            }
            """);

        var waypoints = WaypointCommand.deserializeWaypoints(waypointTag);
        assertEquals(1, waypoints.size());
        assertTrue(waypoints.containsKey("foo"));
        var worldWaypoints = waypoints.get("foo");
        assertEquals(1, worldWaypoints.size());
        assertTrue(worldWaypoints.containsKey("testWaypoint"));
        var waypoint = worldWaypoints.get("testWaypoint");
        assertEquals(new BlockPos(1, 2, 3), waypoint.location());
        assertEquals(Level.OVERWORLD, waypoint.dimension());
    }
}
