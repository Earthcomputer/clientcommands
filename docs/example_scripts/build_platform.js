
var GROUP_NAME = "rsf";
var PLATFORM_Y = 64;
var PLATFORM_X_MIN = 0;
var PLATFORM_X_MAX = 64;
var PLATFORM_Z_MIN = 0;
var PLATFORM_Z_MAX = 64;
var STICK_ITEM = "minecraft:stick";
var FUEL_ITEM = "minecraft:charcoal";
var STONE_BLOCK = "minecraft:stone";
var SLAB_BLOCK = "minecraft:stone_slab";
var COBBLESTONE_BLOCK = "minecraft:cobblestone";

// The "replenish area" is the coordinates of the furnace block
var replenishArea;
var craftingTable;
var smeltingChest;

var directionNames = ["north", "east", "south", "west"];
var dx = [0, 1, 0, -1];
var dz = [-1, 0, 1, 0];
var opposite = function(dir) { return (dir + 2) % 4; };

var isChestContainer = function(type) { return type === "generic_9x3" || type === "generic_9x6"; };

var openContainer = function(x, y, z, type) {
    if (typeof(type) === "string") {
        var typeName = type;
        type = function(it) { return it === typeName };
    }
    if (!player.rightClick(x, y, z))
        throw new Error("Could not right click on container at " + x + ", " + y + ", " + z);
    var timeout = 0;
    while (player.openContainer === null || !type(player.openContainer.type)) {
        tick();
        timeout++;
        if (timeout > 100)
            throw new Error("Failed to open container at " + x + ", " + y + ", " + z);
    }
};

var anticheatDelay = function() {
    for (var i = 0; i < 20; i++)
        tick();
};

