package net.earthcomputer.clientcommands;

import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ForgeHooks {

    private static ForgeHooks instance;

    public static ForgeHooks instance() {
        if (instance == null) {
            try {
                Class.forName("net.minecraftforge.common.MinecraftForge");
                instance = new ForgeImpl();
            } catch (ClassNotFoundException e) {
                instance = new ForgeHooks();
            }
        }
        return instance;
    }

    private ForgeHooks() {}

    public int ForgeEventFactory_onEnchantmentLevelSet(World world, BlockPos tablePos, int slot, int power, ItemStack itemToEnchant, int level) {
        return level;
    }

    public int ForgeHooks_getEnchantPower(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() == Blocks.BOOKSHELF ? 1 : 0;
    }

    private static class ForgeImpl extends ForgeHooks {
        static Class<?> clsForgeEventFactory = getClass("net.minecraftforge.event.ForgeEventFactory");
        static Class<?> clsForgeHooks = getClass("net.minecraftforge.common.ForgeHooks");
        static Method mthForgeEventFactory_onEnchantmentLevelSet = getMethod(clsForgeEventFactory, "onEnchantmentLevelSet", World.class, BlockPos.class, int.class, int.class, ItemStack.class, int.class);
        static Method mthForgeHooks_getEnchantPower = getMethod(clsForgeHooks, "getEnchantPower", World.class, BlockPos.class);

        @Override
        public int ForgeEventFactory_onEnchantmentLevelSet(World world, BlockPos tablePos, int slot, int power, ItemStack itemToEnchant, int level) {
            return (Integer) invokeMethod(mthForgeEventFactory_onEnchantmentLevelSet, null, world, tablePos, slot, power, itemToEnchant, level);
        }

        @Override
        public int ForgeHooks_getEnchantPower(World world, BlockPos pos) {
            return (Integer) invokeMethod(mthForgeHooks_getEnchantPower, null, world, pos);
        }

        private static Class<?> getClass(String name) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
        }
        private static Method getMethod(Class<?> clazz, String method, Class<?>... args) {
            try {
                return clazz.getMethod(method, args);
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
        }
        private static Object invokeMethod(Method method, Object instance, Object... args) {
            try {
                return method.invoke(instance, args);
            } catch (InvocationTargetException e) {
                if (e.getTargetException() instanceof RuntimeException) {
                    throw (RuntimeException) e.getTargetException();
                } else if (e.getTargetException() instanceof Error) {
                    throw (Error) e.getTargetException();
                } else {
                    throw new RuntimeException(e.getTargetException());
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }

}
