package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.mixin.ProjectileEntityAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.FluidTags;
import net.minecraft.tag.Tag;
import net.minecraft.util.collection.ReusableStream;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

public class FishingCracker {


    public static void main(String[] args) {
        Random rand = new Random(0x5deece66dL);
        UUID uuid = MathHelper.randomUuid(rand);
        getSeed(uuid);
    }

    /**
     * Returns the internal seed of the Random the instant before it generates the UUID via {@link MathHelper#randomUuid(Random)}
     */
    private static OptionalLong getSeed(UUID uuid) {
        long uuidLower = uuid.getLeastSignificantBits();

        long hi = 0;
        do {
            long nextLongOutput = hi | (uuidLower & ~(3L << 62));
            long upperBits = nextLongOutput >>> 32;
            long lowerBits = nextLongOutput & ((1L << 32) - 1);

            long a = (24667315L * upperBits + 18218081L * lowerBits + 67552711L) >> 32;
            long b = (-4824621L * upperBits + 7847617L * lowerBits + 7847617L) >> 32;
            long seed = 7847617L * a - 18218081L * b;

            if ((seed >>> 16 << 32) + (int)(((seed * 0x5deece66dL + 0xbL) & ((1L << 48) - 1)) >>> 16) == nextLongOutput) {
                // advance by -3
                seed = (seed * 0x13A1F16F099DL + 0x95756C5D2097L) & ((1L << 48) - 1);
                Random rand = new Random(seed ^ 0x5deece66dL);
                if (MathHelper.randomUuid(rand).equals(uuid)) {
                    return OptionalLong.of(seed);
                }
            }
            
            hi += 1L << 62;
        } while (hi != 0);

        return OptionalLong.empty();
    }





    private static int getLocalPing() {
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        if (networkHandler == null)
            return -1;

        PlayerListEntry localPlayer = networkHandler.getPlayerListEntry(networkHandler.getProfile().getId());
        if (localPlayer == null)
            return -1;
        
        int ping = localPlayer.getLatency();

        return ping;
    }

    public static void processBobberSpawn(UUID fishingBobberUUID, Vec3d pos, Vec3d velocity) {
        OptionalLong optionalSeed = getSeed(fishingBobberUUID);
        if (!optionalSeed.isPresent()) {
            // TODO: display error to user
            return;
        }
        onFishingRodBobberEntity();

        long seed = optionalSeed.getAsLong();
        SimulatedFishingBobber fishingBobber = new SimulatedFishingBobber(seed, lureLevel, pos, velocity);

        boolean wasCatchingFish = false;
        int tickCounter = 0;
        for (int ticks = 0; ticks < 10000; ticks++) {
            if (tickCounter == 0) {
                //System.out.println("CLIENTSIDE SEED: " + PlayerRandCracker.getSeed(fishingBobber.random));
            }
            tickCounter++;
            fishingBobber.tick();
            //System.out.println("Client simulation: " + fishingBobber.state + " " + tickCounter + " " + fishingBobber.waitCountdown + " " + fishingBobber.fishTravelCountdown);
            if (fishingBobber.hookCountdown > 0) {
                //System.out.println("Client fishable: " + tickCounter);
                wasCatchingFish = true;
            } else if (wasCatchingFish) {
                break;
            }
        }
    }

    private static long throwTime;
    public static long rollingAverageEndOfTickDelay = 0;
    public static boolean waitingForFishingRod;
    private static int lureLevel;
    private static int luckOfTheSeaLevel;

    public static void onThrownFishingRod(ItemStack stack) {
        throwTime = System.nanoTime();
        waitingForFishingRod = true;
        lureLevel = EnchantmentHelper.getLure(stack);
        luckOfTheSeaLevel = EnchantmentHelper.getLuckOfTheSea(stack);
    }


