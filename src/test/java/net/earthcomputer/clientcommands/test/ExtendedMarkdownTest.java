package net.earthcomputer.clientcommands.test;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.command.arguments.ExtendedMarkdownArgument;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

public final class ExtendedMarkdownTest {
    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static void doTest(String markdown, Component expected) throws CommandSyntaxException {
        MutableComponent fromMarkdown = ExtendedMarkdownArgument.extendedMarkdown().parse(new StringReader(markdown));
        assertEquals(expected, fromMarkdown);
    }

    private static void doTestExpectFail(String markdown) {
        assertThrows(CommandSyntaxException.class, () -> ExtendedMarkdownArgument.extendedMarkdown().parse(new StringReader(markdown)));
    }

    @Test
    public void testBold() throws CommandSyntaxException {
        doTest("**bold**", Component.literal("bold").withStyle(ChatFormatting.BOLD));
    }

    @Test
    public void testStarItalic() throws CommandSyntaxException {
        doTest("*italic*", Component.literal("italic").withStyle(ChatFormatting.ITALIC));
    }

    @Test
    public void testUnderscoreItalic() throws CommandSyntaxException {
        doTest("_italic_", Component.literal("italic").withStyle(ChatFormatting.ITALIC));
    }

    @Test
    public void testUnderline() throws CommandSyntaxException {
        doTest("__underline__", Component.literal("underline").withStyle(ChatFormatting.UNDERLINE));
    }

    @Test
    public void testStrikethrough() throws CommandSyntaxException {
        doTest("~~strikethrough~~", Component.literal("strikethrough").withStyle(ChatFormatting.STRIKETHROUGH));
    }

    @Test
    public void testLink() throws CommandSyntaxException {
        doTest("[google](https://google.com/)", Component.literal("google").withStyle(style -> style
            .withColor(ChatFormatting.BLUE)
            .withUnderlined(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://google.com/"))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("https://google.com/")))));
    }

    @Test
    public void testEscape() throws CommandSyntaxException {
        doTest("\\*hello \\_world ! \\\\", Component.literal("*hello _world ! \\"));
    }

    @Test
    public void testColor() throws CommandSyntaxException {
        doTest("red{hello}", Component.literal("hello").withStyle(ChatFormatting.RED));
    }

    @Test
    public void testNestedColor() throws CommandSyntaxException {
        doTest("red{hello blue{world}!}", Component.literal("hello ").withStyle(ChatFormatting.RED).append(Component.literal("world").withStyle(ChatFormatting.BLUE)).append("!"));
    }

    @Test
    public void testHexColor() throws CommandSyntaxException {
        doTest("hex{f00baa, hello}", Component.literal("hello").withColor(0xf00baa));
    }

    @Test
    public void testHover() throws CommandSyntaxException {
        doTest("hover{show_text, sneaky hover text, hello}", Component.literal("hello")
            .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("sneaky hover text")))));
    }

    @Test
    public void testClick() throws CommandSyntaxException {
        doTest("click{open_url, https://google.com/, google}", Component.literal("google")
            .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://google.com/"))));
    }

    @Test
    public void testOtherText() throws CommandSyntaxException {
        doTest("prefix **bold** suffix", Component.literal("prefix ").append(Component.literal("bold").withStyle(ChatFormatting.BOLD)).append(" suffix"));
    }

    @Test
    public void testBoldAndItalic() throws CommandSyntaxException {
        doTest("***bold and italic***", Component.literal("bold and italic").withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.ITALIC));
    }

    @Test
    public void testQuadrupleStar() throws CommandSyntaxException {
        doTest("****", Component.empty().withStyle(ChatFormatting.BOLD));
    }

    @Test
    public void testUnclosed() {
        doTestExpectFail("*hello");
    }

    @Test
    public void testInvalidIntermixing() {
        doTestExpectFail("*hello _world*_");
    }

    @Test
    public void testMissingArgument() {
        doTestExpectFail("hex{hello}");
    }

    @Test
    public void testInvalidHexNumber() {
        doTestExpectFail("hex{g, hello}");
    }

    @EnabledIfSystemProperty(named = "clientcommands.fuzzExtendedMarkdown", matches = "true")
    @FuzzTest
    public void fuzzExtendedMarkdown(FuzzedDataProvider data) {
        String markdown = data.consumeRemainingAsString();
        try {
            ExtendedMarkdownArgument.extendedMarkdown().parse(new StringReader(markdown));
        } catch (CommandSyntaxException ignore) {
            // we're trying to detect crashes here, not invalid markdown
        }
    }
}
