package net.earthcomputer.clientcommands.features;

import com.google.gson.Gson;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikiRetriever {

    private static final String WIKI_HOST = "https://minecraft.gamepedia.com/";
    private static final String PAGE_SUMMARY_QUERY = WIKI_HOST + "api.php?action=query&prop=extracts&exintro=true&format=json&titles=%s";
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<\\s*(/)?\\s*(\\w+).*?>", Pattern.DOTALL);
    private static final Formatting CODE_COLOR = Formatting.DARK_GREEN;
    private static final Gson GSON = new Gson();

    public static String decode(String html) {
        Matcher matcher = HTML_TAG_PATTERN.matcher(html);
        StringBuffer raw = new StringBuffer();

        boolean bold = false, italic = false, underline = false, code = false;

        // -1 for not in list, 0 for unordered list, >= 1 for ordered list
        int listIndex = -1;

        while (matcher.find()) {
            matcher.appendReplacement(raw, "");

            boolean endTag = matcher.group(1) != null;
            String tagName = matcher.group(2).toLowerCase(Locale.ENGLISH);

            if (!endTag) {
                switch (tagName) {
                    case "b":
                        raw.append(Formatting.BOLD);
                        bold = true;
                        break;
                    case "i":
                        raw.append(Formatting.ITALIC);
                        italic = true;
                        break;
                    case "u":
                    case "dt":
                        raw.append(Formatting.UNDERLINE);
                        underline = true;
                        break;
                    case "code":
                        raw.append(CODE_COLOR);
                        if (bold) raw.append(Formatting.BOLD);
                        if (italic) raw.append(Formatting.ITALIC);
                        if (underline) raw.append(Formatting.UNDERLINE);
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
                        if (code) raw.append(CODE_COLOR);
                        else raw.append(Formatting.RESET);
                        if (italic) raw.append(Formatting.ITALIC);
                        if (underline) raw.append(Formatting.UNDERLINE);
                        bold = false;
                        break;
                    case "i":
                        if (code) raw.append(CODE_COLOR);
                        else raw.append(Formatting.RESET);
                        if (bold) raw.append(Formatting.BOLD);
                        if (underline) raw.append(Formatting.UNDERLINE);
                        italic = false;
                        break;
                    case "dt":
                        raw.append("\n");
                        //fallthrough
                    case "u":
                        if (code) raw.append(CODE_COLOR);
                        else raw.append(Formatting.RESET);
                        if (bold) raw.append(Formatting.BOLD);
                        if (italic) raw.append(Formatting.ITALIC);
                        underline = false;
                        break;
                    case "code":
                        raw.append(Formatting.RESET);
                        if (bold) raw.append(Formatting.BOLD);
                        if (italic) raw.append(Formatting.ITALIC);
                        if (underline) raw.append(Formatting.UNDERLINE);
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

        String rawStr = raw.toString();
        rawStr = rawStr.replace("&quot;", "\"");
        rawStr = rawStr.replace("&#39;", "'");
        rawStr = rawStr.replace("&lt;", "<");
        rawStr = rawStr.replace("&gt;", ">");
        rawStr = rawStr.replace("&amp;", "&");

        return rawStr;
    }

    public static String getWikiSummary(String pageName) {
        URL url;
        try {
            String encodedPage = URLEncoder.encode(pageName, "UTF-8");
            url = new URL(String.format(PAGE_SUMMARY_QUERY, encodedPage));
        } catch (UnsupportedEncodingException | MalformedURLException e) {
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

    private static class QueryResult {
        public String batchcomplete;
        public Query query;
        private static class Query {
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
