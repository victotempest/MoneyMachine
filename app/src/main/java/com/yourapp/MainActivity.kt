import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // Views
    private lateinit var tabLayout: TabLayout
    private lateinit var panelClips: LinearLayout
    private lateinit var panelStyle: View
    private lateinit var panelAudio: LinearLayout
    private lateinit var addVideoBtn: Button
    private lateinit var mergeBtn: Button
    private lateinit var clipsRecyclerView: RecyclerView
    private lateinit var fontListContainer: LinearLayout
    private lateinit var musicPickArea: LinearLayout
    private lateinit var musicLabel: TextView
    private lateinit var removeMusicBtn: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var clipCountBadge: TextView
    private lateinit var captionHint: TextView

    // State
    private val clips = mutableListOf<VideoClip>()
    private lateinit var adapter: VideoClipAdapter
    private var selectedFont: FontStyle = FontStyles.all[0]
    private var musicUri: Uri? = null
    private var activePreviewClip: VideoClip? = null

    // Launchers
    private val pickVideos = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri ->
            val name = getFileName(uri)
            clips.add(VideoClip(
                id = System.currentTimeMillis() + clips.size,
                uri = uri,
                caption = "Clip ${clips.size + 1}",
                name = name
            ))
        }
        adapter.notifyDataSetChanged()
        updateBadge()
    }

    private val pickMusic = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            musicUri = it
            musicLabel.text = getFileName(it)
            removeMusicBtn.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupTabs()
        setupRecycler()
        setupFontList()
        setupListeners()
    }

    private fun bindViews() {
        tabLayout        = findViewById(R.id.tabLayout)
        panelClips       = findViewById(R.id.panelClips)
        panelStyle       = findViewById(R.id.panelStyle)
        panelAudio       = findViewById(R.id.panelAudio)
        addVideoBtn      = findViewById(R.id.addVideoBtn)
        mergeBtn         = findViewById(R.id.mergeBtn)
        clipsRecyclerView= findViewById(R.id.clipsRecyclerView)
        fontListContainer= findViewById(R.id.fontListContainer)
        musicPickArea    = findViewById(R.id.musicPickArea)
        musicLabel       = findViewById(R.id.musicLabel)
        removeMusicBtn   = findViewById(R.id.removeMusicBtn)
        progressBar      = findViewById(R.id.progressBar)
        statusText       = findViewById(R.id.statusText)
        clipCountBadge   = findViewById(R.id.clipCountBadge)
        captionHint      = findViewById(R.id.captionHint)
    }

    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("馃帪 Clips"))
        tabLayout.addTab(tabLayout.newTab().setText("鉁� Style"))
        tabLayout.addTab(tabLayout.newTab().setText("馃幍 Audio"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                panelClips.visibility = if (tab.position == 0) View.VISIBLE else View.GONE
                panelStyle.visibility = if (tab.position == 1) View.VISIBLE else View.GONE
                panelAudio.visibility = if (tab.position == 2) View.VISIBLE else View.GONE
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupRecycler() {
        adapter = VideoClipAdapter(
            clips = clips,
            onCaptionChanged = { pos, text -> clips[pos].caption = text },
            onPreview = { clip -> activePreviewClip = clip; captionHint.visibility = View.VISIBLE },
            onRemove = { pos ->
                clips.removeAt(pos)
                adapter.notifyItemRemoved(pos)
                adapter.notifyItemRangeChanged(pos, clips.size)
                updateBadge()
            }
        )
        clipsRecyclerView.layoutManager = LinearLayoutManager(this)
        clipsRecyclerView.adapter = adapter
    }

    private fun setupFontList() {
        FontStyles.all.forEach { font ->
            val btn = Button(this).apply {
                text = font.label
                textSize = 12f
                setTextColor(getColor(R.color.text_secondary))
                background = getDrawable(R.drawable.card_bg)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(24, 20, 24, 20)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 8
                layoutParams = lp
                setOnClickListener {
                    selectedFont = font
                    setTextColor(getColor(R.color.accent_red))
                    // Reset others
                    for (i in 0 until fontListContainer.childCount) {
                        val child = fontListContainer.getChildAt(i)
                        if (child != this && child is Button) {
                            child.setTextColor(getColor(R.color.text_secondary))
                        }
                    }
                    Toast.makeText(context, "Style: ${font.label}", Toast.LENGTH_SHORT).show()
                }
            }
            fontListContainer.addView(btn)
        }
    }

    private fun setupListeners() {
        addVideoBtn.setOnClickListener { pickVideos.launch("video/*") }

        musicPickArea.setOnClickListener { pickMusic.launch("audio/*") }

        removeMusicBtn.setOnClickListener {
            musicUri = null
            musicLabel.setText(R.string.music_hint)
            removeMusicBtn.visibility = View.GONE
        }

        mergeBtn.setOnClickListener {
            if (clips.size < 2) {
                showStatus(getString(R.string.need_two_clips), isError = true)
                return@setOnClickListener
            }
            checkPermissionsAndMerge()
        }
    }

    private fun checkPermissionsAndMerge() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            android.Manifest.permission.READ_MEDIA_VIDEO
        else
            android.Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), 100)
        } else {
            startMerge()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startMerge()
        } else {
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private fun startMerge() {
        mergeBtn.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        showStatus(getString(R.string.status_processing))

        val tempDir = File(cacheDir, "vr_temp").apply { deleteRecursively(); mkdirs() }
        val processedPaths = mutableListOf<String>()

        Thread {
            for ((index, clip) in clips.withIndex()) {
                val inputPath = uriToPath(clip.uri) ?: run {
                    runOnUiThread { showStatus("Failed to read clip ${index + 1}", isError = true) }
                    return@Thread
                }

                val outPath = File(tempDir, "clip_$index.mp4").absolutePath
                val drawtextFilter = FontStyles.buildDrawtextFilter(
                    selectedFont, clip.caption, clip.captionX, clip.captionY
                )

                val cmd = "-y -i \"$inputPath\" " +
                    "-vf \"scale=1080:1920:force_original_aspect_ratio=decrease," +
                    "pad=1080:1920:(ow-iw)/2:(oh-ih)/2:black,$drawtextFilter\" " +
                    "-c:v libx264 -preset fast -c:a aac \"$outPath\""

                val session: FFmpegSession = FFmpegKit.execute(cmd)
                if (!ReturnCode.isSuccess(session.returnCode)) {
                    runOnUiThread { showStatus("Failed on clip ${index + 1}", isError = true) }
                    return@Thread
                }
                processedPaths.add(outPath)
                val pct = ((index + 1).toFloat() / clips.size * 80).toInt()
                runOnUiThread { progressBar.progress = pct }
            }

            // Concat list
            val concatFile = File(cacheDir, "concat.txt")
            concatFile.writeText(processedPaths.joinToString("\n") { "file '$it'" })

            val mergedPath = File(cacheDir, "merged.mp4").absolutePath
            val concatCmd = "-y -f concat -safe 0 -i \"${concatFile.absolutePath}\" -c copy \"$mergedPath\""
            val mergeSession = FFmpegKit.execute(concatCmd)

            if (!ReturnCode.isSuccess(mergeSession.returnCode)) {
                runOnUiThread { showStatus("Merge failed", isError = true); resetMergeBtn() }
                return@Thread
            }
            runOnUiThread { progressBar.progress = 90 }

            // Optional music mix
            val finalPath: String
            if (musicUri != null) {
                val musicPath = uriToPath(musicUri!!)
                val mixedPath = File(cacheDir, "final.mp4").absolutePath
                if (musicPath != null) {
                    val mixCmd = "-y -i \"$mergedPath\" -i \"$musicPath\" " +
                        "-filter_complex \"[0:a][1:a]amix=inputs=2:duration=first[aout]\" " +
                        "-map 0:v -map \"[aout]\" -c:v copy -c:a aac \"$mixedPath\""
                    val mixSession = FFmpegKit.execute(mixCmd)
                    finalPath = if (ReturnCode.isSuccess(mixSession.returnCode)) mixedPath else mergedPath
                } else finalPath = mergedPath
            } else {
                finalPath = mergedPath
            }

            // Save to MediaStore
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "VideoRanker_$timestamp.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VideoRanker")
            }
            val outUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            outUri?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { out ->
                    File(finalPath).inputStream().copyTo(out)
                }
            }

            tempDir.deleteRecursively()
            concatFile.delete()

            runOnUiThread {
                progressBar.progress = 100
                showStatus(getString(R.string.status_done))
                resetMergeBtn()
                Toast.makeText(this, getString(R.string.video_saved), Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun resetMergeBtn() {
        mergeBtn.isEnabled = true
        progressBar.visibility = View.GONE
    }

    private fun showStatus(msg: String, isError: Boolean = false) {
        statusText.visibility = View.VISIBLE
        statusText.text = msg
        statusText.setTextColor(getColor(if (isError) R.color.accent_red else R.color.success))
    }

    private fun updateBadge() {
        clipCountBadge.visibility = if (clips.isEmpty()) View.GONE else View.VISIBLE
        clipCountBadge.text = "${clips.size} CLIP${if (clips.size != 1) "S" else ""}"
    }

    private fun getFileName(uri: Uri): String {
        var name = "video.mp4"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) name = cursor.getString(idx)
        }
        return name
    }

    private fun uriToPath(uri: Uri): String? {
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            if (cursor.moveToFirst()) return cursor.getString(col)
        }
        // Fallback: copy to cache
        return try {
            val tmp = File(cacheDir, "tmp_${System.currentTimeMillis()}.mp4")
            contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.absolutePath
        } catch (e: Exception) { null }
    }
}
