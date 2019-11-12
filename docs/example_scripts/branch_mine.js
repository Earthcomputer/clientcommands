
var wantedBlocks = ["emerald_ore", "diamond_ore", "gold_ore", "iron_ore", "coal_ore", "redstone_ore", "lapis_ore"];

var isWantedBlock = function(block) {
    return wantedBlocks.indexOf(block) !== -1;
};
var isWantedItemEntity = function(entity) {
    if (entity.type !== "item")
        return false;
    var nbt = entity.nbt;
    if (!nbt.Item)
        return false;
    var type = nbt.Item.id;
    for (var i = 0; i < wantedBlocks.length; i++)
        if (type === "minecraft:" + wantedBlocks[i])
            return true;
    return type === "minecraft:emerald" || type === "minecraft:diamond"
        || type === "minecraft:coal" || type === "minecraft:redstone"
        || type === "minecraft:lapis_lazuli";
};

var getDirectionName = function(dx, dz) {
    if (Math.abs(dx) > Math.abs(dz)) {
        if (dx > 0)
            return "east";
        else
            return "west";
    } else {
        if (dz > 0)
            return "south";
        else
            return "north";
    }
};

var canWalkThrough = function(block) {
    return block === "air" || block === "cave_air" || block === "torch";
};

var canWalkOn = function(block) {
    if (canWalkThrough(block))
        return false;
    if (block === "water" || block === "flowing_water" || block === "lava" || block === "flowing_lava")
        return false;
    return true;
};

var getTool = function(block) {
    if (block === "dirt" || block === "gravel" || block === "grass_block")
        return "shovel";
    if (block.endsWith("_planks") || block.endsWith("_fence"))
        return "axe";
    if (block === "cobweb")
        return "sword";
    return "pickaxe";
};

var centerPlayer = function() {
    var centerX = Math.floor(player.x) + 0.5;
    var centerZ = Math.floor(player.z) + 0.5;
    var isCentered = function() {
        return Math.abs(player.x - centerX) < 0.2 && Math.abs(player.z - centerZ) < 0.2;
    };
    if (isCentered())
        return;

    player.lookAt(centerX, player.y + player.eyeHeight, centerZ);
    player.blockInput();
    player.pressingForward = true;
    while (!isCentered())
        tick();
    player.pressingForward = false;
    while (player.motionX * player.motionX + player.motionY * player.motionY + player.motionZ * player.motionZ < 0.0001)
        tick();
    player.unblockInput();
    player.snapTo(centerX, player.y, centerZ);
};

var pickUpItems = function() {
    // find items needed to be picked up
    var items = $("@e[type=item,distance=..3]");
    var itemsWanted = [];
    for (var i = 0; i < items.length; i++) {
        var item = items[i];
        if (Math.abs(item.x - player.x) < 1.425 && Math.abs(item.z - player.z) < 1.425
            && item.y - player.y >= -0.5 && item.y - player.y < 2.3 - player.standingEyeHeight + player.eyeHeight) {
            if (isWantedItemEntity(item)) {
                var nbt = item.nbt;
                var itemId;
                if (nbt.Item && itemsWanted.indexOf(itemId = nbt.Item.id) === -1)
                    itemsWanted.push(itemId);
            }
        }
    }

    // throw out items from inventory if there's no space
    var playerItems = player.inventory.items;
    var cobblestoneCount = 0;
    for (var slot = 0; slot < 36; slot++) {
        if (playerItems[slot].Count < 64 && itemsWanted.indexOf(playerItems[slot].id) !== -1)
            itemsWanted.splice(itemsWanted.indexOf(playerItems[slot].id), 1);
        if (playerItems[slot].id === "minecraft:cobblestone" || playerItems[slot].id === "minecraft:stone")
            cobblestoneCount++;
    }
    for (var slot = 0; slot < 36 && itemsWanted.length > 0; slot++) {
        if (playerItems[slot].id === "minecraft:air")
            itemsWanted.pop();
    }
    for (var slot = 0; slot < 36 && itemsWanted.length > 0; slot++) {
        var itemId = playerItems[slot].id;
        var canThrow = false;
        if ((itemId === "minecraft:cobblestone" || itemId === "minecraft:stone") && cobblestoneCount > 1) {
            cobblestoneCount--;
            canThrow = true;
        } else if (itemId === "minecraft:dirt" || itemId === "minecraft:gravel"
            || itemId === "minecraft:granite" || itemId === "minecraft:diorite"
            || itemId === "minecraft:andesite") {
            canThrow = true;
        }
        if (canThrow) {
            player.inventory.click(slot, {type: 'throw', rightClick: true});
            itemsWanted.pop();
        }
    }
    return itemsWanted.length === 0;
};

