import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val subject: String,
    val options: ArrayList<String>,
    val checkboxes: Boolean
)
