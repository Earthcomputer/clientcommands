package net.earthcomputer.clientcommands.features;

import com.google.gson.Gson;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WikiRetriever {

    private static final String WIKI_HOST = "https://minecraft.wiki/";
    private static final String SEARCH_QUERY = WIKI_HOST + "api.php?action=query&list=search&srlimit=1&srprop=snippet&format=json&srsearch=%s";
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

        if (raw.isEmpty()) {
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
    public static QueryResult getResult(URL url) {
        QueryResult result;
        try (InputStream in = url.openConnection().getInputStream()) {
            result = GSON.fromJson(new InputStreamReader(in), QueryResult.class);
        } catch (IOException e) {
            return null;
        }
        return result;
    }

    @Nullable
    public static URL buildURL(String page, String query) {
        String result = Arrays.stream(page.split("\\s+"))
                .map(w -> w.substring(0, 1).toUpperCase(Locale.ROOT)
                        + w.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));


        URL url;
        try {
            String encodedPage = URLEncoder.encode(result, StandardCharsets.UTF_8);
            url = URI.create(String.format(query, encodedPage)).toURL();
        } catch (MalformedURLException e) {
            return null;
        }
        return url;
    }

    @Nullable
    public static String searchArticleName(String pageInput){
        URL url = buildURL(pageInput.trim(), SEARCH_QUERY);
        if (url == null) {
            return null;
        }

        QueryResult result = getResult(url);
        if (result == null) {
            return null;
        }

        var query = result.query;
        if (query == null) {
            return null;
        }

        var search = query.search;
        var searchinfo = query.searchinfo;
        if (search == null || search.isEmpty() || searchinfo == null || searchinfo.totalhits == 0) {
            return null;
        }

        if (searchinfo.suggestion != null) {
            return searchinfo.suggestion;
        }

        return query.search.getFirst().title;
    }

    @Nullable
    public static String getWikiSummary(String pageName) {
        String title = searchArticleName(pageName);
        if (title == null) {
            return null;
        }

        URL url = buildURL(title, PAGE_SUMMARY_QUERY);
        if (url == null) {
            return null;
        }

        QueryResult result = getResult(url);
        if (result == null) {
            return null;
        }

        if (result.query == null || result.query.pages == null || result.query.pages.isEmpty()) {
            return null;
        }
        var page = result.query.pages.values().iterator().next();
        if (page.missing != null || page.extract == null) {
            return null;
        }
        String html = page.extract;
        return decode(html);
    }

    @SuppressWarnings("unused")
    public static class QueryResult {
        @Nullable
        public String batchcomplete;
        @Nullable
        public Query query;
        public static class Query {
            @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
            @Nullable
            private Map<String, Page> pages;
            private static class Page {
                public int pageid;
                @Nullable
                public String title;
                @Nullable
                public String extract;
                @Nullable
                public String missing;
            }
            @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
            @Nullable
            private List<Search> search;
            private static class Search {
                @Nullable
                public String title;
            }
            @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
            @Nullable
            private SearchInfo searchinfo;
            private static class SearchInfo {
                public int totalhits;
                @Nullable
                public String suggestion;
            }
        }
    }

}
