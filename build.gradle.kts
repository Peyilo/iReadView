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
        check(violations.isEmpty()) { violations.joinToString("\n") }
        logger.lifecycle("Architecture boundaries verified: ${coreSources.files.size} core and ${consumerSources.files.size} consumer source files")
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("verifyArchitectureBoundaries"))
    }
}
