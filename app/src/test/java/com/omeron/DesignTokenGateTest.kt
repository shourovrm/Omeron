package com.omeron

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Enforces docs/design-system.md on the XML resources. Layouts take sizes from dimens.xml, type from
 * the text roles in styles.xml and every visible string from strings.xml. The Gradle unit test task
 * runs with the app module as working directory, so the paths below are relative to it.
 */
class DesignTokenGateTest {

    private val layoutDirectory = File("src/main/res/layout")
    private val valuesDirectory = File("src/main/res/values")

    // Nested units the spec allows inside a component.
    private val allowedRawDpValues = setOf("0", "1", "2", "4", "6", "10")

    private val dpLiteral = Regex("\"(-?[0-9]+(?:\\.[0-9]+)?)dp\"")
    private val spLiteral = Regex("\"-?[0-9]+(?:\\.[0-9]+)?sp\"")
    private val colorLiteral = Regex("\"#[0-9A-Fa-f]{3,8}\"")
    private val textLiteral = Regex("android:(?:text|hint)=\"([^\"]*)\"")
    private val styleStart = Regex("<style\\s+name=\"([^\"]+)\"")
    private val textSizeItem = Regex("<item\\s+name=\"(?:android:)?textSize\"")

    @Test
    fun layoutsUseOnlyAllowedDpLiterals() {
        val violations = scanLayouts { line ->
            dpLiteral.findAll(line)
                .map { match -> match.value }
                .filter { literal -> literal.removeSurrounding("\"").removeSuffix("dp") !in allowedRawDpValues }
                .toList()
        }
        assertNoViolations("dp literal other than 0, 1, 2, 4, 6, 10", violations)
    }

    @Test
    fun layoutsUseNoSpLiterals() {
        val violations = scanLayouts { line -> spLiteral.findAll(line).map { it.value }.toList() }
        assertNoViolations("sp literal", violations)
    }

    @Test
    fun layoutsUseNoColorLiterals() {
        val violations = scanLayouts { line -> colorLiteral.findAll(line).map { it.value }.toList() }
        assertNoViolations("# colour literal", violations)
    }

    @Test
    fun layoutsReferenceStringsForTextAndHint() {
        val violations = scanLayouts { line ->
            textLiteral.findAll(line)
                .map { match -> match.groupValues[1] }
                // A data binding expression computes the text, so it is not a literal.
                .filter { value -> !value.startsWith("@string/") && !value.startsWith("@{") }
                .toList()
        }
        assertNoViolations("android:text or android:hint that is not a @string reference", violations)
    }

    @Test
    fun styleFilesSetTextSizeOnlyInTextRoles() {
        val violations = mutableListOf<String>()
        val styleFiles = valuesDirectory.listFiles { file -> file.name.matches(Regex("styles.*\\.xml")) }
            ?: error("Cannot list ${valuesDirectory.absolutePath}")
        for (styleFile in styleFiles.sortedBy { it.name }) {
            var currentStyleName = ""
            styleFile.readLines().forEachIndexed { index, line ->
                styleStart.find(line)?.let { currentStyleName = it.groupValues[1] }
                if (textSizeItem.containsMatchIn(line) && !currentStyleName.startsWith("TextAppearance.App.")) {
                    violations.add("${styleFile.name}:${index + 1}: textSize in $currentStyleName")
                }
            }
        }
        assertNoViolations("textSize outside the TextAppearance.App roles", violations)
    }

    private fun scanLayouts(findViolations: (String) -> List<String>): List<String> {
        assertTrue("Missing ${layoutDirectory.absolutePath}", layoutDirectory.isDirectory)
        val violations = mutableListOf<String>()
        layoutDirectory.walkTopDown()
            .filter { file -> file.isFile && file.extension == "xml" }
            .sortedBy { file -> file.path }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    findViolations(line).forEach { value ->
                        violations.add("${file.path}:${index + 1}: $value")
                    }
                }
            }
        return violations
    }

    private fun assertNoViolations(rule: String, violations: List<String>) {
        assertTrue(
            "${violations.size} violations of '$rule':\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}