    public static void onFishingRodBobberEntity() {
        if (!waitingForFishingRod) {
            return;
        }

        waitingForFishingRod = false;

        long thrownItemDeltaMillis = (System.nanoTime() - throwTime) / 1000000;
        long localPingMillis = getLocalPing();

        //The 1000 divided by 20 is the number milliseconds per tick there are
        long timeFromEndOfTick = thrownItemDeltaMillis - localPingMillis;// - (1000/20);

        rollingAverageEndOfTickDelay = (rollingAverageEndOfTickDelay * 3 + timeFromEndOfTick) / 4;
    }

    public static void reset() {
        // Called when fishingManipulation TempRule is disabled
    }

    // ===== SIMULATE FISHING BOBBER ===== //

    private static class SimulatedFishingBobber {
        private static final EntityDimensions FISHING_BOBBER_DIMENSIONS = EntityType.FISHING_BOBBER.getDimensions();

        private final World world = MinecraftClient.getInstance().world;

        private final FishingBobberEntity fakeEntity = new FishingBobberEntity(Objects.requireNonNull(MinecraftClient.getInstance().player), world, 0, 0);

        // state variables
        private Vec3d pos;
        private Box boundingBox;
        private Vec3d velocity;
        private boolean onGround;
        private State state = State.FLYING;
        private int hookCountdown;
        private int fishTravelCountdown;
        private boolean inOpenWater = true;
        private int outOfOpenWaterTicks;
        private boolean caughtFish;
        private boolean horizontalCollision;
        private boolean verticalCollision;
        private int waitCountdown;
        private boolean touchingWater;
        private boolean firstUpdate;

        private float fishAngle;

        private final Random random;
        private final int lureLevel;

        // output variables
        private boolean failed;

        public SimulatedFishingBobber(long seed, int lure, Vec3d pos, Vec3d velocity) {
            //this.random = new TestRandom("client");
            //this.random.setSeed(seed ^ 0x5deece66dL);
            this.random = new Random(seed ^ 0x5deece66dL);
            // entity UUID
            random.nextLong();
            random.nextLong();

            // entity yaw and pitch
            random.nextGaussian();
            random.nextGaussian();
            random.nextGaussian();

            this.lureLevel = lure;
            this.pos = pos;
            this.velocity = velocity;
            this.boundingBox = FISHING_BOBBER_DIMENSIONS.method_30231(pos.x, pos.y, pos.z);
        }

        public void tick() {
            //((TestRandom) random).dump();

            assert world != null;

            onBaseTick();

            if (this.onGround) {
                failed = true;
            }

            float f = 0.0F;
            BlockPos blockPos = new BlockPos(this.pos);
            FluidState fluidState = this.world.getFluidState(blockPos);
            if (fluidState.isIn(FluidTags.WATER)) {
                f = fluidState.getHeight(this.world, blockPos);
            }

            boolean bl = f > 0.0F;
            if (this.state == State.FLYING) {
                if (bl) {
                    this.velocity = this.velocity.multiply(0.3D, 0.2D, 0.3D);
                    this.state = State.BOBBING;
                    return;
                }

                this.checkForCollision();
            } else {
                if (this.state == State.BOBBING) {
                    Vec3d vec3d = this.velocity;
                    double d = this.pos.y + vec3d.y - (double)blockPos.getY() - (double)f;
                    if (Math.abs(d) < 0.01D) {
                        d += Math.signum(d) * 0.1D;
                    }

                    this.velocity = new Vec3d(vec3d.x * 0.9D, vec3d.y - d * (double)this.random.nextFloat() * 0.2D, vec3d.z * 0.9D);
                    if (this.hookCountdown <= 0 && this.fishTravelCountdown <= 0) {
                        this.inOpenWater = true;
                    } else {
                        this.inOpenWater = this.inOpenWater && this.outOfOpenWaterTicks < 10 && this.isOpenOrWaterAround(blockPos);
                    }

                    if (bl) {
                        this.outOfOpenWaterTicks = Math.max(0, this.outOfOpenWaterTicks - 1);
                        if (this.caughtFish) {
                            // this will just drag the bobber down which we don't care about
                            //this.velocity = (this.velocity.add(0.0D, -0.1D * (double)this.velocityRandom.nextFloat() * (double)this.velocityRandom.nextFloat(), 0.0D));
                        }

                        this.tickFishingLogic(blockPos);
                    } else {
                        this.outOfOpenWaterTicks = Math.min(10, this.outOfOpenWaterTicks + 1);
                    }
                }
            }

            if (!fluidState.isIn(FluidTags.WATER)) {
                this.velocity = this.velocity.add(0.0D, -0.03D, 0.0D);
            }

            this.move(this.velocity);
            if (this.state == State.FLYING && (this.onGround || this.horizontalCollision)) {
                this.velocity = Vec3d.ZERO;
            }

            double e = 0.92D;
            this.velocity = this.velocity.multiply(e);

            boundingBox = FISHING_BOBBER_DIMENSIONS.method_30231(pos.x, pos.y, pos.z);
        }

