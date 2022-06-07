package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class MoteCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cmote")//cmote for c emoticon but shortened because it sounds better
            .then(literal("yay")
                .executes(ctx -> sendEmoticon("╲(｡◕‿◕｡)╱")))
                .then(literal("nice")
                    .executes(ctx -> sendEmoticon("(๑˃̵ᴗ˂̵)و nice!")))
            .then(literal("cry")
                .executes(ctx -> sendEmoticon("(╥_╥)"))
                .then(literal("bawling")
                    .executes(ctx -> sendEmoticon("༼ ╥╥ ෴ ╥╥ ༽ ")))
                .then(literal("blubbering")
                    .executes(ctx -> sendEmoticon("༼☯﹏☯༽")))
                .then(literal("snivelling")
                    .executes(ctx -> sendEmoticon("(つ﹏⊂)")))
                .then(literal("lament")
                    .executes(ctx -> sendEmoticon("（>﹏<）"))))
            .then(literal("flip")
                .executes(ctx -> sendEmoticon("(╯°□°)╯︵ ┻━┻"))
                .then(literal("pissed")
                    .executes(ctx -> sendEmoticon("(ノಠ益ಠ)ノ彡┻━┻")))
                .then(literal("lookin")
                    .executes(ctx -> sendEmoticon("(┛ಠ_ಠ)┛彡┻━┻"))))
            .then(literal("shrug")
                .executes(ctx -> sendEmoticon("¯\\_(ツ)_/¯"))
                .then(literal("1")
                    .executes(ctx -> sendEmoticon("¯\\_(ツ)_/¯")))
                .then(literal("2")
                    .executes(ctx -> sendEmoticon("ヽ(´--｀)┌"))))
            .then(literal("sus")
                .executes(ctx -> sendEmoticon("（・―・）"))
                .then(literal("mini")
                    .executes(ctx -> sendEmoticon("・-・")))
                .then(literal("wide_open")
                    .executes(ctx -> sendEmoticon("( ⊙‿⊙)")))
                .then(literal("bri")
                    .executes(ctx -> sendEmoticon("(╭ರ_⊙)")))
                .then(literal("squint")
                    .executes(ctx -> sendEmoticon("(≖-≖)")))
                .then(literal("hardcore")
                    .executes(ctx -> sendEmoticon("（´◉◞⊖◟◉｀）")))) // that expression when you are really doubting someone and purse your lips
            .then(literal("notplussed")
                .executes(ctx -> sendEmoticon("ب_ب"))) //the "I'm not angry, I'm just disappointed"
            .then(literal("bear")
                .executes(ctx -> sendEmoticon("ʕ•ᴥ•ʔ"))
                .then(literal("left")
                    .executes(ctx -> sendEmoticon("ʕ·ᴥ·　ʔ")))
                .then(literal("right")
                    .executes(ctx -> sendEmoticon("ʕ　·ᴥ·ʔ")))
                .then(literal("fighting")
                    .executes(ctx -> sendEmoticon("ʕง•ᴥ•ʔง")))
                .then(literal("nice")
                    .executes(ctx -> sendEmoticon("ʕง ˃ᴥ˂ʔو nice!")))
                .then(literal("star")
                    .executes(ctx -> sendEmoticon("ʕ•̀ω•́ʔ✧")))
                .then(literal("cry")
                    .executes(ctx -> sendEmoticon("ʕ>⌓<｡ʔ")))
                .then(literal("interested")
                    .executes(ctx -> sendEmoticon("ʕ◉ᴥ◉ʔ")))
                .then(literal("oh")
                    .executes(ctx -> sendEmoticon("ʕ • ₒ • ʔ"))
                    .then(literal("OH")
                        .executes(ctx -> sendEmoticon("ʕ • O • ʔ"))))
                .then(literal("bearception")
                    .executes(ctx -> sendEmoticon("ʕ•ᴥ•ʔ ʕ·ᴥ·　ʔ ʕ　·ᴥ·ʔ ʕง•ᴥ•ʔง"))
                    .then(literal("secret")
                        .then(literal("fu")
                            .executes(ctx -> sendEmoticon("╭∩╮ʕ•ᴥ•ʔ╭∩╮"))))))
            .then(literal("lenny")
                .executes(ctx -> sendEmoticon("( ͡° ͜ʖ ͡°)"))
                .then(literal("stash")
                    .executes(ctx -> sendEmoticon("( ͡°╭͜ʖ╮͡° )"))
                    .then(literal("bigger")
                        .executes(ctx -> sendEmoticon("( ͡ʘ╭͜ʖ╮͡ʘ)"))))
                .then(literal("peepin")
                    .executes(ctx -> sendEmoticon("┴┬┴┤( ͡° ͜ʖ├┬┴┬")))
                .then(literal("flip")
                    .executes(ctx -> sendEmoticon("(ノ͡° ͜ʖ ͡°)ノ︵┻┻"))))
            .then(literal("bird")
                .executes(ctx -> sendEmoticon("(•ө•)"))
                .then(literal("love")
                    .executes(ctx -> sendEmoticon("(•ө•)♡"))))
            .then(literal("goobers")
                .then(literal("gwah")
                    .executes(ctx -> sendEmoticon(" ⊹⋛⋋( ՞ਊ ՞)⋌⋚⊹")))
                .then(literal("noesknows")
                    .executes(ctx -> sendEmoticon("꒡ꆚ꒡")))
                .then(literal("aye_aye_captin")
                    .executes(ctx -> sendEmoticon("(￣-￣)ゞ"))
                    .then(literal("bird_salute")
                        .executes(ctx -> sendEmoticon("（’◇’）ゞ"))))));

    }

    private static int sendEmoticon(String emoticon) {
        MinecraftClient.getInstance().player.sendChatMessage(emoticon);
        return 0;
    }

}

