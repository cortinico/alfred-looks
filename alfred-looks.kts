#!/usr/bin/env kscript
@file:DependsOn("com.esotericsoftware.yamlbeans:yamlbeans:1.13")
@file:DependsOn("com.squareup.moshi:moshi-kotlin:1.9.2")
@file:DependsOn("info.picocli:picocli:4.3.2")

import com.esotericsoftware.yamlbeans.YamlReader
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.util.UUID
import java.util.concurrent.Callable
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


@Command(name = "alfred-looks", mixinStandardHelpOptions = true, version = ["1.0"])
class GenerateSnippets : Callable<Int> {

    @Option(names = ["-i", "--input"], paramLabel = "INPUT", description = ["The input .yml file to read from"])
    private var INPUT_FILE: String = "./looks.wtf/looks.yml"

    @Option(names = ["-o", "--output"], paramLabel = "OUTPUT", description = ["The output .alfredsnippets file to generate"])
    private var OUTPUT_FILE: String = "./looks-wtf.alfredsnippets"

    @Option(names = ["--icon"], paramLabel = "ICON", description = ["The icon to bundle together with the snippets"])
    private var ICON_FILE: String = "./icon.png"

    @Option(names = ["--prefix"], paramLabel = "PREFIX", description = ["The prefix to be used for keywords"])
    private var PREFIX: String = "!"

    @Option(names = ["--postfix"], paramLabel = "POSTFIX", description = ["The postfix to be used for keywords"])
    private var POSTFIX: String = "!"

    @Option(names = ["--space"], paramLabel = "SPACE", description = ["The character used to replace spaces in keywords"])
    private var SPACE: String = "_"

    override fun call(): Int {
        info("🎩🎩🎩🎩🎩 alfred-looks 🎩🎩🎩🎩🎩", "")
        info("Welcome to alfred-looks", "👋")
        info("I'm going to convert an input file from looks.wtf", "")
        info("to an Alfred 4 snippet file.", "")
        info("Input file is ${INPUT_FILE}", "")
        info("Output file is ${OUTPUT_FILE}", "")
        info("Keywork prefix is `${PREFIX}`", "")
        info("Keywork postfix is `${POSTFIX}`", "")
        info("Keywork space replace with `${SPACE}`", "")


        val reader: YamlReader = runCatching {
            YamlReader(FileReader(INPUT_FILE))
        }.getOrElse { error("Impossible to read from: $INPUT_FILE!", it) }

        val result: Any? = runCatching {
            reader.read()
        }.getOrElse { error("Invalid YAML in: $INPUT_FILE!", it) }

        val listResult = (result as List<Any?>).also {
            info("Found ${it.size} emojis")
        }

        val looks = listResult
                .filterIsInstance<Map<String, *>>()
                .map {
                    Look(
                            plain = (it["plain"] as? String),
                            title = (it["title"] as? String),
                            tags = (it["tags"] as? String)
                                    ?.split(" ")
                                    ?.filter { it.isNotBlank() }
                                    ?.filterNot { it == "all" } ?: emptyList()
                    )
                }.also { info("Successfully parsed ${it.size} emojis") }

        val snippets = looks.map {
            Snippet(
                    snippet = it.plain ?: error("Found look with missing snippet $it"),
                    name = it.generateName()
            )
        }.toMutableList().also { info("Generated ${it.size} alfred snippets") }

        snippets.groupBy { it.name }
                .filter { it.value.size > 1 }
                .onEach {
                    it.value.mapIndexed { index, snippet ->
                        snippet.name += index
                    }
                }.also {
                    info("Resolved duplicates for ${it.size} clashing names")
                }

        snippets.onEach {
            it.keyword = it.generateKeyword(PREFIX, POSTFIX, SPACE)
        }.also {
            info("Generating ${PREFIX}unique${SPACE}keywords${POSTFIX}")
        }

        snippets.apply {
            add(Snippet("twitter.com/cortinico", "# by Nicola Corti", "#see_you_on_twitter#"))
            add(Snippet("github.com/cortinico", "# see you on Github", "#see_you_on_github#"))
            add(Snippet("https://ncorti.com", "# check out my stuff", "#see_you_on_the_web#"))
        }

        val alfredSnippet = snippets.map { AlfredSnippet(it) }

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        var adapter: JsonAdapter<AlfredSnippet> = moshi.adapter(AlfredSnippet::class.java)

        val bos: BufferedOutputStream = runCatching {
            BufferedOutputStream(FileOutputStream(OUTPUT_FILE))
        }.getOrElse { error("Impossible to write on $OUTPUT_FILE!", it) }

        ZipOutputStream(bos).use { zip ->
            alfredSnippet.forEach { snippet ->
                ZipEntry(snippet.filename).runCatching {
                    zip.putNextEntry(this)
                    zip.write(adapter.toJson(snippet).toByteArray())
                    zip.closeEntry()
                }.onFailure {
                    error("Failed to zip ${snippet.filename}!", it)
                }
                info("Zipping ${snippet.filename}...", "✍️")
            }
            ZipEntry(ICON_FILE).apply {
                zip.putNextEntry(this)

                val fis : FileInputStream = runCatching {
                    FileInputStream(File(ICON_FILE))
                }.getOrElse { error("Impossible to read from Icon file ${ICON_FILE}!", it) }

                FileInputStream(File(ICON_FILE)).use { fi ->
                    BufferedInputStream(fi).use { origin ->
                        origin.copyTo(zip, 1024)
                    }
                }
                zip.closeEntry()
                info("Bundling ${ICON_FILE}...", "🖼")
            }
        }
        succ("Bundling finished successfully!")
        info("You can now open your snippet bundle with:", "")
        info("\n\topen $OUTPUT_FILE\n", "")
        return 0
    }


