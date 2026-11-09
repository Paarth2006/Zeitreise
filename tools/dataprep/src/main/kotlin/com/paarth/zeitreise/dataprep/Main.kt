package com.paarth.zeitreise.dataprep

/**
 * Entry point of the data preparation tool.
 *
 * The tool downloads and simplifies the geographic source data that the app bundles as compose
 * resources. Each generator is added as the data it produces becomes part of the app, and its
 * output is committed, so the tool runs once per data refresh rather than as part of the build.
 */
fun main() {
    println("zeitreise-dataprep: no generators are registered yet.")
}
