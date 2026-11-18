package mcplus.app

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * Makes failures visible.
 *
 * The packaged exe runs in its own console window, and Windows closes that window the
 * moment the JVM exits — a crash therefore looked like "the game just disappeared".
 * Everything that ends the client abnormally is now written to `ultracraft-error.txt`
 * (next to the launcher, or in the temp directory if that is not writable) and shown in a
 * dialog, because the console is gone by the time anyone can read it.
 */
object CrashReport {

    private var logFile: File? = null

    /**
     * Whether to stop and show a dialog. Self-tests (`--demo`) turn this off: a modal window
     * nobody clicks would hang an unattended run.
     */
    var showDialogs: Boolean = true

    /** Wires the global handler; call once at startup. */
    fun install() {
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            report("Клиент упал", "${thread.name}: ${error.message ?: error.javaClass.name}", error)
        }
    }

    /**
     * Records a problem and shows it to the player, then returns.
     *
     * Also used for ordinary ends of the session (a kick), because those are just as
     * invisible otherwise. The dialog is modal, so the caller sees the message before the
     * process is allowed to end.
     */
    fun report(title: String, message: String, error: Throwable? = null) {
        val text = buildString {
            appendLine("=== $title")
            appendLine(LocalDateTime.now())
            appendLine(message)
            if (error != null) {
                appendLine()
                appendLine(stackTraceOf(error))
            }
            appendLine()
        }

        val file = write(text)
        System.err.print(text)
        if (file != null) System.err.println("Подробности: ${file.absolutePath}")

        val body = if (file != null) "$message\n\nПодробности: ${file.absolutePath}" else message
        if (!showDialogs) return
        runCatching {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName())
            javax.swing.JOptionPane.showMessageDialog(
                null, body, title, javax.swing.JOptionPane.ERROR_MESSAGE,
            )
        }
    }

    private fun write(text: String): File? {
        val candidates = buildList {
            logFile?.let { add(it) }
            add(File("ultracraft-error.txt"))
            add(File(System.getProperty("java.io.tmpdir"), "ultracraft-error.txt"))
        }
        for (file in candidates) {
            val written = runCatching { file.appendText(text); file }
            if (written.isSuccess) {
                logFile = written.getOrNull()
                return written.getOrNull()
            }
        }
        return null
    }

    private fun stackTraceOf(error: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { error.printStackTrace(it) }
        return writer.toString()
    }
}
