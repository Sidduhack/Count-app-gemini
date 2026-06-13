package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.*
import com.example.database.CountDatabase
import com.example.database.CountRecord
import com.example.ui.utils.ImageUtils
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface AnalysisState {
    object Idle : AnalysisState
    data class Loading(val message: String) : AnalysisState
    data class Success(val result: CountResult, val isHistorical: Boolean = false) : AnalysisState
    data class Error(val message: String) : AnalysisState
}

class CountViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CountDatabase.getDatabase(application)
    private val dao = db.countRecordDao()

    // Preferences for API key override
    private val prefs = application.getSharedPreferences("api_settings", Context.MODE_PRIVATE)

    // UI States
    private val _sampleUri = MutableStateFlow<Uri?>(null)
    val sampleUri = _sampleUri.asStateFlow()

    private val _sampleBitmap = MutableStateFlow<Bitmap?>(null)
    val sampleBitmap = _sampleBitmap.asStateFlow()

    private val _sceneUri = MutableStateFlow<Uri?>(null)
    val sceneUri = _sceneUri.asStateFlow()

    private val _sceneBitmap = MutableStateFlow<Bitmap?>(null)
    val sceneBitmap = _sceneBitmap.asStateFlow()

    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState = _analysisState.asStateFlow()

    private val _apiKeyOverride = MutableStateFlow(prefs.getString("api_key", "") ?: "")
    val apiKeyOverride = _apiKeyOverride.asStateFlow()

    // Past scan list flow
    val historyList: StateFlow<List<CountRecord>> = dao.getAllRecords()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val listAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, BoxDetection::class.java)
        RetrofitClient.moshiParser.adapter<List<BoxDetection>>(type)
    }

    private val resultAdapter by lazy {
        RetrofitClient.moshiParser.adapter(CountResult::class.java)
    }

    fun setSampleImage(uri: Uri?) {
        _sampleUri.value = uri
        if (uri != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val bitmap = ImageUtils.uriToBitmap(getApplication(), uri)
                _sampleBitmap.value = bitmap
                // Reset analysis state since images changed
                _analysisState.value = AnalysisState.Idle
            }
        } else {
            _sampleBitmap.value = null
        }
    }

    fun setSampleImageBitmap(bitmap: Bitmap?) {
        _sampleBitmap.value = bitmap
        _sampleUri.value = null
        _analysisState.value = AnalysisState.Idle
    }

    fun setSceneImage(uri: Uri?) {
        _sceneUri.value = uri
        if (uri != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val bitmap = ImageUtils.uriToBitmap(getApplication(), uri)
                _sceneBitmap.value = bitmap
                _analysisState.value = AnalysisState.Idle
            }
        } else {
            _sceneBitmap.value = null
        }
    }

    fun setSceneImageBitmap(bitmap: Bitmap?) {
        _sceneBitmap.value = bitmap
        _sceneUri.value = null
        _analysisState.value = AnalysisState.Idle
    }

    fun setApiKeyOverride(key: String) {
        _apiKeyOverride.value = key
        prefs.edit().putString("api_key", key).apply()
    }

    fun getActiveApiKey(): String {
        val override = _apiKeyOverride.value.trim()
        if (override.isNotEmpty()) return override
        
        // standard fallback
        val buildKey = BuildConfig.GEMINI_API_KEY
        if (buildKey.isNotEmpty() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey
        }
        return ""
    }

    fun selectHistoricalRecord(record: CountRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            _analysisState.value = AnalysisState.Loading("Loading record...")
            
            // Load bitmaps from cached paths
            val sampleBmp = record.sampleImagePath?.let { path ->
                try {
                    val file = File(path)
                    if (file.exists()) {
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
            
            val sceneBmp = record.sceneImagePath?.let { path ->
                try {
                    val file = File(path)
                    if (file.exists()) {
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    } else null
                } catch (e: Exception) {
                    null
                }
            }

            _sampleBitmap.value = sampleBmp
            _sceneBitmap.value = sceneBmp
            _sampleUri.value = null
            _sceneUri.value = null

            val detections = try {
                record.detectionsJson?.let { json ->
                    listAdapter.fromJson(json)
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            val result = CountResult(
                itemName = record.itemName,
                count = record.count,
                description = record.description,
                detections = detections
            )

            _analysisState.value = AnalysisState.Success(result, isHistorical = true)
        }
    }

    fun deleteHistoryRecord(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteRecordById(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAllRecords()
        }
    }

    fun countItems() {
        val sample = _sampleBitmap.value
        val scene = _sceneBitmap.value

        if (sample == null || scene == null) {
            _analysisState.value = AnalysisState.Error("Please provide both a sample and a full scene photo first.")
            return
        }

        val apiKey = getActiveApiKey()
        if (apiKey.isEmpty()) {
            _analysisState.value = AnalysisState.Error("Gemini API key is not configured. Please add your key in the settings panel above.")
            return
        }

        _analysisState.value = AnalysisState.Loading("Preparing photos for analysis...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Resize images
                _analysisState.value = AnalysisState.Loading("Optimizing image sizes...")
                val resizedSample = ImageUtils.resizeBitmap(sample, 800)
                val resizedScene = ImageUtils.resizeBitmap(scene, 1000)

                // 2. Base64 encoding
                _analysisState.value = AnalysisState.Loading("Encoding images...")
                val base64Sample = ImageUtils.bitmapToBase64(resizedSample)
                val base64Scene = ImageUtils.bitmapToBase64(resizedScene)

                // 3. Make request
                _analysisState.value = AnalysisState.Loading("Sending request to Gemini AI...")

                val prompt = """
                    You are a highly precise object detector and counter.
                    The user has uploaded two images:
                    1. The first image displays index 0, which is the REFERENCE 'sample' containing 1 single item.
                    2. The second image displays index 1, which is the TARGET 'scene' containing multiple matching instances of the same object.

                    Task & Guidelines:
                    - Find all matching instances of the reference item in the full scene image.
                    - CRITICAL: Detect matching items in any direction, rotation, orientation, angle, side, flipped state, or variation. They can be scattered, randomly rotated, or arranged in different layouts.
                    - Formulate precise bounding boxes [ymin, xmin, ymax, xmax] for every detected occurrence of the item in the scene image. The coordinates must be normalized from 0 to 1000.
                    - Count the occurrences.
                    - Give a brief description of where the items are distributed.

                    You MUST respond in a strict valid JSON format.
                    JSON Format fields:
                    {
                      "itemName": "<singular brief name of the object, e.g. screw, coin, token, pill>",
                      "count": <total integer counted>,
                      "description": "<one-sentence physical distribution description, e.g. '12 silver coins scattered on a wooden surface'>",
                      "detections": [
                        {
                          "box_2d": [ymin, xmin, ymax, xmax]
                        }
                      ]
                    }
                    
                    Return ONLY this raw JSON object. Do not format with markdown blocks or tags. No headers. Must start with '{' and end with '}'.
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = prompt),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Sample)),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Scene))
                            )
                        )
                    ),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.1
                    )
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val rawJsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("No response returned from Gemini AI model.")

                _analysisState.value = AnalysisState.Loading("Decoding AI results...")

                val cleanedJson = cleanRawJson(rawJsonText)
                val countResult = resultAdapter.fromJson(cleanedJson) 
                    ?: throw Exception("Failed to parse the counting structure. Raw: $rawJsonText")

                // 4. Save optimized bitmaps to app's cache storage for history loading
                _analysisState.value = AnalysisState.Loading("Logging results into local storage...")

                val uniqueId = UUID.randomUUID().toString()
                val samplePath = ImageUtils.saveBitmapToCache(getApplication(), resizedSample, "sample_$uniqueId.jpg")
                val scenePath = ImageUtils.saveBitmapToCache(getApplication(), resizedScene, "scene_$uniqueId.jpg")

                val detectionsJson = listAdapter.toJson(countResult.detections ?: emptyList())

                val record = CountRecord(
                    itemName = countResult.itemName,
                    count = countResult.count,
                    description = countResult.description,
                    sampleImagePath = samplePath,
                    sceneImagePath = scenePath,
                    detectionsJson = detectionsJson
                )

                dao.insertRecord(record)

                _analysisState.value = AnalysisState.Success(countResult)

            } catch (e: Exception) {
                e.printStackTrace()
                _analysisState.value = AnalysisState.Error(e.localizedMessage ?: "An unexpected error occurred during counting.")
            }
        }
    }

    private fun cleanRawJson(input: String): String {
        return input.trim().removeSurrounding("```json", "```").trim()
    }
}
