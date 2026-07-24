package ru.starlitmoon.launcher.minecraft

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import ru.starlitmoon.launcher.WindowsShell
import java.awt.GraphicsEnvironment

/**
 * Borderless windowed mode for the Minecraft process — same idea as
 * [BorderlessMinecraft](https://github.com/Mr-Technician/BorderlessMinecraft):
 * strip window chrome via WinAPI and size to the primary display.
 *
 * Exclusive `--fullscreen` must stay off; this only rewrites a windowed HWND.
 */
object BorderlessMinecraft {
    private const val GWL_STYLE = WinUser.GWL_STYLE
    private const val SW_RESTORE = WinUser.SW_RESTORE
    private const val SWP_NOZORDER = WinUser.SWP_NOZORDER
    private const val SWP_FRAMECHANGED = 0x0020

    private const val WS_CAPTION = 0x00C00000
    private const val WS_THICKFRAME = 0x00040000
    private const val WS_MINIMIZEBOX = 0x00020000
    private const val WS_MAXIMIZEBOX = 0x00010000
    private const val WS_SYSMENU = 0x00080000
    private const val WS_BORDER = 0x00800000
    private const val WS_DLGFRAME = 0x00400000

    private const val STYLE_MASK =
        WS_CAPTION or WS_THICKFRAME or WS_MINIMIZEBOX or WS_MAXIMIZEBOX or
            WS_SYSMENU or WS_BORDER or WS_DLGFRAME

    /**
     * Poll until the game window exists, then make it borderless fullscreen.
     * Safe no-op on non-Windows or if the process exits first.
     */
    fun applyWhenReady(
        process: Process,
        timeoutMs: Long = 90_000L,
        pollMs: Long = 300L,
    ) {
        if (!WindowsShell.isWindows()) return
        val pid = runCatching { process.pid().toInt() }.getOrNull() ?: return
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && process.isAlive) {
            val hwnd = findMainWindow(pid)
            if (hwnd != null) {
                // Title may appear a beat after the HWND — prefer a real Minecraft title.
                val title = windowTitle(hwnd)
                if (title.isNotBlank()) {
                    goBorderless(hwnd)
                    return
                }
            }
            Thread.sleep(pollMs)
        }
    }

    private fun goBorderless(hwnd: HWND) {
        val user32 = User32.INSTANCE
        runCatching {
            user32.ShowWindow(hwnd, SW_RESTORE)
            val style = user32.GetWindowLong(hwnd, GWL_STYLE)
            user32.SetWindowLong(hwnd, GWL_STYLE, style and STYLE_MASK.inv())
            val bounds = primaryScreenBounds()
            user32.SetWindowPos(
                hwnd,
                null,
                bounds.left,
                bounds.top,
                bounds.right - bounds.left,
                bounds.bottom - bounds.top,
                SWP_NOZORDER or SWP_FRAMECHANGED,
            )
            user32.SetForegroundWindow(hwnd)
        }
    }

    private data class ScreenBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun primaryScreenBounds(): ScreenBounds {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val b = ge.defaultScreenDevice.defaultConfiguration.bounds
        return ScreenBounds(b.x, b.y, b.x + b.width, b.y + b.height)
    }

    private fun windowTitle(hwnd: HWND): String {
        val user32 = User32.INSTANCE
        val len = user32.GetWindowTextLength(hwnd) + 1
        if (len <= 1) return ""
        val buf = CharArray(len.coerceAtLeast(2))
        user32.GetWindowText(hwnd, buf, buf.size)
        return Native.toString(buf).trim()
    }

    private fun findMainWindow(pid: Int): HWND? {
        val user32 = User32.INSTANCE
        var best: HWND? = null
        var bestMinecraft: HWND? = null
        user32.EnumWindows({ hWnd: HWND, _: Pointer? ->
            val procId = IntByReference()
            user32.GetWindowThreadProcessId(hWnd, procId)
            if (procId.value != pid) return@EnumWindows true
            if (!user32.IsWindowVisible(hWnd)) return@EnumWindows true
            // Skip owned/tool windows — want the top-level game frame.
            val owner = user32.GetWindow(hWnd, WinDef.DWORD(WinUser.GW_OWNER.toLong()))
            if (owner != null && Pointer.nativeValue(owner.pointer) != 0L) return@EnumWindows true
            val rect = RECT()
            if (!user32.GetWindowRect(hWnd, rect)) return@EnumWindows true
            if (rect.right - rect.left < 200 || rect.bottom - rect.top < 150) return@EnumWindows true
            // Minimized sentinel (-32000,-32000)
            if (rect.left <= -30_000 || rect.top <= -30_000) return@EnumWindows true

            val title = windowTitle(hWnd)
            if (title.contains("Minecraft", ignoreCase = true)) {
                bestMinecraft = hWnd
                return@EnumWindows false
            }
            if (title.isNotBlank() && best == null) {
                best = hWnd
            }
            true
        }, null)
        return bestMinecraft ?: best
    }
}