    /*
     * DEBUG Prints
     ******************************************************************/

    fun error(message: String, throwable: Throwable? = null, statusCode: Int = 1): Nothing {
        System.err.println("❌\t${Colors.ANSI_RED}$message${Colors.ANSI_RESET}")
        throwable?.let {
            System.err.print(Colors.ANSI_RED)
            it.printStackTrace()
            System.err.print(Colors.ANSI_RESET)

        }
        System.exit(statusCode)
        throw Error()
    }

    fun warn(message: String) {
        System.out.println("⚠️\t${Colors.ANSI_YELLOW}$message${Colors.ANSI_RESET}")
    }

    fun succ(message: String) {
        System.out.println("✅\t${Colors.ANSI_GREEN}$message${Colors.ANSI_RESET}")
    }

    fun info(message: String, emoji: String = "ℹ️") {
        System.out.println("$emoji\t$message")
    }
}

CommandLine(GenerateSnippets()).execute(*args)

/*
 * Data Classes
 ************************************************************************************/

data class Look(
        val plain: String?,
        val title: String?,
        val tags: List<String> = emptyList()
) {
    fun generateName(): String {
        if (title != null) return title
        if (tags.isEmpty()) return "noname"
        return tags.joinToString("_") {
            it.trim()
        }
    }
}

data class AlfredSnippet(val alfredsnippet: Snippet) {
    val filename: String
        get() = "${alfredsnippet.name?.replace(" ", "-")}-[${alfredsnippet.uuid}].json"
}

data class Snippet(
        val snippet: String,
        var name: String? = null,
        var keyword: String? = null,
        val dontautoexpand: Boolean = true,
        val uuid: String = UUID.randomUUID().toString()
) {
    fun generateKeyword(prefix: String, postfix: String, spaceReplace: String): String {
        val keyworkInner = name?.toLowerCase()?.replace(" ", spaceReplace)
        return "${prefix}$keyworkInner${postfix}"
    }
}


/*
 * ASCII Color
 ******************************************************************/

object Colors {
    val ANSI_RESET = "\u001B[0m"
    val ANSI_BLACK = "\u001B[30m"
    val ANSI_RED = "\u001B[31m"
    val ANSI_GREEN = "\u001B[32m"
    val ANSI_YELLOW = "\u001B[33m"
    val ANSI_BLUE = "\u001B[34m"
    val ANSI_PURPLE = "\u001B[35m"
    val ANSI_CYAN = "\u001B[36m"
    val ANSI_WHITE = "\u001B[37m"
}