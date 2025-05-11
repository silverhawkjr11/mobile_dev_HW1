package com.example.obstaclecargame

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    
    private lateinit var startButton: Button
    private lateinit var highScoreTextView: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Find views
        startButton = findViewById(R.id.start_button)
        highScoreTextView = findViewById(R.id.high_score_text)
        
        // Get high score from SharedPreferences
        val sharedPref = getSharedPreferences("game_prefs", MODE_PRIVATE)
        val highScore = sharedPref.getInt("high_score", 0)
        highScoreTextView.text = "High Score: $highScore"
        
        // Set click listener for start button
        startButton.setOnClickListener {
            // Start game activity
            val intent = Intent(this, GameActivity::class.java)
            startActivity(intent)
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Update high score when returning to the main screen
        val sharedPref = getSharedPreferences("game_prefs", MODE_PRIVATE)
        val highScore = sharedPref.getInt("high_score", 0)
        highScoreTextView.text = "High Score: $highScore"
    }
}