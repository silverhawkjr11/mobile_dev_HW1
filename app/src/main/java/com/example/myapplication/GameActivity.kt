package com.example.myapplication

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.random.Random

class GameActivity : AppCompatActivity() {

    // Constants for game settings
    companion object {
        private const val TAG = "GameActivity" // For logging
        private const val INITIAL_LIVES = 3
        private const val OBSTACLE_SPEED_PIXELS_PER_TICK = 40 // Speed of obstacles
        private const val OBSTACLE_GENERATION_INTERVAL_MS = 1000L // Time between new obstacles
        private const val GAME_UPDATE_INTERVAL_MS = 50L // Game loop refresh rate
        private const val OBSTACLE_SIZE_DP = 55 // Desired obstacle size in DP
        private const val CAR_HITBOX_SCALE_FACTOR = 0.75f // e.g., 75% of visual size
        private const val OBSTACLE_HITBOX_SCALE_FACTOR = 0.75f
        private const val SCORE_PER_DODGED_OBSTACLE = 10
        private const val SCORE_PER_TICK = 0 // Set to 1 if you want score to increase over time
    }

    // UI Elements
    private lateinit var gameLayout: ConstraintLayout
    private lateinit var carImageView: ImageView
    private lateinit var leftButton: ImageButton
    private lateinit var rightButton: ImageButton
    private lateinit var scoreTextView: TextView
    private lateinit var livesTextView: TextView

    // Game Objects & State
    private val activeObstacles = mutableListOf<ImageView>()
    private val gameLoopHandler = Handler(Looper.getMainLooper())
    private val obstacleGenerationHandler = Handler(Looper.getMainLooper())
    private var vibrator: Vibrator? = null

    private var currentScore = 0
    private var currentLives = INITIAL_LIVES
    private var isGameRunning = false
    private var currentCarLane = 1 // 0: left, 1: center, 2: right

    // Lane positioning (calculated after layout)
    private val laneCenterPositionsX = IntArray(3) // Stores X coordinates for center of each lane
    private var screenWidthPixels = 0
    private var carVisualWidthPixels = 0
    private var carVisualHeightPixels = 0 // Added to store car height
    private var obstacleVisualSizePixels = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        initializeUIElements()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        setupButtonListeners()

