package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.features.ClientWeather;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class WeatherCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cweather")
            .then(literal("clear").executes(ctx -> executeWeatherClear(ctx.getSource())))
            .then(literal("rain").executes(ctx -> executeWeatherRain(ctx.getSource())))
            .then(literal("thunder").executes(ctx -> executeWeatherThunder(ctx.getSource())))
            .then(literal("reset").executes(ctx -> executeWeatherReset(ctx.getSource())))
        );
    }

    private static int executeWeatherClear(FabricClientCommandSource source) {
        ClientWeather.setRain(0);
        ClientWeather.setThunder(0);
        Component feedback = Component.translatable("commands.weather.set.clear");
        source.sendFeedback(feedback);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeWeatherRain(FabricClientCommandSource source) {
        ClientWeather.setRain(1);
        ClientWeather.setThunder(0);
        Component feedback = Component.translatable("commands.weather.set.rain");
        source.sendFeedback(feedback);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeWeatherThunder(FabricClientCommandSource source) {
        ClientWeather.setRain(1);
        ClientWeather.setThunder(1);
        Component feedback = Component.translatable("commands.weather.set.thunder");
        source.sendFeedback(feedback);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeWeatherReset(FabricClientCommandSource source) {
        ClientWeather.setRain(-1);
        ClientWeather.setThunder(-1);
        Component feedback = Component.translatable("commands.cweather.reset");
        source.sendFeedback(feedback);
        return Command.SINGLE_SUCCESS;
    }

}
