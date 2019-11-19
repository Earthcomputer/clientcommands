
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
    if (BlockState.defaultState(block).liquid)
        return false;
    return true;
};

var getTool = function(block) {
    var effective = function(item, block) {
        var stack = ItemStack.of(item);
        if (!BlockState.defaultState(block).canBreakByHand && !stack.isEffectiveOn(block))
            return false;
        return stack.getMiningSpeed(block) > 1;
    };
    if (effective("diamond_pickaxe", block))
        return "pickaxe";
    if (effective("diamond_shovel", block))
        return "shovel";
    if (effective("diamond_axe", block))
        return "axe";
    if (effective("diamond_sword", block))
        return "sword";
    return null;
};

var centerPlayer = function() {
    player.pressingForward = false;
    while (player.motionX * player.motionX + player.motionZ * player.motionZ > 0.001)
        tick();
    if (Math.abs(Math.floor(player.x) + 0.5 - player.x) >= 0.2 || Math.abs(Math.floor(player.z) + 0.5 - player.z) >= 0.2)
        return player.moveTo(Math.floor(player.x) + 0.5, Math.floor(player.z) + 0.5);
    return true;
};

var clearWay = function(x, y, z, dx, dz) {
    // mine block in front of player's face if necessary
    if (!canWalkThrough(world.getBlock(x + dx, y + 1, z + dz))) {
        if (!mineBlock(x + dx, y + 1, z + dz)) {
            throw new Error();
        }
    }
    // mine block in front of player's feet if necessary
    if (!canWalkThrough(world.getBlock(x + dx, y, z + dz))) {
        if (!mineBlock(x + dx, y, z + dz)) {
            throw new Error();
        }
    }
    // build bridge if necessary
    if (!canWalkOn(world.getBlock(x + dx, y - 1, z + dz))) {
        if (!makeBridge(x, y, z, dx, dz)) {
            throw new Error();
        }
    }
    return true;
};

var placeBlock = function(x, y, z) {
    if (!player.pick(function(itemNbt) {
        return itemNbt.id === "minecraft:cobblestone" || itemNbt.id === "minecraft:stone";
    }))
        throw new Error();

    if (player.rightClick(x - 1, y, z, "east")) return true;
    if (player.rightClick(x + 1, y, z, "west")) return true;
    if (player.rightClick(x, y, z - 1, "south")) return true;
    if (player.rightClick(x, y, z + 1, "north")) return true;
    if (player.rightClick(x, y - 1, z, "up")) return true;
    if (player.rightClick(x, y + 1, z, "down")) return true;
    return false;
};

var mineBlock = function(x, y, z) {
    var toolMaterialOrder = ["diamond", "iron", "stone", "wooden", "golden"];
    var tool = getTool(world.getBlock(x, y, z));
    if (tool) {
        var picked = false;
        for (var i = 0; i < toolMaterialOrder.length; i++) {
            if (player.pick(toolMaterialOrder[i] + "_" + tool)) {
                picked = true;
                break;
            }
        }
        if (!picked && !world.getBlockState(x, y, z).canBreakByHand)
            throw new Error();
    }

    var oldBlock = world.getBlock(x, y, z);
    if (oldBlock === "air") return true;
    var failCount = 0;
    do {
        if (!player.longMineBlock(x, y, z)) {
            failCount++;
            tick();
        }
        if (failCount > 5)
            throw new Error("Block pos: (" + x + ", " + y + ", " + z + ")");
    } while (world.getBlock(x, y, z) === oldBlock);

    var stateAbove = world.getBlockState(x, y + 1, z);
    if (stateAbove.fallable) {
        while (world.getBlock(x, y, z) !== stateAbove.block)
            tick();
        if (!mineBlock(x, y, z))
            throw new Error();
    }

    return true;
};

