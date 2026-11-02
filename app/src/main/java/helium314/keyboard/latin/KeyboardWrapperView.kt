// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.FoldableUtils
import helium314.keyboard.latin.utils.prefs
import kotlin.math.abs

class KeyboardWrapperView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle), View.OnClickListener {

    var keyboardActionListener: KeyboardActionListener? = null

    private lateinit var stopOneHandedModeBtn: ImageButton
    private lateinit var switchOneHandedModeBtn: ImageButton
    private lateinit var resizeOneHandedModeBtn: ImageButton

    var oneHandedModeEnabled = false
        set(enabled) {
            field = enabled
            updateViewsVisibility()
            requestLayout()
        }

    var oneHandedGravity = Gravity.NO_GRAVITY
        set(value) {
            field = value
            updateSwitchButtonSide()
            requestLayout()
        }


    @SuppressLint("ClickableViewAccessibility")
    override fun onFinishInflate() {
        super.onFinishInflate()
        val keyboardIconsSet = KeyboardIconsSet.instance
        keyboardIconsSet.loadIcons(context)
        stopOneHandedModeBtn = findViewById(R.id.btn_stop_one_handed_mode)
        stopOneHandedModeBtn.setImageDrawable(keyboardIconsSet.getNewDrawable(KeyboardIconsSet.NAME_STOP_ONEHANDED_KEY, context))
        stopOneHandedModeBtn.visibility = GONE
        switchOneHandedModeBtn = findViewById(R.id.btn_switch_one_handed_mode)
        switchOneHandedModeBtn.setImageDrawable(keyboardIconsSet.getNewDrawable(KeyboardIconsSet.NAME_SWITCH_ONEHANDED_KEY, context))
        switchOneHandedModeBtn.visibility = GONE
        resizeOneHandedModeBtn = findViewById(R.id.btn_resize_one_handed_mode)
        resizeOneHandedModeBtn.setImageDrawable(keyboardIconsSet.getNewDrawable(KeyboardIconsSet.NAME_RESIZE_ONEHANDED_KEY, context))
        resizeOneHandedModeBtn.visibility = GONE

        stopOneHandedModeBtn.setOnClickListener(this)
        switchOneHandedModeBtn.setOnClickListener(this)

        var x = 0f
        resizeOneHandedModeBtn.setOnTouchListener { _, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> x = motionEvent.rawX
                MotionEvent.ACTION_MOVE -> {
                    // performance is not great because settings are reloaded and keyboard is redrawn
                    // on every move, but it's good enough
                    val sign = -switchOneHandedModeBtn.scaleX
                    // factor 2 to make it more sensitive (maybe could be tuned a little)
                    val changePercent = 2 * sign * (x - motionEvent.rawX) / context.resources.displayMetrics.density
                    if (abs(changePercent) < 1) return@setOnTouchListener true
                    x = motionEvent.rawX
                    val landscape = Settings.getValues().mDisplayOrientation == Configuration.ORIENTATION_LANDSCAPE
                    val split = Settings.getValues().mIsSplitKeyboardEnabled
                    val oldScale = Settings.readOneHandedModeScale(context.prefs(), landscape, split, FoldableUtils.isFolded)
                    val newScale = (oldScale + changePercent / 100f).coerceAtMost(2.5f).coerceAtLeast(0.5f)
                    if (newScale == oldScale) return@setOnTouchListener true
                    Settings.getInstance().writeOneHandedModeScale(newScale)
                    KeyboardSwitcher.getInstance().setOneHandedModeEnabled(true, true)
                }
                else -> x = 0f
            }
            true
        }

