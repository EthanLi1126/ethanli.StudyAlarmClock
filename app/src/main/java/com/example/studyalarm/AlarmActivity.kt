package com.example.studyalarm

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.example.studyalarm.data.AppPreferences
import com.example.studyalarm.data.FlashcardItem
import com.example.studyalarm.data.FlashcardRepository
import com.example.studyalarm.receiver.AlarmReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import kotlin.concurrent.fixedRateTimer

class AlarmActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var fadeTimer: Timer? = null

    // Stage 0
    private lateinit var layoutStage0: LinearLayout
    private lateinit var tvCurrentTime: TextView
    private lateinit var btnDismissAlarm: Button

    // Stage 1
    private lateinit var layoutStage1: LinearLayout
    private lateinit var tvStage1Timer: TextView
    private lateinit var pbStage1Progress: ProgressBar
    private lateinit var containerStudyCards: LinearLayout
    private lateinit var btnStartQuizNow: Button
    private var countDownTimer: CountDownTimer? = null

    // Stage 2
    private lateinit var layoutStage2: LinearLayout
    private lateinit var tvStage2Attempts: TextView
    private lateinit var containerPrompts: LinearLayout
    private lateinit var containerTargets: LinearLayout

    private var currentCards: List<FlashcardItem> = emptyList()
    private var remainingAttempts = 0
    private var totalFailRounds = 0 // 🟢 紀錄答題累積失敗輪數
    private var selectedPromptButton: Button? = null
    private var selectedPromptCard: FlashcardItem? = null
    private var matchedCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockScreenFlags()
        setContentView(R.layout.activity_alarm)

        prefs = AppPreferences(this)
        FlashcardRepository.loadCustomCards(this)

        // 🟢 啟動震動與漸進式音量響鈴
        startVibration()
        playAlarmSoundWithFadeIn()

        initViews()
        showStage0()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(enabled = true) {
                override fun handleOnBackPressed() {
                    // 🟢 阻止使用者透過返回鍵直接退出鬧鐘
                }
            },
        )
    }

    // 🟢 震動控制邏輯
    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 800, 500, 800, 500)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 🟢 漸進式音量響鈴 (Fade-in)
    private fun playAlarmSoundWithFadeIn() {
        try {
            audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 10

            // 響鈴初始設定為最小音量 1
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0)

            val customUriStr = prefs.customRingtoneUri
            var alarmUri: Uri? = if (!customUriStr.isNullOrEmpty()) {
                customUriStr.toUri()
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }

            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            ringtone?.play()

            // 每 2 秒逐漸調高音量直到最大
            var currentVol = 1
            fadeTimer = fixedRateTimer("fade_in_timer", daemon = false, initialDelay = 2000L, period = 2000L) {
                if (currentVol < maxVol) {
                    currentVol++
                    audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, currentVol, 0)
                } else {
                    cancel()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarmSoundAndVibration() {
        fadeTimer?.cancel()
        fadeTimer = null
        ringtone?.stop()
        vibrator?.cancel()
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun initViews() {
        // Stage 0
        layoutStage0 = findViewById(R.id.layoutStage0)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        btnDismissAlarm = findViewById(R.id.btnDismissAlarm)

        // Stage 1
        layoutStage1 = findViewById(R.id.layoutStage1)
        tvStage1Timer = findViewById(R.id.tvStage1Timer)
        pbStage1Progress = findViewById(R.id.pbStage1Progress)
        containerStudyCards = findViewById(R.id.containerStudyCards)
        btnStartQuizNow = findViewById(R.id.btnStartQuizNow)

        // Stage 2
        layoutStage2 = findViewById(R.id.layoutStage2)
        tvStage2Attempts = findViewById(R.id.tvStage2Attempts)
        containerPrompts = findViewById(R.id.containerPrompts)
        containerTargets = findViewById(R.id.containerTargets)

        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        tvCurrentTime.text = sdf.format(Date())

        btnDismissAlarm.setOnClickListener {
            // 🟢 按下「關閉鬧鐘」進入 Stage 1 時自動停止響鈴與震動
            stopAlarmSoundAndVibration()
            showStage1()
        }

        btnStartQuizNow.setOnClickListener {
            countDownTimer?.cancel()
            showStage2()
        }
    }

    private fun showStage0() {
        layoutStage0.visibility = View.VISIBLE
        layoutStage1.visibility = View.GONE
        layoutStage2.visibility = View.GONE
    }

    private fun showStage1() {
        layoutStage0.visibility = View.GONE
        layoutStage1.visibility = View.VISIBLE
        layoutStage2.visibility = View.GONE

        currentCards = FlashcardRepository.getRandomCards(
            prefs.selectedCategory,
            prefs.questionCount,
        )

        containerStudyCards.removeAllViews()
        currentCards.forEach { card ->
            val cardView = TextView(this).apply {
                text = getString(R.string.card_display_format, card.category, card.prompt, card.target)
                textSize = 18f
                setPadding(24, 24, 24, 24)
                setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 12, 0, 12)
            }
            containerStudyCards.addView(cardView, params)
        }

        val totalSec = prefs.memorizeDurationSec
        pbStage1Progress.max = totalSec
        pbStage1Progress.progress = totalSec

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(totalSec * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secLeft = (millisUntilFinished / 1000).toInt()
                tvStage1Timer.text = getString(R.string.stage1_timer_format, secLeft)
                pbStage1Progress.progress = secLeft
            }

            override fun onFinish() {
                showStage2()
            }
        }.start()
    }

    private fun showStage2() {
        layoutStage0.visibility = View.GONE
        layoutStage1.visibility = View.GONE
        layoutStage2.visibility = View.VISIBLE

        remainingAttempts = prefs.maxAttempts
        updateAttemptsUI()

        matchedCount = 0
        resetPromptSelection()

        setupMatchingQuiz()
    }

    private fun updateAttemptsUI() {
        tvStage2Attempts.text = getString(R.string.stage2_attempts_format, remainingAttempts, totalFailRounds)
    }

    private fun setupMatchingQuiz() {
        containerPrompts.removeAllViews()
        containerTargets.removeAllViews()

        val shuffledPrompts = currentCards.shuffled()
        val shuffledTargets = currentCards.shuffled()

        shuffledPrompts.forEach { card ->
            val btn = Button(this).apply {
                text = card.prompt
                textSize = 16f
                isAllCaps = false
                setOnClickListener {
                    onPromptClicked(this, card)
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            containerPrompts.addView(btn, params)
        }

        shuffledTargets.forEach { card ->
            val btn = Button(this).apply {
                text = card.target
                textSize = 16f
                isAllCaps = false
                setOnClickListener {
                    onTargetClicked(this, card)
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            containerTargets.addView(btn, params)
        }
    }

    private fun resetPromptSelection() {
        for (i in 0 until containerPrompts.childCount) {
            val child = containerPrompts.getChildAt(i)
            if (child.isEnabled) {
                child.alpha = 1.0f
            }
        }
        selectedPromptButton = null
        selectedPromptCard = null
    }

    private fun onPromptClicked(button: Button, card: FlashcardItem) {
        resetPromptSelection()
        button.alpha = 0.5f
        selectedPromptButton = button
        selectedPromptCard = card
    }

    private fun onTargetClicked(targetButton: Button, targetCard: FlashcardItem) {
        val promptCard = selectedPromptCard
        val promptBtn = selectedPromptButton

        if ((promptCard == null) || (promptBtn == null)) {
            Toast.makeText(this, "請先選擇左側題目", Toast.LENGTH_SHORT).show()
            return
        }

        if (promptCard.id == targetCard.id) {
            promptBtn.isEnabled = false
            promptBtn.visibility = View.INVISIBLE
            targetButton.isEnabled = false
            targetButton.visibility = View.INVISIBLE

            matchedCount++
            resetPromptSelection()

            if (matchedCount == currentCards.size) {
                // 🟢 紀錄答題成功通關
                prefs.recordQuizResult(isSuccess = true)
                Toast.makeText(this, "🎉 恭喜過關！鬧鐘已完全解除", Toast.LENGTH_LONG).show()
                finish()
            }
        } else {
            remainingAttempts--
            updateAttemptsUI()

            resetPromptSelection()

            if (remainingAttempts <= 0) {
                totalFailRounds++
                if (totalFailRounds >= 2) {
                    // 🟢 紀錄答題失敗並排程 5 分鐘貪睡鬧鐘，自動關閉頁面
                    prefs.recordQuizResult(isSuccess = false)
                    scheduleSnoozeAlarmInMinutes(5)
                    Toast.makeText(
                        this,
                        "⚠️ 累積失敗 2 次！題目自動關閉，5 分鐘後將再次響鈴！",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    Toast.makeText(this, "挑戰失敗，請重新背誦！", Toast.LENGTH_SHORT).show()
                    showStage1()
                }
            } else {
                Toast.makeText(this, "❌ 配對錯誤，請再試一次", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🟢 排程 N 分鐘後的貪睡鬧鐘
    @Suppress("SameParameterValue")
    private fun scheduleSnoozeAlarmInMinutes(minutes: Int) {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val triggerTime = System.currentTimeMillis() + (minutes * 60 * 1000L)

        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, MainActivity.ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(this, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            this, 0, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        stopAlarmSoundAndVibration()
    }
}