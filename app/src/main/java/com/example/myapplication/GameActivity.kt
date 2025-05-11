package com.example.myapplication

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.random.Random

class GameActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var gameLayout: ConstraintLayout
    private lateinit var carImageView: ImageView
    private lateinit var leftButton: ImageButton
    private lateinit var rightButton: ImageButton
    private lateinit var scoreTextView: TextView
    private lateinit var livesTextView: TextView

    // Game objects
    private val obstacleList = mutableListOf<ImageView>()
    private val gameHandler = Handler(Looper.getMainLooper())
    private val obstacleHandler = Handler(Looper.getMainLooper())
    private var vibrator: Vibrator? = null

    // Game state
    private var score = 0
    private var lives = 3
    private var gameRunning = false
    private var currentLane = 1 // 0: left, 1: center, 2: right
    private val lanes = intArrayOf(0, 0, 0) // Will store the lane positions

    // Game settings - renamed to follow Kotlin naming conventions
    private val obstacleSpeed = 15 // Speed of obstacles moving down
    private val obstacleInterval = 1500L // Time between obstacles (ms)
    private val gameTick = 50L // Game update interval (ms)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        // Initialize UI elements
        gameLayout = findViewById(R.id.game_layout)
        carImageView = findViewById(R.id.car_image)
        leftButton = findViewById(R.id.left_button)
        rightButton = findViewById(R.id.right_button)
        scoreTextView = findViewById(R.id.score_text)
        livesTextView = findViewById(R.id.lives_text)

        // Initialize vibrator service
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Set up click listeners for buttons
        leftButton.setOnClickListener {
            moveCarLeft()
        }

        rightButton.setOnClickListener {
            moveCarRight()
        }

        // Calculate lane positions
        calculateLanePositions()

        // Start the game
        startGame()
    }

    private fun calculateLanePositions() {
        // This will be calculated after layout is drawn
        gameLayout.post {
            val laneWidth = gameLayout.width / 3
            lanes[0] = laneWidth / 2 // Left lane center
            lanes[1] = laneWidth + laneWidth / 2 // Center lane center
            lanes[2] = 2 * laneWidth + laneWidth / 2 // Right lane center

            // Position car in center lane initially
            positionCarInLane(currentLane)
        }
    }

    private fun positionCarInLane(lane: Int) {
        // Position the car horizontally in the specified lane
        carImageView.translationX = (lanes[lane] - carImageView.width / 2).toFloat()
    }

    private fun moveCarLeft() {
        if (currentLane > 0) {
            currentLane--
            positionCarInLane(currentLane)
        }
    }

    private fun moveCarRight() {
        if (currentLane < 2) {
            currentLane++
            positionCarInLane(currentLane)
        }
    }

    private fun startGame() {
        // Reset game state
        score = 0
        lives = 3
        updateUI()

        // Remove any existing obstacles
        for (obstacle in obstacleList) {
            gameLayout.removeView(obstacle)
        }
        obstacleList.clear()

        gameRunning = true

        // Start game loop
        gameLoop()

        // Start generating obstacles
        generateObstacles()
    }

    private fun gameLoop() {
        gameHandler.postDelayed({
            if (gameRunning) {
                moveObstacles()
                checkCollisions()
                updateScore()
                gameLoop() // Continue the loop
            }
        }, gameTick)
    }

    private fun generateObstacles() {
        obstacleHandler.postDelayed({
            if (gameRunning) {
                createObstacle()
                generateObstacles() // Continue generating obstacles
            }
        }, obstacleInterval)
    }

    private fun createObstacle() {
        // Create a new obstacle ImageView
        val obstacle = ImageView(this)
        obstacle.setImageResource(R.drawable.obstacle)

        // Increase obstacle size - make them bigger
        val params = ConstraintLayout.LayoutParams(
            100, // width in pixels - increased from 60
            100  // height in pixels - increased from 60
        )
        obstacle.layoutParams = params

        // Choose a random lane
        val lane = Random.nextInt(3)

        // Add obstacle to the layout
        gameLayout.addView(obstacle)
        obstacleList.add(obstacle)

        // Position obstacle at the top of the chosen lane
        obstacle.translationX = (lanes[lane] - params.width / 2).toFloat()
        obstacle.translationY = -params.height.toFloat()
    }
    private fun moveObstacles() {
        // Move all obstacles down
        val iterator = obstacleList.iterator()
        while (iterator.hasNext()) {
            val obstacle = iterator.next()
            obstacle.translationY += obstacleSpeed

            // Remove obstacles that go off screen
            if (obstacle.translationY > gameLayout.height) {
                // First remove from view
                gameLayout.removeView(obstacle)
                // Then remove from our list
                iterator.remove()
                // Increase score when an obstacle passes successfully
                score += 10
            }
        }
    }

    private fun checkCollisions() {
        if (obstacleList.isEmpty()) return // No obstacles to check

        // Get car bounds with a smaller hitbox for more accurate collision
        val carWidth = carImageView.width * 0.8f // 80% of car width
        val carHeight = carImageView.height * 0.8f // 80% of car height
        val carCenterX = carImageView.translationX + (carImageView.width / 2)
        val carCenterY = carImageView.translationY + (carImageView.height / 2)

        val carLeft = carCenterX - (carWidth / 2)
        val carRight = carCenterX + (carWidth / 2)
        val carTop = carCenterY - (carHeight / 2)
        val carBottom = carCenterY + (carHeight / 2)

        // Check collision with each obstacle
        val iterator = obstacleList.iterator()
        while (iterator.hasNext()) {
            val obstacle = iterator.next()

            // Skip collision check for obstacles below the screen (they'll be removed in moveObstacles)
            if (obstacle.translationY > gameLayout.height) {
                continue
            }

            val obstacleCenterX = obstacle.translationX + (obstacle.width / 2)
            val obstacleCenterY = obstacle.translationY + (obstacle.height / 2)

            // Use 80% of obstacle size for more accurate collision
            val obstacleWidth = obstacle.width * 0.8f
            val obstacleHeight = obstacle.height * 0.8f

            val obstacleLeft = obstacleCenterX - (obstacleWidth / 2)
            val obstacleRight = obstacleCenterX + (obstacleWidth / 2)
            val obstacleTop = obstacleCenterY - (obstacleHeight / 2)
            val obstacleBottom = obstacleCenterY + (obstacleHeight / 2)

            // Check if car and obstacle overlap - using smaller hitboxes
            if (carRight > obstacleLeft && carLeft < obstacleRight &&
                carBottom > obstacleTop && carTop < obstacleBottom) {

                // Collision detected - remove obstacle from view and list
                gameLayout.removeView(obstacle)
                iterator.remove()

                handleCollision()
                break // Only handle one collision per frame
            }
        }
    }

    // Modified to not take an obstacle parameter
    private fun handleCollision() {
        // Decrease lives
        lives--

        // Vibrate phone
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(500)
        }

        // Show toast message
        Toast.makeText(this, getString(R.string.crash_message, lives), Toast.LENGTH_SHORT).show()

        // Update UI
        updateUI()

        // Check if game over
        if (lives <= 0) {
            gameOver()
        }
    }

    private fun gameOver() {
        gameRunning = false

        // Save high score if current score is higher
        val sharedPref = getSharedPreferences("game_prefs", MODE_PRIVATE)
        val highScore = sharedPref.getInt("high_score", 0)

        if (score > highScore) {
            with(sharedPref.edit()) {
                putInt("high_score", score)
                apply()
            }
        }

        // Show game over message
//        Toast.makeText(this, getString(R.string.game_over_message, score), Toast.LENGTH_LONG).show()
        Toast.makeText(this, getString(R.string.game_over_message, score), Toast.LENGTH_LONG).show()
        // Restart the game after a delay (endless game)
        Handler(Looper.getMainLooper()).postDelayed({
            startGame()
        }, 3000)
    }

    private fun updateScore() {
        score++
        updateUI()
    }

    private fun updateUI() {
        // Update score and lives text
        scoreTextView.text = getString(R.string.score_format, score)
        livesTextView.text = getString(R.string.lives_format, lives)
    }

    override fun onPause() {
        super.onPause()
        // Pause the game when activity is paused
        gameRunning = false
    }

    override fun onResume() {
        super.onResume()
        // Resume the game if it was running
        if (!gameRunning && lives > 0) {
            gameRunning = true
            gameLoop()
            generateObstacles()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove callbacks to prevent memory leaks
        gameHandler.removeCallbacksAndMessages(null)
        obstacleHandler.removeCallbacksAndMessages(null)
    }
}