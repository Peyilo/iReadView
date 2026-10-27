plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
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
    val curlSources = fileTree("app/pagecurl-core/src/main") { include("**/*.kt", "**/*.java") }
    inputs.files(coreSources, consumerSources, curlSources)
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
        inspect(curlSources, Regex("import\\s+(android|androidx|java|javax|org\\.peyilo\\.ireadview|org\\.peyilo\\.iread\\.demo)(?:\\.|\\b)"),
            "Portable curl algorithms must not depend on platform, Widget, EPUB or Demo types")
        inspect(curlSources, Regex("System\\.loadLibrary|external\\s+fun"),
            "Portable curl algorithms must not load or invoke native code")
        val curlRenderers = coreSources.files.flatMap { source ->
            Regex("\\bclass\\s+(\\w*CurlEffect)\\b").findAll(source.readText()).map { it.groupValues[1] }.toList()
        }.toSet()
        check(curlRenderers == setOf("IosCurlEffect", "GoogleCurlEffect")) {
            "Only IosCurlEffect and GoogleCurlEffect may implement built-in curl rendering: $curlRenderers"
        }
        check(violations.isEmpty()) { violations.joinToString("\n") }
        logger.lifecycle("Architecture boundaries verified: ${coreSources.files.size} core, ${consumerSources.files.size} consumer and ${curlSources.files.size} portable curl source files")
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("verifyArchitectureBoundaries"))
    }
}
