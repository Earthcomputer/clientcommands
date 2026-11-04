Starting from clientcommands 2.15, clientcommands must be installed on the server to perform certain commands when not opped. This is to comply with [Modrinth's Content Rules](https://modrinth.com/legal/rules) section 3 on "Cheats or Hacks". Clientcommands is not intended to be used to cheat, hack, or bypass server rules, however some commands, in particular some commands used frequently by the technical community, have the side effect of falling foul of Modrinth's rules on Cheats or Hacks.

These commands are:
- `/cfindblock` can be used to effectively X-ray as you can know of the existence of blocks behind opaque blocks.
    - We may consider re-allowing this command if a line-of-sight test is implemented before the result is printed to the user. If you are interested in contributing this feature, please join the Discord and discuss the implementation with us. We could then add an `--x-ray` flag to enable current behaviour with the opt-in.
- `/careastats` doesn't tell you where the blocks are but it does tell you the existence of blocks in an area, which could for example be used to effectively widen your branch mine tunnel.
- `/csignsearch` can search for signs hidden behind other blocks.
- `/cfind` can search for entities hidden behind blocks.
- `/cglow entities` can search for entities hidden behind blocks.
- `/cgetdata entity` can search for and get data (including coordinates) from entities hidden behind blocks.
- `/cghostblock` has the side effect of enabling flight by walking on the created ghost blocks.

Other commands considered but not included in the list requiring the opt-in are:
- `/cfinditem` only searches containers near the player by clicking on them, unless the player is opped.
    - If clientcommands is installed on the server, `/cfinditem` instead asks the server to search all containers within the player's render distance, or within all loaded chunks with `--all-loaded-chunks`. Server admins can disable this by setting `serverFindItem` to `false` in `config/clientcommands-server/config.json`, or by running `/config clientcommands-server serverFindItem set false`.
- `/csnap` is a teleport but restricted to a maximum of one block distance, reflecting its intention of being used for precise alignment ("snapping") rather than longer-distance teleportation, speed hacks, or flying. It needs to be used with a script or macro to be at all an effective method to do these things, which is functionality that clientcommands doesn't provide.
- `/cgetdata block` requires you to already know the coordinates of the block you want to get data from, making it an ineffective method to X-ray.

If you want to continue using these commands, ask your server admin to install Fabric Loader and place clientcommands and Fabric API in the mods folder. When installed on the server, clientcommands does not modify the game in any way other than to enable the aforementioned commands. If there is enough demand for it, we may also implement a Paper plugin and a NeoForge mod which implements the same opt-in functionality.

If you are a server developer but can't install clientcommands for whatever reason but would like to enable the opt-in, register the `clientcommands:opt_in` serverbound plugin (custom payload) channel. It does not need to do anything in the handler and will never be sent by clientcommands. If you want to have finer-grained control on what commands players are allowed to use, including over commands not listed above, you can listen for the `clientcommands:command_execution` payload, after which clientcommands will send the content of most commands to the server. You can decide based on this how to handle the information that the command was executed, including by kicking the player from the server. This feature has existed since clientcommands 2.8.4.