var findReplenishArea = function() {
    var minDistanceSq = 1000000;
    for (var xDelta = -7; xDelta <= 7; xDelta++) {
        for (var zDelta = -7; zDelta <= 7; zDelta++) {
            for (var yDelta = -7; yDelta <= 7; yDelta++) {
                var distanceSq = xDelta * xDelta + yDelta * yDelta + zDelta * zDelta;
                if (distanceSq >= minDistanceSq)
                    continue;
                var x = player.x + xDelta;
                var y = player.y + player.eyeHeight + yDelta;
                var z = player.z + zDelta;
                if (world.getBlock(x, y, z) === "furnace") {
                    for (var dir = 0; dir < 4; dir++) {
                        var craftX = x + dx[dir];
                        var craftZ = z + dz[dir];
                        if (world.getBlock(craftX, y, craftZ) === "crafting_table") {
                            for (var dir2 = 0; dir2 < 4; dir2++) {
                                var chestX = craftX + dx[dir];
                                var chestZ = craftZ + dz[dir];
                                if (world.getBlock(chestX, y, chestZ) === "chest" || world.getBlock(chestX, y, chestZ) === "trapped_chest") {
                                    replenishArea = [x, y, z];
                                    craftingTable = [craftX, y, craftZ];
                                    smeltingChest = [chestX, y, chestZ];
                                    minDistanceSq = distanceSq;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    throw new Error("Could not find replenish area");
};

var gatherCobblestone = function(cobblestoneNeeded) {
    for (var xDelta = -5; xDelta <= 5; xDelta++) {
        for (var zDelta = -5; zDelta <= 5; zDelta++) {
            for (var yDelta = -5; yDelta <= 5; yDelta++) {
                var x = player.x + xDelta;
                var y = player.y + player.eyeHeight + yDelta;
                var z = player.z + zDelta;
                if (world.getBlock(x, y, z) === "chest" || world.getBlock(x, y, z) === "trapped_chest") {
                    var blockX = Math.floor(x);
                    var blockY = Math.floor(y);
                    var blockZ = Math.floor(z);
                    if (blockX === smeltingChest[0] && blockY === smeltingChest[1] && blockZ === smeltingChest[2])
                        continue;
                    if (Math.abs(blockX - smeltingChest[0]) + Math.abs(blockZ - smeltingChest[2]) === 1 && blockY === smeltingChest[1]
                            && world.getBlock(x, y, z) === world.getBlock(smeltingChest[0], smeltingChest[1], smeltingChest[2]))
                        continue;
                    try {
                        openContainer(x, y, z, isChestContainer);
                        var chestItems = player.openContainer.items;
                        for (var i = 0; i < chestItems.length; i++) {
                            if (chestItems[i].id === COBBLESTONE_BLOCK) {
                                cobblestoneNeeded -= chestItems[i].Count;
                                player.openContainer.click(i, {type: "quick_move"});
                                if (cobblestoneNeeded <= 0)
                                    return;
                            }
                        }
                        player.closeContainer();
                        anticheatDelay();
                    } catch (e) {
                        if (!(e instanceof Error))
                            throw e;
                    }
                }
            }
        }
    }
    throw new Error("Not enough cobblestone");
};

var ensureResources = function() {
    // Check if we already have the resources
    var foundStone = 0, foundSlabs = 0, foundFuel = 0;
    var items = player.inventory.items;
    for (var slot = 0; slot < 36; slot++) {
        if (items[slot].id === STONE_BLOCK)
            foundStone += items[slot].Count;
        else if (items[slot].id === SLAB_BLOCK)
            foundSlabs += items[slot].Count;
        else if (items[slot].id === FUEL_ITEM)
            foundFuel += items[slot].Count;
    }
    if (foundStone !== 0 && foundSlabs !== 0)
        return;

    var slabsNeeded = Math.max(0, 64 * 10 - foundSlabs);
    slabsNeeded = Math.ceil(slabsNeeded / 6) * 6;
    var stoneNeeded = 64 * 10 - foundStone;
    stoneNeeded += slabsNeeded / 2;

    // Travel near to the replenish area (blocks may not be visible until nearby)
    var xDistanceToReplenishArea = replenishArea[0] + 0.5 - player.x;
    var zDistanceToReplenishArea = replenishArea[2] + 0.5 - player.z;
    var hDistanceToReplenishArea = Math.sqrt(xDistanceToReplenishArea * xDistanceToReplenishArea + zDistanceToReplenishArea * zDistanceToReplenishArea);
    var targetX = replenishArea[0] + 0.5 + xDistanceToReplenishArea * 2 / hDistanceToReplenishArea;
    var targetZ = replenishArea[2] + 0.5 + zDistanceToReplenishArea * 2 / hDistanceToReplenishArea;

    if (!player.moveTo(targetX, targetZ))
        throw new Error("Could not move to replenish area");

    // Re-find the replenish area in case it has moved
    replenishArea = findReplenishArea();

    if (stoneNeeded > 0) {
        // Search items in the smelting chest, and check if there is already enough
        openContainer(smeltingChest[0], smeltingChest[1], smeltingChest[2], isChestContainer);
        var chestItems = player.openContainer.items;
        var cobblestoneFound = 0;
        for (var i = 0; i < chestItems.length; i++) {
            if (chestItems[i].id === STONE_BLOCK) {
                stoneNeeded -= chestItems[i].Count;
                player.openContainer.click(i, {type: "quick_move"});
                if (stoneNeeded <= 0)
                    break;
            } else if (chestItems[i].id === COBBLESTONE_BLOCK) {
                cobblestoneFound += chestItems[i].Count;
            }
        }

        // Try to smelt more stone
        if (stoneNeeded > 0) {
            stoneNeeded = Math.ceil(stoneNeeded / 64) * 64;

            // Find the fuel necessary to smelt this stone
            var fuelNeeded = stoneNeeded / 16 - foundFuel;
            if (fuelNeeded > 0) {
                for (var i = 0; i < chestItems.length; i++) {
                    if (chestItems[i].id === FUEL_ITEM) {
                        fuelNeeded -= chestItems[i].Count;
                        player.openContainer.click(i, {type: "quick_move"});
                        if (fuelNeeded <= 0)
                            break;
                    }
                }
                if (fuelNeeded > 0) {
                    throw new Error("Not enough fuel");
                }
            }
            player.closeContainer();
            anticheatDelay();

            // Ensure the cobblestone ingredients are in the smelting chest
            var cobblestoneNeeded = stoneNeeded - cobblestoneFound;
            if (cobblestoneNeeded > 0) {
                gatherCobblestone(cobblestoneNeeded);
                openContainer(smeltingChest[0], smeltingChest[1], smeltingChest[2], isChestContainer);
                items = player.inventory.items;
                for (var i = 0; i < 36; i++) {
                    if (items[i].id === COBBLESTONE_BLOCK) {
                        cobblestoneNeeded -= items[i].Count;
                        player.inventory.click(i, {type: "quick_move"});
                    }
                }
                if (cobblestoneNeeded > 0)
                    throw new Error("An error occurred while trying to replenish cobblestone, did you throw some out?");
                player.closeContainer();
                anticheatDelay();
            }

            // Check how much fuel is already in the furnace, and do an initial replenish
            openContainer(replenishArea[0], replenishArea[1], replenishArea[2], "furnace");
            var furnaceItems = player.openContainer.items;
            var fuelInSlot;
            if (furnaceItems[1].id === FUEL_ITEM) {
                fuelInSlot = furnaceItems[1].Count;
            } else if (furnaceItems[1].id && furnaceItems[1].id !== "minecraft:air") {
                fuelInSlot = 0;
                player.openContainer.click(1, {type: "throw", rightClick: true});
            } else {
                fuelInSlot = 0;
            }
            if (fuelInSlot < 64) {
                items = player.inventory.items;
                for (var i = 0; i < 36; i++) {
                    if (items[i].id === FUEL_ITEM) {
                        fuelInSlot = Math.min(64, fuelInSlot + items[i].Count);
                        player.inventory.click(i, {type: "quick_move"});
                        if (fuelInSlot === 64)
                            break;
                    }
                }
            }
            if (fuelInSlot === 0)
                throw new Error("An error occurred while trying to replenish fuel, did you throw some out?");
            player.closeContainer();
            anticheatDelay();

            // Activate the furnace
            if (!player.pick(STICK_ITEM))
                throw new Error("Where's your stick?");
            if (!player.rightClick(replenishArea[0], replenishArea[1], replenishArea[2]))
                throw new Error("Unable to activate furnace");

            // Wait for the smelting to finish, replenishing fuel as we go
            openContainer(replenishArea[0], replenishArea[1], replenishArea[2], "furnace");
            var fuelToConsume = stoneNeeded / 16;
            while (fuelToConsume > 0) {
                var newFuelInSlot = player.openContainer.items[1].Count;
                if (!newFuelInSlot) newFuelInSlot = 0;
                if (newFuelInSlot !== fuelInSlot) {
                    fuelToConsume -= fuelInSlot - newFuelInSlot;
                    fuelInSlot = newFuelInSlot;
                }

                if (fuelInSlot === 0 && fuelToConsume > 0)
                    throw new Error("The fuel... got lost?");

                if (fuelInSlot < 64 && fuelInSlot < fuelToConsume) {
                    items = player.inventory.items;
                    for (var i = 0; i < 36; i++) {
                        if (items[i].id === FUEL_ITEM) {
                            fuelInSlot = Math.min(64, fuelInSlot + items[i].Count);
                            player.inventory.click(i, {type: "quick_move"});
                            break;
                        }
                    }
                }

                tick();
            }
            player.closeContainer();
            anticheatDelay();

            // Take the smelting result out the chest
            openContainer(smeltingChest[0], smeltingChest[1], smeltingChest[2], isChestContainer);
            chestItems = player.openContainer.items;
            for (var i = 0; i < chestItems.length; i++) {
                if (chestItems[i].id === STONE_BLOCK) {
                    stoneNeeded -= chestItems[i].Count;
                    player.openContainer.click(i, {type: "quick_move"});
                    if (stoneNeeded <= 0)
                        break;
                }
            }
            player.closeContainer();
            anticheatDelay();
        }
    }

    // Craft slabs
    if (slabsNeeded > 0) {
        openContainer(craftingTable[0], craftingTable[1], craftingTable[2], "crafting");
        var recipeCount = slabsNeeded / 6;
        while (recipeCount > 0) {
            for (var slot = 1; slot <= 3; slot++) {
                var itemsNeeded = Math.min(64, recipeCount);
                items = player.inventory.items;
                for (var i = 0; i < 36; i++) {
                    if (items[i].id === STONE_BLOCK) {
                        var count = items[i].Count;
                        player.inventory.click(i); // pickup the stone
                        if (count <= itemsNeeded || itemsNeeded === 64) {
                            // drop down all the stone
                            itemsNeeded -= count;
                            player.openContainer.click(slot);
                        } else {
                            // drop down as many stone as needed then put the rest back
                            for (var j = 0; j < itemsNeeded; j++) {
                                player.openContainer.click(slot, {rightClick: true});
                            }
                            player.inventory.click(i);
                            itemsNeeded = 0;
                        }
                    }
                }
            }

            var timeout = 0;
            while (player.openContainer.items[0].id !== SLAB_BLOCK) {
                tick();
                timeout++;
                if (timeout > 100)
                    throw new Error("Failed to craft slabs");
            }

            player.openContainer.click(0, {type: "quick_move"});

            recipeCount -= 64;
        }
        player.closeContainer();
        anticheatDelay();
    }

    if (!player.pick(STONE_BLOCK))
        throw new Error("Someone is tampering with the bot");
    chat("/ctf " + GROUP_NAME);
};

var placePlatform = function() {
    for (var x = PLATFORM_X_MIN; x <= PLATFORM_X_MAX; x++) {
        var countDown = x % 2 !== PLATFORM_X_MIN % 2;
        for (var z = countDown ? PLATFORM_Z_MAX : PLATFORM_Z_MIN; countDown ? z >= PLATFORM_Z_MIN : z <= PLATFORM_Z_MAX; z += countDown ? -1 : 1) {
            ensureResources();
            if (!player.moveTo(x + 0.5, z + 0.5))
                throw new Error("Movement to " + x + ", " + z + " failed");
            player.pick(SLAB_BLOCK);
            var placementSide = -1;
            for (var dir = 0; dir < 4; dir++) {
                if (world.getBlockState(x + dx[dir], PLATFORM_Y, z + dz[dir]).solid) {
                    placementSide = dir;
                    break;
                }
            }
            if (placementSide === -1)
                throw new Error("Nothing to place the slab against at " + x + ", " + z);
            player.rightClick(x + dx[placementSide], PLATFORM_Y, z + dz[placementSide], directionNames[opposite(dir)]);
        }
    }
};

replenishArea = findReplenishArea();
if (player.pick(STONE_BLOCK)) {
    chat("/ctf " + GROUP_NAME);
}
placePlatform();
