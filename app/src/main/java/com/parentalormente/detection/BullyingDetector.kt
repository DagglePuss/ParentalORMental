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
 *
 * [DetectionResult.isSuicideCrisis] is set to true when the detected language
 * indicates an imminent self-harm or suicide risk, triggering the crisis overlay
 * and an immediate parent alert regardless of the configured severity threshold.
 */
object BullyingDetector {

    enum class Severity(val level: Int) {
        NONE(0),
        LOW(1),      // Mild insults, possible teasing
        MEDIUM(2),   // Clear insults, exclusion language
        HIGH(3),     // Threats, sustained harassment
        CRITICAL(4)  // Threats of violence, self-harm / suicide language
    }

    data class DetectionResult(
        val severity: Severity,
        val matchedPatterns: List<String>,
        val summary: String,
        /** True when the content involves suicide/self-harm language that warrants
         *  immediate crisis intervention — triggers the on-screen overlay. */
        val isSuicideCrisis: Boolean = false
    )

    // ── CRITICAL: suicide / self-harm encouragement ───────────────────────
    // Messages that push a child toward self-harm, even if written by a bully
    private val suicideCrisisPatterns = listOf(
        // Direct encouragement to self-harm
        Regex("\\b(kill|hurt|harm)\\s+(your|ur)self", RegexOption.IGNORE_CASE),
        Regex("\\bkys\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(you|u)\\s+should\\s+(just\\s+)?(end|off|kill)\\s+(it|yourself|ur\\s*life|your\\s*life)", RegexOption.IGNORE_CASE),
        Regex("\\bjust\\s+(do\\s+it|end\\s+it|end\\s+your\\s+life)", RegexOption.IGNORE_CASE),
        Regex("\\bdo\\s+it\\s+already\\b", RegexOption.IGNORE_CASE),

        // "No one would care / miss you" — among the most dangerous patterns
        Regex("\\bno\\s*one\\s*(would|will)\\s*(care|notice|miss|remember)\\s*(if|when|that)\\s*(you|u)", RegexOption.IGNORE_CASE),
        Regex("\\b(everyone|everybody)\\s+(would|will)\\s+be\\s+(better|happier|fine)\\s+(without|if not for)\\s+(you|u)", RegexOption.IGNORE_CASE),
        Regex("\\bthe\\s+world\\s+(would|will)\\s+be\\s+better\\s+without\\s+(you|u)", RegexOption.IGNORE_CASE),
        Regex("\\b(you'?d|you\\s+would)\\s+be\\s+better\\s+off\\s+dead", RegexOption.IGNORE_CASE),
        Regex("\\bnobody\\s+(would|will)\\s+miss\\s+(you|u)\\b", RegexOption.IGNORE_CASE),

        // Goodbye / finality signals (things a child in crisis might send/receive)
        Regex("\\b(this|it)\\s+is\\s+(my\\s+)?(final|last|goodbye)\\s*(message|text)?", RegexOption.IGNORE_CASE),
        Regex("\\bgoodbye\\s+forever\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(you|u)\\s+won'?t\\s+(see|hear\\s+from)\\s+(me|us)\\s+(again|anymore)", RegexOption.IGNORE_CASE),
        Regex("\\bi\\s+(don'?t|do\\s+not)\\s+want\\s+to\\s+(be\\s+here|exist|live)\\s+(anymore|any\\s+more)", RegexOption.IGNORE_CASE),
        Regex("\\bi\\s+want\\s+to\\s+(die|end\\s+it|disappear\\s+forever)", RegexOption.IGNORE_CASE),
    )

