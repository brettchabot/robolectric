import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

class DecorateJavadocTask extends DefaultTask {
    @InputDirectory
    def File inputDir

    @OutputDirectory
    def File outputDir

    def File originalJavadoc

    @TaskAction
    public void execute(IncrementalTaskInputs inputs) throws Exception {
        println "Executing DecorateJavadocTask!"

        final Path srcBase = originalJavadoc.toPath()
        final destBase = outputDir.toPath()
        println "destBase = $destBase"

        final filesInSrc = filesIn(srcBase)
        final filesInDest = filesIn(destBase)

        final changedClasses = new HashMap<String, ClassDesc>()

        if (!inputs.incremental) {
            def inputBase = inputDir.toPath()
            def filesInInput = filesIn(inputBase)
            for (Path path : filesInInput) {
                def className = path.toString().replace(".json", "")
                def desc = getClassDesc(path)
                changedClasses.put(className, desc)
            }
        } else {
            inputs.outOfDate { change ->
                println "update = $change.file"
                def className = change.file.name.replace(".json", "")
                def path = change.file.toPath()
                def desc = getClassDesc(path)
                changedClasses.put(className, desc)
                println "change = $change.file"
            }

            inputs.removed { change ->
                def className = change.file.name.replace(".json", "")
                changedClasses.put(className, new ClassDesc())
                println "remove = $change.file"
            }
        }

        println "filesInSrc = ${filesInSrc.size()}"
        println "filesInDest = ${filesInDest.size()}"

        for (Path relPath : filesInSrc) {
//            println "relPath = $relPath"
            def classDesc = null
            if (relPath.startsWith("reference")) {
                def className = relPath.subpath(1, relPath.nameCount - 1).toString().replaceAll(".html\$", "").replace("/", ".")
                classDesc = changedClasses.get(className)
            }

            def srcPath = srcBase.resolve(relPath)
            def destPath = destBase.resolve(relPath)

            if (classDesc == null) {
                if (!filesInDest.contains(relPath)) {
                    mkdir(destPath)
                    Files.copy(srcPath, destPath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
                    println "cp $srcPath -> $destPath"
                }
            } else {
                println "Regenerate ${relPath}!"
            }
        }

        def filesToDeleteFromDest = new HashSet<Path>(filesInDest)
        filesToDeleteFromDest.removeAll(filesInSrc)

        for (Path path : filesToDeleteFromDest) {
            def destPath = destBase.resolve(path)
            println "rm $destPath"
            Files.delete(destPath)
        }

        throw new RuntimeException()
    }

    private static void mkdir(Path destPath) {
        if (!Files.isDirectory(destPath.parent)) {
            Files.createDirectories(destPath.parent)
        }
    }

    private Set<Path> filesIn(Path path) {
        final Set<Path> filesInSrc = new HashSet<>()
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                filesInSrc.add(path.relativize(file))
                return super.visitFile(file, attrs)
            }
        })
        return filesInSrc
    }

    ClassDesc getClassDesc(Path jsonPath) throws FileNotFoundException {
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

        public ClassDesc() {
            this.map = new JsonObject();
        }

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

}
