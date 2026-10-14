package com.fuerzasalvaje.senda.util.gemini

/** How much the model may think before answering: low to generate quickly, medium to review. */
enum class ThinkingLevel { LOW, MEDIUM }

/** What each model family accepts: Gemini 3+ takes a level, Gemini 2.5 a token budget, older models nothing. */
sealed interface ThinkingSetting {
    data class Level(val value: String) : ThinkingSetting
    data class Budget(val tokens: Int) : ThinkingSetting
}

object Thinking {
    private val version = Regex("gemini-(\\d+)(?:\\.(\\d+))?")

    fun forModel(model: String, level: ThinkingLevel?): ThinkingSetting? {
        if (level == null) return null
        val name = model.lowercase().removePrefix("models/")
        val match = version.find(name) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: 0
        val pro = "pro" in name
        return when {
            // Gemini 3 Pro only has "low" and "high"; its review uses "high".
            major >= 3 -> ThinkingSetting.Level(
                when (level) {
                    ThinkingLevel.LOW -> "low"
                    ThinkingLevel.MEDIUM -> if (pro) "high" else "medium"
                }
            )
            major == 2 && minor >= 5 -> ThinkingSetting.Budget(
                when (level) {
                    // 2.5 Pro cannot turn thinking off; 128 is its minimum budget.
                    ThinkingLevel.LOW -> if (pro) 128 else 0
                    ThinkingLevel.MEDIUM -> 2048
                }
            )
            else -> null
        }
    }
}
