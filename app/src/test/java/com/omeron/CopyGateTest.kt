package com.omeron

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Fails when interface text breaks the rules in docs/copy.md: implementation words, exclamation
 * marks, "please", "oops" or a bare "Something went wrong". The Gradle working directory of a
 * unit test is the :app module.
 */
class CopyGateTest {

    private data class Finding(val name: String, val text: String, val problem: String)

    private val scannedFiles = listOf(
        "src/main/res/values/strings.xml",
        "src/main/res/values/strings_layout.xml"
    )

    private val bannedImplementationWords = listOf(
        "sessions?", "cookies?", "tokens?", "api", "apis", "scrap(?:e|es|ed|er|ers|ing)",
        "cache", "caches", "cached", "oauth", "http", "https", "endpoints?", "exceptions?",
        "null", "json", "webviews?", "datastore", "threads?", "sources?", "instances?", "teddit"
    )

    private val bannedPhrases = listOf("please", "oops")

    /**
     * Entries are "string name:matched word" mapped to the reason the text may keep the word.
     * Add an entry only when the user must see the word to act on it.
     */
    private val allowList = mapOf(
        "backup_location_explanation_restore_reddit:json" to
            "The Reddit data export contains a file literally named mine.json and the user must pick it."
    )

    @Test
    fun interfaceTextFollowsTheCopyRules() {
        val findings = scannedFiles
            .map { File(it) }
            .filter { it.exists() }
            .flatMap { readStrings(it) }
            .flatMap { (name, text) -> check(name, text) }
            .filterNot { allowList.containsKey("${it.name}:${matchedWord(it)}") }

        val report = findings.joinToString("\n") { "${it.name}: ${it.text}  [${it.problem}]" }
        assertTrue("Copy rules broken:\n$report", findings.isEmpty())
    }

    @Test
    fun everyAllowListEntryStillMatchesAString() {
        val allStrings = scannedFiles
            .map { File(it) }
            .filter { it.exists() }
            .flatMap { readStrings(it) }
            .toMap()
        val stale = allowList.keys.filter { key ->
            val name = key.substringBefore(':')
            val word = key.substringAfter(':')
            allStrings[name]?.contains(word, ignoreCase = true) != true
        }
        assertTrue("Allow-list entries without a matching string: $stale", stale.isEmpty())
    }

    private fun matchedWord(finding: Finding): String =
        finding.problem.substringAfter("'", "").substringBefore("'")

    private fun check(name: String, text: String): List<Finding> {
        val findings = mutableListOf<Finding>()
        for (word in bannedImplementationWords + bannedPhrases) {
            val match = Regex("\\b$word\\b", RegexOption.IGNORE_CASE).find(text)
            if (match != null) {
                findings += Finding(name, text, "banned word '${match.value.lowercase()}'")
            }
        }
        if (text.contains("!")) {
            findings += Finding(name, text, "banned word '!'")
        }
        if (text.trim().trimEnd('.').equals("Something went wrong", ignoreCase = true)) {
            findings += Finding(name, text, "banned word 'something went wrong'")
        }
        return findings
    }

    /** Returns every `<string>` and every `<item>` of a `<plurals>` as name to visible text. */
    private fun readStrings(file: File): List<Pair<String, String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = mutableListOf<Pair<String, String>>()
        val strings = document.getElementsByTagName("string")
        for (index in 0 until strings.length) {
            val element = strings.item(index) as Element
            if (element.getAttribute("translatable") == "false") continue
            result += element.getAttribute("name") to element.textContent
        }
        val items = document.getElementsByTagName("item")
        for (index in 0 until items.length) {
            val element = items.item(index) as Element
            val parent = element.parentNode as Element
            if (parent.tagName != "plurals") continue
            result += "${parent.getAttribute("name")}[${element.getAttribute("quantity")}]" to
                element.textContent
        }
        return result
    }
}