        private void onBaseTick() {
            this.updateWaterState();

            this.firstUpdate = false;
        }

        private void updateWaterState() {
            this.checkWaterState();
        }

        private void checkWaterState() {
            if (this.updateMovementInFluid(FluidTags.WATER, 0.014D)) {
                if (!this.touchingWater && !this.firstUpdate) {
                    this.onSwimmingStart();
                }

                this.touchingWater = true;
            } else {
                this.touchingWater = false;
            }
        }

        private void onSwimmingStart() {
            float f = 0.2F;
            Vec3d vec3d = velocity;
            float g = MathHelper.sqrt(vec3d.x * vec3d.x * 0.20000000298023224D + vec3d.y * vec3d.y + vec3d.z * vec3d.z * 0.20000000298023224D) * f;
            if (g > 1.0F) {
                g = 1.0F;
            }

            if ((double)g < 0.25D) {
                random.nextFloat();
                random.nextFloat();
                //this.playSound(this.getSplashSound(), g, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
            } else {
                random.nextFloat();
                random.nextFloat();
                //this.playSound(this.getHighSpeedSplashSound(), g, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
            }

            float h = (float)MathHelper.floor(this.pos.y);

            int j;
            double k;
            double l;
            for(j = 0; (float)j < 1.0F + FISHING_BOBBER_DIMENSIONS.width * 20.0F; ++j) {
                k = (this.random.nextDouble() * 2.0D - 1.0D) * (double)FISHING_BOBBER_DIMENSIONS.width;
                l = (this.random.nextDouble() * 2.0D - 1.0D) * (double)FISHING_BOBBER_DIMENSIONS.width;
                random.nextDouble();
                //this.world.addParticle(ParticleTypes.BUBBLE, this.pos.x + k, (double)(h + 1.0F), this.pos.z + l, vec3d.x, vec3d.y - this.random.nextDouble() * 0.20000000298023224D, vec3d.z);
            }

            for(j = 0; (float)j < 1.0F + FISHING_BOBBER_DIMENSIONS.width * 20.0F; ++j) {
                k = (this.random.nextDouble() * 2.0D - 1.0D) * (double)FISHING_BOBBER_DIMENSIONS.width;
                l = (this.random.nextDouble() * 2.0D - 1.0D) * (double)FISHING_BOBBER_DIMENSIONS.width;
                //this.world.addParticle(ParticleTypes.SPLASH, this.getX() + k, (double)(h + 1.0F), this.getZ() + l, vec3d.x, vec3d.y, vec3d.z);
            }
        }

