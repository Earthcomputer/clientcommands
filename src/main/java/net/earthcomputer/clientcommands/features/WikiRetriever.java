package net.earthcomputer.clientcommands.features;

import com.google.gson.Gson;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikiRetriever {

    private static final String WIKI_HOST = "https://minecraft.wiki/";
    private static final String PAGE_SUMMARY_QUERY = WIKI_HOST + "api.php?action=query&prop=extracts&exintro=true&format=json&titles=%s";
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<\\s*(/)?\\s*(\\w+).*?>|<!--.*?-->|\n", Pattern.DOTALL);
    private static final ChatFormatting CODE_COLOR = ChatFormatting.DARK_GREEN;
    private static final Gson GSON = new Gson();

    public static String decode(String html) {
        Matcher matcher = HTML_TAG_PATTERN.matcher(html);
        StringBuilder raw = new StringBuilder();

        boolean bold = false, italic = false, underline = false, code = false;

        // -1 for not in list, 0 for unordered list, >= 1 for ordered list
        int listIndex = -1;

        while (matcher.find()) {
            matcher.appendReplacement(raw, "");

            boolean endTag = matcher.group(1) != null;
            String tagName = matcher.group(2);
            if (tagName == null) {
                // we're in a comment or newline
                continue;
            }
            tagName = tagName.toLowerCase(Locale.ROOT);

            if (!endTag) {
                switch (tagName) {
                    case "b":
                        raw.append(ChatFormatting.BOLD);
                        bold = true;
                        break;
                    case "i":
                        raw.append(ChatFormatting.ITALIC);
                        italic = true;
                        break;
                    case "u":
                    case "dt":
                        raw.append(ChatFormatting.UNDERLINE);
                        underline = true;
                        break;
                    case "code":
                        raw.append(CODE_COLOR);
                        if (bold) {
                            raw.append(ChatFormatting.BOLD);
                        }
                        if (italic) {
                            raw.append(ChatFormatting.ITALIC);
                        }
                        if (underline) {
                            raw.append(ChatFormatting.UNDERLINE);
                        }
                        code = true;
                        break;
                    case "dd":
                        raw.append("  ");
                        break;
                    case "ul":
                        listIndex = 0;
                        break;
                    case "ol":
                        listIndex = 1;
                        break;
                    case "li":
                        if (listIndex >= 1) {
                            raw.append("  ").append(listIndex).append(". ");
                            listIndex++;
                        } else {
                            raw.append("  \u2022");
                        }
                        break;
                    case "br":
                        raw.append("\n");
                }
            } else {
                switch (tagName) {
                    case "b":
                        if (code) {
                            raw.append(CODE_COLOR);
                        } else {
                            raw.append(ChatFormatting.RESET);
                        }
                        if (italic) {
                            raw.append(ChatFormatting.ITALIC);
                        }
                        if (underline) {
                            raw.append(ChatFormatting.UNDERLINE);
                        }
                        bold = false;
                        break;
                    case "i":
                        if (code) {
                            raw.append(CODE_COLOR);
                        } else {
                            raw.append(ChatFormatting.RESET);
                        }
                        if (bold) {
                            raw.append(ChatFormatting.BOLD);
                        }
                        if (underline) {
                            raw.append(ChatFormatting.UNDERLINE);
                        }
                        italic = false;
                        break;
                    case "dt":
                        raw.append("\n");
                        //fallthrough
                    case "u":
                        if (code) {
                            raw.append(CODE_COLOR);
                        } else {
                            raw.append(ChatFormatting.RESET);
                        }
                        if (bold) {
                            raw.append(ChatFormatting.BOLD);
                        }
                        if (italic) {
                            raw.append(ChatFormatting.ITALIC);
                        }
                        underline = false;
                        break;
                    case "code":
                        raw.append(ChatFormatting.RESET);
                        if (bold) {
                            raw.append(ChatFormatting.BOLD);
                        }
                        if (italic) {
                            raw.append(ChatFormatting.ITALIC);
                        }
                        if (underline) {
                            raw.append(ChatFormatting.UNDERLINE);
                        }
                        code = false;
                        break;
                    case "ul":
                    case "ol":
                        listIndex = -1;
                        break;
                    case "dd":
                    case "li":
                    case "br":
                    case "p":
                        raw.append("\n");
                        break;
                }
            }
        }
        matcher.appendTail(raw);

        if (raw.length() == 0) {
            return ChatFormatting.ITALIC + I18n.get("commands.cwiki.noContent");
        }

        String rawStr = raw.toString();
        rawStr = rawStr.replace("&quot;", "\"");
        rawStr = rawStr.replace("&#39;", "'");
        rawStr = rawStr.replace("&lt;", "<");
        rawStr = rawStr.replace("&gt;", ">");
        rawStr = rawStr.replace("&amp;", "&");

        return rawStr;
    }

    @Nullable
    public static String getWikiSummary(String pageName) {
        URL url;
        try {
            String encodedPage = URLEncoder.encode(pageName, StandardCharsets.UTF_8);
            url = new URL(String.format(PAGE_SUMMARY_QUERY, encodedPage));
        } catch (MalformedURLException e) {
            return null;
        }

        QueryResult result;
        try (InputStream in = url.openConnection().getInputStream()) {
            result = GSON.fromJson(new InputStreamReader(in), QueryResult.class);
        } catch (IOException e) {
            return null;
        }

        if (result.query.pages.isEmpty())
            return null;
        var page = result.query.pages.values().iterator().next();
        if (page.missing != null || page.extract == null)
            return null;
        String html = page.extract;
        return decode(html);
    }

    @SuppressWarnings("unused")
    private static class QueryResult {
        public String batchcomplete;
        public Query query;
        private static class Query {
            @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
            private Map<String, Page> pages;
            private static class Page {
                public int pageid;
                public String title;
                public String extract;
                public String missing;
            }
        }
    }

}
