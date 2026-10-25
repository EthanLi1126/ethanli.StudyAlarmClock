package com.example.studyalarm.data

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class FlashcardItem(
    val id: Int,
    val prompt: String,
    val target: String,
    val category: String,
)

object FlashcardRepository {
    private const val PREFS_NAME = "flashcard_prefs"
    private const val KEY_CUSTOM_CARDS = "custom_cards"
    private val gson = Gson()

    private val defaultCards = mutableListOf(
        FlashcardItem(1, "Biceps brachii", "肱二頭肌", "解剖學"),
        FlashcardItem(2, "Triceps brachii", "肱三頭肌", "解剖學"),
        FlashcardItem(3, "Femur", "股骨", "解剖學"),
        FlashcardItem(4, "Deltoid", "三角肌", "解剖學"),
        FlashcardItem(5, "Aspirin", "阿司匹靈 (抗凝血/解熱鎮痛)", "藥理學"),
        FlashcardItem(6, "Metformin", "二甲雙胍 (降血糖)", "藥理學"),
    )

    fun getCategories(): List<String> {
        return defaultCards.map { it.category }.distinct()
    }

    @Suppress("unused")
    fun addCustomCard(context: Context, card: FlashcardItem) {
        defaultCards.add(card)
        saveCustomCards(context)
    }

    // 🟢 解析貼上的 CSV 文字並批量新增題目
    fun importCardsFromCsvText(context: Context, csvText: String, defaultCategory: String): Int {
        var importedCount = 0
        val newCards = mutableListOf<FlashcardItem>()

        try {
            csvText.reader().readLines().forEach { line ->
                if (line.isBlank()) return@forEach

                // 清除單雙引號，並支援以逗點 "," 或 Tab "\t" 分割欄位
                val tokens = line.split(",", "\t").map {
                    it.trim().removeSurrounding("\"").removeSurrounding("'")
                }

                if (tokens.size >= 2) {
                    val prompt = tokens[0]
                    val target = tokens[1]
                    val category = if ((tokens.size >= 3) && tokens[2].isNotEmpty()) {
                        tokens[2]
                    } else {
                        defaultCategory.ifEmpty { "匯入題庫" }
                    }

                    if (prompt.isNotEmpty() && target.isNotEmpty()) {
                        val cardId = (System.currentTimeMillis() + importedCount).toInt()
                        newCards.add(
                            FlashcardItem(
                                id = cardId,
                                prompt = prompt,
                                target = target,
                                category = category
                            )
                        )
                        importedCount++
                    }
                }
            }

            if (newCards.isNotEmpty()) {
                defaultCards.addAll(newCards)
                saveCustomCards(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return importedCount
    }

    fun deleteCard(context: Context, cardId: Int) {
        defaultCards.removeAll { it.id == cardId }
        saveCustomCards(context)
    }

    fun getCardsByCategory(category: String): List<FlashcardItem> {
        val list = defaultCards.filter { it.category == category }
        return list.ifEmpty { defaultCards }
    }

    fun getRandomCards(category: String, count: Int): List<FlashcardItem> {
        val available = getCardsByCategory(category)
        return available.shuffled().take(count)
    }

    private fun saveCustomCards(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // 儲存所有非預設建立（ID > 6 或 < 0）的自訂/匯入題目
        val customCards = defaultCards.filter { (it.id > 6) || (it.id < 0) }
        val json = gson.toJson(customCards)
        prefs.edit { putString(KEY_CUSTOM_CARDS, json) }
    }

    fun loadCustomCards(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CUSTOM_CARDS, null) ?: return
        val type = object : TypeToken<List<FlashcardItem>>() {}.type
        val loadedCards: List<FlashcardItem> = gson.fromJson(json, type) ?: emptyList()

        loadedCards.forEach { card ->
            if (defaultCards.none { it.id == card.id }) {
                defaultCards.add(card)
            }
        }
    }
}