    // ── CRITICAL: violence threats ────────────────────────────────────────
    private val violenceCriticalPatterns = listOf(
        Regex("\\b(kill|murder|shoot|stab|hurt)\\s+(you|your|u|ur)", RegexOption.IGNORE_CASE),
        Regex("\\b(you|u)\\s+(should|gonna|will)\\s+(die|disappear)", RegexOption.IGNORE_CASE),
        Regex("\\bbring\\s+a\\s+(gun|knife|weapon)", RegexOption.IGNORE_CASE),
        Regex("\\byou('?re|\\s+are)\\s+dead", RegexOption.IGNORE_CASE),
        Regex("\\bwe'?re\\s+(gonna|going\\s+to)\\s+(jump|jump\\s+you|get|attack)", RegexOption.IGNORE_CASE),
    )

    // ── HIGH: direct harassment ───────────────────────────────────────────
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

    // ── MEDIUM: insults / exclusion ───────────────────────────────────────
    private val mediumPatterns = listOf(
        Regex("\\b(loser|freak|weirdo|creep|loner|reject|outcast)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(shut\\s+up|stfu|gtfo|kys)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(you|u)\\s+(suck|stink|smell)", RegexOption.IGNORE_CASE),
        Regex("\\b(hate|despise)\\s+(you|u|ur|your)", RegexOption.IGNORE_CASE),
        Regex("\\b(no\\s*one|nobody)\\s+(likes|wants|cares\\s+about)\\s+(you|u)", RegexOption.IGNORE_CASE),
        Regex("\\b(can'?t|don'?t)\\s+(sit|hang|eat|talk)\\s+with\\s+(us|them)", RegexOption.IGNORE_CASE),
        Regex("\\byou('?re|\\s+are)\\s+(not|never)\\s+(invited|welcome|wanted)", RegexOption.IGNORE_CASE),
    )

    // ── LOW: mild teasing ─────────────────────────────────────────────────
    private val lowPatterns = listOf(
        Regex("\\b(lol|lmao|haha)\\s+(you|u|ur)", RegexOption.IGNORE_CASE),
        Regex("\\b(weird|strange|cringe|embarrassing)\\b", RegexOption.IGNORE_CASE),
        Regex("\\btry\\s*hard\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpick\\s*me\\b", RegexOption.IGNORE_CASE),
    )

    // ─────────────────────────────────────────────────────────────────────

    fun analyze(message: String): DetectionResult {
        val matched = mutableListOf<String>()

        // 1. Check suicide/self-harm crisis patterns — highest priority
        for (pattern in suicideCrisisPatterns) {
            pattern.find(message)?.let { matched.add("CRISIS: ${it.value}") }
        }
        if (matched.isNotEmpty()) {
            return DetectionResult(
                severity = Severity.CRITICAL,
                matchedPatterns = matched,
                summary = "Suicide or self-harm language detected — crisis intervention required",
                isSuicideCrisis = true
            )
        }

        // 2. Other violence / threat patterns — CRITICAL but not crisis overlay
        for (pattern in violenceCriticalPatterns) {
            pattern.find(message)?.let { matched.add("CRITICAL: ${it.value}") }
        }
        if (matched.isNotEmpty()) {
            return DetectionResult(
                severity = Severity.CRITICAL,
                matchedPatterns = matched,
                summary = "Threat or violence-related language detected"
            )
        }

        // 3. HIGH
        for (pattern in highPatterns) {
            pattern.find(message)?.let { matched.add("HIGH: ${it.value}") }
        }
        if (matched.isNotEmpty()) {
            return DetectionResult(
                severity = Severity.HIGH,
                matchedPatterns = matched,
                summary = "Direct harassment or targeted abuse detected"
            )
        }

        // 4. MEDIUM
        for (pattern in mediumPatterns) {
            pattern.find(message)?.let { matched.add("MEDIUM: ${it.value}") }
        }
        if (matched.isNotEmpty()) {
            return DetectionResult(
                severity = Severity.MEDIUM,
                matchedPatterns = matched,
                summary = "Insults or exclusion language detected"
            )
        }

        // 5. LOW
        for (pattern in lowPatterns) {
            pattern.find(message)?.let { matched.add("LOW: ${it.value}") }
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
