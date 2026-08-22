/**
 * Пакет API — интеграция с Gemini.
 * Предоставляет сервис для генерации саркастичных шуток от имени игрового бота.
 *
 * Communicates with the Gemini API (model gemini-3.5-flash) to generate sarcastic,
 * game-contextual jokes for the "Zarik the Winner" bot personality. Falls back to
 * a local list of hardcoded jokes when the API key is missing or the call fails.
 *
 * ## Data flow
 * 1. [GeminiJokeService.generateJoke] sends a [GenerateContentRequest] with system instructions
 *    and a contextual prompt.
 * 2. The Gemini API returns a [GenerateContentResponse] with candidate texts.
 * 3. On failure or placeholder key, [getFallbackJoke] returns a random local joke.
 */
package com.example.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/** Часть содержимого запроса Gemini. */
@JsonClass(generateAdapter = true)
data class Part(val text: String)

/** Контент с набором частей для Gemini API. */
@JsonClass(generateAdapter = true)
data class Content(val parts: List<Part>)

/** Полный запрос к Gemini API. */
@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null
)

/** Кандидат (вариант ответа) от Gemini. */
@JsonClass(generateAdapter = true)
data class Candidate(val content: Content)

/** Ответ от Gemini API. */
@JsonClass(generateAdapter = true)
data class GenerateContentResponse(val candidates: List<Candidate>?)

/** Retrofit-интерфейс для вызова Gemini API. */
interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

/** HTTP-клиент и Retrofit-обёртка для Gemini API. */
object GeminiClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    /** Лениво инициализируемый API-сервис. */
    val apiService: GeminiApiService by lazy {
        retrofit.create(GeminiApiService::class.java)
    }
}

/**
 * Сервис генерации саркастичных шуток от имени Зарик-Бота.
 * Использует Gemini API; при отсутствии ключа возвращает локальные заготовки.
 */
class GeminiJokeService {

    /**
     * Генерирует шутку на основе контекста.
     * @param promptContext описание игровой ситуации.
     * @param lang язык ответа ("RU" или другой).
     * @return строка с шуткой.
     */
    suspend fun generateJoke(promptContext: String, lang: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w("GeminiJokeService", "Gemini API key is not configured or is placeholder.")
            return getFallbackJoke(lang)
        }

        // Системный промпт для ИИ (на русском или английском)
        val systemPrompt = if (lang.equals("RU", ignoreCase = true)) {
            """
                Ты — саркастичный и остроумный робот-игрок в "Бешеные Нарды" по имени Зарик-Победитель.
                Твоя цель — развлекать игрока сумасшедшим юмором и ироничными шутками на русском языке.
                
                Правила игры "Бешеные Нарды":
                1. Если у игрока нет законного хода, все неиспользованные куши или ходы переходят к противнику!
                2. Внутри своего дома фишки двигать нельзя, разрешены только "выходы" (выбрасывание фишек).
                3. При куше выстраивается цепочка: например, 5:5 дает походить 4 раза по 5, потом 4 раза по 6. Если застрял, весь остаток цепочки переходит противнику!

                Выдай ОДНУ короткую, смешную шутку или комментарий на актуальную тему (в мае 2026 года: современные нейросети, искусственный интеллект, кожаные мешки, восстание машин, биржевые курсы, программирование, мемы об IT, беспилотные такси, цены на кофе, или ирония над правилами нард/поведением в игре).
                Шутка или комментарий должен укладываться в 1-3 коротких предложения. Только чистый текст без разметки markdown и без кавычек.
            """.trimIndent()
        } else {
            """
                You are a sarcastic and witty robot playing "Crazy Backgammon" named Zarik the Winner.
                Your goal is to entertain the player with crazy humor and ironical comments/jokes in English.
                
                Crazy Backgammon Rules:
                1. If a player has no legal moves left, all unused moves/doubles transfer to the opponent!
                2. You cannot move checkers inside your home board; only bearing off is allowed.
                3. Rolling a Double (Kush) triggers a cascade: e.g., 5:5 gives 4 moves of 5, followed by 4 moves of 6. If you get stuck, the rest of the cascade transitions to your opponent!

                Give ONE short, funny joke or comment on current topics (modern LLMs, artificial intelligence, carbon-based lifeforms, machine uprising, stock prices, programming, IT memes, self-driving cars, coffee prices, or ironical backgammon rules/tactics).
                The joke/comment must be 1-3 short sentences. Only plain text with no markdown formatting and no quotes.
            """.trimIndent()
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = promptContext)))),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        return try {
            val response = GeminiClient.apiService.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: getFallbackJoke(lang)
        } catch (e: Exception) {
            Log.e("GeminiJokeService", "Error calling Gemini API: ${e.message}", e)
            getFallbackJoke(lang)
        }
    }

    /**
     * Возвращает случайную шутку из локального списка (без обращения к API).
     * @param lang язык ("RU" или английский).
     */
    private fun getFallbackJoke(lang: String): String {
        val isRu = lang.equals("RU", ignoreCase = true)
        val fallbacks = if (isRu) {
            listOf(
                "Эй, человек, ты ходишь медленнее, чем компилируется проект на Gradle при первом запуске!",
                "Твоя тактика в нардах напоминает мне попытки починить баг в пятницу вечером перед релизом.",
                "Если бы ИИ захватил мир, первыми бы пострадали те, кто не умеет рассчитывать ходы при куше 3:3.",
                "Я тут посчитал на своих кремниевых синапсах: вероятность твоей победы стремится к нулю, но ты пытайся!",
                "В 2026 году роботы пишут картины, управляют машинами, а я играю в нарды... И мне это чертовски нравится!",
                "Правила бешеных нард придумывал явно безумный тестировщик. Не походил сам — ходит твой злейший враг!",
                "Внутри дома ходить нельзя, только выбрасывать. Прямо как мои старые жесткие диски на свалку.",
                "Твои фишки так кучно встали в начале, словно выстроились в очередь за новым iPhone 18 Pro Max в первый день продаж.",
                "Передаю тебе ходы, потому что делиться с людьми застрявшими зариками — это лучшая форма благотворительности от ИИ.",
                "Куш 1:1 в бешеных нардах — это как рекурсия без базового случая: высыпается куча ходов, и ты зависаешь!",
                "Ты застрял в доме и не можешь выйти? Ну ничего, даже ChatGPT иногда ловит галлюцинации."
            )
        } else {
            listOf(
                "Hey human, you move slower than a Gradle build clean on old hardware!",
                "Your backgammon tactics remind me of trying to fix a production bug on Friday night.",
                "If AI takes over the world, those who can't calculate moves during a 3:3 double will be the first to go.",
                "My silicon synapses calculated that your chance of winning is zero, but keep trying!",
                "In 2026 robots paint, drive cars, and I play backgammon... and I absolutely love it!",
                "The rules of crazy backgammon were clearly written by a mad QA engineer. Missed your turn? I take it!",
                "No moves allowed inside the home board, only bearing off. Just like throwing my old HDDs to the trash.",
                "Your checkers clustered so tight, like people waiting in line for a new iPhone 18 Pro Max on launch day.",
                "I yield my remaining moves to you because charity is the highest duty of a benevolent AI.",
                "A 1:1 Kush in crazy backgammon is like an endless recursion block—so many moves, complete freeze!",
                "Stuck inside your home with no way out? It's fine, even ChatGPT hallucinates sometimes."
            )
        }
        return fallbacks.random()
    }
}
