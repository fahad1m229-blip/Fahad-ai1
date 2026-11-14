package com.fahad.ai

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var llm: LlmInference? = null
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendBtn: Button
    private lateinit var status: TextView

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importModel(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        chat = findViewById(R.id.chat)
        scroll = findViewById(R.id.scroll)
        input = findViewById(R.id.input)
        sendBtn = findViewById(R.id.sendBtn)
        status = findViewById(R.id.status)

        findViewById<Button>(R.id.importBtn).setOnClickListener { picker.launch(arrayOf("*/*")) }
        sendBtn.setOnClickListener { send() }

        val saved = File(filesDir, "model.task")
        if (saved.exists()) loadModel(saved)
    }

    private fun importModel(uri: Uri) {
        setStatus("মডেল কপি হচ্ছে... (বড় ফাইল, একটু সময় লাগবে)")
        worker.execute {
            try {
                val out = File(filesDir, "model.task")
                contentResolver.openInputStream(uri)!!.use { i ->
                    out.outputStream().use { o -> i.copyTo(o, 1024 * 1024) }
                }
                loadModel(out, onWorker = true)
            } catch (e: Exception) {
                setStatus("ইমপোর্ট ব্যর্থ: ${e.message}")
            }
        }
    }

    private fun loadModel(file: File, onWorker: Boolean = false) {
        val job = Runnable {
            try {
                setStatus("মডেল লোড হচ্ছে...")
                llm?.close()
                val opts = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(file.absolutePath)
                    .setMaxTokens(1024)
                    .build()
                llm = LlmInference.createFromOptions(this, opts)
                setStatus("মডেল তৈরি ✅ — চ্যাট শুরু করুন")
            } catch (e: Throwable) {
                llm = null
                setStatus("লোড ব্যর্থ: ${e.message}\n(MediaPipe .task/.bin মডেল লাগবে, GGUF চলবে না)")
            }
        }
        if (onWorker) job.run() else worker.execute(job)
    }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        if (llm == null) { Toast.makeText(this, "আগে মডেল ইমপোর্ট করুন", Toast.LENGTH_SHORT).show(); return }
        input.setText("")
        addMsg(text, true)
        sendBtn.isEnabled = false
        val prompt = "<start_of_turn>user\n$text<end_of_turn>\n<start_of_turn>model\n"
        worker.execute {
            val reply = try { llm!!.generateResponse(prompt) } catch (e: Throwable) { "ত্রুটি: ${e.message}" }
            runOnUiThread { addMsg(reply.trim(), false); sendBtn.isEnabled = true }
        }
    }

    private fun addMsg(text: String, mine: Boolean) {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(24, 16, 24, 16)
            setTextColor(if (mine) Color.WHITE else Color.BLACK)
            setBackgroundColor(if (mine) Color.parseColor("#1565C0") else Color.parseColor("#E0E0E0"))
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 8, 0, 8)
        lp.gravity = if (mine) Gravity.END else Gravity.START
        chat.addView(tv, lp)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun setStatus(s: String) = runOnUiThread { status.text = s }
}