var clearWay = function(x, y, z, dx, dz) {
    // mine block in front of player's face if necessary
    if (!canWalkThrough(world.getBlock(x + dx, y + 1, z + dz))) {
        if (!mineBlock(x + dx, y + 1, z + dz)) {
            return false;
        }
        if (!pickUpItems())
            return false;
    }
    // mine block in front of player's feet if necessary
    if (!canWalkThrough(world.getBlock(x + dx, y, z + dz))) {
        if (!mineBlock(x + dx, y, z + dz)) {
            return false;
        }
        if (!pickUpItems())
            return false;
    }
    // build bridge if necessary
    if (!canWalkOn(world.getBlock(x + dx, y - 1, z + dz))) {
        if (!makeBridge(x, y, z, dx, dz)) {
            return false;
        }
    }
    return true;
};

var mineBlock = function(x, y, z) {
    var toolMaterialOrder = ["diamond", "iron", "stone", "wooden", "golden"];
    var tool = getTool(world.getBlock(x, y, z));
    var picked = false;
    for (var i = 0; i < toolMaterialOrder.length; i++) {
        if (player.pick(toolMaterialOrder[i] + "_" + tool)) {
            picked = true;
            break;
        }
    }
    if (!picked)
        return false;
    return player.longMineBlock(x, y, z);
};

var makeBridge = function(x, y, z, dx, dz) {
    if (!player.pick(function(itemNbt) {
        return itemNbt.id === "minecraft:cobblestone" || itemNbt.id === "minecraft:stone";
    }))
        return false;
    // face backwards
    player.lookAt(player.x - dx, player.y, player.z - dz);
    // sneak backwards
    var continueSneaking = function() {
        player.pressingBack = true;
        tick();
        if (!pickUpItems()) {
            player.pressingBack = false;
            player.sneaking = false;
            player.unblockInput();
            return false;
        }
        timeout++;
        if (timeout % 20 === 0) {
            player.pressingBack = false;
            player.sneaking = false;
            if (!clearWay(x, y, z, dx, dz)) {
                player.unblockInput();
                return false;
            }
            player.sneaking = true;
        }
        return true;
    };
    player.blockInput();
    player.sneaking = true;
    var timeout = 0;
    while (Math.floor(player.x) === x && Math.floor(player.z) === z) {
        if (!continueSneaking())
            return false;
    }
    // keep sneaking for an extra 5 ticks to make sure there's part of the block in view
    for (var i = 0; i < 5; i++) {
        if (!continueSneaking())
            return false;
    }
    player.pressingBack = false;
    player.sneaking = false;
    player.unblockInput();
    return player.rightClick(x, y - 1, z, getDirectionName(dx, dz));
};

