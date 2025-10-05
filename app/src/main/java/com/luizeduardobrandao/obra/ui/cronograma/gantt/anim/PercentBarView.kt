package com.luizeduardobrandao.obra.ui.cronograma.gantt.anim

import android.animation.ValueAnimator
import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.luizeduardobrandao.obra.R

class PercentBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val label = TextView(context)

    private var progress = 0                 // 0..100 (valor comprometido)
    private var animator: ValueAnimator? = null
    private var pendingAfterAnim: Int? = null

    init {
        // ProgressBar horizontal usando o layer-list com background + progress + stroke
        bar.apply {
            isIndeterminate = false
            max = 100
            progress = 0
            // ESTE é o seu drawable novo:
            progressDrawable = AppCompatResources.getDrawable(
                context, R.drawable.progress_status_layer
            )
            // o desenho deve ocupar toda a área; não aplique padding aqui
            setPadding(0, 0, 0, 0)
        }
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Texto central
        label.apply {
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            setTextSize(
                android.util.TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.progress_status_text_sp)
            )
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
            )
            gravity = Gravity.CENTER
            // padding só no texto para “respirar” sem encolher o desenho do progresso
            val pad = resources.getDimensionPixelSize(R.dimen.progress_status_inner_pad)
            setPadding(pad, pad, pad, pad)
        }
        addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        isSaveEnabled = true
        updateLabel(0)
    }

    private fun updateLabel(p: Int) {
        val clamped = p.coerceIn(0, 100)
        val txt = context.getString(R.string.progress_status_value, clamped)
        label.text = txt
        label.contentDescription = txt
    }

    fun setProgress(pct: Int, animate: Boolean) {
        val target = pct.coerceIn(0, 100)

        // Se ainda está animando, fila o próximo valor
        if (animator?.isRunning == true) {
            pendingAfterAnim = target
            return
        }

        val start = bar.progress
        if (!animate || start == target) {
            bar.progress = target
            progress = target
            updateLabel(progress)
            return
        }

        animator = ValueAnimator.ofInt(start, target).apply {
            duration = resources.getInteger(R.integer.progress_status_anim_ms).toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val cur = (va.animatedValue as Int).coerceIn(0, 100)
                bar.progress = cur
                updateLabel(cur)
            }
            doOnEnd {
                progress = target
                updateLabel(progress)
                animator = null
                pendingAfterAnim?.let { next ->
                    pendingAfterAnim = null
                    setProgress(next, true)
                }
            }
        }
        animator?.start()
    }

    // ——— Estado ———
    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState() ?: return null
        return SavedState(superState, progress)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            progress = state.progress.coerceIn(0, 100)
            bar.progress = progress
            updateLabel(progress)
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private class SavedState : BaseSavedState {
        val progress: Int

        constructor(superState: Parcelable?, progress: Int) : super(superState) {
            this.progress = progress
        }

        private constructor(`in`: Parcel) : super(`in`) {
            progress = `in`.readInt()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags); out.writeInt(progress)
        }

        companion object {
            @Suppress("unused")
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> =
                object : Parcelable.Creator<SavedState> {
                    override fun createFromParcel(source: Parcel) = SavedState(source)
                    override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
                }
        }
    }
}

// pequena extensão local
private fun ValueAnimator.doOnEnd(block: () -> Unit) {
    addListener(object : android.animation.Animator.AnimatorListener {
        override fun onAnimationStart(animation: android.animation.Animator) {}
        override fun onAnimationEnd(animation: android.animation.Animator) = block()
        override fun onAnimationCancel(animation: android.animation.Animator) {}
        override fun onAnimationRepeat(animation: android.animation.Animator) {}
    })
}