        private boolean updateMovementInFluid(Tag<Fluid> tag, double d) {
            Box box = this.boundingBox.contract(0.001D);
            int i = MathHelper.floor(box.minX);
            int j = MathHelper.ceil(box.maxX);
            int k = MathHelper.floor(box.minY);
            int l = MathHelper.ceil(box.maxY);
            int m = MathHelper.floor(box.minZ);
            int n = MathHelper.ceil(box.maxZ);
            if (!this.world.isRegionLoaded(i, k, m, j, l, n)) {
                return false;
            } else {
                double e = 0.0D;
                boolean bl2 = false;
                Vec3d vec3d = Vec3d.ZERO;
                int o = 0;
                BlockPos.Mutable mutable = new BlockPos.Mutable();

                for(int p = i; p < j; ++p) {
                    for(int q = k; q < l; ++q) {
                        for(int r = m; r < n; ++r) {
                            mutable.set(p, q, r);
                            FluidState fluidState = this.world.getFluidState(mutable);
                            if (fluidState.isIn(tag)) {
                                double f = (double)((float)q + fluidState.getHeight(this.world, mutable));
                                if (f >= box.minY) {
                                    bl2 = true;
                                    e = Math.max(f - box.minY, e);
                                }
                            }
                        }
                    }
                }

                if (vec3d.length() > 0.0D) {
                    if (o > 0) {
                        vec3d = vec3d.multiply(1.0D / (double)o);
                    }

                    vec3d = vec3d.normalize();

                    Vec3d vec3d3 = this.velocity;
                    vec3d = vec3d.multiply(d);
                    double g = 0.003D;
                    if (Math.abs(vec3d3.x) < 0.003D && Math.abs(vec3d3.z) < 0.003D && vec3d.length() < 0.0045000000000000005D) {
                        vec3d = vec3d.normalize().multiply(0.0045000000000000005D);
                    }

                    this.velocity = this.velocity.add(vec3d);
                }

                return bl2;
            }
        }

        private void checkForCollision() {
            assert fakeEntity != null;
            fakeEntity.updatePosition(pos.x, pos.y, pos.z);
            fakeEntity.setVelocity(velocity);
            HitResult hitResult = ProjectileUtil.getCollision(fakeEntity, ((ProjectileEntityAccessor) fakeEntity)::callCanCollideWith);
            if (hitResult.getType() != HitResult.Type.MISS) {
                failed = true;
            }
        }

        private void move(Vec3d movement) {
            assert world != null;

            Vec3d vec3d = this.adjustMovementForCollisions(movement);
            if (vec3d.lengthSquared() > 1.0E-7D) {
                this.boundingBox = this.boundingBox.offset(vec3d);
                this.pos = new Vec3d((boundingBox.minX + boundingBox.maxX) / 2.0D, boundingBox.minY, (boundingBox.minZ + boundingBox.maxZ) / 2.0D);
            }

            this.horizontalCollision = !MathHelper.approximatelyEquals(movement.x, vec3d.x) || !MathHelper.approximatelyEquals(movement.z, vec3d.z);
            this.verticalCollision = movement.y != vec3d.y;
            this.onGround = this.verticalCollision && movement.y < 0.0D;
            //this.fall(vec3d.y, this.onGround, blockState, blockPos);
            Vec3d vec3d2 = this.velocity;
            if (movement.x != vec3d.x) {
                this.velocity = new Vec3d(0.0D, vec3d2.y, vec3d2.z);
            }

            if (movement.z != vec3d.z) {
                this.velocity = new Vec3d(vec3d2.x, vec3d2.y, 0.0D);
            }

            if (movement.y != vec3d.y) {
                // block.onLanded
                velocity = velocity.multiply(1.0D, 0.0D, 1.0D);
            }

            if (this.onGround) {
                // block.onSteppedOn
            }

            //this.checkBlockCollision(); // no interesting blocks

            float i = this.getVelocityMultiplier();
            this.velocity = this.velocity.multiply((double)i, 1.0D, (double)i);
            if (this.world.method_29556(this.boundingBox.contract(0.001D)).noneMatch((blockStatex) -> blockStatex.isIn(BlockTags.FIRE) || blockStatex.isOf(Blocks.LAVA))) {
                failed = true;
            }
        }

