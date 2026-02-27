package com.flamingo.ticktickboom

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class GroupPresetManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bomb_group_prefs", Context.MODE_PRIVATE)

    fun savePresets(presets: List<GroupPreset>) {
        val jsonArray = JSONArray()
        presets.forEach { preset ->
            val presetObj = JSONObject().apply {
                put("id", preset.id)
                put("presetName", preset.presetName)
                put("defaultTime", preset.defaultTime.toDouble()) // JSON natively prefers Double over Float
                put("resetOnExplosion", preset.resetOnExplosion)

                val playersArray = JSONArray()
                preset.players.forEach { player ->
                    val playerObj = JSONObject().apply {
                        put("id", player.id)
                        put("name", player.name)
                        put("timeLeft", player.timeLeft.toDouble())
                        put("isEliminated", player.isEliminated)
                        put("isAbsent", player.isAbsent)
                    }
                    playersArray.put(playerObj)
                }
                put("players", playersArray)
            }
            jsonArray.put(presetObj)
        }
        prefs.edit { putString("saved_presets", jsonArray.toString()) }
    }

    fun loadPresets(): List<GroupPreset> {
        val jsonString = prefs.getString("saved_presets", null) ?: return emptyList()
        val presets = mutableListOf<GroupPreset>()

        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val presetObj = jsonArray.getJSONObject(i)

                val playersArray = presetObj.getJSONArray("players")
                val players = mutableListOf<Player>()
                for (j in 0 until playersArray.length()) {
                    val playerObj = playersArray.getJSONObject(j)
                    players.add(
                        Player(
                            id = playerObj.getString("id"),
                            name = playerObj.getString("name"),
                            timeLeft = playerObj.getDouble("timeLeft").toFloat(),
                            isEliminated = playerObj.optBoolean("isEliminated", false),
                            isAbsent = playerObj.optBoolean("isAbsent", false)
                        )
                    )
                }

                presets.add(
                    GroupPreset(
                        id = presetObj.getString("id"),
                        presetName = presetObj.getString("presetName"),
                        players = players,
                        defaultTime = presetObj.getDouble("defaultTime").toFloat(),
                        resetOnExplosion = presetObj.getBoolean("resetOnExplosion")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace() // Failsafe: If the JSON is corrupted, we just return an empty list
        }

        return presets
    }
}