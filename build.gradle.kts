plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
}

/** Keep optional engines and applications behind the public core contracts. */
tasks.register("verifyArchitectureBoundaries") {
    group = "verification"
    description = "Reject core-to-engine/app and consumer-to-internal source dependencies."
    val coreSources = fileTree("app/ireadview/src/main") { include("**/*.kt", "**/*.java") }
    val consumerSources = files(
        fileTree("app/src/main") { include("**/*.kt", "**/*.java") },
        fileTree("app/ireadview-epub/src/main") { include("**/*.kt", "**/*.java") },
        fileTree("examples") { include("**/src/main/**/*.kt", "**/src/main/**/*.java"); exclude("**/build/**") }
    )
    inputs.files(coreSources, consumerSources)
    doLast {
        val violations = mutableListOf<String>()
        fun inspect(sources: Iterable<File>, forbidden: Regex, rule: String) {
            sources.forEach { source ->
                source.readLines().forEachIndexed { index, line ->
                    if (forbidden.containsMatchIn(line))
                        violations += "${source.relativeTo(rootDir)}:${index + 1}: $rule"
                }
            }
        }
        inspect(coreSources, Regex("org\\.peyilo\\.(iread\\.demo|ireadview\\.epub)(?:\\.|\\b)"),
            "Core must not reference the Demo or optional EPUB engine")
        inspect(consumerSources, Regex("org\\.peyilo\\.ireadview\\.internal(?:\\.|\\b)"),
            "Consumers must use public API/SPI instead of core internals")
        inspect(coreSources.files + consumerSources.files, Regex("org\\.peyilo\\.ioscurl(?:\\.|\\b)"),
            "Use the published PageCurl Core API instead of the retired source module")
        inspect(consumerSources, Regex("org\\.peyilo\\.pagecurl(?:\\.|\\b)"),
            "Consumers must configure curl through the reader's public effect API")
        val outsideCurlAdapter = coreSources.files.filterNot {
            it.invariantSeparatorsPath.contains("/internal/effect/ioscurl/")
        }
        inspect(outsideCurlAdapter, Regex("org\\.peyilo\\.pagecurl(?:\\.|\\b)"),
            "PageCurl Core types belong only in the iOSCurl adapter")
        inspect(coreSources, Regex("org\\.peyilo\\.pagecurl\\.mesh\\.generation(?:\\.|\\b)"),
            "The adapter must use public PageCurl Core types instead of mesh internals")
        val curlRenderers = coreSources.files.flatMap { source ->
            Regex("\\bclass\\s+(\\w*CurlEffect)\\b").findAll(source.readText()).map { it.groupValues[1] }.toList()
        }.toSet()
        check(curlRenderers == setOf("IosCurlEffect", "GoogleCurlEffect")) {
            "Only IosCurlEffect and GoogleCurlEffect may implement built-in curl rendering: $curlRenderers"
        }
        check(violations.isEmpty()) { violations.joinToString("\n") }
        logger.lifecycle("Architecture boundaries verified: ${coreSources.files.size} core, ${consumerSources.files.size} consumer")
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("verifyArchitectureBoundaries"))
    }
}
