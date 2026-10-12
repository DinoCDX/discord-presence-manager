package com.dpm.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.text.InputType
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.R as MR
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.color(attr: Int): Int = MaterialColors.getColor(this, attr, Color.MAGENTA)

fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

fun Context.styled(t: String, size: Float, colorAttr: Int, medium: Boolean = false): TextView =
    TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color(colorAttr))
        if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

fun Context.headline(t: String): TextView =
    styled(t, 30f, MR.attr.colorOnSurface, true).apply { setPadding(0, dp(4), 0, dp(4)) }

fun Context.body(t: String): TextView =
    styled(t, 14f, MR.attr.colorOnSurfaceVariant).apply { setPadding(0, dp(2), 0, dp(2)) }

fun Context.section(t: String): TextView =
    styled(t, 13f, MR.attr.colorPrimary, true).apply { setPadding(0, dp(2), 0, dp(6)) }

fun Context.page(): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(28), dp(20), dp(24))
    }

fun <T : View> T.full(topDp: Int = 8): T {
    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
        topMargin = (topDp * resources.displayMetrics.density).toInt()
    }
    return this
}

fun Context.input(hint: String, password: Boolean = false): TextInputLayout {
    val til = TextInputLayout(this)
    til.hint = hint
    val et = TextInputEditText(til.context)
    et.setSingleLine()
    til.addView(et, LinearLayout.LayoutParams(MATCH, WRAP))
    if (password) {
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        til.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
    }
    return til.full(10)
}

val TextInputLayout.value: String get() = editText?.text?.toString().orEmpty()

fun TextInputLayout.set(v: String) {
    editText?.setText(v)
}

fun Context.dropdown(hint: String, items: List<String>, selected: Int): TextInputLayout {
    val til = TextInputLayout(this, null, MR.attr.textInputOutlinedExposedDropdownMenuStyle)
    til.hint = hint
    val dd = MaterialAutoCompleteTextView(til.context)
    dd.inputType = InputType.TYPE_NULL
    dd.setSimpleItems(items.toTypedArray())
    dd.setText(items[selected], false)
    til.addView(dd, LinearLayout.LayoutParams(MATCH, WRAP))
    return til.full(10)
}

fun Context.filled(label: String, onClick: () -> Unit): MaterialButton =
    MaterialButton(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

fun Context.outlined(label: String, onClick: () -> Unit): MaterialButton =
    MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle).apply {
        text = label
        setOnClickListener { onClick() }
    }

fun Context.tonal(label: String, onClick: () -> Unit): MaterialButton =
    filled(label, onClick).apply {
        backgroundTintList = ColorStateList.valueOf(color(MR.attr.colorSecondaryContainer))
        setTextColor(color(MR.attr.colorOnSecondaryContainer))
    }

fun Context.toggle(labels: List<String>, selected: Int, onSelect: (Int) -> Unit): MaterialButtonToggleGroup {
    val g = MaterialButtonToggleGroup(this)
    g.isSingleSelection = true
    g.isSelectionRequired = true
    val ids = labels.map { l ->
        val b = MaterialButton(this, null, MR.attr.materialButtonOutlinedStyle)
        b.text = l
        b.id = View.generateViewId()
        g.addView(b, LinearLayout.LayoutParams(0, WRAP, 1f))
        b.id
    }
    g.check(ids[selected])
    g.addOnButtonCheckedListener { _, id, checked ->
        if (checked) onSelect(ids.indexOf(id))
    }
    return g.full(16)
}

fun Context.card(vararg views: View): MaterialCardView {
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(14), dp(18), dp(18))
    }
    views.forEach { col.addView(it) }
    return MaterialCardView(this).apply {
        radius = dp(24).toFloat()
        cardElevation = 0f
        strokeWidth = 0
        setCardBackgroundColor(color(MR.attr.colorSurfaceContainer))
        addView(col)
    }.full(14)
}

fun View.enter(delay: Long = 0L) {
    alpha = 0f
    translationY = context.dp(20).toFloat()
    animate()
        .alpha(1f)
        .translationY(0f)
        .setStartDelay(delay)
        .setDuration(340L)
        .setInterpolator(DecelerateInterpolator(1.6f))
        .start()
}

fun ViewGroup.staggerIn(step: Long = 55L) {
    for (i in 0 until childCount) getChildAt(i).enter(i * step)
}

fun ViewGroup.smooth() {
    TransitionManager.beginDelayedTransition(this, AutoTransition().setDuration(240L))
}

@Suppress("DEPRECATION")
fun Activity.go(i: Intent) {
    startActivity(i)
    overridePendingTransition(R.anim.enter, R.anim.exit)
}

fun View.round(r: Int) {
    clipToOutline = true
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, r.toFloat())
        }
    }
}

fun View.circle() {
    clipToOutline = true
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }
}

fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
}
