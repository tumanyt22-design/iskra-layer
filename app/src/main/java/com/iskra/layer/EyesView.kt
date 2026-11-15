package com.iskra.layer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.sin

class EyesView(context: Context) : View(context) {
    private val dens = context.resources.displayMetrics.density
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eyePath = Path()
    private val ui = Handler(Looper.getMainLooper())

    private var blink = 1f
    private var mood = Mood.HAPPY
    private var eyeColor = Mood.HAPPY.color
    private var dancing = false
    private var active = true
    private var loop: ValueAnimator? = null

    private var gx = 0f; private var gy = 0f
    private var tx = 0f; private var ty = 0f
    private var nextGaze = 0L
    private var offX = 0f; private var offY = 0f
    private var lid = 1f; private var tearPhase = 0f

    private fun rnd(a: Float, b: Float): Float = a + Math.random().toFloat() * (b - a)

    private fun doBlink() {
        val a = ValueAnimator.ofFloat(1f, 0.1f, 1f)
        a.duration = 180L
        a.addUpdateListener { anim ->
            blink = anim.animatedValue as Float
            invalidate()
        }
        a.start()
    }

    private val blinkRun = object : Runnable {
        override fun run() {
            doBlink()
            ui.postDelayed(this, 2500L + (Math.random() * 3000.0).toLong())
        }
    }

    fun setLook(m: Mood, c: Int) { mood = m; eyeColor = c; invalidate() }
    fun setDancing(d: Boolean) { dancing = d }
    fun blinkNow() { doBlink() }

    fun setActive(a: Boolean) {
        active = a
        if (a && isAttachedToWindow) startLoop() else stopLoop()
    }

    private fun startLoop() {
        if (loop != null) return
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 1000L
        a.repeatCount = ValueAnimator.INFINITE
        a.interpolator = LinearInterpolator()
        a.addUpdateListener { step(); invalidate() }
        a.start()
        loop = a
    }

    private fun stopLoop() { loop?.cancel(); loop = null }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ui.postDelayed(blinkRun, 2000L)
        if (active) startLoop()
    }

    override fun onDetachedFromWindow() {
        ui.removeCallbacksAndMessages(null)
        stopLoop()
        super.onDetachedFromWindow()
    }

    private fun step() {
        val now = System.currentTimeMillis()
        val t = (now % 100000L) / 1000f

        if (now >= nextGaze) {
            when (mood) {
                Mood.SAD, Mood.UPSET -> { tx = rnd(-0.6f, 0.6f); ty = 0.9f }
                Mood.ANGRY -> { tx = 0f; ty = 0f }
                Mood.SLEEPY -> { tx = 0f; ty = 0.7f }
                Mood.SURPRISED -> { tx = rnd(-0.3f, 0.3f); ty = -0.3f }
                else -> { tx = rnd(-1f, 1f); ty = rnd(-0.7f, 0.7f) }
            }
            nextGaze = now + 1200L + (Math.random() * 2600.0).toLong()
        }
        gx += (tx - gx) * 0.1f
        gy += (ty - gy) * 0.1f

        var mx = 0f; var my = 0f
        lid = 1f
        when (mood) {
            Mood.HAPPY -> { my = sin(t * 4f) * 0.6f }
            Mood.SICK -> { mx = sin(t * 1.6f) * 3f; my = sin(t * 3.2f) * 1f }
            Mood.ANGRY -> {
                mx = ((Math.random() - 0.5) * 1.4).toFloat()
                my = ((Math.random() - 0.5) * 0.8).toFloat()
            }
            Mood.SAD -> { my = 1.5f + sin(t * 0.9f) * 0.8f }
            Mood.UPSET -> { mx = sin(t * 18f) * 0.7f; my = 1.2f }
            Mood.SLEEPY -> { my = sin(t * 0.8f) * 1.2f; lid = 0.85f + sin(t * 0.8f) * 0.15f }
            Mood.SURPRISED -> { my = -1f + sin(t * 20f) * 0.4f }
            else -> { }
        }
        if (dancing) {
            my -= abs(sin(t * 6f)) * 2.5f
            mx += sin(t * 3f) * 1.5f
        }
        offX = gx * 3.5f + mx
        offY = gy * 2.5f + my
        tearPhase = (t * 0.9f) % 1f
    }

    private fun drawEye(canvas: Canvas, l: Float, top: Float, w: Float, bottom: Float, tl: Float, tr: Float) {
        eyePath.reset()
        eyePath.moveTo(l, top + tl)
        eyePath.lineTo(l + w, top + tr)
        eyePath.lineTo(l + w, bottom)
        eyePath.lineTo(l, bottom)
        eyePath.close()
        canvas.drawPath(eyePath, eyePaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = height / (32f * dens)
        val u = dens * s
        eyePaint.color = eyeColor
        eyePaint.pathEffect = CornerPathEffect(4f * u)

        val eyeW = (if (mood == Mood.SURPRISED) 12f else 11f) * u
        val baseH = when (mood) {
            Mood.SICK -> 8f
            Mood.UPSET -> 13f
            Mood.SLEEPY -> 7f
            Mood.SURPRISED -> 20f
            else -> 16f
        }
        val eyeH = baseH * u * blink * lid
        val gap = 9f * u
        val left = (width - (eyeW * 2f + gap)) / 2f + offX * u
        val cy = height / 2f + offY * u
        val top = cy - eyeH / 2f
        val bottom = cy + eyeH / 2f
        val k = minOf(5f * u, eyeH / 2f)

        var a1 = 0f; var b1 = 0f
        var a2 = 0f; var b2 = 0f
        when (mood) {
            Mood.ANGRY -> { a1 = 0f; b1 = k; a2 = k; b2 = 0f }
            Mood.SAD, Mood.UPSET -> { a1 = k; b1 = 0f; a2 = 0f; b2 = k }
            else -> { }
        }
        drawEye(canvas, left, top, eyeW, bottom, a1, b1)
        drawEye(canvas, left + eyeW + gap, top, eyeW, bottom, a2, b2)

        if (mood == Mood.UPSET) {
            eyePaint.alpha = ((1f - tearPhase) * 180f).toInt()
            canvas.drawCircle(left + eyeW / 2f, bottom + tearPhase * 7f * u, 2f * u, eyePaint)
            eyePaint.alpha = 255
        }
    }
}
