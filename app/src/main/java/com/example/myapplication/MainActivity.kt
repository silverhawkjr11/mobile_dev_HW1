package com.example.myapplication

import android.content.Context // Added for SharedPreferences
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log // Added for logging
import android.widget.Button
import android.widget.TextView
import com.google.gson.Gson // For retrieving list of scores
import com.google.gson.reflect.TypeToken // For retrieving list of scores

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREVIOUS_SCORES_KEY = "previous_scores" // Must match key in GameActivity
    }

    private lateinit var startButton: Button
    private lateinit var highScoreTextView: TextView
    private lateinit var previousScoresListTextView: TextView // New

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startButton = findViewById(R.id.start_button)
        highScoreTextView = findViewById(R.id.high_score_text)
        previousScoresListTextView = findViewById(R.id.previous_scores_list_text) // New

        startButton.setOnClickListener {
            val intent = Intent(this, GameActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadAndDisplayScores()
    }

    private fun loadAndDisplayScores() {
        val sharedPref = getSharedPreferences("game_prefs", Context.MODE_PRIVATE)

        // Load and display high score
        val highScore = sharedPref.getInt("high_score", 0)
        highScoreTextView.text = getString(R.string.high_score_format, highScore)
        Log.d(TAG, "Loaded high score: $highScore")

        // Load and display previous scores list
        val gson = Gson()
        val jsonScores = sharedPref.getString(PREVIOUS_SCORES_KEY, null)
        val type = object : TypeToken<List<Int>>() {}.type // Use List<Int> for reading
        val previousScores: List<Int> = if (jsonScores != null) {
            try {
                gson.fromJson(jsonScores, type)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing previous scores JSON", e)
                listOf() // Return empty list on error
            }
        } else {
            listOf()
        }

        if (previousScores.isNotEmpty()) {
            val scoresText = previousScores.mapIndexed { index, score ->
                "${index + 1}. $score"
            }.joinToString("\n")
            previousScoresListTextView.text = scoresText
            Log.d(TAG, "Displayed previous scores: ${previousScores.size} items")
        } else {
            previousScoresListTextView.text = "No recent scores yet!"
            Log.d(TAG, "No previous scores to display.")
        }
    }
}