        val colors = Settings.getValues().mColors
        colors.setColor(stopOneHandedModeBtn, ColorType.ONE_HANDED_MODE_BUTTON)
        colors.setColor(switchOneHandedModeBtn, ColorType.ONE_HANDED_MODE_BUTTON)
        colors.setColor(resizeOneHandedModeBtn, ColorType.ONE_HANDED_MODE_BUTTON)
        colors.setBackground(stopOneHandedModeBtn, ColorType.ONE_HANDED_MODE_BUTTON)
        colors.setBackground(switchOneHandedModeBtn, ColorType.ONE_HANDED_MODE_BUTTON)
        colors.setBackground(resizeOneHandedModeBtn, ColorType.ONE_HANDED_MODE_BUTTON)
    }

    @SuppressLint("RtlHardcoded")
    fun switchOneHandedModeSide() {
        oneHandedGravity = if (oneHandedGravity == Gravity.LEFT) Gravity.RIGHT else Gravity.LEFT
    }

    private fun updateViewsVisibility() {
        stopOneHandedModeBtn.visibility = if (oneHandedModeEnabled) VISIBLE else GONE
        switchOneHandedModeBtn.visibility = if (oneHandedModeEnabled) VISIBLE else GONE
        resizeOneHandedModeBtn.visibility = if (oneHandedModeEnabled) VISIBLE else GONE
    }

    @SuppressLint("RtlHardcoded")
    private fun updateSwitchButtonSide() {
        switchOneHandedModeBtn.scaleX = if (oneHandedGravity == Gravity.LEFT) -1f else 1f
    }

    override fun onClick(view: View) {
        if (view === stopOneHandedModeBtn) {
            keyboardActionListener?.onCodeInput(KeyCode.TOGGLE_ONE_HANDED_MODE,
                Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                false /* isKeyRepeat */)
        } else if (view === switchOneHandedModeBtn) {
            keyboardActionListener?.onCodeInput(KeyCode.SWITCH_ONE_HANDED_MODE,
                Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                false /* isKeyRepeat */)
        }
    }

    // Odin KB: suggestions shown in the empty center of the split keyboard
    private val splitSuggestions: LinearLayout by lazy {
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = GONE
            this@KeyboardWrapperView.addView(this, LayoutParams(0, 0))
        }
    }

    /** Shows up to 3 [words] in the split gap, returns false if there is no gap to show them in. */
    fun setSplitSuggestions(words: SuggestedWords, pick: (SuggestedWordInfo) -> Unit): Boolean {
        if (findSplitGap() == null) {
            splitSuggestions.visibility = GONE
            return false
        }
        splitSuggestions.removeAllViews()
        val colors = Settings.getValues().mColors
        val count = if (words.isPunctuationSuggestions) 0 else minOf(words.size(), 3)
        for (i in 0 until count) {
            val info = words.getInfo(i)
            val isAutoCorrect = words.mWillAutoCorrect && i == SuggestedWords.INDEX_OF_AUTO_CORRECTION
            splitSuggestions.addView(TextView(context).apply {
                text = info.mWord
                textSize = 20f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTypeface(typeface, if (isAutoCorrect) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(colors.get(if (isAutoCorrect) ColorType.SUGGESTION_AUTO_CORRECT else ColorType.KEY_TEXT))
                setOnClickListener {
                    AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this, HapticEvent.KEY_PRESS)
                    pick(info)
                }
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
        splitSuggestions.visibility = if (count > 0) VISIBLE else GONE
        requestLayout()
        return true
    }

    fun clearSplitSuggestions() {
        splitSuggestions.removeAllViews()
        splitSuggestions.visibility = GONE
    }

    /** Area of the split keyboard's center gap in this view, or null if the keyboard is not split. */
    private fun findSplitGap(): Rect? {
        val keyboardView = KeyboardSwitcher.getInstance().mainKeyboardView ?: return null
        if (keyboardView.visibility != VISIBLE) return null
        val spacers = keyboardView.keyboard?.sortedKeys?.filter { it.isSpacer && it.width > 0 } ?: return null
        if (spacers.isEmpty()) return null
        return Rect(
            keyboardView.left + spacers.maxOf { it.x },
            keyboardView.top + spacers.minOf { it.y },
            keyboardView.left + spacers.minOf { it.x + it.width },
            keyboardView.top + spacers.maxOf { it.y + it.height }
        )
    }

    private fun layoutSplitSuggestions() {
        if (splitSuggestions.visibility != VISIBLE) return
        val gap = findSplitGap()
        if (gap == null) {
            splitSuggestions.visibility = GONE
            return
        }
        splitSuggestions.measure(
            MeasureSpec.makeMeasureSpec(gap.width(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(gap.height(), MeasureSpec.EXACTLY)
        )
        splitSuggestions.layout(gap.left, gap.top, gap.right, gap.bottom)
    }

    @SuppressLint("RtlHardcoded")
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (!oneHandedModeEnabled) {
            super.onLayout(changed, left, top, right, bottom)
            layoutSplitSuggestions()
            return
        }

        val isLeftGravity = oneHandedGravity == Gravity.LEFT
        val width = right - left
        val keyboardView = KeyboardSwitcher.getInstance().visibleKeyboardView
        val spareWidth = width - keyboardView.measuredWidth

        val keyboardLeft = if (isLeftGravity) 0 else spareWidth
        keyboardView.layout(
                keyboardLeft,
                0,
                keyboardLeft + keyboardView.measuredWidth,
                keyboardView.measuredHeight
        )

        val scale = Settings.getValues().mKeyboardHeightScale
        // scale one-handed mode button height if keyboard height scale is < 80%
        val heightScale = if (scale < 0.8f) scale + 0.2f else 1f
        val buttonsLeft = if (isLeftGravity) keyboardView.measuredWidth else 0
        val buttonXLeft = buttonsLeft + (spareWidth - stopOneHandedModeBtn.measuredWidth) / 2
        val buttonXRight = buttonsLeft + (spareWidth + stopOneHandedModeBtn.measuredWidth) / 2
        val buttonHeight = (heightScale * stopOneHandedModeBtn.measuredHeight).toInt()
        fun View.setLayout(yPosition: Int) {
            layout(buttonXLeft, yPosition - buttonHeight / 2, buttonXRight, yPosition + buttonHeight / 2)
        }
        stopOneHandedModeBtn.setLayout((keyboardView.measuredHeight * 0.2f).toInt())
        switchOneHandedModeBtn.setLayout((keyboardView.measuredHeight * 0.5f).toInt())
        resizeOneHandedModeBtn.setLayout((keyboardView.measuredHeight * 0.8f).toInt())
        layoutSplitSuggestions()
    }
}
