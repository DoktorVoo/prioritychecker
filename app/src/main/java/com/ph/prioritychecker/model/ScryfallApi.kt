package com.ph.prioritychecker.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ─── Card Data ───────────────────────────────────────────────────────────────

data class ScryfallCard(
    val scryfallId: String,
    val name: String,           // English name (oracle)
    val printedName: String,    // Name in searched language
    val typeLine: String,       // English type line
    val printedTypeLine: String,// Local type line (if available)
    val imageSmall: String?,
    val imageNormal: String?,
    val lang: String,
    val manaCost: String = ""
) {
    /** Display name: local name if different from English, else English */
    val displayName get() = if (printedName.isNotBlank() && printedName != name) printedName else name

    /** Detected card type for stack categorization */
    fun detectType(inGerman: Boolean): String {
        val t = typeLine
        return when {
            t.contains("Instant") -> if (inGerman) "Spontanzauber" else "Instant"
            t.contains("Sorcery") -> if (inGerman) "Hexerei" else "Sorcery"
            t.contains("Creature") -> if (inGerman) "Kreatur" else "Creature"
            t.contains("Artifact") && t.contains("Creature") ->
                if (inGerman) "Artefaktkreatur" else "Artifact Creature"
            t.contains("Artifact") -> if (inGerman) "Artefakt" else "Artifact"
            t.contains("Enchantment") -> if (inGerman) "Verzauberung" else "Enchantment"
            t.contains("Planeswalker") -> "Planeswalker"
            t.contains("Battle") -> if (inGerman) "Schlacht" else "Battle"
            t.contains("Land") -> if (inGerman) "Land" else "Land"
            else -> if (inGerman) "Sonstiges" else "Other"
        }
    }

    /** Type line to show in UI (local or English) */
    val displayTypeLine get() = if (printedTypeLine.isNotBlank() && printedTypeLine != typeLine)
        printedTypeLine else typeLine
}

// ─── Search Result Wrapper ───────────────────────────────────────────────────

sealed class SearchResult {
    data class Success(val cards: List<ScryfallCard>) : SearchResult()
    data class Error(val message: String) : SearchResult()
    object Empty : SearchResult()
}

// ─── Scryfall API ────────────────────────────────────────────────────────────

object ScryfallApi {

    private const val BASE = "https://api.scryfall.com"

    /**
     * Autocomplete card names via Scryfall /cards/autocomplete endpoint.
     * Returns up to 20 name suggestions (de + en merged).
     */
    suspend fun autocomplete(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()
        try {
            val enc = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "$BASE/cards/autocomplete?q=$enc"
            val json = httpGet(url) ?: return@withContext emptyList()
            val root = JSONObject(json)
            val data = root.optJSONArray("data") ?: return@withContext emptyList()
            val result = mutableListOf<String>()
            for (i in 0 until minOf(data.length(), 20)) {
                result.add(data.getString(i))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Search both German and English in parallel, merge and deduplicate by oracle id.
     * Returns up to 20 results.
     */
    suspend fun searchBoth(query: String): SearchResult = coroutineScope {
        if (query.isBlank()) return@coroutineScope SearchResult.Empty

        val enc = withContext(Dispatchers.IO) { URLEncoder.encode(query.trim(), "UTF-8") }

        val deferred = async(Dispatchers.IO) { searchLang(enc, "de") }
        val enDeferred = async(Dispatchers.IO) {
            Thread.sleep(60) // polite delay between requests
            searchLang(enc, "en")
        }

        val deCards = deferred.await()
        val enCards = enDeferred.await()

        // Merge: German first, then English, deduplicate by oracle name
        val seen = mutableSetOf<String>()
        val merged = (deCards + enCards).filter { card ->
            seen.add(card.name.lowercase())
        }.take(20)

        if (merged.isEmpty()) SearchResult.Empty
        else SearchResult.Success(merged)
    }

    private fun searchLang(encodedQuery: String, lang: String): List<ScryfallCard> {
        return try {
            val url = "$BASE/cards/search?q=${encodedQuery}+lang:${lang}&unique=cards&order=name"
            val json = httpGet(url) ?: return emptyList()
            parseCardList(JSONObject(json))
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseCardList(root: JSONObject): List<ScryfallCard> {
        if (root.optString("object") == "error") return emptyList()
        val data = root.optJSONArray("data") ?: return emptyList()
        val result = mutableListOf<ScryfallCard>()
        for (i in 0 until data.length()) {
            parseCard(data.getJSONObject(i))?.let { result.add(it) }
        }
        return result
    }

    private fun parseCard(obj: JSONObject): ScryfallCard? {
        return try {
            val id = obj.optString("id", "")
            val name = obj.optString("name", "")
            val printedName = obj.optString("printed_name", name)
            val typeLine = obj.optString("type_line", "")
            val printedTypeLine = obj.optString("printed_type_line", typeLine)
            val lang = obj.optString("lang", "en")
            val manaCost = obj.optString("mana_cost", "")

            // Image URIs
            val imageUris = obj.optJSONObject("image_uris")
            val imageSmall = imageUris?.optString("small")
            val imageNormal = imageUris?.optString("normal")

            if (name.isBlank() || typeLine.isBlank()) null
            else ScryfallCard(
                scryfallId = id,
                name = name,
                printedName = printedName,
                typeLine = typeLine,
                printedTypeLine = printedTypeLine,
                imageSmall = imageSmall,
                imageNormal = imageNormal,
                lang = lang,
                manaCost = manaCost
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun httpGet(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 8000
                setRequestProperty("User-Agent", "PriorityChecker/1.0")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().readText()
            } else null
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
