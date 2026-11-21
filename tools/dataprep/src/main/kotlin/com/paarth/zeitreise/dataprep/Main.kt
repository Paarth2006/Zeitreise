package com.paarth.zeitreise.dataprep

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.system.exitProcess

/** The names that pick a generator on the command line. */
private const val GENERATOR_COUNTRIES = "countries"
private const val GENERATOR_STATES = "states"
private const val GENERATOR_LANDMARKS = "landmarks"
private const val GENERATOR_COUNTRY_FACTS = "facts"

/** Every generator, by the name that runs it. Each writes its files under the directory it's given. */
private val GENERATORS = mapOf(
    GENERATOR_COUNTRIES to ::generateCountries,
    GENERATOR_STATES to ::generateStates,
    GENERATOR_LANDMARKS to ::generateLandmarks,
    GENERATOR_COUNTRY_FACTS to ::generateCountryFacts,
)

/**
 * Entry point of the data preparation tool.
 *
 * The tool downloads the data the app bundles as compose resources, from Natural Earth (country and
 * state outlines) and Wikidata (capitals, flags and landmarks), and trims it to what the app reads.
 * Each generator runs on demand and its output is committed, so the data is prepared once per
 * refresh rather than on every build.
 *
 * Usage: `./gradlew :tools:dataprep:run --args="countries"`, with `states`, `facts` or `landmarks`
 * in place of `countries` for the other generators, or no arguments to run all of them.
 */
fun main(args: Array<String>) {
    val requested = args.toList().ifEmpty { GENERATORS.keys.toList() }
    val unknown = requested - GENERATORS.keys
    if (unknown.isNotEmpty()) {
        println("Unknown generator(s): ${unknown.joinToString()}. Available: ${GENERATORS.keys.joinToString()}")
        exitProcess(1)
    }
    val resources = composeResourcesDirectory()
    requested.forEach { name ->
        println("Running the '$name' generator")
        GENERATORS.getValue(name).invoke(resources)
    }
}

/**
 * Locates `shared/src/commonMain/composeResources/files`, where the generated data is committed.
 *
 * The tool is launched by Gradle from the module directory, so the repository root is resolved
 * by walking up until the shared module is found.
 */
private fun composeResourcesDirectory(): Path {
    val marker = "shared/src/commonMain/composeResources/files"
    var candidate: Path? = Path("").absolute()
    while (candidate != null) {
        val resources = candidate.resolve(marker)
        if (resources.toFile().isDirectory) return resources
        candidate = candidate.parent
    }
    error("Could not find '$marker' above ${Path("").absolute()}; run this tool from inside the repository.")
}
