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
import androidx.annotation.RequiresApi
import java.io.IOException
import kotlin.math.abs // Import abs for absolute value

class GameActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        private const val TAG = "GameActivity_TiltDebug" // Changed TAG for focused debugging
        private const val INITIAL_LIVES = 3
        private const val OBSTACLE_GENERATION_INTERVAL_MS = 1800L
        private const val GAME_UPDATE_INTERVAL_MS = 50L
        private const val OBSTACLE_SIZE_DP = 50
        private const val CAR_HITBOX_SCALE_FACTOR = 0.70f
        private const val OBSTACLE_HITBOX_SCALE_FACTOR = 0.70f
        private const val SCORE_PER_DODGED_OBSTACLE = 10
        private const val SCORE_PER_COIN = 25
        private const val SCORE_PER_TICK = 0

        // Tilt Control Parameters - **ADJUST THESE VALUES**
        private const val ROLL_SENSITIVITY_DEGREES = 20.0f // Max roll in degrees (e.g., 20-30 deg) for full lane span
        private const val TILT_DEAD_ZONE_DEGREES = 3.0f   // Ignore tilts smaller than this (in degrees)
        private const val SENSOR_UPDATE_DEBOUNCE_MS = 100L // How often to process sensor for lane change

        private const val PITCH_SPEED_THRESHOLD_DEGREES = 15.0f // Tilt for speed change (degrees)
        private const val SPEED_FACTOR_BONUS_FAST = 1.3f
        private const val SPEED_FACTOR_BONUS_SLOW = 0.7f

        private const val PREVIOUS_SCORES_KEY = "previous_scores"
        private const val MAX_SAVED_SCORES = 10
        private const val MAX_LANES = 5
        private var BASE_OBSTACLE_SPEED_PIXELS_PER_TICK = 15
    }

    // ... (Keep all other existing UI elements, game state variables, etc.)
    private lateinit var gameLayout: ConstraintLayout
    private lateinit var carImageView: ImageView
    private lateinit var leftButton: ImageButton
    private lateinit var rightButton: ImageButton
    private lateinit var scoreTextView: TextView
    private lateinit var livesTextView: TextView
    private lateinit var pauseButton: ImageButton
    private lateinit var pauseScreenLayout: ConstraintLayout
    private lateinit var resumeButton: Button
    private lateinit var distanceTextView: TextView

    private val activeObstacles = mutableListOf<ImageView>()
    private val activeCoins = mutableListOf<ImageView>()
    private val gameLoopHandler = Handler(Looper.getMainLooper())
    private val obstacleGenerationHandler = Handler(Looper.getMainLooper())
    private var vibrator: Vibrator? = null

    private var currentScore = 0
    private var currentLives = INITIAL_LIVES
    private var isGameEffectivelyRunning = false
    private var isGamePausedManually = false

    private var currentCarLane = MAX_LANES / 2
    private val laneCenterPositionsX = IntArray(MAX_LANES)
    private var screenWidthPixels = 0
    private var carVisualWidthPixels = 0
    private var carVisualHeightPixels = 0
    private var obstacleVisualSizePixels = 0
    private var coinVisualSizePixels = 0

    private var currentGameMode: String = MainActivity.MODE_BUTTONS
    private var baseSpeedFactor: Float = 1.0f
    private var dynamicBonusSpeedFactor: Float = 1.0f

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3) // Values are in RADIANS
    private var lastSensorLaneUpdateTime: Long = 0

    private lateinit var soundPool: SoundPool
    private var crashSoundId: Int = 0
    private var coinSoundId: Int = 0
    private var soundPoolLoadedCount = 0
    private val totalSoundsToLoad = 2

    private var distanceTravelled = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game) // Make sure this is activity_game_xml_5lanes

        currentGameMode = intent.getStringExtra(MainActivity.EXTRA_GAME_MODE) ?: MainActivity.MODE_BUTTONS
        val speedSetting = intent.getStringExtra(MainActivity.EXTRA_GAME_SPEED) ?: MainActivity.SPEED_SLOW
        baseSpeedFactor = if (speedSetting == MainActivity.SPEED_FAST) 1.5f else 1.0f
        BASE_OBSTACLE_SPEED_PIXELS_PER_TICK = (15 * baseSpeedFactor).toInt()

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
            coinVisualSizePixels = (OBSTACLE_SIZE_DP * 0.6f * resources.displayMetrics.density).toInt()

            if (screenWidthPixels == 0 || carVisualWidthPixels == 0 || carVisualHeightPixels == 0) {
                Log.e(TAG, "Layout not ready, cannot initialize game.")
                Toast.makeText(this, "Error initializing game layout.", Toast.LENGTH_LONG).show()
                finish()
                return@post
            }
            calculateLaneCenterPositions()
            currentCarLane = MAX_LANES / 2
            startGame()
        }
    }

    // ... (Keep initializeUIElements, setupButtonListeners, togglePauseGame, calculateLaneCenterPositions, positionCarInCurrentLane, moveCarLeft, moveCarRight, startGame, gameRunnable, obstacleGeneratorRunnable, spawnNewObstacle, spawnNewCoin, moveAllObstacles, moveAllCoins, checkAllCollisions, checkCoinCollisions, processCollision, triggerVibration, gameOver, updateHighScore, saveScoreToList, updateGameUI from GameActivity_kt_5lanes)
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
        distanceTextView = findViewById(R.id.distance_text)
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
            .setMaxStreams(3)
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
        try {
            crashSoundId = soundPool.load(this, R.raw.crash, 1)
            coinSoundId = soundPool.load(this, R.raw.coin, 1)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sounds from res/raw", e)
        }
    }

    private fun playSound(soundId: Int) {
        if (soundId != 0 && soundPoolLoadedCount >= totalSoundsToLoad) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        } else if (soundId == 0) {
            Log.w(TAG, "Attempted to play sound with ID 0")
        }
    }


    private fun setupButtonListeners() {
        leftButton.setOnClickListener { if (isGameEffectivelyRunning) moveCarLeft() }
        rightButton.setOnClickListener { if (isGameEffectivelyRunning) moveCarRight() }
        pauseButton.setOnClickListener { togglePauseGame() }
        resumeButton.setOnClickListener { togglePauseGame() }
    }

    private fun togglePauseGame() {
        if (currentLives <= 0 && !isGamePausedManually) return

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
        val lanePixelWidth = screenWidthPixels / MAX_LANES
        for (i in 0 until MAX_LANES) {
            laneCenterPositionsX[i] = (lanePixelWidth / 2) + (i * lanePixelWidth)
        }
        Log.d(TAG, "Calculated ${MAX_LANES} Lane Centers X: ${laneCenterPositionsX.joinToString()}")
    }

    private fun positionCarInCurrentLane() {
        Log.d(TAG, "positionCarInCurrentLane() CALLED for lane: $currentCarLane")
        if (currentCarLane < 0 || currentCarLane >= MAX_LANES || carVisualWidthPixels == 0) {
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
        if (currentCarLane < MAX_LANES - 1) {
            currentCarLane++
            positionCarInCurrentLane()
        }
    }

    private fun startGame() {
        Log.d(TAG, "Starting game...")
        currentScore = 0
        currentLives = INITIAL_LIVES
        distanceTravelled = 0
        isGamePausedManually = false
        isGameEffectivelyRunning = true
        pauseButton.setImageResource(R.drawable.ic_pause)
        pauseScreenLayout.visibility = View.GONE

        updateGameUI()

        activeObstacles.forEach { gameLayout.removeView(it) }
        activeObstacles.clear()
        activeCoins.forEach { gameLayout.removeView(it) }
        activeCoins.clear()

        currentCarLane = MAX_LANES / 2
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
            distanceTravelled += currentEffectiveSpeed / 15 // Adjusted scaling for "meters"
            moveAllObstacles(currentEffectiveSpeed)
            moveAllCoins(currentEffectiveSpeed)
            checkAllCollisions()
            checkCoinCollisions()
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
            if (Random.nextFloat() < 0.75) {
                spawnNewObstacle()
            } else {
                spawnNewCoin()
            }
            obstacleGenerationHandler.postDelayed(this, (OBSTACLE_GENERATION_INTERVAL_MS / baseSpeedFactor).toLong())
        }
    }

    private fun spawnNewObstacle() {
        if (laneCenterPositionsX.all { it == 0 }) {
            Log.w(TAG, "Lanes not initialized, skipping obstacle spawn.")
            return
        }
        val newObstacle = ImageView(this)
        newObstacle.setImageResource(R.drawable.obstacle)
        val layoutParams = ConstraintLayout.LayoutParams(obstacleVisualSizePixels, obstacleVisualSizePixels)
        newObstacle.layoutParams = layoutParams
        val spawnLane = Random.nextInt(MAX_LANES)
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
        newCoin.setImageResource(R.drawable.coin)
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
            }
        }
    }

    private fun checkAllCollisions() {
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
        val carHitboxWidth = carVisualWidthPixels * (CAR_HITBOX_SCALE_FACTOR + 0.1f)
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
            }
        }
    }

    private fun processCollision() {
        currentLives--
        triggerVibration()
        playSound(crashSoundId)
        Toast.makeText(this, getString(R.string.crash_message, currentLives), Toast.LENGTH_SHORT).show()
        if (currentLives <= 0) {
            gameOver()
        }
    }

    private fun triggerVibration() {
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
        val sharedPref = getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
        val highScore = sharedPref.getInt("high_score", 0)
        if (currentScore > highScore) {
            with(sharedPref.edit()) {
                putInt("high_score", currentScore)
                apply()
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
        previousScores.add(0, score)
        while (previousScores.size > MAX_SAVED_SCORES) {
            previousScores.removeAt(previousScores.size - 1)
        }
        val updatedJsonScores = gson.toJson(previousScores)
        with(sharedPref.edit()) {
            putString(PREVIOUS_SCORES_KEY, updatedJsonScores)
            apply()
        }
    }

    private fun updateGameUI() {
        scoreTextView.text = getString(R.string.score_format, currentScore)
        livesTextView.text = getString(R.string.lives_format, currentLives)
        distanceTextView.text = getString(R.string.distance_format, distanceTravelled)
    }


    // SensorEventListener Methods
//    override fun onSensorChanged(event: SensorEvent?) {
//        Log.d(TAG, "SensorEventListener.onSensorChanged() CALLED with event: $event")
//        if (currentGameMode != MainActivity.MODE_SENSOR || !isGameEffectivelyRunning || event == null) {
//            return
//        }
//
//        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
//            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
//        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
//            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
//        }
//
//        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
//        val orientationAnglesRad = FloatArray(3) // Keep original radians for pitch
//        SensorManager.getOrientation(rotationMatrix, orientationAnglesRad)
//
//        // Convert to degrees for easier thresholding and logging
//        val rollDegrees = Math.toDegrees(orientationAnglesRad[2].toDouble()).toFloat() // Roll: Left/Right tilt.
//        val pitchDegrees = Math.toDegrees(orientationAnglesRad[1].toDouble()).toFloat() // Pitch: Front/Back tilt.
//
//        Log.d(TAG, "Sensor - Raw Roll: ${orientationAnglesRad[2]} rad (${rollDegrees} deg), Raw Pitch: ${orientationAnglesRad[1]} rad (${pitchDegrees} deg)")
//
//        // --- Tilt to Move Car (Roll) ---
//        val currentTime = System.currentTimeMillis()
//        if (currentTime - lastSensorLaneUpdateTime > SENSOR_UPDATE_DEBOUNCE_MS) {
//            var targetLane = currentCarLane // Default to current lane
//
//            // Android's standard 'roll':
//            // - Positive roll: Left side of device tilts UP (device rolls to its RIGHT)
//            // - Negative roll: Right side of device tilts UP (device rolls to its LEFT)
//            // So, to move car LEFT (lane index decreases), user tilts device to THEIR LEFT (left edge down, right edge up) -> NEGATIVE roll.
//            // To move car RIGHT (lane index increases), user tilts device to THEIR RIGHT (right edge down, left edge up) -> POSITIVE roll.
//
//            if (abs(rollDegrees) > TILT_DEAD_ZONE_DEGREES) {
//                // Normalize the roll from -ROLL_SENSITIVITY_DEGREES to +ROLL_SENSITIVITY_DEGREES into a -1 to +1 range
//                // where -1 is full tilt for moving to lane 0, and +1 is full tilt for moving to lane MAX_LANES - 1.
//                // This interpretation means negative roll (tilt left) should map to left lanes, positive roll (tilt right) to right lanes.
//
//                // If rollDegrees is negative (tilt left for user), we want a negative factor.
//                // If rollDegrees is positive (tilt right for user), we want a positive factor.
//                val normalizedEffectiveRoll = (rollDegrees / ROLL_SENSITIVITY_DEGREES).coerceIn(-1.0f, 1.0f)
//
//                // Calculate the number of lanes to shift from the center lane
//                // MAX_LANES / 2 gives the span of lanes on one side of the center.
//                // For 5 lanes, center is lane 2. Span is 2 lanes to the left (0,1) and 2 to the right (3,4).
//                val laneShift = (normalizedEffectiveRoll * (MAX_LANES / 2.0f))
//
//                targetLane = (MAX_LANES / 2 + laneShift).toInt().coerceIn(0, MAX_LANES - 1)
//
//                Log.d(TAG, "Tilt - RollDeg: $rollDegrees, NormRoll: $normalizedEffectiveRoll, LaneShift: $laneShift, TargetLane: $targetLane")
//            }
//
//
//            if (targetLane != currentCarLane) {
//                currentCarLane = targetLane
//                positionCarInCurrentLane()
//                lastSensorLaneUpdateTime = currentTime
//                Log.d(TAG, "Sensor moved to lane: $currentCarLane")
//            }
//        }
//
//        // --- Bonus: Tilt for Speed (Pitch) ---
//        if (abs(pitchDegrees) > PITCH_SPEED_THRESHOLD_DEGREES) {
//            if (pitchDegrees < -PITCH_SPEED_THRESHOLD_DEGREES) { // Tilt forward (top of phone away) - Faster
//                dynamicBonusSpeedFactor = SPEED_FACTOR_BONUS_FAST
//                Log.d(TAG, "Tilt Speed: FASTER (Pitch: $pitchDegrees)")
//            } else if (pitchDegrees > PITCH_SPEED_THRESHOLD_DEGREES) { // Tilt back (top of phone towards you) - Slower
//                dynamicBonusSpeedFactor = SPEED_FACTOR_BONUS_SLOW
//                Log.d(TAG, "Tilt Speed: SLOWER (Pitch: $pitchDegrees)")
//            }
//        } else {
//            if (dynamicBonusSpeedFactor != 1.0f) { // Only log if it changes back to normal
//                Log.d(TAG, "Tilt Speed: NORMAL (Pitch: $pitchDegrees)")
//            }
//            dynamicBonusSpeedFactor = 1.0f // Normal speed
//        }
//    }
    // SensorEventListener Methods
    // SensorEventListener Methods
    override fun onSensorChanged(event: SensorEvent?) {
        // User-added log to confirm entry
        Log.d(TAG, "SensorEventListener.onSensorChanged() CALLED with event sensor type: ${event?.sensor?.type}")

        if (currentGameMode != MainActivity.MODE_SENSOR) {
            Log.v(TAG, "onSensorChanged: Not in sensor mode. Current mode: $currentGameMode")
            return
        }
        if (!isGameEffectivelyRunning) {
            Log.v(TAG, "onSensorChanged: Game not effectively running.")
            return
        }
        if (event == null) {
            Log.v(TAG, "onSensorChanged: Event is null.")
            return
        }

        // Process sensor data to get orientation
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            }
            else -> {
                // Log.v(TAG, "onSensorChanged: Event from other unhandled sensor type: ${event.sensor.type}")
                return // Only process accel and mag events for orientation
            }
        }

        // We need both accelerometer and magnetometer readings to get accurate orientation
        // SensorManager.getOrientation() relies on SensorManager.getRotationMatrix()
        // which typically needs both. Ensure both sensors are providing data.
        // If magnetometer is not present or not providing data, orientation might be less stable or inaccurate.

        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
        val orientationAnglesRad = FloatArray(3) // Output for orientation angles in radians
        SensorManager.getOrientation(rotationMatrix, orientationAnglesRad)

        // Convert radians to degrees for easier interpretation
        // orientationAnglesRad[0]: Azimuth (not used here)
        // orientationAnglesRad[1]: Pitch (forward/backward tilt)
        // orientationAnglesRad[2]: Roll (left/right tilt)
        val rollDegrees = Math.toDegrees(orientationAnglesRad[2].toDouble()).toFloat()
        val pitchDegrees = Math.toDegrees(orientationAnglesRad[1].toDouble()).toFloat()

        // --- DEBOUNCE: Only process sensor data periodically to avoid jitter ---
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSensorLaneUpdateTime < SENSOR_UPDATE_DEBOUNCE_MS) {
            // This log can be very frequent, use Log.v (verbose) or comment out if too noisy
            // Log.v(TAG, "onSensorChanged: Debounced. Diff: ${currentTime - lastSensorLaneUpdateTime}ms")
            return // Not enough time has passed, skip this update
        }
        lastSensorLaneUpdateTime = currentTime // Update the time for the next check


        // --- LOGGING: The most important log to check! ---
        Log.d(TAG, "Sensor Readout -> Roll: ${String.format("%.1f", rollDegrees)} deg, Pitch: ${String.format("%.1f", pitchDegrees)} deg")


        // --- TILT-TO-LANE LOGIC (ROLL) ---
        // Standard Android 'roll':
        // - Tilting device's LEFT edge DOWN (to steer left) -> NEGATIVE rollDegrees.
        // - Tilting device's RIGHT edge DOWN (to steer right) -> POSITIVE rollDegrees.
        var targetLane = currentCarLane // Default to the current lane

        // Define the tilt angle thresholds (in degrees) for each lane change for 5 lanes
        // These are examples, you'll need to TUNE them.
        val strongTiltLeft = -15.0f  // e.g., more than 15 degrees left tilt
        val moderateTiltLeft = -5.0f // e.g., between 5 and 15 degrees left tilt
        val moderateTiltRight = 5.0f // e.g., between 5 and 15 degrees right tilt
        val strongTiltRight = 15.0f  // e.g., more than 15 degrees right tilt
        // The dead zone is implicitly between -moderateTiltLeft and +moderateTiltRight if they are symmetrical like +/-5.0f

        if (rollDegrees < strongTiltLeft) {                // Strongest Left Tilt
            targetLane = 0
        } else if (rollDegrees < moderateTiltLeft) {       // Moderate Left Tilt
            targetLane = 1
        } else if (rollDegrees > strongTiltRight) {        // Strongest Right Tilt
            targetLane = 4
        } else if (rollDegrees > moderateTiltRight) {      // Moderate Right Tilt
            targetLane = 3
        } else {                                           // Center Zone (within dead zone or slight tilts)
            targetLane = 2 // Middle lane
        }

        // If the calculated target lane is different, update the car's position
        if (targetLane != currentCarLane) {
            Log.i(TAG, "TILT ACTION -> Roll: ${String.format("%.1f", rollDegrees)} deg => Changing lane from $currentCarLane to $targetLane")
            currentCarLane = targetLane
            positionCarInCurrentLane()
        } else {
            // This log helps see if the tilt is recognized but not enough to change lanes
            Log.d(TAG, "Tilt Check -> Roll: ${String.format("%.1f", rollDegrees)} deg => TargetLane $targetLane is same as CurrentLane $currentCarLane. No lane change.")
        }


        // --- Bonus: Tilt for Speed (Pitch) ---
        // (Using kotlin.math.abs)
        if (abs(pitchDegrees) > PITCH_SPEED_THRESHOLD_DEGREES) {
            if (pitchDegrees < -PITCH_SPEED_THRESHOLD_DEGREES) { // Tilt forward (top of phone away) - Faster
                if (dynamicBonusSpeedFactor != SPEED_FACTOR_BONUS_FAST) Log.d(TAG, "Tilt Speed: FASTER (Pitch: ${String.format("%.1f", pitchDegrees)} deg)")
                dynamicBonusSpeedFactor = SPEED_FACTOR_BONUS_FAST
            } else if (pitchDegrees > PITCH_SPEED_THRESHOLD_DEGREES) { // Tilt back (top of phone towards you) - Slower
                if (dynamicBonusSpeedFactor != SPEED_FACTOR_BONUS_SLOW) Log.d(TAG, "Tilt Speed: SLOWER (Pitch: ${String.format("%.1f", pitchDegrees)} deg)")
                dynamicBonusSpeedFactor = SPEED_FACTOR_BONUS_SLOW
            }
        } else {
            if (dynamicBonusSpeedFactor != 1.0f) { // Only log if it changes back to normal
                Log.d(TAG, "Tilt Speed: NORMAL (Pitch: ${String.format("%.1f", pitchDegrees)} deg)")
            }
            dynamicBonusSpeedFactor = 1.0f // Normal speed
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* Not used */ }

    @RequiresApi(Build.VERSION_CODES.Q)
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
                Log.d(TAG, "Sensor listeners REGISTERED.")
            }
        } else if (isGamePausedManually) {
            pauseButton.setImageResource(R.drawable.ic_play)
            pauseScreenLayout.visibility = View.VISIBLE
        }
    }

    override fun onPause() {
        super.onPause()
        if (isGameEffectivelyRunning) {
            isGameEffectivelyRunning = false // This will stop game loops
            pauseButton.setImageResource(R.drawable.ic_play)
            Log.d(TAG, "Activity Paused: Game was running, now effectively paused.")
        }
        if (::sensorManager.isInitialized) {
            sensorManager.unregisterListener(this)
            Log.d(TAG, "Sensor listeners UNREGISTERED.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "GameActivity destroyed. Cleaning up handlers.")
        isGameEffectivelyRunning = false
        gameLoopHandler.removeCallbacksAndMessages(null)
        obstacleGenerationHandler.removeCallbacksAndMessages(null)
        if (::sensorManager.isInitialized) {
            sensorManager.unregisterListener(this)
        }
        soundPool.release()
    }
}
