package com.example.studyalarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.studyalarm.data.AppPreferences
import com.example.studyalarm.data.FlashcardRepository
import com.example.studyalarm.receiver.AlarmReceiver
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        const val ALARM_REQUEST_CODE = 1001
    }

    private lateinit var prefs: AppPreferences

    private lateinit var tvStatsSummary: TextView
    private lateinit var tvAlarmTime: TextView
    private lateinit var tvAlarmStatus: TextView
    private lateinit var btnSelectTime: Button
    private lateinit var btnSetAlarm: Button
    private lateinit var btnCancelAlarm: Button
    private lateinit var btnTestAlarmNow: Button
    private lateinit var btnManageCards: Button // 🟢 檢視與管理題庫按鈕（替換原單筆新增）
    private lateinit var btnImportCards: Button // 🟢 貼上 CSV 匯入按鈕（唯一新增入口）
    private lateinit var btnSelectRingtone: Button

    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerMemorizeDuration: Spinner
    private lateinit var spinnerQuestionCount: Spinner
    private lateinit var spinnerMaxAttempts: Spinner

    private var selectedHour = 7
    private var selectedMinute = 0
    private lateinit var categories: MutableList<String>
    private lateinit var catAdapter: ArrayAdapter<String>

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "請允許通知權限以確保鬧鐘正常顯示", Toast.LENGTH_LONG).show()
            }
        }

    private val ringtonePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                }

                if (uri != null) {
                    prefs.customRingtoneUri = uri.toString()
                    val ringtone = RingtoneManager.getRingtone(this, uri)
                    val title = ringtone.getTitle(this)
                    Toast.makeText(this, "已選擇鈴聲：$title", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.customRingtoneUri = null
                    Toast.makeText(this, "已恢復為預設鬧鐘鈴聲", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        FlashcardRepository.loadCustomCards(this)

        prefs = AppPreferences(this)
        selectedHour = prefs.alarmHour
        selectedMinute = prefs.alarmMinute

        initViews()
        setupSettingsSpinners()
        checkAndRequestPermissions()
        updateAlarmStatusUI()
    }

    override fun onResume() {
        super.onResume()
        updateStatsUI()
    }

    private fun initViews() {
        tvStatsSummary = findViewById(R.id.tvStatsSummary)
        tvAlarmTime = findViewById(R.id.tvAlarmTime)
        tvAlarmStatus = findViewById(R.id.tvAlarmStatus)
        btnSelectTime = findViewById(R.id.btnSelectTime)
        btnSetAlarm = findViewById(R.id.btnSetAlarm)
        btnCancelAlarm = findViewById(R.id.btnCancelAlarm)
        btnTestAlarmNow = findViewById(R.id.btnTestAlarmNow)
        btnManageCards = findViewById(R.id.btnAddCard) // 綁定原本 btnAddCard ID 改為管理功能
        btnManageCards.text = "🔍 瀏覽與管理題庫"
        btnImportCards = findViewById(R.id.btnImportCards)
        btnSelectRingtone = findViewById(R.id.btnSelectRingtone)

        spinnerCategory = findViewById(R.id.spinnerCategory)
        spinnerMemorizeDuration = findViewById(R.id.spinnerMemorizeDuration)
        spinnerQuestionCount = findViewById(R.id.spinnerQuestionCount)
        spinnerMaxAttempts = findViewById(R.id.spinnerMaxAttempts)

        updateTimeText(selectedHour, selectedMinute)

        btnSelectTime.setOnClickListener { showTimePicker() }
        btnSetAlarm.setOnClickListener { scheduleExactAlarm(selectedHour, selectedMinute) }
        btnCancelAlarm.setOnClickListener { cancelAlarm() }
        btnTestAlarmNow.setOnClickListener { scheduleTestAlarmInSeconds(5) }
        btnManageCards.setOnClickListener { showManageCardsDialog() } // 🟢 開啟瀏覽與刪除視窗
        btnImportCards.setOnClickListener { showImportTextDialog() } // 🟢 開啟貼上 CSV 對話框
        btnSelectRingtone.setOnClickListener { showRingtonePicker() }
    }

    // 🟢 顯示檢視與刪除題庫對話框
    private fun showManageCardsDialog() {
        val currentCategory = prefs.selectedCategory

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manage_cards, null)
        val tvManageCategoryTitle = dialogView.findViewById<TextView>(R.id.tvManageCategoryTitle)
        val containerManageCards = dialogView.findViewById<LinearLayout>(R.id.containerManageCards)

        val dialog = AlertDialog.Builder(this)
            .setTitle("題庫瀏覽與管理")
            .setView(dialogView)
            .setPositiveButton("關閉", null)
            .create()

        fun populateCards() {
            containerManageCards.removeAllViews()
            val currentCards = FlashcardRepository.getCardsByCategory(currentCategory)
            tvManageCategoryTitle.text = getString(R.string.manage_category_title, currentCategory, currentCards.size)

            if (currentCards.isEmpty()) {
                val tvEmpty = TextView(this).apply {
                    text = "此分類目前沒有題目"
                    setPadding(0, 32, 0, 32)
                }
                containerManageCards.addView(tvEmpty)
                return
            }

            currentCards.forEach { card ->
                val cardRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(8, 16, 8, 16)
                    gravity = Gravity.CENTER_VERTICAL
                }

                val tvCardInfo = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = getString(R.string.card_manage_item_format, card.prompt, card.target)
                    textSize = 14f
                }

                val btnDelete = Button(this).apply {
                    text = "刪除"
                    setTextColor(Color.RED)
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("刪除題目")
                            .setMessage("確定要刪除「${card.prompt}」嗎？")
                            .setPositiveButton("確定") { _, _ ->
                                FlashcardRepository.deleteCard(this@MainActivity, card.id)
                                populateCards() // 刷新彈出視窗內的列表
                                refreshCategorySpinner() // 同步刷新主畫面下拉選單
                                Toast.makeText(this@MainActivity, "已刪除題目", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }

                cardRow.addView(tvCardInfo)
                cardRow.addView(btnDelete)
                containerManageCards.addView(cardRow)
            }
        }

        populateCards()
        dialog.show()
    }

    // 🟢 貼上 CSV 文字匯入題庫對話框
    private fun showImportTextDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("📋 貼上 CSV 文字匯入題目")

        val etCsvContent = EditText(this).apply {
            hint = "每行格式：題目,答案,分類\n\n範例：\nFemur, 股骨, 解剖學\nClavicle, 鎖骨, 解剖學\n\n（若未填寫分類，預設歸類於「${prefs.selectedCategory}」）"
            minLines = 6
            maxLines = 12
            setPadding(32, 24, 32, 24)
            gravity = Gravity.TOP or Gravity.START
        }

        builder.setView(etCsvContent)
        builder.setPositiveButton("匯入") { dialog, _ ->
            val text = etCsvContent.text.toString().trim()
            if (text.isNotEmpty()) {
                val count = FlashcardRepository.importCardsFromCsvText(
                    context = this,
                    csvText = text,
                    defaultCategory = prefs.selectedCategory
                )

                if (count > 0) {
                    refreshCategorySpinner()
                    Toast.makeText(this, "🎉 成功匯入 $count 筆題目！", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "⚠️ 匯入失敗，請確認每行格式 (題目,答案,分類)", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "請先貼上 CSV 內容", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("取消") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun refreshCategorySpinner() {
        categories.clear()
        categories.addAll(FlashcardRepository.getCategories())
        catAdapter.notifyDataSetChanged()
    }

    private fun updateStatsUI() {
        val total = prefs.totalQuizCount
        val success = prefs.successQuizCount
        val rate = if (total > 0) (success * 100 / total) else 0
        tvStatsSummary.text = getString(R.string.stats_summary, total, success, rate)
    }

    private fun showRingtonePicker() {
        val currentUri = prefs.customRingtoneUri?.toUri()
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "選擇鬧鐘響鈴聲音")
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        }
        ringtonePickerLauncher.launch(intent)
    }

    private fun setupSettingsSpinners() {
        categories = FlashcardRepository.getCategories().toMutableList()
        catAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerCategory.adapter = catAdapter
        spinnerCategory.setSelection(categories.indexOf(prefs.selectedCategory).coerceAtLeast(0))
        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                prefs.selectedCategory = categories[position]
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val memorizeOptions = AppPreferences.MEMORIZE_OPTIONS
        val memorizeDisplay = memorizeOptions.map { "$it 秒" }
        val memAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, memorizeDisplay).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerMemorizeDuration.adapter = memAdapter
        spinnerMemorizeDuration.setSelection(memorizeOptions.indexOf(prefs.memorizeDurationSec).coerceAtLeast(0))
        spinnerMemorizeDuration.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                prefs.memorizeDurationSec = memorizeOptions[position]
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val questionOptions = AppPreferences.QUESTION_OPTIONS
        val questionDisplay = questionOptions.map { "$it 題" }
        val qAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, questionDisplay).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerQuestionCount.adapter = qAdapter
        spinnerQuestionCount.setSelection(questionOptions.indexOf(prefs.questionCount).coerceAtLeast(0))
        spinnerQuestionCount.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                prefs.questionCount = questionOptions[position]
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val attemptOptions = AppPreferences.ATTEMPT_OPTIONS
        val attemptDisplay = attemptOptions.map { "$it 次" }
        val attAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, attemptDisplay).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerMaxAttempts.adapter = attAdapter
        spinnerMaxAttempts.setSelection(attemptOptions.indexOf(prefs.maxAttempts).coerceAtLeast(0))
        spinnerMaxAttempts.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                prefs.maxAttempts = attemptOptions[position]
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun showTimePicker() {
        TimePickerDialog(this, { _, hourOfDay, minute ->
            selectedHour = hourOfDay
            selectedMinute = minute
            prefs.alarmHour = hourOfDay
            prefs.alarmMinute = minute
            updateTimeText(hourOfDay, minute)
        }, selectedHour, selectedMinute, true).show()
    }

    private fun updateTimeText(hour: Int, minute: Int) {
        tvAlarmTime.text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    }

    private fun scheduleExactAlarm(hour: Int, minute: Int) {
        if (!checkExactAlarmPermission()) return

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val triggerTime = calendar.timeInMillis
        val operation = createAlarmPendingIntent()
        val showIntent = Intent(this, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            this, 0, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, operation)

        prefs.isAlarmEnabled = true
        updateAlarmStatusUI()

        val diffMinutes = ((triggerTime - System.currentTimeMillis()) / (1000 * 60)).toInt()
        val hours = diffMinutes / 60
        val mins = diffMinutes % 60
        Toast.makeText(this, "鬧鐘已設定：將於 $hours 小時 $mins 分鐘後響起", Toast.LENGTH_LONG).show()
    }

    @Suppress("SameParameterValue")
    private fun scheduleTestAlarmInSeconds(seconds: Int) {
        if (!checkExactAlarmPermission()) return

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val triggerTime = System.currentTimeMillis() + (seconds * 1000L)
        val operation = createAlarmPendingIntent()
        val showIntent = Intent(this, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            this, 0, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, operation)
        Toast.makeText(this, "測試鬧鐘已就緒！將在 $seconds 秒後響鈴", Toast.LENGTH_SHORT).show()
    }

    private fun cancelAlarm() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val pendingIntent = createAlarmPendingIntent()
        alarmManager.cancel(pendingIntent)

        prefs.isAlarmEnabled = false
        updateAlarmStatusUI()
        Toast.makeText(this, "鬧鐘已取消", Toast.LENGTH_SHORT).show()
    }

    private fun createAlarmPendingIntent(): PendingIntent {
        val intent = Intent(this, AlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            this, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateAlarmStatusUI() {
        if (prefs.isAlarmEnabled) {
            val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", prefs.alarmHour, prefs.alarmMinute)
            tvAlarmStatus.text = getString(R.string.alarm_status_enabled, formattedTime)
            tvAlarmStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            tvAlarmStatus.text = getString(R.string.alarm_status_disabled)
            tvAlarmStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(this, "請先開啟「精準鬧鐘」權限以確保鬧鐘正常響起", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri())
                startActivity(intent)
                return false
            }
        }
        return true
    }
}