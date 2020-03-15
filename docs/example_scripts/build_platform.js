
var GROUP_NAME = "rsf";
var PLATFORM_Y = 32;
var TEMPLATE_Y = 20;
var TEMPLATE_Z = -3367;
var PLATFORM_X_MIN = 3229;
var PLATFORM_X_MAX = 3305;
var PLATFORM_Z_MIN = -3442;
var PLATFORM_Z_MAX = -3364;
var STONE_BLOCK = "minecraft:stone";
var SLAB_BLOCK = "minecraft:smooth_stone_slab";

var craftingTable;

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
    player.lookAt(x, y, z);
    player.syncRotation();
    if (!player.rightClick(x, y, z))
        throw new Error("Could not right click on container at " + x + ", " + y + ", " + z);
    var timeout = 0;
    while (player.openContainer === null || !type(player.openContainer.type)) {
        tick();
        timeout++;
        if (timeout > 100)
            throw new Error("Failed to open container at " + x + ", " + y + ", " + z);
    }
    anticheatDelay();
};

var anticheatDelay = function() {
    for (var i = 0; i < 20; i++)
        tick();
};

var anticheatMediumDelay = function() {
    for (var i = 0; i < 4; i++)
        tick();
};

var anticheatLessDelay = function() {
    tick();
};

var findReplenishArea = function() {
    var found = false;
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
                if (world.getBlock(x, y, z) === "crafting_table") {
                    craftingTable = [x, y, z];
                    minDistanceSq = distanceSq;
                    found = true;
                }
            }
        }
    }
    if (!found)
        throw new Error("Could not find replenish area");
    return craftingTable;
};

var gatherStone = function(stoneNeeded) {
    for (var xDelta = -5; xDelta <= 5; xDelta++) {
        for (var zDelta = -5; zDelta <= 5; zDelta++) {
            for (var yDelta = -5; yDelta <= 5; yDelta++) {
                var x = player.x + xDelta;
                var y = player.y + player.eyeHeight + yDelta;
                var z = player.z + zDelta;
                if (world.getBlock(x, y, z) === "chest" || world.getBlock(x, y, z) === "trapped_chest") {
                    try {
                        openContainer(x, y, z, isChestContainer);
                        var chestItems = player.openContainer.items;
                        for (var i = 0; i < chestItems.length; i++) {
                            if (chestItems[i].id === STONE_BLOCK) {
                                stoneNeeded -= chestItems[i].Count;
                                player.openContainer.click(i, {type: "quick_move"});
                                anticheatLessDelay();
                                if (stoneNeeded <= 0)
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
    throw new Error("Not enough stone");
};

var ensureResources = function() {
    // Check if we already have the resources
    var foundStone = 0, foundSlabs = 0;
    var items = player.inventory.items;
    for (var slot = 0; slot < 36; slot++) {
        if (items[slot].id === STONE_BLOCK)
            foundStone += items[slot].Count;
        else if (items[slot].id === SLAB_BLOCK)
            foundSlabs += items[slot].Count;
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
    var targetX = replenishArea[0] + 0.5 - xDistanceToReplenishArea * 2 / hDistanceToReplenishArea;
    var targetZ = replenishArea[2] + 0.5 - zDistanceToReplenishArea * 2 / hDistanceToReplenishArea;

    if (!player.pathTo(targetX, player.y, targetZ))
        throw new Error("Could not move to replenish area");

    // Re-find the replenish area in case it has moved
    findReplenishArea();

    if (stoneNeeded > 0) {
        gatherStone(stoneNeeded);
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
                        anticheatMediumDelay();
                        if (count <= itemsNeeded || itemsNeeded === 64) {
                            // drop down all the stone
                            itemsNeeded -= count;
                            player.openContainer.click(slot);
                            anticheatMediumDelay();
                        } else {
                            // drop down as many stone as needed then put the rest back
                            for (var j = 0; j < itemsNeeded; j++) {
                                player.openContainer.click(slot, {rightClick: true});
                                anticheatMediumDelay();
                            }
                            player.inventory.click(i);
                            anticheatMediumDelay();
                            itemsNeeded = 0;
                        }
                        if (itemsNeeded <= 0)
                            break;
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
            anticheatMediumDelay();

            recipeCount -= 64;
        }
        player.closeContainer();
        anticheatDelay();
    }

    if (foundStone === 0) {
        if (!player.pick(STONE_BLOCK))
            throw new Error("Someone is tampering with the bot");
        anticheatDelay();
        chat("/ctf " + GROUP_NAME);
    }
};

var placePlatform = function() {
    for (var x = PLATFORM_X_MAX; x >= PLATFORM_X_MIN; x--) {
        var countDown = x % 2 === PLATFORM_X_MAX % 2;
        var standingX = x;
        if (!countDown)
            standingX--;
        for (var z = countDown ? PLATFORM_Z_MAX : PLATFORM_Z_MIN; countDown ? z >= PLATFORM_Z_MIN : z <= PLATFORM_Z_MAX; z += countDown ? -1 : 1) {
            ensureResources();
            if (world.getBlock(x, PLATFORM_Y, z).endsWith("_slab"))
                continue;
            if (!world.getBlockState(x, TEMPLATE_Y, z).solid || world.getBlock(x, TEMPLATE_Y, z).endsWith("sign"))
                continue;
            if (!player.pathTo(standingX + 0.5, player.y, z + 0.5))
                throw new Error("Movement to " + standingX + ", " + z + " failed");
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
            var closestPoint = world.getClosestVisiblePoint(x + dx[placementSide], PLATFORM_Y, z + dz[placementSide], directionNames[opposite(dir)]);
            if (!closestPoint) {
                if (!player.moveTo(standingX + 0.5, z + 0.5))
                    throw new Error("Movement to " + standingX + ", " + z + " failed");
                closestPoint = world.getClosestVisiblePoint(x + dx[placementSide], PLATFORM_Y, z + dz[placementSide], directionNames[opposite(dir)]);
                if (!closestPoint)
                    throw new Error("Slab not in view");
            }
            var clickX = closestPoint.x;
            var clickY = closestPoint.y;
            var clickZ = closestPoint.z;
            if (clickY - Math.floor(clickY) < 0.6)
                clickY += 0.1;
            anticheatMediumDelay();
            player.lookAt(clickX, clickY, clickZ);
            anticheatMediumDelay();
            player.rightClick(x + dx[placementSide], PLATFORM_Y, z + dz[placementSide], directionNames[opposite(dir)]);
        }
    }
};

replenishArea = findReplenishArea();
chat("/cto");
if (player.pick(STONE_BLOCK)) {
    anticheatDelay();
    chat("/ctf " + GROUP_NAME);
}
placePlatform();
