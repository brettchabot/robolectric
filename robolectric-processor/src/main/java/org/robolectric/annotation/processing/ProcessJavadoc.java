package org.robolectric.annotation.processing;

import ch.raffael.doclets.pegdown.Options;
import ch.raffael.doclets.pegdown.SimpleTagRenderer;
import ch.raffael.doclets.pegdown.Tags;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.sun.javadoc.Doc;
import com.sun.javadoc.SourcePosition;
import com.sun.javadoc.Tag;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.pegdown.PegDownProcessor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcessJavadoc {
  public static void main(String[] args) {
    new ProcessJavadoc().run();
  }

  private void run() {
    final Path srcBase = FileSystems.getDefault().getPath("/usr/local/google/home/christianw/Android/Sdk/docs/reference");
    final Path destBase = FileSystems.getDefault().getPath("/usr/local/google/home/christianw/docs-out/reference");
    final Path jsonBase = FileSystems.getDefault().getPath("/usr/local/google/home/christianw/Dev/robolectric/docs");
    try {
      Files.walkFileTree(srcBase, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
          Path relPath = srcBase.relativize(path);
          if (!path.toString().contains("MediaPlayer")) {
            return FileVisitResult.CONTINUE;
          }

          if (relPath.getFileName().toString().matches("[A-Z].*\\.html")) {
            String className = relPath.toString().replace(".html", "").replace("/", ".");
            System.out.println(className + " looks like a class");

            Path jsonPath = jsonBase.resolve(className + ".json");
            ClassDesc classDesc = getClassDesc(jsonPath);

            Document document = Jsoup.parse(path.toFile(), "utf8");

            document.select("head").first().children().last().after("<style>.robolectric {" +
                "  border: 3px solid #7FC06B;\n" +
                "  padding: 1em;\n" +
                "}</style>");

            if (classDesc != null) {
              String doc = md(classDesc.getDoc());
              if (doc != null) {
                document.select(".api-section").eq(0).before("<div class=\"robolectric type\">" + doc + "</div>");
              }
            }

            for (Element element : document.select("div.api pre.api-signature")) {
              Element probablyAnchor = element.parent().previousElementSibling();
              String s = probablyAnchor.tagName();
              if ("a".equals(s) || "A".equals(s)) {
                String name = probablyAnchor.attr("name");
                name = name.replaceAll(" ", "");
                MethodDesc methodDesc = classDesc.getMethod(name);
                String doc = md(methodDesc.getDoc());
                if (doc != null) {
                  element.parent().after("<div class=\"robolectric method\">" + doc + "</div>");
                }
                System.out.println("name = " + name);
              }
            }

            Path destPath = destBase.resolve(relPath);
            Files.createDirectories(destPath.getParent());
            Writer out = new PrintWriter(new BufferedWriter(new FileWriter(destPath.toFile())));
            out.append(document.html());
          }

          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private ClassDesc getClassDesc(Path jsonPath) throws FileNotFoundException {
    if (Files.exists(jsonPath)) {
      JsonObject map;
      JsonReader jsonReader = new JsonReader(new BufferedReader(new FileReader(jsonPath.toFile())));
      map = new Gson().fromJson(jsonReader, JsonObject.class);
      System.out.println("map = " + map);
      return new ClassDesc(map);
    } else {
      return new ClassDesc(new JsonObject());
    }
  }

  private static class ClassDesc {
    private final JsonObject map;

    public ClassDesc(JsonObject map) {
      this.map = map;
    }

    public String getDoc() {
      JsonElement doc = map.get("doc");
      return doc == null ? null : doc.getAsString();
    }

    public MethodDesc getMethod(String desc) {
      JsonObject methods = map.getAsJsonObject("methods");
      JsonObject methodMap = methods == null ? new JsonObject() : methods.getAsJsonObject(desc);
      return new MethodDesc(methodMap == null ? new JsonObject() : methodMap);
    }
  }

  private static class MethodDesc {
    private JsonObject map;

    public MethodDesc(JsonObject map) {
      this.map = map;
    }

    public String getDoc() {
      JsonElement doc = map.get("doc");
      return doc == null ? null : doc.getAsString();
    }
  }

  String md(String input) {
    if (input == null) {
      return null;
    }

    String html = new Options() {
      private final Pattern LINE_START = Pattern.compile("^ ", Pattern.MULTILINE);
      private final Pattern LINK_RE = Pattern.compile("\\{@link\\s+([a-zA-Z0-9.#()<>]+)(?:\\s+(.+))?\\}", Pattern.MULTILINE);
      private final Pattern TAG_RE = Pattern.compile("^@([a-z]+)\\s*(.*)$", Pattern.MULTILINE);
      private PegDownProcessor processor;

      @Override
      public String toHtml(String markup, boolean fixLeadingSpaces) {
        if (processor == null) {
          processor = createProcessor();
        }
        if (fixLeadingSpaces) {
          markup = LINE_START.matcher(markup).replaceAll("");
        }

        StringBuilder buf = new StringBuilder();
        String tag = null;
        Map<String, List<StringBuilder>> tags = new HashMap<>();
        StringBuilder tagBuf = null;

        for (String line : markup.split("\n")) {
          Matcher tagMatcher = TAG_RE.matcher(line);
          if (tagMatcher.find()) {
            tag = tagMatcher.group(1);
            tagBuf = new StringBuilder();
            List<StringBuilder> tagList = tags.get(tag);
            if (tagList == null) {
              tagList = new ArrayList<>();
              tags.put(tag, tagList);
            }
            tagList.add(tagBuf);
            tagBuf.append(tagMatcher.group(2));
          } else {
            if (tag != null) {
              tagBuf.append("\n");
              tagBuf.append(line);
            } else {
              buf.append("\n");
              buf.append(line);
            }
          }
        }

        List<String> inlineTags = new ArrayList<>();
        String html = createDocletSerializer().toHtml(processor.parseMarkdown(Tags.extractInlineTags(buf.toString(), inlineTags).toCharArray()));

        List<String> processedTags = new ArrayList<String>();
        for (String inlineTag : inlineTags) {
          Matcher matcher = LINK_RE.matcher(inlineTag);
          if (matcher.find()) {
            String symbol = matcher.group(1);
            String display = matcher.group(2);
            display = display == null ? symbol : display;
            System.out.println("symbol = " + symbol);
            System.out.println("display = " + display);
            inlineTag = "<a href=\"" + symbol + "\">" + display + "</a>";
          }
          processedTags.add(inlineTag);
        }
        System.out.println("tags = " + inlineTags);

        StringBuilder out = new StringBuilder();
        out.append(Tags.insertInlineTags(html, processedTags));
        out.append("\n");
        for (final Map.Entry<String, List<StringBuilder>> entry : tags.entrySet()) {
          final String tagName = entry.getKey();
          String value = entry.getValue().toString();
          out.append("<b>" + tagName + ":</b> " + value);
        }
        return out.toString();
      }
    }.toHtml(input);
    System.out.println("html = " + html);
    return html;
  }
}