        private Vec3d adjustMovementForCollisions(Vec3d movement) {
            Box box = this.boundingBox;
            assert fakeEntity != null;
            fakeEntity.updatePosition(pos.x, pos.y, pos.z);
            fakeEntity.setVelocity(velocity);
            assert world != null;

            ShapeContext shapeContext = ShapeContext.of(fakeEntity);
            VoxelShape voxelShape = this.world.getWorldBorder().asVoxelShape();
            Stream<VoxelShape> stream = VoxelShapes.matchesAnywhere(voxelShape, VoxelShapes.cuboid(box.contract(1.0E-7D)), BooleanBiFunction.AND) ? Stream.empty() : Stream.of(voxelShape);
            Stream<VoxelShape> stream2 = this.world.getEntityCollisions(fakeEntity, box.stretch(movement), (entity) -> true);
            ReusableStream<VoxelShape> reusableStream = new ReusableStream<>(Stream.concat(stream2, stream));

            return movement.lengthSquared() == 0.0D ? movement : Entity.adjustMovementForCollisions(fakeEntity, movement, box, this.world, shapeContext, reusableStream);
        }

        private float getVelocityMultiplier() {
            assert world != null;
            Block block = this.world.getBlockState(new BlockPos(pos)).getBlock();
            float f = block.getVelocityMultiplier();
            if (block != Blocks.WATER && block != Blocks.BUBBLE_COLUMN) {
                return (double)f == 1.0D ? this.world.getBlockState(new BlockPos(this.pos.x, this.boundingBox.minY - 0.5000001D, this.pos.z)).getBlock().getVelocityMultiplier() : f;
            } else {
                return f;
            }
        }

        private boolean isOpenOrWaterAround(BlockPos pos) {
            PositionType positionType = PositionType.INVALID;

            for(int i = -1; i <= 2; ++i) {
                PositionType positionType2 = this.getPositionType(pos.add(-2, i, -2), pos.add(2, i, 2));
                switch(positionType2) {
                    case INVALID:
                        return false;
                    case ABOVE_WATER:
                        if (positionType == PositionType.INVALID) {
                            return false;
                        }
                        break;
                    case INSIDE_WATER:
                        if (positionType == PositionType.ABOVE_WATER) {
                            return false;
                        }
                }

                positionType = positionType2;
            }

            return true;
        }

        private PositionType getPositionType(BlockPos start, BlockPos end) {
            return BlockPos.stream(start, end).map(this::getPositionType).reduce((positionType, positionType2) -> positionType == positionType2 ? positionType : PositionType.INVALID).orElse(PositionType.INVALID);
        }

        private PositionType getPositionType(BlockPos pos) {
            assert world != null;
            BlockState blockState = this.world.getBlockState(pos);
            if (!blockState.isAir() && !blockState.isOf(Blocks.LILY_PAD)) {
                FluidState fluidState = blockState.getFluidState();
                return fluidState.isIn(FluidTags.WATER) && fluidState.isStill() && blockState.getCollisionShape(this.world, pos).isEmpty() ? PositionType.INSIDE_WATER : PositionType.INVALID;
            } else {
                return PositionType.ABOVE_WATER;
            }
        }

