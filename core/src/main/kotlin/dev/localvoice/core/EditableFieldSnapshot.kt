package dev.localvoice.core

/** Normalizes editor metadata without guessing a cursor inside existing text. */
data class EditableFieldSnapshot private constructor(val text: String, val start: Int, val end: Int) {
    companion object {
        fun capture(text: String?, start: Int, end: Int, showingHint: Boolean = false): EditableFieldSnapshot? {
            // Accessibility may expose a placeholder as text, with no selection, in an empty editor.
            val content = if (showingHint) "" else text.orEmpty()
            if (content.isEmpty() && start in -1..0 && end in -1..0) {
                return EditableFieldSnapshot("", 0, 0)
            }
            if (start !in 0..content.length || end !in start..content.length) return null
            return EditableFieldSnapshot(content, start, end)
        }
    }
}
