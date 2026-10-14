package com.fuerzasalvaje.senda.data.local

import androidx.room.TypeConverter
import com.fuerzasalvaje.senda.data.model.AnswerData
import com.fuerzasalvaje.senda.data.model.ClassSlotData
import com.fuerzasalvaje.senda.data.model.PhaseData
import com.fuerzasalvaje.senda.data.model.RubricItemData
import kotlinx.serialization.json.Json

/** Stores lists as JSON text using kotlinx-serialization. */
class Converters {

    @TypeConverter
    fun fromStrings(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStrings(value: String): List<String> = json.decodeFromString(value)

    @TypeConverter
    fun fromClassSlots(value: List<ClassSlotData>): String = json.encodeToString(value)

    @TypeConverter
    fun toClassSlots(value: String): List<ClassSlotData> = json.decodeFromString(value)

    @TypeConverter
    fun fromPhases(value: List<PhaseData>): String = json.encodeToString(value)

    @TypeConverter
    fun toPhases(value: String): List<PhaseData> = json.decodeFromString(value)

    @TypeConverter
    fun fromRubric(value: List<RubricItemData>): String = json.encodeToString(value)

    @TypeConverter
    fun toRubric(value: String): List<RubricItemData> = json.decodeFromString(value)

    @TypeConverter
    fun fromAnswers(value: List<AnswerData>): String = json.encodeToString(value)

    @TypeConverter
    fun toAnswers(value: String): List<AnswerData> = json.decodeFromString(value)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