        private void tickFishingLogic(BlockPos pos) {
            assert world != null;
            int i = 1;
            BlockPos blockPos = pos.up();
            if (this.random.nextFloat() < 0.25F && this.world.hasRain(blockPos)) {
                ++i;
            }

            if (this.random.nextFloat() < 0.5F && !this.world.isSkyVisible(blockPos)) {
                --i;
            }

            if (this.hookCountdown > 0) {
                --this.hookCountdown;
                if (this.hookCountdown <= 0) {
                    this.waitCountdown = 0;
                    this.fishTravelCountdown = 0;
                    this.caughtFish = false;
                }
            } else {
                float n;
                float o;
                float p;
                double q;
                double r;
                double s;
                BlockState blockState2;
                if (this.fishTravelCountdown > 0) {
                    this.fishTravelCountdown -= i;
                    if (this.fishTravelCountdown > 0) {
                        this.fishAngle = (float)((double)this.fishAngle + this.random.nextGaussian() * 4.0D);
                        n = this.fishAngle * 0.017453292F;
                        o = MathHelper.sin(n);
                        p = MathHelper.cos(n);
                        q = this.pos.x + (double)(o * (float)this.fishTravelCountdown * 0.1F);
                        r = (double)((float)MathHelper.floor(this.pos.y) + 1.0F);
                        s = this.pos.z + (double)(p * (float)this.fishTravelCountdown * 0.1F);
                        blockState2 = world.getBlockState(new BlockPos(q, r - 1.0D, s));
                        if (blockState2.isOf(Blocks.WATER)) {
                            if (this.random.nextFloat() < 0.15F) {
                                //serverWorld.spawnParticles(ParticleTypes.BUBBLE, q, r - 0.10000000149011612D, s, 1, (double)o, 0.1D, (double)p, 0.0D);
                            }

                            float k = o * 0.04F;
                            float l = p * 0.04F;
                            //serverWorld.spawnParticles(ParticleTypes.FISHING, q, r, s, 0, (double)l, 0.01D, (double)(-k), 1.0D);
                            //serverWorld.spawnParticles(ParticleTypes.FISHING, q, r, s, 0, (double)(-l), 0.01D, (double)k, 1.0D);
                        }
                    } else {
                        random.nextFloat();
                        random.nextFloat();
                        //this.playSound(SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, 0.25F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
                        double m = this.pos.y + 0.5D;
                        //serverWorld.spawnParticles(ParticleTypes.BUBBLE, this.getX(), m, this.getZ(), (int)(1.0F + this.getWidth() * 20.0F), (double)this.getWidth(), 0.0D, (double)this.getWidth(), 0.20000000298023224D);
                        //serverWorld.spawnParticles(ParticleTypes.FISHING, this.getX(), m, this.getZ(), (int)(1.0F + this.getWidth() * 20.0F), (double)this.getWidth(), 0.0D, (double)this.getWidth(), 0.20000000298023224D);
                        this.hookCountdown = MathHelper.nextInt(this.random, 20, 40);
                        this.caughtFish = true;
                    }
                } else if (this.waitCountdown > 0) {
                    this.waitCountdown -= i;
                    n = 0.15F;
                    if (this.waitCountdown < 20) {
                        n = (float)((double)n + (double)(20 - this.waitCountdown) * 0.05D);
                    } else if (this.waitCountdown < 40) {
                        n = (float)((double)n + (double)(40 - this.waitCountdown) * 0.02D);
                    } else if (this.waitCountdown < 60) {
                        n = (float)((double)n + (double)(60 - this.waitCountdown) * 0.01D);
                    }

                    if (this.random.nextFloat() < n) {
                        o = MathHelper.nextFloat(this.random, 0.0F, 360.0F) * 0.017453292F;
                        p = MathHelper.nextFloat(this.random, 25.0F, 60.0F);
                        q = this.pos.x + (double)(MathHelper.sin(o) * p * 0.1F);
                        r = (double)((float)MathHelper.floor(this.pos.y) + 1.0F);
                        s = this.pos.z + (double)(MathHelper.cos(o) * p * 0.1F);
                        blockState2 = world.getBlockState(new BlockPos(q, r - 1.0D, s));
                        if (blockState2.isOf(Blocks.WATER)) {
                            random.nextInt(2);
                            //serverWorld.spawnParticles(ParticleTypes.SPLASH, q, r, s, 2 + this.random.nextInt(2), 0.10000000149011612D, 0.0D, 0.10000000149011612D, 0.0D);
                        }
                    }

                    if (this.waitCountdown <= 0) {
                        this.fishAngle = MathHelper.nextFloat(this.random, 0.0F, 360.0F);
                        this.fishTravelCountdown = MathHelper.nextInt(this.random, 20, 80);
                    }
                } else {
                    this.waitCountdown = MathHelper.nextInt(this.random, 100, 600);
                    this.waitCountdown -= this.lureLevel * 20 * 5;
                }
            }
        }

        private enum PositionType {
            ABOVE_WATER,
            INSIDE_WATER,
            INVALID;
        }

        private enum State {
            FLYING,
            BOBBING;
        }
    }

}
