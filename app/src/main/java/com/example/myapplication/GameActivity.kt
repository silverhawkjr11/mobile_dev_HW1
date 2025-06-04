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
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.random.Random
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.SoundPool
import android.media.AudioAttributes
import java.io.IOException

class GameActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        private const val TAG = "GameActivity"
        private const val INITIAL_LIVES = 3
        private const val OBSTACLE_GENERATION_INTERVAL_MS = 1800L // Can be adjusted by speed factor
        private const val GAME_UPDATE_INTERVAL_MS = 50L
        private const val OBSTACLE_SIZE_DP = 50 // Slightly smaller for more lanes
        private const val CAR_HITBOX_SCALE_FACTOR = 0.70f // Adjusted for potentially tighter spaces
        private const val OBSTACLE_HITBOX_SCALE_FACTOR = 0.70f
        private const val SCORE_PER_DODGED_OBSTACLE = 10
        private const val SCORE_PER_COIN = 25 // Score for collecting a coin
        private const val SCORE_PER_TICK = 0 // Set to 1 for time based score

        // Game Mode and Speed Constants (from MainActivity)
        // const val EXTRA_GAME_MODE = "extra_game_mode" (defined in MainActivity)
        // const val EXTRA_GAME_SPEED = "extra_game_speed" (defined in MainActivity)
        // const val MODE_BUTTONS = "mode_buttons" (defined in MainActivity)
        // const val MODE_SENSOR = "mode_sensor" (defined in MainActivity)
        // const val SPEED_SLOW = "slow" (defined in MainActivity)
        // const val SPEED_FAST = "fast" (defined in MainActivity)

        private const val SENSOR_SENSITIVITY_ROLL_MAP = (Math.PI / 6.0).toFloat() // e.g., map +/-30deg roll to full lane span
        private const val TILT_DEAD_ZONE = 0.08f // Radians, ignore small tilts around center for stability
        private const val PITCH_SPEED_THRESHOLD = 0.3f // Radians for bonus speed
        private const val SPEED_FACTOR_BONUS_FAST = 1.3f
        private const val SPEED_FACTOR_BONUS_SLOW = 0.7f


        private const val PREVIOUS_SCORES_KEY = "previous_scores"
        private const val MAX_SAVED_SCORES = 10

        private const val MAX_LANES = 5 // ***** UPDATED FOR 5 LANES *****
        private var BASE_OBSTACLE_SPEED_PIXELS_PER_TICK = 15 // Default base speed, will be adjusted
    }

    // UI Elements
    private lateinit var gameLayout: ConstraintLayout
    private lateinit var carImageView: ImageView
    private lateinit var leftButton: ImageButton
    private lateinit var rightButton: ImageButton
    private lateinit var scoreTextView: TextView
    private lateinit var livesTextView: TextView
    private lateinit var pauseButton: ImageButton
    private lateinit var pauseScreenLayout: ConstraintLayout
    private lateinit var resumeButton: Button
    private lateinit var distanceTextView: TextView // For Odometer

    // Game Objects & State
    private val activeObstacles = mutableListOf<ImageView>()
    private val activeCoins = mutableListOf<ImageView>() // For Coins
    private val gameLoopHandler = Handler(Looper.getMainLooper())
    private val obstacleGenerationHandler = Handler(Looper.getMainLooper())
    private var vibrator: Vibrator? = null

    private var currentScore = 0
    private var currentLives = INITIAL_LIVES
    private var isGameEffectivelyRunning = false
    private var isGamePausedManually = false

    private var currentCarLane = MAX_LANES / 2 // Start in the middle lane (e.g., lane 2 for 5 lanes)
    private val laneCenterPositionsX = IntArray(MAX_LANES) // ***** UPDATED FOR 5 LANES *****
    private var screenWidthPixels = 0
    private var carVisualWidthPixels = 0
    private var carVisualHeightPixels = 0
    private var obstacleVisualSizePixels = 0
    private var coinVisualSizePixels = 0 // For coins

    // Game Mode and Speed
    private var currentGameMode: String = MainActivity.MODE_BUTTONS
    private var baseSpeedFactor: Float = 1.0f // From slow/fast menu selection
    private var dynamicBonusSpeedFactor: Float = 1.0f // From pitch tilt

    // Sensor related variables
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var lastSensorLaneUpdateTime: Long = 0 // To throttle sensor-based lane changes

    // Sound related variables
    private lateinit var soundPool: SoundPool
    private var crashSoundId: Int = 0
    private var coinSoundId: Int = 0
    private var soundPoolLoadedCount = 0
    private val totalSoundsToLoad = 2 // crash + coin

    // Odometer
    private var distanceTravelled = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        currentGameMode = intent.getStringExtra(MainActivity.EXTRA_GAME_MODE) ?: MainActivity.MODE_BUTTONS
        val speedSetting = intent.getStringExtra(MainActivity.EXTRA_GAME_SPEED) ?: MainActivity.SPEED_SLOW
        baseSpeedFactor = if (speedSetting == MainActivity.SPEED_FAST) 1.5f else 1.0f // SPEED_FACTOR_FAST or _SLOW
        BASE_OBSTACLE_SPEED_PIXELS_PER_TICK = (15 * baseSpeedFactor).toInt() // Adjust base speed

        initializeUIElements()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        setupButtonListeners()
        initializeSoundPool()

        if (currentGameMode == MainActivity.MODE_SENSOR) {
            initializeSensors()
            leftButton.visibility = View.GONE
            rightButton.visibility = View.GONE
        } else {
            leftButton.visibility = View.VISIBLE
            rightButton.visibility = View.VISIBLE
        }

        gameLayout.post {
            screenWidthPixels = gameLayout.width
            carVisualWidthPixels = carImageView.width
            carVisualHeightPixels = carImageView.height
            obstacleVisualSizePixels = (OBSTACLE_SIZE_DP * resources.displayMetrics.density).toInt()
            coinVisualSizePixels = (OBSTACLE_SIZE_DP * 0.6f * resources.displayMetrics.density).toInt() // Coins smaller

            if (screenWidthPixels == 0 || carVisualWidthPixels == 0 || carVisualHeightPixels == 0) {
                Log.e(TAG, "Layout not ready, cannot initialize game.")
                Toast.makeText(this, "Error initializing game layout.", Toast.LENGTH_LONG).show()
                finish()
                return@post
            }
            calculateLaneCenterPositions()
            currentCarLane = MAX_LANES / 2 // Recalculate starting lane after positions are set
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
        pauseButton = findViewById(R.id.pause_button)
        pauseScreenLayout = findViewById(R.id.pause_screen_layout)
        resumeButton = findViewById(R.id.resume_button)
        distanceTextView = findViewById(R.id.distance_text) // Initialize Odometer TextView
    }

    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (accelerometer == null) {
            Log.e(TAG, "Accelerometer not available. Switching to button mode.")
            Toast.makeText(this, "Tilt control not available, using buttons.", Toast.LENGTH_LONG).show()
            currentGameMode = MainActivity.MODE_BUTTONS
            leftButton.visibility = View.VISIBLE
            rightButton.visibility = View.VISIBLE
        }
    }

    private fun initializeSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(3) // Max simultaneous sounds
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                soundPoolLoadedCount++
                Log.d(TAG, "Sound loaded: $sampleId. Total loaded: $soundPoolLoadedCount")
            } else {
                Log.e(TAG, "Error loading sound $sampleId, status $status")
            }
        }
        // Create a res/raw folder and add your sound files (e.g., crash.ogg, coin.ogg)
        try {
            crashSoundId = soundPool.load(this, R.raw.crash, 1) // Make sure you have res/raw/crash.ogg (or .wav)
            coinSoundId = soundPool.load(this, R.raw.coin, 1)   // Make sure you have res/raw/coin.ogg (or .wav)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sounds from res/raw", e)
            Toast.makeText(this, "Error loading game sounds.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playSound(soundId: Int) {
        if (soundId != 0 && soundPoolLoadedCount >= totalSoundsToLoad) { // Check if specific sound ID is valid and all sounds loaded
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        } else if (soundId == 0) {
            Log.w(TAG, "Attempted to play sound with ID 0")
        } else {
            Log.w(TAG, "Sounds not fully loaded, cannot play sound ID $soundId")
        }
    }


    private fun setupButtonListeners() {
        // ... (same as before from GameActivity_kt_pause) ...
        leftButton.setOnClickListener { if (isGameEffectivelyRunning) moveCarLeft() }
        rightButton.setOnClickListener { if (isGameEffectivelyRunning) moveCarRight() }
        pauseButton.setOnClickListener { togglePauseGame() }
        resumeButton.setOnClickListener { togglePauseGame() }
    }

    private fun togglePauseGame() {
        // ... (same as before from GameActivity_kt_pause) ...
        if (currentLives <= 0 && !isGamePausedManually) return // Don't allow pause/resume if game over and not already paused

        isGamePausedManually = !isGamePausedManually
        isGameEffectivelyRunning = !isGamePausedManually && currentLives > 0

        if (isGamePausedManually) {
            Log.d(TAG, "Game Paused by user.")
            pauseButton.setImageResource(R.drawable.ic_play)
            pauseScreenLayout.visibility = View.VISIBLE
        } else {
            Log.d(TAG, "Game Resumed by user.")
            pauseButton.setImageResource(R.drawable.ic_pause)
            pauseScreenLayout.visibility = View.GONE
            if (currentLives > 0 && !gameLoopHandler.hasCallbacks(gameRunnable)) {
                gameLoopHandler.post(gameRunnable)
                obstacleGenerationHandler.post(obstacleGeneratorRunnable)
            }
        }
    }

    private fun calculateLaneCenterPositions() {
        if (screenWidthPixels == 0) {
            Log.e(TAG, "Screen width is zero, cannot calculate lane positions.")
            return
        }
        val lanePixelWidth = screenWidthPixels / MAX_LANES // ***** USE MAX_LANES *****
        for (i in 0 until MAX_LANES) { // ***** USE MAX_LANES *****
            laneCenterPositionsX[i] = (lanePixelWidth / 2) + (i * lanePixelWidth)
        }
        Log.d(TAG, "Calculated ${MAX_LANES} Lane Centers X: ${laneCenterPositionsX.joinToString()}")
    }

    private fun positionCarInCurrentLane() {
        // ... (same as before, using the fix for centering) ...
        Log.d(TAG, "positionCarInCurrentLane() CALLED for lane: $currentCarLane")
        if (currentCarLane < 0 || currentCarLane >= MAX_LANES || carVisualWidthPixels == 0) { // ***** USE MAX_LANES *****
            Log.e(TAG, "CANNOT position car: Invalid lane ($currentCarLane for $MAX_LANES lanes), or car width ($carVisualWidthPixels), or screen width ($screenWidthPixels) is zero.")
            return
        }
        val targetCarCenterX = laneCenterPositionsX[currentCarLane]
        val targetTranslationX = (targetCarCenterX - screenWidthPixels / 2).toFloat()
        carImageView.translationX = targetTranslationX
        Log.d(TAG, "Car translationX AFTER setting: ${carImageView.translationX} (for lane $currentCarLane)")
    }

    private fun moveCarLeft() {
        if (currentCarLane > 0) {
            currentCarLane--
            positionCarInCurrentLane()
        }
    }

    private fun moveCarRight() {
        if (currentCarLane < MAX_LANES - 1) { // ***** USE MAX_LANES - 1 *****
            currentCarLane++
            positionCarInCurrentLane()
        }
    }

    private fun startGame() {
        // ... (same as before from GameActivity_kt_pause) ...
        Log.d(TAG, "Starting game...")
        currentScore = 0
        currentLives = INITIAL_LIVES
        distanceTravelled = 0 // Reset odometer
        isGamePausedManually = false
        isGameEffectivelyRunning = true
        pauseButton.setImageResource(R.drawable.ic_pause)
        pauseScreenLayout.visibility = View.GONE

        updateGameUI()

        activeObstacles.forEach { gameLayout.removeView(it) }
        activeObstacles.clear()
        activeCoins.forEach { gameLayout.removeView(it) } // Clear coins
        activeCoins.clear()


        currentCarLane = MAX_LANES / 2 // Ensure car starts in the middle of 5 lanes
        positionCarInCurrentLane()

        gameLoopHandler.removeCallbacks(gameRunnable)
        obstacleGenerationHandler.removeCallbacks(obstacleGeneratorRunnable)
        gameLoopHandler.post(gameRunnable)
        obstacleGenerationHandler.post(obstacleGeneratorRunnable)
    }

    private val gameRunnable = object : Runnable {
        override fun run() {
            if (!isGameEffectivelyRunning) {
                gameLoopHandler.postDelayed(this, GAME_UPDATE_INTERVAL_MS)
                return
            }

            val currentEffectiveSpeed = (BASE_OBSTACLE_SPEED_PIXELS_PER_TICK * dynamicBonusSpeedFactor).toInt()
            distanceTravelled += currentEffectiveSpeed / 10 // Adjust scaling for "meters"

            moveAllObstacles(currentEffectiveSpeed)
            moveAllCoins(currentEffectiveSpeed) // Move coins
            checkAllCollisions()
            checkCoinCollisions() // Check for coin collection
            currentScore += SCORE_PER_TICK
            updateGameUI()

            gameLoopHandler.postDelayed(this, GAME_UPDATE_INTERVAL_MS)
        }
    }

    private val obstacleGeneratorRunnable = object : Runnable {
        override fun run() {
            if (!isGameEffectivelyRunning) {
                obstacleGenerationHandler.postDelayed(this, (OBSTACLE_GENERATION_INTERVAL_MS / baseSpeedFactor).toLong())
                return
            }
            // Randomly spawn obstacle or coin
            if (Random.nextFloat() < 0.75) { // 75% chance to spawn obstacle
                spawnNewObstacle()
            } else { // 25% chance to spawn coin
                spawnNewCoin()
            }
            obstacleGenerationHandler.postDelayed(this, (OBSTACLE_GENERATION_INTERVAL_MS / baseSpeedFactor).toLong())
        }
    }

    private fun spawnNewObstacle() {
        // ... (same as before, but ensure it uses MAX_LANES for spawnLane) ...
        if (laneCenterPositionsX.all { it == 0 }) {
            Log.w(TAG, "Lanes not initialized, skipping obstacle spawn.")
            return
        }
        val newObstacle = ImageView(this)
        newObstacle.setImageResource(R.drawable.obstacle)
        val layoutParams = ConstraintLayout.LayoutParams(obstacleVisualSizePixels, obstacleVisualSizePixels)
        newObstacle.layoutParams = layoutParams
        val spawnLane = Random.nextInt(MAX_LANES) // ***** USE MAX_LANES *****
        val obstacleCenterX = laneCenterPositionsX[spawnLane]
        newObstacle.translationX = (obstacleCenterX - obstacleVisualSizePixels / 2).toFloat()
        newObstacle.translationY = -obstacleVisualSizePixels.toFloat()
        gameLayout.addView(newObstacle)
        activeObstacles.add(newObstacle)
    }

    private fun spawnNewCoin() {
        if (laneCenterPositionsX.all { it == 0 }) {
            Log.w(TAG, "Lanes not initialized, skipping coin spawn.")
            return
        }
        val newCoin = ImageView(this)
        newCoin.setImageResource(R.drawable.coin) // Make sure you have res/drawable/coin.xml
        val layoutParams = ConstraintLayout.LayoutParams(coinVisualSizePixels, coinVisualSizePixels)
        newCoin.layoutParams = layoutParams
        val spawnLane = Random.nextInt(MAX_LANES)
        val coinCenterX = laneCenterPositionsX[spawnLane]
        newCoin.translationX = (coinCenterX - coinVisualSizePixels / 2).toFloat()
        newCoin.translationY = -coinVisualSizePixels.toFloat()
        gameLayout.addView(newCoin)
        activeCoins.add(newCoin)
    }


    private fun moveAllObstacles(currentSpeed: Int) {
        val iterator = activeObstacles.iterator()
        while (iterator.hasNext()) {
            val obstacle = iterator.next()
            obstacle.translationY += currentSpeed

            if (obstacle.translationY > gameLayout.height) {
                gameLayout.removeView(obstacle)
                iterator.remove()
                currentScore += SCORE_PER_DODGED_OBSTACLE
            }
        }
    }

    private fun moveAllCoins(currentSpeed: Int) {
        val iterator = activeCoins.iterator()
        while (iterator.hasNext()) {
            val coin = iterator.next()
            coin.translationY += currentSpeed
            if (coin.translationY > gameLayout.height) {
                gameLayout.removeView(coin)
                iterator.remove()
                // No score if coin is missed
            }
        }
    }


    private fun checkAllCollisions() { // Obstacle collisions
        // ... (same as before from GameActivity_kt_pause) ...
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
            // ... (rest of collision logic for obstacles, same as before) ...
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
                Log.i(TAG, "Collision detected with obstacle")
                gameLayout.removeView(obstacle)
                iterator.remove()
                processCollision()
                break
            }
        }
    }

    private fun checkCoinCollisions() {
        if (carVisualWidthPixels == 0 || carVisualHeightPixels == 0) return

        val carHitboxWidth = carVisualWidthPixels * (CAR_HITBOX_SCALE_FACTOR + 0.1f) // Slightly larger hitbox for coins
        val carHitboxHeight = carVisualHeightPixels * (CAR_HITBOX_SCALE_FACTOR + 0.1f)
        val carVisualLeft = carImageView.x
        val carVisualTop = carImageView.y
        val carHitboxLeft = carVisualLeft + (carVisualWidthPixels - carHitboxWidth) / 2f
        val carHitboxRight = carHitboxLeft + carHitboxWidth
        val carHitboxTop = carVisualTop + (carVisualHeightPixels - carHitboxHeight) / 2f
        val carHitboxBottom = carHitboxTop + carHitboxHeight

        val iterator = activeCoins.iterator()
        while (iterator.hasNext()) {
            val coin = iterator.next()
            if (coin.width == 0 || coin.height == 0) continue

            // Assuming coin hitbox is its visual size for simplicity
            val coinVisualLeft = coin.x
            val coinVisualTop = coin.y
            val coinVisualRight = coin.x + coin.width
            val coinVisualBottom = coin.y + coin.height

            if (carHitboxRight > coinVisualLeft &&
                carHitboxLeft < coinVisualRight &&
                carHitboxBottom > coinVisualTop &&
                carHitboxTop < coinVisualBottom) {

                Log.i(TAG, "Coin collected!")
                currentScore += SCORE_PER_COIN
                playSound(coinSoundId)
                Toast.makeText(this, getString(R.string.coin_collected), Toast.LENGTH_SHORT).show()
                gameLayout.removeView(coin)
                iterator.remove()
                // updateGameUI() will be called by the main game loop
            }
        }
    }


    private fun processCollision() {
        currentLives--
        triggerVibration()
        playSound(crashSoundId) // Play crash sound
        Toast.makeText(this, getString(R.string.crash_message, currentLives), Toast.LENGTH_SHORT).show()
        if (currentLives <= 0) {
            gameOver()
        }
    }

    private fun triggerVibration() {
        // ... (same as before) ...
    }

    private fun gameOver() {
        // ... (same as before, including saveScoreToList and updateHighScore) ...
        Log.i(TAG, "Game Over. Final Score: $currentScore. Distance: $distanceTravelled")
        isGameEffectivelyRunning = false
        saveScoreToList(currentScore)
        updateHighScore()
        Toast.makeText(this, getString(R.string.game_over_message, currentScore), Toast.LENGTH_LONG).show()
        Handler(Looper.getMainLooper()).postDelayed({
            startGame()
        }, 3000)
    }

    private fun updateHighScore() {
        // ... (same as before) ...
    }

    private fun saveScoreToList(score: Int) {
        // ... (same as before) ...
    }

    private fun updateGameUI() {
        scoreTextView.text = getString(R.string.score_format, currentScore)
        livesTextView.text = getString(R.string.lives_format, currentLives)
        distanceTextView.text = getString(R.string.distance_format, distanceTravelled) // Update odometer
    }

    // SensorEventListener Methods
    override fun onSensorChanged(event: SensorEvent?) {
        if (currentGameMode != MainActivity.MODE_SENSOR || !isGameEffectivelyRunning || event == null) {
            return
        }

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }

        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val roll = orientationAngles[2] // Roll: Left/Right tilt. Positive when device tilts left.
        val pitch = orientationAngles[1] // Pitch: Front/Back tilt. Positive when bottom tilts up.

        // --- Tilt to Move Car ---
        // Debounce sensor updates for lane changes to avoid overly sensitive movement
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSensorLaneUpdateTime > 150) { // Update lane max ~6 times/sec
            var desiredLaneBasedOnRoll = MAX_LANES / 2 // Default to middle lane
            // Map roll to a lane index. If roll is negative (tilt right), index increases.
            // If roll is positive (tilt left), index decreases.
            if (kotlin.math.abs(roll) > TILT_DEAD_ZONE) { // Only consider significant tilts
                // Map roll from -SENSOR_SENSITIVITY_ROLL_MAP to +SENSOR_SENSITIVITY_ROLL_MAP
                // to an offset from the middle lane.
                // Example: roll of -0.5 (strong right tilt) should push towards MAX_LANES-1
                // roll of +0.5 (strong left tilt) should push towards 0
                val normalizedRoll = (roll / SENSOR_SENSITIVITY_ROLL_MAP).coerceIn(-1.0f, 1.0f)
                // This normalizedRoll is -1 (full right) to +1 (full left)
                // We want to map this to a lane change delta.
                // Middle lane is MAX_LANES / 2.
                // If normalizedRoll is -1, we want lane MAX_LANES -1. Delta = (MAX_LANES-1) - (MAX_LANES/2)
                // If normalizedRoll is +1, we want lane 0. Delta = 0 - (MAX_LANES/2)
                // So, delta is roughly -normalizedRoll * (MAX_LANES / 2)
                val laneOffset = (-normalizedRoll * (MAX_LANES / 2.0f)).toInt()
                desiredLaneBasedOnRoll = (MAX_LANES / 2) + laneOffset
            }

            val newLane = desiredLaneBasedOnRoll.coerceIn(0, MAX_LANES - 1)

            if (newLane != currentCarLane) {
                currentCarLane = newLane
                positionCarInCurrentLane()
                lastSensorLaneUpdateTime = currentTime
                Log.d(TAG, "Sensor moved to lane: $currentCarLane based on roll: $roll")
            }
        }


        // --- Bonus: Tilt for Speed ---
        if (kotlin.math.abs(pitch) > PITCH_SPEED_THRESHOLD) {
            if (pitch < -PITCH_SPEED_THRESHOLD) { // Tilt forward (top of phone away) - Faster
                dynamicBonusSpeedFactor = SPEED_FACTOR_BONUS_FAST
            } else if (pitch > PITCH_SPEED_THRESHOLD) { // Tilt back (top of phone towards you) - Slower
                dynamicBonusSpeedFactor = SPEED_FACTOR_BONUS_SLOW
            }
        } else {
            dynamicBonusSpeedFactor = 1.0f // Normal speed
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* Not used */ }

    override fun onResume() {
        super.onResume()
        if (!isGamePausedManually && currentLives > 0 && screenWidthPixels > 0) {
            isGameEffectivelyRunning = true
            pauseButton.setImageResource(R.drawable.ic_pause)
            pauseScreenLayout.visibility = View.GONE
            if (!gameLoopHandler.hasCallbacks(gameRunnable)) gameLoopHandler.post(gameRunnable)
            if (!obstacleGenerationHandler.hasCallbacks(obstacleGeneratorRunnable)) obstacleGenerationHandler.post(obstacleGeneratorRunnable)

            if (currentGameMode == MainActivity.MODE_SENSOR) {
                accelerometer?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                magnetometer?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            }
        } else if (isGamePausedManually) {
            pauseButton.setImageResource(R.drawable.ic_play)
            pauseScreenLayout.visibility = View.VISIBLE
        }
    }

    override fun onPause() {
        super.onPause()
        if (isGameEffectivelyRunning) {
            isGameEffectivelyRunning = false
            pauseButton.setImageResource(R.drawable.ic_play) // Show play if game was running
        }
        // Always unregister sensor listeners on pause if they were registered
        if (::sensorManager.isInitialized) { // Check if sensorManager was initialized
            sensorManager.unregisterListener(this)
        }
        // Handlers will stop checking isGameEffectivelyRunning
    }

    override fun onDestroy() {
        super.onDestroy()
        isGameEffectivelyRunning = false
        gameLoopHandler.removeCallbacksAndMessages(null)
        obstacleGenerationHandler.removeCallbacksAndMessages(null)
        if (::sensorManager.isInitialized) {
            sensorManager.unregisterListener(this)
        }
        soundPool.release() // Release SoundPool resources
    }
}
