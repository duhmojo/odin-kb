// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import kotlin.math.abs
import kotlin.math.sign

/**
 * Odin KB: type with a game controller. The D-pad or left stick moves a highlight over the keys,
 * A presses the highlighted key, B deletes, X types a space and Y toggles shift.
 */
class GamepadKeyNavigator(private val switcher: KeyboardSwitcher, private val listener: KeyboardActionListener) {
    private var focused: Key? = null
    // center of the focused key, used to find it again after the keyboard changes (e.g. shift)
    private var focusX = -1
    private var focusY = -1
    private var stickX = 0
    private var stickY = 0

    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val view = shownKeyboardView() ?: return false
        if (!isController(event)) return false
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> move(view, -1, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(view, 1, 0)
            KeyEvent.KEYCODE_DPAD_UP -> move(view, 0, -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(view, 0, 1)
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> current(view)?.let { press(view, it, event.repeatCount) }
            KeyEvent.KEYCODE_BUTTON_B -> pressCode(KeyCode.DELETE, event.repeatCount)
            KeyEvent.KEYCODE_BUTTON_X -> pressCode(Constants.CODE_SPACE, event.repeatCount)
            KeyEvent.KEYCODE_BUTTON_Y -> if (event.repeatCount == 0) pressCode(KeyCode.SHIFT, 0)
            else -> return false
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BUTTON_X || keyCode == KeyEvent.KEYCODE_BUTTON_Y)
            view.post { highlight(view, current(view)) }
        return true
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (shownKeyboardView() == null || !isController(event)) return false
        return keyCode in HANDLED_KEYS
    }

    /** Left stick and hat switch, moving one key each time the stick is pushed past the threshold. */
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val view = shownKeyboardView() ?: return false
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE) return false
        val x = event.getAxisValue(MotionEvent.AXIS_X).takeIf { abs(it) > STICK_THRESHOLD } ?: event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y).takeIf { abs(it) > STICK_THRESHOLD } ?: event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val newX = if (abs(x) > STICK_THRESHOLD) x.sign.toInt() else 0
        val newY = if (abs(y) > STICK_THRESHOLD) y.sign.toInt() else 0
        if (newX != 0 && newX != stickX) move(view, newX, 0)
        if (newY != 0 && newY != stickY) move(view, 0, newY)
        stickX = newX
        stickY = newY
        return true
    }

    private fun shownKeyboardView(): MainKeyboardView? =
        switcher.mainKeyboardView?.takeIf { it.isShown && it.keyboard != null }

    private fun isController(event: InputEvent) =
        event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                || (event.device?.sources ?: 0) and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD

    private fun keys(view: MainKeyboardView) =
        view.keyboard?.sortedKeys?.filter { !it.isSpacer && it.width > 0 && it.code != KeyCode.NOT_SPECIFIED }.orEmpty()

    private val Key.centerX get() = x + width / 2
    private val Key.centerY get() = y + height / 2

    /** The focused key in the current keyboard, or the key closest to where it was. */
    private fun current(view: MainKeyboardView): Key? {
        val keys = keys(view)
        if (keys.isEmpty()) return null
        focused?.let { if (it in keys) return it }
        val x = if (focusX < 0) view.width / 2 else focusX
        val y = if (focusY < 0) view.height / 2 else focusY
        val key = keys.minBy { (it.centerX - x) * (it.centerX - x) + (it.centerY - y) * (it.centerY - y) }
        highlight(view, key)
        return key
    }

    private fun move(view: MainKeyboardView, dx: Int, dy: Int) {
        val hadFocus = focused?.let { it in keys(view) } == true
        val cur = current(view) ?: return
        if (!hadFocus) return // first press only shows where the highlight is
        val keys = keys(view)
        val next = if (dx != 0) {
            val row = keys.filter { abs(it.centerY - cur.centerY) < cur.height / 2 }
            row.filter { (it.centerX - cur.centerX).sign == dx }.minByOrNull { abs(it.centerX - cur.centerX) }
                ?: row.maxByOrNull { -dx * it.centerX } // wrap around to the other end of the row
        } else {
            val candidates = keys.filter { (it.centerY - cur.centerY) * dy > cur.height / 2 }
            val nearestRowY = candidates.minByOrNull { abs(it.centerY - cur.centerY) }?.centerY
            candidates.filter { nearestRowY != null && abs(it.centerY - nearestRowY) < it.height / 2 }
                .minByOrNull { abs(it.centerX - cur.centerX) }
        }
        if (next != null) highlight(view, next)
    }

    private fun highlight(view: MainKeyboardView, key: Key?) {
        val old = focused
        if (old != null && old != key) {
            old.onReleased()
            view.invalidateKey(old)
        }
        focused = key
        if (key == null) return
        focusX = key.centerX
        focusY = key.centerY
        key.onPressed()
        view.invalidateKey(key)
    }

    private fun press(view: MainKeyboardView, key: Key, repeatCount: Int) {
        if (key.code == KeyCode.MULTIPLE_CODE_POINTS) {
            if (repeatCount == 0) listener.onTextInput(key.outputText)
        } else {
            pressCode(key.code, repeatCount)
        }
        // the keyboard may have changed (shift, symbols), find the key again once it's laid out
        view.post { highlight(view, current(view)) }
    }

    private fun pressCode(code: Int, repeatCount: Int) {
        listener.onPressKey(code, repeatCount, 1, if (repeatCount == 0) HapticEvent.KEY_PRESS else HapticEvent.KEY_REPEAT)
        listener.onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, repeatCount > 0)
        listener.onReleaseKey(code, false)
    }

    companion object {
        private const val STICK_THRESHOLD = 0.5f
        private val HANDLED_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y
        )
    }
}