        // Crucial: Calculate dimensions and start game *after* layout is complete
        gameLayout.post {
            screenWidthPixels = gameLayout.width
            carVisualWidthPixels = carImageView.width
            carVisualHeightPixels = carImageView.height // Get car's height

            if (screenWidthPixels == 0 || carVisualWidthPixels == 0 || carVisualHeightPixels == 0) {
                Log.e(TAG, "Layout not ready, cannot initialize game.")
                Toast.makeText(this, "Error initializing game layout.", Toast.LENGTH_LONG).show()
                finish() // Close activity if layout fails
                return@post
            }

            obstacleVisualSizePixels = (OBSTACLE_SIZE_DP * resources.displayMetrics.density).toInt()
            Log.d(TAG, "Obstacle size in pixels: $obstacleVisualSizePixels")


            calculateLaneCenterPositions()
            startGame()
        }
    }

    private fun initializeUIElements() {
        gameLayout = findViewById(R.id.game_layout)
        carImageView = findViewById(R.id.car_image)
        leftButton = findViewById(R.id.left_button)
        rightButton = findViewById(R.id.right_button)
        scoreTextView = findViewById(R.id.score_text)
        livesTextView = findViewById(R.id.lives_text)
    }

    private fun setupButtonListeners() {
        leftButton.setOnClickListener { if (isGameRunning) moveCarLeft() }
        rightButton.setOnClickListener { if (isGameRunning) moveCarRight() }
    }

    private fun calculateLaneCenterPositions() {
        if (screenWidthPixels == 0) {
            Log.e(TAG, "Screen width is zero, cannot calculate lane positions.")
            return
        }
        val lanePixelWidth = screenWidthPixels / 3
        laneCenterPositionsX[0] = lanePixelWidth / 2
        laneCenterPositionsX[1] = lanePixelWidth + (lanePixelWidth / 2)
        laneCenterPositionsX[2] = (2 * lanePixelWidth) + (lanePixelWidth / 2)
        Log.d(TAG, "Calculated Lane Centers X: L0=${laneCenterPositionsX[0]}, L1=${laneCenterPositionsX[1]}, L2=${laneCenterPositionsX[2]}")
    }

    private fun positionCarInCurrentLane() {
        Log.d(TAG, "positionCarInCurrentLane() CALLED for lane: $currentCarLane")
        if (currentCarLane < 0 || currentCarLane >= laneCenterPositionsX.size || carVisualWidthPixels == 0) {
            Log.e(TAG, "CANNOT position car: Invalid lane ($currentCarLane), or car width ($carVisualWidthPixels), or screen width ($screenWidthPixels) is zero.")
            Log.e(TAG, "Lane Centers X: L0=${laneCenterPositionsX.getOrNull(0)}, L1=${laneCenterPositionsX.getOrNull(1)}, L2=${laneCenterPositionsX.getOrNull(2)}")
            return
        }

        // The center of the lane we are aiming for.
        val targetCarCenterX = laneCenterPositionsX[currentCarLane]

        // THE FIX:
        // Calculate the translation needed to move the car from the screen's center
        // to the lane's center.
        val targetTranslationX = (targetCarCenterX - screenWidthPixels / 2).toFloat()

        Log.d(TAG, "Car current translationX BEFORE setting: ${carImageView.translationX}, Car current visual X BEFORE: ${carImageView.x}")
        Log.d(TAG, "For lane $currentCarLane: TargetCenter=$targetCarCenterX, ScreenCenter=${screenWidthPixels/2}. Calculated targetTranslationX: $targetTranslationX")

        carImageView.translationX = targetTranslationX

        Log.d(TAG, "Car translationX AFTER setting: ${carImageView.translationX}, Car visual X AFTER setting: ${carImageView.x} (for lane $currentCarLane)")
    }


    private fun moveCarLeft() {
        Log.d(TAG, "moveCarLeft() TAPPED. currentCarLane BEFORE: $currentCarLane, isGameRunning: $isGameRunning")
        if (currentCarLane > 0) {
            currentCarLane--
            Log.d(TAG, "moveCarLeft() - currentCarLane AFTER decrement: $currentCarLane")
            positionCarInCurrentLane()
        } else {
            Log.d(TAG, "moveCarLeft() - Condition not met (currentCarLane was already 0 or less). currentCarLane: $currentCarLane")
        }
    }

    private fun moveCarRight() {
        if (currentCarLane < laneCenterPositionsX.size - 1) {
            currentCarLane++
            positionCarInCurrentLane()
        }
    }

    private fun startGame() {
        Log.d(TAG, "Starting game...")
        currentScore = 0
        currentLives = INITIAL_LIVES
        updateGameUI()

        activeObstacles.forEach { gameLayout.removeView(it) }
        activeObstacles.clear()

        positionCarInCurrentLane() // Ensure car is in the correct starting lane

        isGameRunning = true
        gameLoopHandler.post(gameRunnable)
        obstacleGenerationHandler.post(obstacleGeneratorRunnable)
    }

    private val gameRunnable = object : Runnable {
        override fun run() {
            if (!isGameRunning) return

            moveAllObstacles()
            checkAllCollisions()
            currentScore += SCORE_PER_TICK // Optional: score increases over time
            updateGameUI()

            gameLoopHandler.postDelayed(this, GAME_UPDATE_INTERVAL_MS)
        }
    }

    private val obstacleGeneratorRunnable = object : Runnable {
        override fun run() {
            if (!isGameRunning) return
            spawnNewObstacle()
            obstacleGenerationHandler.postDelayed(this, OBSTACLE_GENERATION_INTERVAL_MS)
        }
    }

    private fun spawnNewObstacle() {
        if (laneCenterPositionsX.all { it == 0 }) {
            Log.w(TAG, "Lanes not initialized, skipping obstacle spawn.")
            return
        }

        val newObstacle = ImageView(this)
        newObstacle.setImageResource(R.drawable.obstacle) // Ensure you have this drawable
        val layoutParams = ConstraintLayout.LayoutParams(obstacleVisualSizePixels, obstacleVisualSizePixels)
        newObstacle.layoutParams = layoutParams

        val spawnLane = Random.nextInt(laneCenterPositionsX.size) // 0, 1, or 2
        val obstacleCenterX = laneCenterPositionsX[spawnLane]

        newObstacle.translationX = (obstacleCenterX - obstacleVisualSizePixels / 2).toFloat()
        newObstacle.translationY = -obstacleVisualSizePixels.toFloat() // Start above screen

        gameLayout.addView(newObstacle)
        activeObstacles.add(newObstacle)
        Log.d(TAG, "Spawned obstacle in lane $spawnLane at X: ${newObstacle.translationX}")
    }

    private fun moveAllObstacles() {
        val iterator = activeObstacles.iterator()
        while (iterator.hasNext()) {
            val obstacle = iterator.next()
            obstacle.translationY += OBSTACLE_SPEED_PIXELS_PER_TICK

            if (obstacle.translationY > gameLayout.height) {
                gameLayout.removeView(obstacle)
                iterator.remove()
                currentScore += SCORE_PER_DODGED_OBSTACLE
                // updateGameUI() will be called by the main game loop
                Log.d(TAG, "Obstacle passed. Score: $currentScore")
            }
        }
    }

    private fun checkAllCollisions() {
        if (carVisualWidthPixels == 0 || carVisualHeightPixels == 0) return // Car not measured

        // Car's hitbox (adjust scale factor for leniency)
        val carHitboxWidth = carVisualWidthPixels * CAR_HITBOX_SCALE_FACTOR
        val carHitboxHeight = carVisualHeightPixels * CAR_HITBOX_SCALE_FACTOR

        // Car's visual position: carImageView.getX() and carImageView.getY() give the top-left
        // Note: carImageView.getX() includes its translationX relative to its initial layout position.
        val carVisualLeft = carImageView.x
        val carVisualTop = carImageView.y

        val carHitboxLeft = carVisualLeft + (carVisualWidthPixels - carHitboxWidth) / 2f
        val carHitboxRight = carHitboxLeft + carHitboxWidth
        val carHitboxTop = carVisualTop + (carVisualHeightPixels - carHitboxHeight) / 2f
        val carHitboxBottom = carHitboxTop + carHitboxHeight

        // Log car hitbox for debugging (can be verbose)
        // Log.d(TAG, "Car Hitbox - L:$carHitboxLeft, R:$carHitboxRight, T:$carHitboxTop, B:$carHitboxBottom (Visual L:${carImageView.x} T:${carImageView.y})")


        val iterator = activeObstacles.iterator()
        while (iterator.hasNext()) {
            val obstacle = iterator.next()
            if (obstacle.width == 0 || obstacle.height == 0) continue // Obstacle not measured

            val obstacleCurrentWidth = obstacle.width
            val obstacleCurrentHeight = obstacle.height

            val obstacleHitboxWidth = obstacleCurrentWidth * OBSTACLE_HITBOX_SCALE_FACTOR
            val obstacleHitboxHeight = obstacleCurrentHeight * OBSTACLE_HITBOX_SCALE_FACTOR

            // Obstacle's visual position: obstacle.getX() and obstacle.getY()
            val obstacleVisualLeft = obstacle.x
            val obstacleVisualTop = obstacle.y

            val obstacleHitboxLeft = obstacleVisualLeft + (obstacleCurrentWidth - obstacleHitboxWidth) / 2f
            val obstacleHitboxRight = obstacleHitboxLeft + obstacleHitboxWidth
            val obstacleHitboxTop = obstacleVisualTop + (obstacleCurrentHeight - obstacleHitboxHeight) / 2f
            val obstacleHitboxBottom = obstacleHitboxTop + obstacleHitboxHeight

            // Log obstacle hitbox for debugging (can be verbose)
            // Log.d(TAG, "Obstacle Hitbox - L:$obstacleHitboxLeft, R:$obstacleHitboxRight, T:$obstacleHitboxTop, B:$obstacleHitboxBottom (Visual L:${obstacle.x} T:${obstacle.y})")


            // Simple AABB (Axis-Aligned Bounding Box) collision check
            if (carHitboxRight > obstacleHitboxLeft &&
                carHitboxLeft < obstacleHitboxRight &&
                carHitboxBottom > obstacleHitboxTop &&
                carHitboxTop < obstacleHitboxBottom) {

                Log.i(TAG, "Collision detected with obstacle at X:${obstacle.x}, Y:${obstacle.y}")
                gameLayout.removeView(obstacle)
                iterator.remove()
                processCollision()
                break // Process one collision per frame
            }
        }
    }

    private fun processCollision() {
        currentLives--
        triggerVibration()
        Toast.makeText(this, getString(R.string.crash_message, currentLives), Toast.LENGTH_SHORT).show()
        // updateGameUI() will be called by the main game loop shortly

        if (currentLives <= 0) {
            gameOver()
        }
    }

    private fun triggerVibration() {
        if (vibrator?.hasVibrator() == true) { // Check if vibrator exists
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(200)
            }
        }
    }

    private fun gameOver() {
        Log.i(TAG, "Game Over. Final Score: $currentScore")
        isGameRunning = false
        gameLoopHandler.removeCallbacks(gameRunnable)
        obstacleGenerationHandler.removeCallbacks(obstacleGeneratorRunnable)

        saveHighScore()
        Toast.makeText(this, getString(R.string.game_over_message, currentScore), Toast.LENGTH_LONG).show()

        // Option: Go back to MainActivity or offer restart after a delay
        Handler(Looper.getMainLooper()).postDelayed({
            // For now, just finish GameActivity. You can restart or go to MainActivity.
            // finish()
            // To restart the game:
            startGame() // This will restart the game endlessly as per original logic
        }, 3000) // 3-second delay
    }

    private fun saveHighScore() {
        val sharedPref = getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
        val highScore = sharedPref.getInt("high_score", 0)
        if (currentScore > highScore) {
            with(sharedPref.edit()) {
                putInt("high_score", currentScore)
                apply()
                Log.i(TAG, "New high score saved: $currentScore")
            }
        }
    }

    private fun updateGameUI() {
        scoreTextView.text = getString(R.string.score_format, currentScore)
        livesTextView.text = getString(R.string.lives_format, currentLives)
    }

    override fun onPause() {
        super.onPause()
        if (isGameRunning) {
            Log.d(TAG, "Game paused.")
            isGameRunning = false // Pause game logic
            gameLoopHandler.removeCallbacks(gameRunnable)
            obstacleGenerationHandler.removeCallbacks(obstacleGeneratorRunnable)
        }
    }

    override fun onResume() {
        super.onResume()
        // Only resume if the game was previously running and layout is ready
        // and it's not already in a game over -> restart sequence.
        if (!isGameRunning && currentLives > 0 && screenWidthPixels > 0) {
            Log.d(TAG, "Game resumed.")
            isGameRunning = true
            gameLoopHandler.post(gameRunnable) // Restart handlers
            obstacleGenerationHandler.post(obstacleGeneratorRunnable)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "GameActivity destroyed. Cleaning up handlers.")
        isGameRunning = false
        gameLoopHandler.removeCallbacksAndMessages(null)
        obstacleGenerationHandler.removeCallbacksAndMessages(null)
    }
}
