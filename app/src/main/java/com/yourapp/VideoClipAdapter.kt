import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class VideoClip(
    val id: Long,
    val uri: Uri,
    var caption: String,
    val name: String,
    var captionX: Float = 0.5f,
    var captionY: Float = 0.82f
)

class VideoClipAdapter(
    val clips: MutableList<VideoClip>,
    private val onCaptionChanged: (Int, String) -> Unit,
    private val onPreview: (VideoClip) -> Unit,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<VideoClipAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val number: TextView = itemView.findViewById(R.id.clipNumber)
        val uriText: TextView = itemView.findViewById(R.id.videoUriText)
        val captionEdit: EditText = itemView.findViewById(R.id.captionEdit)
        val previewBtn: ImageButton = itemView.findViewById(R.id.previewBtn)
        val removeBtn: ImageButton = itemView.findViewById(R.id.removeBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video_clip, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val clip = clips[position]
        holder.number.text = (position + 1).toString()
        holder.uriText.text = clip.name
        holder.captionEdit.setText(clip.caption)

        holder.captionEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) {
                    onCaptionChanged(pos, s.toString())
                }
            }
        })

        holder.previewBtn.setOnClickListener { onPreview(clip) }
        holder.removeBtn.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onRemove(pos)
        }
    }

    override fun getItemCount() = clips.size
}
