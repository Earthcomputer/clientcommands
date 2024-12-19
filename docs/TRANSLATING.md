# Translating

## How to translate
### General guidelines
Translation is an art, not a science, and sometimes there is not a word-for-word translation that feels natural in the
target language. Your goal is to translate the *meaning* of the phrase, not the *words*. Sometimes significant
rephrasing is required to preserve the same meaning of a sentence in the target language, and that's okay.

### String substitutions
A string substitution is a placeholder that will be replaced by a number or another string when the user sees it. In the
English, substitutions will always look like `%s`. For example, the `/cfinditem` success translation reads
`Found %s at %s, %s blocks away`, which might show up to the user as
`Found minecraft:stone at (45, 87, 24) [Glow], 25.47 blocks away`. If the substitutions in your translation appear in
the same order as in the English, you can use `%s`. If you want substitutions to appear in a different order than in the
English, then you need to use `%1$s`, `%2$s`, `%3$s` etc, where the numbers are referring to which substitution it is. 
For example, the simplified Chinese translation for `/cfinditem` success is `在%2$s找到%1$s, 距离你%3$s格`, which might
show up to the user as `在(45, 87, 24) [发光]找到minecraft:stone, 距离你25.47格` (notice how the coordinates and the item
type are the other way round from in English).

### Minecraft terminology

Try to look for examples of Minecraft-specific terminology (such as "item", "entity", "creeper" etc) in the
[official Minecraft translations](https://crowdin.com/project/minecraft) (requires an account to view, or you can try
to find them in-game). If it exists then use that. For example, in German, "item" is consistently translated as
"Gegenstand" in Minecraft, so always use "Gegenstand" in German to be consistent with the rest of Minecraft.

### Computer terminology

If you're looking for technical terminology that's not specific to Minecraft but is related to computers, or
programming, you can look at
[Microsoft Terminology](https://learn.microsoft.com/en-us/globalization/reference/microsoft-terminology) to see if there
is any precedent for translating this term in any of Microsoft's programs.

### Figuring out context

Often the way you translate a phrase depends on the context in which that phrase is used. Here are some ways you can
find out the context of a translation:

#### String instructions
When we switch to a translation service, some particularly difficult translations have instructions on how to translate
them.

#### Translation key
Often the clue you need is found in the translation key, which is how the translation is referenced in the code. In the
example below, the translation key for `Alias "%s" not found` is `commands.calias.notFound`, which indicates that this
message may appear after running the `/calias` command. Once you know what the `/calias` command does, you know what
sense of the word "alias" you're translating which should make things easier (if you're still stuck, the Microsoft
Terminology Search linked above will help with this example).

#### Read the code
If you're able to read Java code, you can find where the translation key is used in the clientcommands codebase to
figure out the context. Don't worry if you're not able to do this.

#### Ask in Discord
If you're still stuck on what the context is, you can ask in the `clientcommands-dev` channel on
[Discord](https://discord.gg/Jg7Bun7). We'll explain the context needed to translate the string. We may also add a
string instruction to help others with this translation in the future.

### Plurals
In languages with plurals, when there is an unknown number of a particular noun, always use the most general form of the
noun that will apply to most circumstances. Do not attempt to account for other forms of the noun such as the singular,
dual, etc.

For example, it should be `%s blocks away` rather than `%s block(s) away`, and `%s Blöcke entfernt` in German rather
than `%s Block/Blöcke entfernt`, even though the user may end up seeing `1 blocks away`.

I understand that this differs from the official Minecraft translations, at least for English. However, for languages
with more complicated plurals this can end up creating a mess which is harder to read than if the grammar can be
sometimes incorrect but still understandable. This is the compromise I have decided to go for until Mojang adds a better
way to handle plurals.

## Requesting new languages
If your language isn't in the list of target languages for clientcommands, you can request for it to be added via
[Discord](https://discord.gg/Jg7Bun7) or via [GitHub issues](https://github.com/Earthcomputer/clientcommands/issues/).
A language is eligible to be added if it is supported by Minecraft. All eligible requests will be accepted.

## Becoming a reviewer
You can ask in Discord to become a reviewer, which allows you to verify translations as correct and appropriate. You
should be able to become a reviewer if you're a trusted member of the community or you have a history of providing good
translations.
