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
import android.widget.Button // Added for Resume Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.gson.Gson // For saving list of scores
import com.google.gson.reflect.TypeToken // For saving list of scores
import kotlin.random.Random

class GameActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GameActivity"
        private const val INITIAL_LIVES = 3
        private const val OBSTACLE_SPEED_PIXELS_PER_TICK = 15
        private const val OBSTACLE_GENERATION_INTERVAL_MS = 1800L
        private const val GAME_UPDATE_INTERVAL_MS = 50L
        private const val OBSTACLE_SIZE_DP = 55
        private const val CAR_HITBOX_SCALE_FACTOR = 0.75f
        private const val OBSTACLE_HITBOX_SCALE_FACTOR = 0.75f
        private const val SCORE_PER_DODGED_OBSTACLE = 10
        private const val SCORE_PER_TICK = 0
        private const val PREVIOUS_SCORES_KEY = "previous_scores"
        private const val MAX_SAVED_SCORES = 10 // Max number of previous scores to keep
    }

    private lateinit var gameLayout: ConstraintLayout
    private lateinit var carImageView: ImageView
    private lateinit var leftButton: ImageButton
    private lateinit var rightButton: ImageButton
    private lateinit var scoreTextView: TextView
    private lateinit var livesTextView: TextView
    private lateinit var pauseButton: ImageButton // New
    private lateinit var pauseScreenLayout: ConstraintLayout // New
    private lateinit var resumeButton: Button // New


    private val activeObstacles = mutableListOf<ImageView>()
    private val gameLoopHandler = Handler(Looper.getMainLooper())
    private val obstacleGenerationHandler = Handler(Looper.getMainLooper())
    private var vibrator: Vibrator? = null

    private var currentScore = 0
    private var currentLives = INITIAL_LIVES
    private var isGameEffectivelyRunning = false // Combines running and not paused
    private var isGamePausedManually = false // True if user pressed pause

    private var currentCarLane = 1
    private val laneCenterPositionsX = IntArray(3)
    private var screenWidthPixels = 0
    private var carVisualWidthPixels = 0
    private var carVisualHeightPixels = 0
    private var obstacleVisualSizePixels = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        initializeUIElements()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        setupButtonListeners()

        gameLayout.post {
            screenWidthPixels = gameLayout.width
            carVisualWidthPixels = carImageView.width
            carVisualHeightPixels = carImageView.height
            obstacleVisualSizePixels = (OBSTACLE_SIZE_DP * resources.displayMetrics.density).toInt()

            if (screenWidthPixels == 0 || carVisualWidthPixels == 0 || carVisualHeightPixels == 0) {
                Log.e(TAG, "Layout not ready, cannot initialize game.")
                Toast.makeText(this, "Error initializing game layout.", Toast.LENGTH_LONG).show()
                finish()
                return@post
            }
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
        pauseButton = findViewById(R.id.pause_button) // New
        pauseScreenLayout = findViewById(R.id.pause_screen_layout) // New
        resumeButton = findViewById(R.id.resume_button) // New
    }

    private fun setupButtonListeners() {
        leftButton.setOnClickListener { if (isGameEffectivelyRunning) moveCarLeft() }
        rightButton.setOnClickListener { if (isGameEffectivelyRunning) moveCarRight() }
        pauseButton.setOnClickListener { togglePauseGame() } // New
        resumeButton.setOnClickListener { togglePauseGame() } // New (resume is also toggling pause)
    }

    private fun togglePauseGame() {
        if (currentLives <= 0) return // Don't allow pause/resume if game over

        isGamePausedManually = !isGamePausedManually
        isGameEffectivelyRunning = !isGamePausedManually && currentLives > 0

        if (isGamePausedManually) {
            Log.d(TAG, "Game Paused by user.")
            pauseButton.setImageResource(R.drawable.ic_play) // Show play icon
            pauseScreenLayout.visibility = View.VISIBLE
            // Handlers are stopped by isGameEffectivelyRunning check in runnables
        } else {
            Log.d(TAG, "Game Resumed by user.")
            pauseButton.setImageResource(R.drawable.ic_pause) // Show pause icon
            pauseScreenLayout.visibility = View.GONE
            // If handlers were stopped, they need to be restarted if game was running
            // The check in onResume and the game loop itself should handle this
            if (currentLives > 0 && !gameLoopHandler.hasCallbacks(gameRunnable)) { // Ensure handlers are running
                gameLoopHandler.post(gameRunnable)
                obstacleGenerationHandler.post(obstacleGeneratorRunnable)
            }
        }
    }


    private fun calculateLaneCenterPositions() {
        // ... (same as before)
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
        // ... (same as before, using the fix)
        Log.d(TAG, "positionCarInCurrentLane() CALLED for lane: $currentCarLane")
        if (currentCarLane < 0 || currentCarLane >= laneCenterPositionsX.size || carVisualWidthPixels == 0) {
            Log.e(TAG, "CANNOT position car: Invalid lane ($currentCarLane), or car width ($carVisualWidthPixels), or screen width ($screenWidthPixels) is zero.")
            Log.e(TAG, "Lane Centers X: L0=${laneCenterPositionsX.getOrNull(0)}, L1=${laneCenterPositionsX.getOrNull(1)}, L2=${laneCenterPositionsX.getOrNull(2)}")
            return
        }
        val targetCarCenterX = laneCenterPositionsX[currentCarLane]
        val targetTranslationX = (targetCarCenterX - screenWidthPixels / 2).toFloat() // Corrected line
        Log.d(TAG, "Car current translationX BEFORE setting: ${carImageView.translationX}, Car current visual X BEFORE: ${carImageView.x}")
        Log.d(TAG, "For lane $currentCarLane: TargetCenter=$targetCarCenterX, ScreenCenter=${screenWidthPixels/2}. Calculated targetTranslationX: $targetTranslationX")
        carImageView.translationX = targetTranslationX
        Log.d(TAG, "Car translationX AFTER setting: ${carImageView.translationX}, Car visual X AFTER setting: ${carImageView.x} (for lane $currentCarLane)")
    }

    private fun moveCarLeft() {
        // ... (same as before)
        if (currentCarLane > 0) {
            currentCarLane--
            positionCarInCurrentLane()
        }
    }

    private fun moveCarRight() {
        // ... (same as before)
        if (currentCarLane < laneCenterPositionsX.size - 1) {
            currentCarLane++
            positionCarInCurrentLane()
        }
    }

    private fun startGame() {
        Log.d(TAG, "Starting game...")
        currentScore = 0
        currentLives = INITIAL_LIVES
        isGamePausedManually = false // Reset pause state
        isGameEffectivelyRunning = true // Game starts running
        pauseButton.setImageResource(R.drawable.ic_pause) // Ensure pause icon is shown
        pauseScreenLayout.visibility = View.GONE // Ensure pause screen is hidden

        updateGameUI()

        activeObstacles.forEach { gameLayout.removeView(it) }
        activeObstacles.clear()

        positionCarInCurrentLane()

        gameLoopHandler.removeCallbacks(gameRunnable) // Ensure no duplicates
        obstacleGenerationHandler.removeCallbacks(obstacleGeneratorRunnable) // Ensure no duplicates
        gameLoopHandler.post(gameRunnable)
        obstacleGenerationHandler.post(obstacleGeneratorRunnable)
    }

    private val gameRunnable = object : Runnable {
        override fun run() {
            if (!isGameEffectivelyRunning) { // Check combined flag
                gameLoopHandler.postDelayed(this, GAME_UPDATE_INTERVAL_MS) // Still check periodically
                return
            }

            moveAllObstacles()
            checkAllCollisions()
            currentScore += SCORE_PER_TICK
            updateGameUI()

            gameLoopHandler.postDelayed(this, GAME_UPDATE_INTERVAL_MS)
        }
    }

    private val obstacleGeneratorRunnable = object : Runnable {
        override fun run() {
            if (!isGameEffectivelyRunning) { // Check combined flag
                obstacleGenerationHandler.postDelayed(this, OBSTACLE_GENERATION_INTERVAL_MS) // Still check
                return
            }
            spawnNewObstacle()
            obstacleGenerationHandler.postDelayed(this, OBSTACLE_GENERATION_INTERVAL_MS)
        }
    }

    private fun spawnNewObstacle() {
        // ... (same as before)
        if (laneCenterPositionsX.all { it == 0 }) {
            Log.w(TAG, "Lanes not initialized, skipping obstacle spawn.")
            return
        }
        val newObstacle = ImageView(this)
        newObstacle.setImageResource(R.drawable.obstacle)
        val layoutParams = ConstraintLayout.LayoutParams(obstacleVisualSizePixels, obstacleVisualSizePixels)
        newObstacle.layoutParams = layoutParams
        val spawnLane = Random.nextInt(laneCenterPositionsX.size)
        val obstacleCenterX = laneCenterPositionsX[spawnLane]
        newObstacle.translationX = (obstacleCenterX - obstacleVisualSizePixels / 2).toFloat()
        newObstacle.translationY = -obstacleVisualSizePixels.toFloat()
        gameLayout.addView(newObstacle)
        activeObstacles.add(newObstacle)
        Log.d(TAG, "Spawned obstacle in lane $spawnLane at X: ${newObstacle.translationX}")
    }

    private fun moveAllObstacles() {
        // ... (same as before)
        val iterator = activeObstacles.iterator()
        while (iterator.hasNext()) {
            val obstacle = iterator.next()
            obstacle.translationY += OBSTACLE_SPEED_PIXELS_PER_TICK
            if (obstacle.translationY > gameLayout.height) {
                gameLayout.removeView(obstacle)
                iterator.remove()
                currentScore += SCORE_PER_DODGED_OBSTACLE
                Log.d(TAG, "Obstacle passed. Score: $currentScore")
            }
        }
    }

    private fun checkAllCollisions() {
        // ... (same as before)
        if (carVisualWidthPixels == 0 || carVisualHeightPixels == 0) return
        val carHitboxWidth = carVisualWidthPixels * CAR_HITBOX_SCALE_FACTOR
        val carHitboxHeight = carVisualHeightPixels * CAR_HITBOX_SCALE_FACTOR
        val carVisualLeft = carImageView.x
        val carVisualTop = carImageView.y
        val carHitboxLeft = carVisualLeft + (carVisualWidthPixels - carHitboxWidth) / 2f
        val carHitboxRight = carHitboxLeft + carHitboxWidth
        val carHitboxTop = carVisualTop + (carVisualHeightPixels - carHitboxHeight) / 2f
        val carHitboxBottom = carHitboxTop + carHitboxHeight

        val iterator = activeObstacles.iterator()
        while (iterator.hasNext()) {
            val obstacle = iterator.next()
            if (obstacle.width == 0 || obstacle.height == 0) continue
            val obstacleCurrentWidth = obstacle.width
            val obstacleCurrentHeight = obstacle.height
            val obstacleHitboxWidth = obstacleCurrentWidth * OBSTACLE_HITBOX_SCALE_FACTOR
            val obstacleHitboxHeight = obstacleCurrentHeight * OBSTACLE_HITBOX_SCALE_FACTOR
            val obstacleVisualLeft = obstacle.x
            val obstacleVisualTop = obstacle.y
            val obstacleHitboxLeft = obstacleVisualLeft + (obstacleCurrentWidth - obstacleHitboxWidth) / 2f
            val obstacleHitboxRight = obstacleHitboxLeft + obstacleHitboxWidth
            val obstacleHitboxTop = obstacleVisualTop + (obstacleCurrentHeight - obstacleHitboxHeight) / 2f
            val obstacleHitboxBottom = obstacleHitboxTop + obstacleHitboxHeight

            if (carHitboxRight > obstacleHitboxLeft &&
                carHitboxLeft < obstacleHitboxRight &&
                carHitboxBottom > obstacleHitboxTop &&
                carHitboxTop < obstacleHitboxBottom) {
                Log.i(TAG, "Collision detected with obstacle at X:${obstacle.x}, Y:${obstacle.y}")
                gameLayout.removeView(obstacle)
                iterator.remove()
                processCollision()
                break
            }
        }
    }

    private fun processCollision() {
        // ... (same as before)
        currentLives--
        triggerVibration()
        Toast.makeText(this, getString(R.string.crash_message, currentLives), Toast.LENGTH_SHORT).show()
        if (currentLives <= 0) {
            gameOver()
        }
    }

    private fun triggerVibration() {
        // ... (same as before)
        if (vibrator?.hasVibrator() == true) {
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
        isGameEffectivelyRunning = false // Stop game logic
        // Handlers will stop due to isGameEffectivelyRunning check
        // gameLoopHandler.removeCallbacks(gameRunnable)
        // obstacleGenerationHandler.removeCallbacks(obstacleGeneratorRunnable)

        saveScoreToList(currentScore) // Save current score to the list
        updateHighScore() // Update single high score (if you still want this separate)

        Toast.makeText(this, getString(R.string.game_over_message, currentScore), Toast.LENGTH_LONG).show()
        Handler(Looper.getMainLooper()).postDelayed({
            startGame()
        }, 3000)
    }

    private fun updateHighScore() { // Renamed from saveHighScore to avoid confusion
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

    private fun saveScoreToList(score: Int) {
        val sharedPref = getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
        val gson = Gson()
        val jsonScores = sharedPref.getString(PREVIOUS_SCORES_KEY, null)
        val type = object : TypeToken<MutableList<Int>>() {}.type
        val previousScores: MutableList<Int> = if (jsonScores != null) {
            gson.fromJson(jsonScores, type)
        } else {
            mutableListOf()
        }

        previousScores.add(0, score) // Add new score to the beginning
        // Keep only the last MAX_SAVED_SCORES
        while (previousScores.size > MAX_SAVED_SCORES) {
            previousScores.removeAt(previousScores.size - 1)
        }

        val updatedJsonScores = gson.toJson(previousScores)
        with(sharedPref.edit()) {
            putString(PREVIOUS_SCORES_KEY, updatedJsonScores)
            apply()
        }
        Log.d(TAG, "Saved score list: $updatedJsonScores")
    }


    private fun updateGameUI() {
        scoreTextView.text = getString(R.string.score_format, currentScore)
        livesTextView.text = getString(R.string.lives_format, currentLives)
    }

    override fun onPause() {
        super.onPause()
        if (isGameEffectivelyRunning) { // If game was running (not manually paused, not game over)
            Log.d(TAG, "Activity onPause: Game was running, now effectively pausing.")
            // isGamePausedManually remains false if system paused it
            isGameEffectivelyRunning = false // Stop game logic
            pauseButton.setImageResource(R.drawable.ic_play) // Show play icon as game is now effectively paused
            // Handlers will stop based on isGameEffectivelyRunning
        }
    }

    override fun onResume() {
        super.onResume()
        // Resume if game is not manually paused by user and not game over
        if (!isGamePausedManually && currentLives > 0 && screenWidthPixels > 0) {
            Log.d(TAG, "Activity onResume: Resuming game logic.")
            isGameEffectivelyRunning = true
            pauseButton.setImageResource(R.drawable.ic_pause) // Show pause icon
            pauseScreenLayout.visibility = View.GONE // Ensure pause screen is hidden

            // Restart handlers if they are not already running
            if (!gameLoopHandler.hasCallbacks(gameRunnable)) {
                gameLoopHandler.post(gameRunnable)
            }
            if (!obstacleGenerationHandler.hasCallbacks(obstacleGeneratorRunnable)) {
                obstacleGenerationHandler.post(obstacleGeneratorRunnable)
            }
        } else if (isGamePausedManually) {
            Log.d(TAG, "Activity onResume: Game is still manually paused.")
            // Keep pause screen visible, show play icon
            pauseButton.setImageResource(R.drawable.ic_play)
            pauseScreenLayout.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "GameActivity destroyed. Cleaning up handlers.")
        isGameEffectivelyRunning = false
        gameLoopHandler.removeCallbacksAndMessages(null)
        obstacleGenerationHandler.removeCallbacksAndMessages(null)
    }
}
