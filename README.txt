========================= EARTHCOMPUTER'S CLIENT COMMANDS MOD =========================

===== SETUP =====

To install the mod:
	1. Make sure you have Minecraft Forge installed
	2. Go to the downloads page of this project and download the latest version of clientcommands
	3. Copy the downloaded JAR into the mods folder

To build the mod from source:
	1. Clone this repository
	2. Run `gradlew build` from the command line
	3. The mod JAR file is in build/libs

To setup in the dev environment:
	1. Clone this repository
	2. Run `gradlew setupDevWorkspace --refresh-dependencies`
	3. Run `gradlew eclipse` for Eclipse, or `gradlew idea` for IDEA
	4. Open the project in your IDE 

Common problems with setup:
	Could not find tools.jar:
		Ensure that you have the latest JDK8 installed, not the JRE.
		If this doesn't work, then try https://stackoverflow.com/questions/11345193/gradle-does-not-find-tools-jar
	Could not determine java version:
		Minecraft Forge and ForgeGradle doesn't currently work with Java 9 or later.
		Make sure you have JDK8 installed on your system, then set `JAVA_HOME` to
		that location before running `gradlew build`. On Windows, the command is:
			`set JAVA_HOME=C:\path\to\java\directory`.
		For me, this is:
			`set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_152`.

===== COMMANDS =====
/cabort - aborts the current task. You are usually told when you need to use this.
/ccalc - calculates an expression. For example, `/ccalc 7 / 2` would return the
         result `3.5`. For positive integers it also doubles as an item stack
         calculator. For example, `/ccalc 1000` would return `1000 = 15 * 64 + 40`,
         indicating that 1000 items consists of 15 full stacks, and an extra stack
         of 40.
/cclear - works the same as the vanilla `/clear` command, except:
             a) you can use it without being op'ed.
             b) there's no need to specify the target player (since it only works on yourself).
             c) it only works in creative mode.
/cgive - works the same as the vanilla `/give` command, except:
            a) you can use it without being op'ed.
            b) there's no need to specify the target player (since it only works on yourself).
            c) it only works in creative mode.
/chelp - displays a list of client-side commands.
/cfind - finds entities matching a query. You start off by specifying the type of entity you're
         looking for, and the command creates a list of all entities of that type which
         are loaded in the client-side world. Subsequent pairs of arguments filter that list.
         These filters are applied in order.
         These filters are in a key-value format, where the possible keys are as follows:
            rmin - the minimum distance the entity can be from the player.
            rmax - the maximum distance the entity can be from the player.
            order - specifies the order these entities must be sorted. The value can either
                    be `nearest` for nearest first, `furthest` for furthest first or `random`
                    for a random order. If the `order` filter is not included the order remains
                    unspecified.
            limit - limits the size of the list which can be returned to the given value,
                    removing any additional entities from the list.
            name - filters by username.
         Examples:
            `/cfind creeper rmin 10` - finds all creepers at least 10 blocks away.
            `/cfind player order random limit 1` - picks a random player.
            `/cfind item limit 1 order nearest` - finds an unspecified item in the world,
                                                  despite `order nearest`. This is because
                                                  the list is shortened before it's sorted.
/cfindblock - searches for the closest block of a certain type near the player. The radius
              argument limits the maximum distance the block can be from the player. The
              `radiustype` argument specifies how distance is calculated:
                 `cartesian` - sqrt(dx^2 + dy^2 + dz^2), the "intuitive" version of distance.
                 `taxicab` - dx + dy + dz. This is the distance you would have to travel in
                             underground tunnels which don't cut across diagonals.
                 `square` - max(dx, dy, dz). The distance along a single axis.
/cfinditem - searches for a matching item in nearby chests and other containers.
/clook - faces the player in a certain direction. There are three modes:
            `block` - looks at the exact center of a block.
            `angles` - looks with the exact yaw and pitch (can be relative with ~).
            `cardinal` - looks a cardinal direction, such as `north` or `down`.
/cnote - creates a message in the client-side chat, without broadcasting it to the whole server.
/crelog - relogs. Currently only works in multiplayer.
/csimgen (deprecated) - simulated the generation of a world generator.
/ctemprule - get and set variables which affect the workings of clientcommands. TempRules reset
             when you log out of a world.
/ctime - queries or changes the time client-side. All subcommands except `mock` and `unmock`
         only query the time. Using `/ctime mock <daytime> [doDaylightCycle]` starts mocking
         the time, and `/ctime unmock` stops mocking the time. Mocking the time means
         making it appear to be a certain time on the client-side only, while the server-
         side time (which is the time that matters for mob spawning etc.) is unaffected.
         `/ctime mock` is intended for builders to inspect their builds at different times of
         day.
/cvw - (upcoming: 1.1) starts or stops a Client Virtual World (CVW). A CVW is basically a copy
       of the world from the previous server, except you have cheats enabled. This could be
       useful e.g. for testing out contraptions in creative mode.
