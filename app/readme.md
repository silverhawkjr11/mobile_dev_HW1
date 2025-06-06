# Obstacle Racer: An Android Mobile Game

This project is an endless runner style car game developed for Android using Kotlin in Android Studio. The primary goal is to dodge oncoming obstacles, collect coins, and survive as long as possible to achieve a high score.

##  Gameplay

The player controls a car on a five-lane highway. Obstacles and coins spawn at the top of the screen and move downwards. The player must steer the car left and right to avoid the obstacles and collect the coins. The game features a lives system, a scoring system based on time survived and coins collected, and a persistent high score list.

## Features

* **Five-Lane Road:** A dynamic and challenging five-lane highway.
* **Multiple Control Modes:**
    * **Button Controls:** Simple left/right on-screen buttons for steering.
    * **Sensor Controls:** Use your device's accelerometer to steer by tilting left and right, like a real steering wheel.
* **Selectable Speed:** Choose between "Slow" and "Fast" game speeds from the main menu for button controls.
* **Dynamic Obstacles & Coins:** Avoid randomly spawning obstacles and collect coins to boost your score.
* **Odometer:** Tracks and displays the distance traveled in each run.
* **Sound Effects & Vibration:** Features crash sounds, coin collection sounds, and vibration feedback for collisions, enhancing the user experience.
* **Pause/Resume Functionality:** A pause button allows players to suspend and resume the game at any time.
* **Persistent High Scores:** The game saves a list of the top 10 recent scores locally on the device, which can be viewed from the main menu.

## How to Play

1.  **Launch the Game:** The main menu appears.
2.  **Select a Game Mode:**
    * Choose **Button Controls (Slow/Fast)** for on-screen steering.
    * Choose **Tilt Controls** to steer by tilting your device.
3.  **Steer the Car:** Use the buttons or tilt your device left and right to move between the five lanes.
4.  **Avoid Obstacles:** Crashing into an obstacle will cost you one of your three lives.
5.  **Collect Coins:** Drive over coins to increase your score.
6.  **Survive:** The longer you drive and the more coins you collect, the higher your score!

## Technologies Used

* **Platform:** Android
* **Language:** Kotlin
* **IDE:** Android Studio
* **Core Components:**
    * `AppCompatActivity` for managing UI and lifecycle.
    * `ConstraintLayout` for creating complex and responsive UI.
    * `SensorManager` and `SensorEventListener` for implementing tilt controls (Accelerometer & Magnetometer).
    * `SoundPool` for managing and playing short audio clips efficiently.
    * `SharedPreferences` with `Gson` for local, persistent storage of high scores.
    * `Handler` for managing game loops and timed events.
