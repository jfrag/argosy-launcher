package com.nendo.argosy.core.input

import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import java.util.concurrent.ConcurrentHashMap

private const val OEM_VIRTUAL_DEVICE_PREFIX = "uinput-"

/**
 * Firmware that re-injects its built-in controls through Android's virtual device. The device
 * describes itself as a keyboard, so it counts as a pad only once it sends pad input: an event
 * that carries a pad source, or a gamepad button code.
 */
object VirtualPad {
    @Volatile
    var seen: Boolean = false
        private set

    fun observe(event: InputEvent): Boolean {
        if (seen || event.device?.isVirtual != true) return false
        if (!event.hasPadSource() && !(event is KeyEvent && event.keyCode.isGamepadButtonCode())) {
            return false
        }
        seen = true
        return true
    }

    private fun InputEvent.hasPadSource(): Boolean =
        source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    private fun Int.isGamepadButtonCode(): Boolean =
        this in KeyEvent.KEYCODE_BUTTON_A..KeyEvent.KEYCODE_BUTTON_MODE
}

/**
 * A keyboard standing in for a pad on hosts that pass no gamepad to Android, such as the Steam
 * Frame's Lepton container, where the host binds each pad button to one of these keys.
 * [translate] returns the pad press a bound key stands for, or null for any other key. A keyboard
 * that has sent a bound key counts as a pad from then on.
 */
object KeyboardPad {
    private val PAD_BUTTON_BY_KEY: Map<Int, Int> = mapOf(
        KeyEvent.KEYCODE_ENTER to KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_ESCAPE to KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_F1 to KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_F2 to KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_F3 to KeyEvent.KEYCODE_BUTTON_L1,
        KeyEvent.KEYCODE_F4 to KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_F5 to KeyEvent.KEYCODE_BUTTON_L2,
        KeyEvent.KEYCODE_F6 to KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_F7 to KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_F8 to KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_F9 to KeyEvent.KEYCODE_BUTTON_THUMBL,
        KeyEvent.KEYCODE_F10 to KeyEvent.KEYCODE_BUTTON_THUMBR,
        KeyEvent.KEYCODE_F11 to KeyEvent.KEYCODE_BUTTON_MODE
    )

    private val padKeyboards: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    fun translate(event: KeyEvent): KeyEvent? {
        val button = PAD_BUTTON_BY_KEY[event.keyCode] ?: return null
        if (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) return null
        padKeyboards.add(event.deviceId)
        return KeyEvent(
            event.downTime,
            event.eventTime,
            event.action,
            button,
            event.repeatCount,
            event.metaState,
            event.deviceId,
            event.scanCode,
            event.flags,
            event.source
        )
    }

    fun drives(device: InputDevice): Boolean = device.id in padKeyboards
}

fun InputDevice.actsAsGamepad(): Boolean =
    isPhysicalGamepad() || (isVirtual && VirtualPad.seen) || KeyboardPad.drives(this)

/**
 * Whether a device is a pad the launcher should act on. The source flags alone are not enough:
 * HyperOS registers a `uinput-xiaomi` device that advertises a gamepad source with no controller
 * behind it, which hid the on-screen controls and claimed player one on a bare phone.
 */
fun InputDevice.isPhysicalGamepad(): Boolean {
    val padSource = sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
        sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    if (!padSource || isVirtual) return false
    return !name.orEmpty().startsWith(OEM_VIRTUAL_DEVICE_PREFIX, ignoreCase = true)
}
