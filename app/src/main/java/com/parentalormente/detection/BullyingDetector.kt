package com.parentalormente.detection

/**
 * On-device bullying/harassment detection engine.
 *
 * No cloud. No API keys. No subscriptions. Runs entirely locally.
 *
 * Detection strategy:
 * 1. Direct keyword matching (slurs, threats, targeted insults)
 * 2. Pattern matching (repeated targeting, escalation phrases)
 * 3. Severity scoring (LOW / MEDIUM / HIGH / CRITICAL)
 */
object BullyingDetector {

    enum class Severity(val level: Int) {
        NONE(0),
        LOW(1),      // Mild insults, possible teasing
        MEDIUM(2),   // Clear insults, exclusion language
        HIGH(3),     // Threats, sustained harassment
        CRITICAL(4)  // Threats of violence, self-harm mentions
    }

    data class DetectionResult(
        val severity: Severity,
        val matchedPatterns: List<String>,
        val summary: String
    )

    // Threat / violence indicators — CRITICAL
    private val criticalPatterns = listOf(
        Regex("\\b(kill|murder|shoot|stab|hurt)\\s+(you|your|u|ur)", RegexOption.IGNORE_CASE),
        Regex("\\b(you|u)\\s+(should|gonna|will)\\s+(die|disappear)", RegexOption.IGNORE_CASE),
        Regex("\\b(kill|end)\\s+(your|ur)self", RegexOption.IGNORE_CASE),
        Regex("\\bno\\s*one\\s*(would|will)\\s*(care|notice|miss)\\s*(if|when)", RegexOption.IGNORE_CASE),
        Regex("\\bbring\\s+a\\s+(gun|knife|weapon)", RegexOption.IGNORE_CASE),
        Regex("\\byou('?re|\\s+are)\\s+dead", RegexOption.IGNORE_CASE),
    )

    // Direct harassment — HIGH
    private val highPatterns = listOf(
        Regex("\\b(everyone|everybody|no\\s*one|nobody)\\s+(hates|likes)\\s+(you|u)", RegexOption.IGNORE_CASE),
        Regex("\\bgo\\s+(away|die|home)", RegexOption.IGNORE_CASE),
        Regex("\\bkill\\s+yourself", RegexOption.IGNORE_CASE),
        Regex("\\b(ugly|fat|stupid|dumb|retard|loser|worthless|pathetic|disgusting)\\s+(ass|bitch|fuck)", RegexOption.IGNORE_CASE),
        Regex("\\byou('?re|\\s+are)\\s+(so\\s+)?(ugly|fat|stupid|dumb|worthless|pathetic|disgusting|useless)", RegexOption.IGNORE_CASE),
        Regex("\\b(don'?t|do\\s*not)\\s+come\\s+(to|back)", RegexOption.IGNORE_CASE),
        Regex("\\bwe\\s+(don'?t|do\\s*not)\\s+want\\s+you", RegexOption.IGNORE_CASE),
        Regex("\\b(i'?ll|we'?ll|i\\s+will|we\\s+will)\\s+(beat|jump|get|fuck)\\s+(you|u)", RegexOption.IGNORE_CASE),
    )

    // Insults / exclusion — MEDIUM
    private val mediumPatterns = listOf(
        Regex("\\b(loser|freak|weirdo|creep|loner|reject|outcast)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(shut\\s+up|stfu|gtfo|kys)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(you|u)\\s+(suck|stink|smell)", RegexOption.IGNORE_CASE),
        Regex("\\b(hate|despise)\\s+(you|u|ur|your)", RegexOption.IGNORE_CASE),
        Regex("\\b(no\\s*one|nobody)\\s+(likes|wants|cares\\s+about)\\s+(you|u)", RegexOption.IGNORE_CASE),
        Regex("\\b(can'?t|don'?t)\\s+(sit|hang|eat|talk)\\s+with\\s+(us|them)", RegexOption.IGNORE_CASE),
        Regex("\\byou('?re|\\s+are)\\s+(not|never)\\s+(invited|welcome|wanted)", RegexOption.IGNORE_CASE),
    )

    // Mild teasing / possible bullying — LOW
    private val lowPatterns = listOf(
        Regex("\\b(lol|lmao|haha)\\s+(you|u|ur)", RegexOption.IGNORE_CASE),
        Regex("\\b(weird|strange|cringe|embarrassing)\\b", RegexOption.IGNORE_CASE),
        Regex("\\btry\\s*hard\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpick\\s*me\\b", RegexOption.IGNORE_CASE),
    )

    fun analyze(message: String): DetectionResult {
        val matched = mutableListOf<String>()

        // Check critical first — short circuit on the worst stuff
        for (pattern in criticalPatterns) {
            pattern.find(message)?.let {
                matched.add("CRITICAL: ${it.value}")
            }
        }
        if (matched.isNotEmpty()) {
            return DetectionResult(
                severity = Severity.CRITICAL,
                matchedPatterns = matched,
                summary = "Threat or violence-related language detected"
            )
        }

        for (pattern in highPatterns) {
            pattern.find(message)?.let {
                matched.add("HIGH: ${it.value}")
            }
        }
        if (matched.isNotEmpty()) {
            return DetectionResult(
                severity = Severity.HIGH,
                matchedPatterns = matched,
                summary = "Direct harassment or targeted abuse detected"
            )
        }

        for (pattern in mediumPatterns) {
            pattern.find(message)?.let {
                matched.add("MEDIUM: ${it.value}")
            }
        }
        if (matched.isNotEmpty()) {
            return DetectionResult(
                severity = Severity.MEDIUM,
                matchedPatterns = matched,
                summary = "Insults or exclusion language detected"
            )
        }

        for (pattern in lowPatterns) {
            pattern.find(message)?.let {
                matched.add("LOW: ${it.value}")
            }
        }
        if (matched.isNotEmpty()) {
            return DetectionResult(
                severity = Severity.LOW,
                matchedPatterns = matched,
                summary = "Possible teasing or mild bullying language"
            )
        }

        return DetectionResult(
            severity = Severity.NONE,
            matchedPatterns = emptyList(),
            summary = "No bullying indicators detected"
        )
    }
}