var makeBridge = function(x, y, z, dx, dz) {
    // face backwards
    player.lookAt(player.x - dx, player.y, player.z - dz);
    // sneak backwards
    var continueSneaking = function() {
        player.pressingBack = true;
        tick();
        timeout++;
        if (timeout % 20 === 0) {
            player.pressingBack = false;
            player.sneaking = false;
            if (!clearWay(x, y, z, dx, dz)) {
                player.unblockInput();
                throw new Error();
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
            throw new Error();
    }
    // keep sneaking for an extra 5 ticks to make sure there's part of the block in view
    for (var i = 0; i < 5; i++) {
        if (!continueSneaking())
            throw new Error();
    }
    player.pressingBack = false;
    player.sneaking = false;
    player.unblockInput();
    return placeBlock(x + dx, y - 1, z + dz);
};

var mineNearbyOre = function(x, y, z) {
    centerPlayer();

    var cardinals4 = [[-1, 0], [1, 0], [0, -1], [0, 1]];
    var cardinals6 = [[-1, 0, 0], [1, 0, 0], [0, 0, -1], [0, 0, 1], [0, -1, 0], [0, 1, 0]];

    // mine blocks around head and above head
    for (var dy = 1; dy <= 2; dy++) {
        if (canWalkThrough(world.getBlock(x, y + dy, z))) {
            for (var dir = 0; dir < cardinals6.length; dir++) {
                var ddx = cardinals6[dir][0], ddy = cardinals6[dir][1], ddz = cardinals6[dir][2];
                if (isWantedBlock(world.getBlock(x + ddx, y + dy + ddy, z + ddz))) {
                    if (!mineBlock(x + ddx, y + dy + ddy, z + ddz))
                        throw new Error();
                }
            }
        }
    }

    // step up
    for (var i = 0; i < 4; i++) {
        var dx = cardinals4[i][0], dz = cardinals4[i][1];

        // check if we want to step up
        if (!canWalkThrough(world.getBlock(x + dx, y + 2, z + dz))) continue;
        if (!canWalkThrough(world.getBlock(x + dx, y + 1, z + dz)) && !canWalkThrough(world.getBlock(x, y + 2, z))) continue;
        var wantToStepUp = false;
        for (var j = 0; j < 6; j++) {
            var ddx = cardinals6[j][0], ddy = cardinals6[j][1], ddz = cardinals6[j][2];
            if (isWantedBlock(world.getBlock(x + dx + ddx, y + 2 + ddy, z + dz + ddz))) {
                wantToStepUp = true;
                break;
            }
        }
        if (!wantToStepUp) continue;

        // mine block(s) to allow us to step up if necessary
        if (!canWalkThrough(world.getBlock(x + dx, y + 1, z + dz)))
            if (!mineBlock(x + dx, y + 1, z + dz))
                throw new Error();
        if (!canWalkThrough(world.getBlock(x, y + 2, z)))
            if (!mineBlock(x, y + 2, z))
                throw new Error();
        if (!canWalkOn(world.getBlock(x + dx, y, z + dz)))
            if (!placeBlock(x + dx, y, z + dz))
                continue;

        centerPlayer();

        // do the step up
        if (!player.moveTo(x + dx + 0.5, z + dz + 0.5)) throw new Error();
        if (!mineNearbyOre(x + dx, y + 1, z + dz)) throw new Error();
        centerPlayer();
        for (var dy = 2; dy >= 0; dy--) {
            if (!canWalkThrough(world.getBlock(x, y + dy, z)))
                if (!mineBlock(x, y + dy, z))
                    throw new Error();
        }
        if (!canWalkOn(world.getBlock(x, y - 1, z)))
            if (!placeBlock(x, y - 1, z)) throw new Error();
        if (!player.moveTo(x + 0.5, z + 0.5)) throw new Error();
    }

    // mine blocks around feet level
    for (var i = 0; i < 4; i++) {
        var dx = cardinals4[i][0], dz = cardinals4[i][1];
        if (isWantedBlock(world.getBlock(x + dx, y, z + dz))) {
            if (!mineBlock(x + dx, y, z + dz))
                throw new Error();

            if (!canWalkOn(world.getBlock(x + dx, y - 1, z + dz)))
                if (!placeBlock(x + dx, y - 1, z + dz))
                    continue;

            if (!canWalkThrough(world.getBlock(x + dx, y + 1, z + dz))) {
                if (!mineBlock(x + dx, y + 1, z + dz))
                    throw new Error();
            }

            centerPlayer();
            if (!player.moveTo(x + dx + 0.5, z + dz + 0.5)) throw new Error();
            if (!mineNearbyOre(x + dx, y, z + dz)) throw new Error();
            centerPlayer();
            for (var dy = 1; dy >= 0; dy--) {
                if (!canWalkThrough(world.getBlock(x, y + dy, z)))
                    if (!mineBlock(x, y + dy, z))
                        throw new Error();
            }
            if (!canWalkOn(world.getBlock(x, y - 1, z)))
                if (!placeBlock(x, y - 1, z)) throw new Error();
            if (!player.moveTo(x + 0.5, z + 0.5)) throw new Error();
        }
    }

    // keep mining for blocks possibly exposed by the mining operation
    for (var dy = 1; dy >= 0; dy--) {
        for (var i = 0; i < 4; i++) {
            var dx = cardinals4[i][0], dz = cardinals4[i][1];
            if (canWalkThrough(world.getBlock(x + dx, y + dy, z + dz))) {
                for (var j = 0; j < 6; j++) {
                    var ddx = cardinals6[j][0], ddy = cardinals6[j][1], ddz = cardinals6[j][2];
                    if (ddy === -1 && dy === 0) continue; // mineNearbyOre doesn't mine straight down
                    if (isWantedBlock(world.getBlock(x + dx + ddx, y + dy + ddy, z + dz + ddz))) {
                        if (!clearWay(x, y, z, dx, dz))
                            throw new Error();

                        centerPlayer();
                        if (!player.moveTo(x + dx + 0.5, z + dz + 0.5)) throw new Error();
                        if (!mineNearbyOre(x + dx, y, z + dz)) throw new Error();
                        centerPlayer();
                        for (var dy = 1; dy >= 0; dy--) {
                            if (!canWalkThrough(world.getBlock(x, y + dy, z)))
                                if (!mineBlock(x, y + dy, z))
                                    throw new Error();
                        }
                        if (!canWalkOn(world.getBlock(x, y - 1, z)))
                            if (!placeBlock(x, y - 1, z)) throw new Error();
                        if (!player.moveTo(x + 0.5, z + 0.5)) throw new Error();
                    }
                }
            }
        }
    }

    // mine block below feet level
    for (var i = 0; i < 4; i++) {
        var dx = cardinals4[i][0], dz = cardinals4[i][1];

        if (canWalkThrough(world.getBlock(x + dx, y, z + dz)) && isWantedBlock(world.getBlock(x + dx, y - 1, z + dz))) {
            if (!mineBlock(x + dx, y - 1, z + dz))
                throw new Error();
            if (!canWalkOn(world.getBlock(x + dx, y - 2, z + dz)))
                if (!placeBlock(x + dx, y - 2, z + dz))
                    continue;

            // collect the block
            if (!player.moveTo(x + dx + 0.5, z + dz + 0.5)) throw new Error();
            if (!mineNearbyOre(x + dx, y - 1, z + dz)) throw new Error();
            centerPlayer();
            if (!canWalkThrough(x + dx, y + 1, z + dz))
                if (!mineBlock(x + dx, y + 1, z + dz))
                    throw new Error();
            for (var dy = 1; dy >= 0; dy--) {
                if (!canWalkThrough(world.getBlock(x, y + dy, z)))
                    if (!mineBlock(x, y + dy, z))
                        throw new Error();
            }
            if (!canWalkOn(world.getBlock(x, y - 1, z)))
                if (!placeBlock(x, y - 1, z)) throw new Error();
            if (!player.moveTo(x + 0.5, z + 0.5)) throw new Error();
        }
    }

    return true;
};

var makeTunnel = function(x, y, z, dx, dz) {
    centerPlayer();

    if (!clearWay(x, y, z, dx, dz))
        throw new Error();

    // walk to next spot
    if (!player.moveTo(x + dx + 0.5, z + dz + 0.5, false))
        throw new Error();

    // place torch
    if (world.getBlockLight(x, y, z) <= 1) {
        if (!player.pick("torch"))
            throw new Error();
        if (!player.rightClick(x, y - 1, z, "up"))
            print("Couldn't place torch");
    }

    if (!mineNearbyOre(x + dx, y, z + dz)) throw new Error();

    return true;
};

var makeTunnelLoop = function() {
    try {
        var x = Math.floor(player.x);
        var y = Math.floor(player.y);
        var z = Math.floor(player.z);
        var dx = 1, dz = 0;

        while (makeTunnel(x, y, z, dx, dz)) {
            x += dx;
            z += dz;
        }
    } finally {
        mainThread.kill();
    }
};

var pickUpItemsLoop = function() {
    while (true) {
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

        if (itemsWanted.length !== 0)
            break;

        tick();
    }
    mainThread.kill();
};

var mainThread = Thread.current;

new Thread(pickUpItemsLoop).run();
new Thread(makeTunnelLoop).run();

while (true) tick(); // keep running until killed
