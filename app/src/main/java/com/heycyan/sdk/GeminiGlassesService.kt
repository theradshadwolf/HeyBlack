package com.heycyan.sdk

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class GeminiGlassesService(
    private val context: Context,
    private val apiKey: String,
    private val hardwareController: HardwareController
) {

    fun processVoiceQuery(userQuery: String, onResponse: (String) -> Unit) {
        thread {
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                // Build Request Payload with Tools (Function Declarations)
                val payload = JSONObject().apply {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", SkeletonKeyCodex.SYSTEM_INSTRUCTIONS)))
                    })
                    
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", userQuery)))
                    }))

                    // Register Hardware Tools for Function Calling
                    put("tools", JSONArray().put(JSONObject().apply {
                        put("functionDeclarations", JSONArray().apply {
                            put(JSONObject().apply {
                                put("name", "toggle_flashlight")
                                put("description", "Turns the LED flashlight/torch on or off.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("state", JSONObject().apply {
                                            put("type", "BOOLEAN")
                                            put("description", "true for on, false for off.")
                                        })
                                    })
                                    put("required", JSONArray().put("state"))
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "start_audio_recording")
                                put("description", "Starts recording audio/voice memo.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject())
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "stop_audio_recording")
                                put("description", "Stops current audio recording.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject())
                                })
                            })
                        })
                    }))
                }

                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(responseText)

                val candidate = jsonResponse.getJSONArray("candidates").getJSONObject(0)
                val parts = candidate.getJSONObject("content").getJSONArray("parts")
                val firstPart = parts.getJSONObject(0)

                var reply = ""

                // Handle Function Call from Gemini
                if (firstPart.has("functionCall")) {
                    val functionCall = firstPart.getJSONObject("functionCall")
                    val functionName = functionCall.getString("name")
                    val args = functionCall.optJSONObject("args")

                    reply = when (functionName) {
                        "toggle_flashlight" -> {
                            val state = args?.optBoolean("state", true) ?: true
                            hardwareController.toggleFlashlight(state)
                        }
                        "start_audio_recording" -> {
                            hardwareController.startAudioRecording()
                        }
                        "stop_audio_recording" -> {
                            hardwareController.stopAudioRecording()
                        }
                        else -> "Executed $functionName"
                    }
                } else if (firstPart.has("text")) {
                    reply = firstPart.getString("text")
                }

                hardwareController.speak(reply)
                onResponse(reply)

            } catch (e: Exception) {
                val errorMsg = "Error processing query: ${e.message}"
                hardwareController.speak(errorMsg)
                onResponse(errorMsg)
            }
        }
    }
}