var mineNearbyOre = function(x, y, z) {
    centerPlayer();

    var cardinals4 = [[-1, 0], [1, 0], [0, -1], [0, 1]];
    var cardinals6 = [[-1, 0, 0], [1, 0, 0], [0, 0, -1], [0, 0, 1], [0, -1, 0], [0, 1, 0]];

    // mine blocks around head and above head
    var stepUpDirs = [];
    for (var dy = 1; dy <= 2; dy++) {
        // TODO: if we don't want the block at dy = 2, but there are visible wanted blocks next to it, they won't be mined
        if (canWalkThrough(world.getBlock(x, y + dy, z))) {
            for (var dir = 0; dir < cardinals6.length; dir++) {
                var ddx = cardinals6[dir][0], ddy = cardinals6[dir][1], ddz = cardinals6[dir][2];
                if (isWantedBlock(world.getBlock(x + ddx, y + dy + ddy, z + ddz))) {
                    if (!mineBlock(x + ddx, y + dy + ddy, z + ddz))
                        return false;
                    if (!pickUpItems())
                        return false;
                    if (dy === 2 && dir < 4)
                        stepUpDirs.push(dir);
                }
            }
        }
    }

    // step up if possible
    for (var i = 0; i < stepUpDirs.length; i++) {
        var dx = cardinals4[stepUpDirs[i]][0], dz = cardinals4[stepUpDirs[i]][1];

        // mine block to allow us to step up if necessary
        if (!canWalkThrough(world.getBlock(x + dx, y + 1, z + dz)))
            if (!mineBlock(x + dx, y + 1, z + dz))
                return false;
        if (canWalkThrough(world.getBlock(x + dx, y, z + dz)))
            continue;

        centerPlayer();

        // do the step up
        player.lookAt(player.x + dx, player.y + player.eyeHeight, player.z + dz);
        player.blockInput();
        player.pressingForward = true;
        for (var i = 0; i < 10; i++)
            tick();
        player.jumping = true;
        tick();
        player.jumping = false;
        while (Math.floor(player.x) === x && Math.floor(player.z) === z)
            tick();
        player.pressingForward = false;
        player.unblockInput();

        mineNearbyOre(x + dx, y + 1, z + dz);

        centerPlayer();

        player.lookAt(player.x - dx, player.y + player.eyeHeight, player.z - dz);
        player.blockInput();
        player.pressingForward = true;
        var currentY = player.y;
        while (player.y === currentY)
            tick();
        player.pressingForward = false;
        player.unblockInput();
    }

    // mine blocks around feet level
    for (var i = 0; i < 4; i++) {
        var dx = cardinals4[i][0], dz = cardinals4[i][1];
        if (isWantedBlock(world.getBlock(x + dx, y, z + dz))) {
            if (!mineBlock(x + dx, y, z + dz))
                return false;
            if (!pickUpItems())
                return false;

            if (!canWalkThrough(world.getBlock(x + dx, y - 1, z + dz))) {
                if (!canWalkThrough(world.getBlock(x + dx, y + 1, z + dz))) {
                    if (!mineBlock(x + dx, y + 1, z + dz))
                        return false;
                }

                centerPlayer();

                player.lookAt(player.x + dx, player.y + player.eyeHeight, player.z + dz);
                player.blockInput();
                player.pressingForward = true;
                while (Math.floor(player.x) === x && Math.floor(player.z) === z)
                    tick();
                player.pressingForward = false;
                player.unblockInput();

                mineNearbyOre(x + dx, y, z + dz);

                centerPlayer();

                player.lookAt(player.x - dx, player.y + player.eyeHeight, player.z - dz);
                player.blockInput();
                player.pressingForward = true;
                while (Math.floor(player.x) !== x || Math.floor(player.z) !== z)
                    tick();
                player.pressingForward = false;
                player.unblockInput();

                // mine block below feet level
                if (isWantedBlock(world.getBlock(x + dx, y - 1, z + dz))) {
                    if (!mineBlock(x + dx, y - 1, z + dz))
                        return false;
                    if (!canWalkThrough(world.getBlock(x + dx, y - 2, z + dz))) {
                        // collect the block
                        player.lookAt(player.x + dx, player.y + player.eyeHeight, player.z + dz);
                        player.blockInput();
                        player.pressingForward = true;
                        while (Math.floor(player.x) === x && Math.floor(player.z) === z)
                            tick();
                        player.pressingForward = false;
                        player.unblockInput();

                        if (!pickUpItems())
                            return false;

                        mineNearbyOre(x + dx, y, z + dz);

                        centerPlayer();

                        player.lookAt(player.x - dx, player.y + player.eyeHeight, player.z - dz);
                        player.blockInput();
                        player.pressingForward = true;
                        for (var i = 0; i < 10; i++)
                            tick();
                        player.jumping = true;
                        tick();
                        player.jumping = false;
                        while (Math.floor(player.x) !== x || Math.floor(player.z) !== z)
                            tick();
                        player.pressingForward = false;
                        player.unblockInput();
                    }
                }
            }
        }
    }
};

var makeTunnel = function(dx, dz) {
    var x = Math.floor(player.x);
    var y = Math.floor(player.y);
    var z = Math.floor(player.z);

    centerPlayer();

    if (!clearWay(x, y, z, dx, dz))
        return false;

    // walk to next spot
    player.lookAt(player.x + dx, player.y + player.eyeHeight, player.z + dz);
    player.blockInput();
    var timeout = 0;
    while (Math.floor(player.x) === x && Math.floor(player.z) === z) {
        player.pressingForward = true;
        tick();
        if (!pickUpItems()) {
            player.pressingForward = false;
            player.unblockInput();
            return false;
        }
        timeout++;
        if (timeout % 20 === 0) {
            player.pressingForward = false;
            if (!clearWay(x, y, z, dx, dz))
                return false;
        }
    }
    player.pressingForward = false;
    player.unblockInput();

    // place torch
    if (world.getBlockLight(x, y, z) <= 1) {
        if (!player.pick("torch"))
            return false;
        if (!player.rightClick(x, y - 1, z, "up"))
            print("Couldn't place torch");
    }

    mineNearbyOre(x + dx, y, z + dz);

    return true;
};

while (makeTunnel(1, 0));

print("Finished making tunnel");
