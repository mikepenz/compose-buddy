package dev.mikepenz.composebuddy.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import java.io.File

/**
 * Installs compose-buddy skills into AI coding agent systems.
 *
 * Systems that support the Agent Skills standard (SKILL.md):
 * - claude:    .claude/skills/<name>/SKILL.md
 * - gemini:    .gemini/skills/<name>/SKILL.md
 * - copilot:   .github/skills/<name>/SKILL.md
 * - cursor:    .cursor/skills/<name>/SKILL.md
 * - windsurf:  .windsurf/skills/<name>/SKILL.md
 * - cline:     .cline/skills/<name>/SKILL.md
 *
 * Systems with rules-only (no skills support):
 * - junie:     .junie/guidelines/<name>.md
 */
class InstallCommand : CliktCommand(name = "install") {
    override fun help(context: Context) =
        "Install compose-buddy skills into AI coding agents (claude, gemini, copilot, cursor, windsurf, cline, junie)"

    private val system by option("--system", "-s",
        help = "Target: claude, gemini, copilot, cursor, windsurf, cline, junie, or 'all'")
        .default("all")

    private val project by option("--project", help = "Project directory to install into")
        .file(canBeFile = false)
        .default(File("."))

    private val global by option("--global", help = "Install globally (user-level) instead of project-level")
        .flag()

    private val list by option("--list", help = "List available skills without installing")
        .flag()

    override fun run() {
        if (list) {
            listSkills()
            return
        }

        val allSystems = listOf("claude", "gemini", "copilot", "cursor", "windsurf", "cline", "junie")
        val systems = if (system == "all") allSystems else listOf(system)

        for (sys in systems) {
            val installer = resolveInstaller(sys)
            if (installer == null) {
                echo("Unknown system: $sys. Supported: ${allSystems.joinToString(", ")}, all", err = true)
                continue
            }
            val count = installer.install()
            val format = if (installer is AgentSkillsInstaller) "skills" else "rules"
            echo("  $sys: installed $count $format → ${installer.targetPath()}")
        }
    }

    private fun listSkills() {
        echo("Available compose-buddy skills:")
        echo("")
        for (skill in SkillRegistry.skills) {
            echo("  ${skill.name}")
            echo("    ${skill.description}")
            echo("")
        }
        echo("Install: compose-buddy install [--system claude|gemini|copilot|cursor|windsurf|cline|junie|aider|all]")
    }

    private fun resolveInstaller(sys: String): SkillInstaller? {
        val targetDir = if (global) globalDir(sys) else project
        return when (sys) {
            // Agent Skills standard (SKILL.md in skills/<name>/ directory)
            "claude" -> AgentSkillsInstaller(targetDir, ".claude/skills")
            "gemini" -> AgentSkillsInstaller(targetDir, ".gemini/skills")
            "copilot" -> AgentSkillsInstaller(targetDir, ".github/skills")
            "cursor" -> AgentSkillsInstaller(targetDir, ".cursor/skills")
            "windsurf" -> AgentSkillsInstaller(targetDir, ".windsurf/skills")
            "cline" -> AgentSkillsInstaller(targetDir, ".cline/skills")
            // Rules-only systems
            "junie" -> JunieInstaller(targetDir)
            else -> null
        }
    }

    private fun globalDir(sys: String): File {
        val home = File(System.getProperty("user.home"))
        return when (sys) {
            "claude" -> File(home, ".claude")
            "gemini" -> File(home, ".gemini")
            "copilot" -> File(home, ".copilot")
            "cline" -> File(home, ".cline")
            "windsurf" -> File(home, ".codeium/windsurf")
            else -> project // cursor, junie, aider are project-level only
        }
    }
}

// --- Skill Registry ---

data class Skill(
    val name: String,
    val description: String,
    val content: String,
)

object SkillRegistry {
    val skills: List<Skill> by lazy {
        listOf("compose-buddy-render", "compose-buddy-a11y", "compose-ui-loop").map { name ->
            val content = SkillRegistry::class.java.getResourceAsStream("/skills/$name.md")
                ?.bufferedReader()?.readText()
                ?: error("Skill resource not found: $name")
            Skill(name = name, description = extractDescription(content), content = content)
        }
    }

    private fun extractDescription(content: String): String {
        // Multi-line description with >-
        val multiLine = Regex("""description:\s*>-\s*\n([\s\S]*?)(?=\n[a-z]|\n---)""").find(content)
        if (multiLine != null) return multiLine.groupValues[1].trim().replace(Regex("\\s+"), " ").take(200)
        // Single-line description
        val singleLine = Regex("""description:\s*(.+)""").find(content)
        return singleLine?.groupValues?.get(1)?.trim()?.take(200) ?: "compose-buddy skill"
    }
}

// --- Installers ---

interface SkillInstaller {
    fun install(): Int
    fun targetPath(): String
}

/**
 * Installs skills using the Agent Skills standard (agentskills.io).
 * Creates <skillsDir>/<name>/SKILL.md for each skill.
 * Used by: Claude, Gemini, Copilot, Cursor, Windsurf, Cline.
 */
class AgentSkillsInstaller(
    private val rootDir: File,
    private val skillsDirPath: String,
) : SkillInstaller {
    override fun install(): Int {
        var count = 0
        for (skill in SkillRegistry.skills) {
            val skillDir = File(rootDir, "$skillsDirPath/${skill.name}")
            skillDir.mkdirs()
            File(skillDir, "SKILL.md").writeText(skill.content)
            count++
        }
        return count
    }

    override fun targetPath() = "$skillsDirPath/"
}

/**
 * Junie: .junie/guidelines/<name>.md (rules only, no skills support).
 * Plain markdown without frontmatter.
 */
class JunieInstaller(private val targetDir: File) : SkillInstaller {
    override fun install(): Int {
        val dir = File(targetDir, ".junie/guidelines")
        dir.mkdirs()
        var count = 0
        for (skill in SkillRegistry.skills) {
            val body = stripFrontmatter(skill.content)
            File(dir, "${skill.name}.md").writeText(body)
            count++
        }
        return count
    }

    override fun targetPath() = ".junie/guidelines/"
}


private fun stripFrontmatter(content: String): String {
    if (!content.startsWith("---")) return content
    val endIdx = content.indexOf("---", 3)
    if (endIdx < 0) return content
    return content.substring(endIdx + 3).trimStart()
}
