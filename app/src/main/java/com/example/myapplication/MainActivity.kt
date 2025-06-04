package com.example.myapplication

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_GAME_MODE = "extra_game_mode"
        const val EXTRA_GAME_SPEED = "extra_game_speed" // "slow" or "fast"
        const val MODE_BUTTONS = "mode_buttons"
        const val MODE_SENSOR = "mode_sensor"
        const val SPEED_SLOW = "slow"
        const val SPEED_FAST = "fast"
        // Keep PREVIOUS_SCORES_KEY if you display scores here, or move to a dedicated scores activity
        private const val PREVIOUS_SCORES_KEY = "previous_scores"
    }

    private lateinit var buttonModeSlowButton: Button
    private lateinit var buttonModeFastButton: Button
    private lateinit var sensorModeButton: Button
    private lateinit var highScoresButton: Button
    private lateinit var highScoreTextViewMain: TextView // Renamed to avoid conflict

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Ensure this uses your new menu layout

        buttonModeSlowButton = findViewById(R.id.button_mode_slow_button)
        buttonModeFastButton = findViewById(R.id.button_mode_fast_button)
        sensorModeButton = findViewById(R.id.sensor_mode_button)
        highScoresButton = findViewById(R.id.high_scores_button)
        highScoreTextViewMain = findViewById(R.id.high_score_text_main)


        buttonModeSlowButton.setOnClickListener {
            startGame(MODE_BUTTONS, SPEED_SLOW)
        }

        buttonModeFastButton.setOnClickListener {
            startGame(MODE_BUTTONS, SPEED_FAST)
        }

        sensorModeButton.setOnClickListener {
            startGame(MODE_SENSOR, SPEED_SLOW) // Default speed for sensor, can be adjusted later
        }

        highScoresButton.setOnClickListener {
            // TODO: Intent to start HighScoresActivity
            Log.d(TAG, "High Scores button clicked - Implement HighScoresActivity intent")
            Toast.makeText(this, "High Scores Screen - Coming Soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startGame(mode: String, speed: String) {
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra(EXTRA_GAME_MODE, mode)
            putExtra(EXTRA_GAME_SPEED, speed)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadAndDisplayHighScore() // Only display single high score here for simplicity now
    }

    private fun loadAndDisplayHighScore() {
        val sharedPref = getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
        val highScore = sharedPref.getInt("high_score", 0) // Assuming "high_score" is still your key
        highScoreTextViewMain.text = getString(R.string.high_score_format, highScore)
        Log.d(TAG, "Loaded high score for main menu: $highScore")
    }

    // If you were displaying the list of scores on MainActivity, keep that logic.
    // For now, I've simplified it to just show the single high score.
    // The detailed score list will be in its own activity/fragments.
}
