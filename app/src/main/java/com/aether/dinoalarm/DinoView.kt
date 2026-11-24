package com.aether.dinoalarm

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

class DinoView(c: Context, private val onScore: (Int) -> Unit, private val onWin: () -> Unit) : View(c) {
    private class Ob(var x: Float, val w: Float, val h: Float)
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = 0xFF535353.toInt()
    private val obs = mutableListOf<Ob>()
    private var last = 0L; private var y = 0f; private var vy = 0f
    private var speed = 0f; private var score = 0f; private var dist = 0f; private var gap = 0f
    private var t = 0f; private var over = false; private var won = false
    private val dx = 56f * d
    private val dw = 44f * d
    private val dh = 46f * d

    init { reset() }

    private fun reset() {
        obs.clear(); y = 0f; vy = 0f; speed = 360 * d; score = 0f
        dist = 0f; gap = 500 * d; last = 0; over = false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) {
            if (over) reset() else if (y == 0f) vy = 950 * d
        }
        return true
    }

    private fun gy() = height * 0.72f

    private fun update(dt: Float) {
        if (over || dt <= 0f) return
        t += dt
        speed = (speed + 8 * d * dt).coerceAtMost(700 * d)
        y += vy * dt; vy -= 2800 * d * dt
        if (y <= 0f) { y = 0f; vy = 0f }
        score += speed / d * dt * 0.025f
        dist += speed * dt
        obs.forEach { it.x -= speed * dt }
        obs.removeAll { it.x + it.w < 0 }
        if (dist >= gap) {
            obs.add(Ob(width + 20 * d, (18 + Random.nextInt(16)) * d, (34 + Random.nextInt(20)) * d))
            dist = 0f; gap = speed * (0.95f + Random.nextFloat() * 0.8f)
        }
        val bottom = gy() - y
        val dino = RectF(dx + 6 * d, bottom - dh + 6 * d, dx + dw - 6 * d, bottom - 4 * d)
        for (o in obs) if (RectF.intersects(dino, RectF(o.x + 3 * d, gy() - o.h, o.x + o.w - 3 * d, gy()))) over = true
        onScore(score.toInt())
        if (score >= 100f && !won) { won = true; onWin() }
    }

    override fun onDraw(cv: Canvas) {
        val now = System.nanoTime()
        val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.033f)
        last = now
        update(dt)

        cv.drawColor(0xFFF7F7F7.toInt())
        p.color = ink
        cv.drawRect(0f, gy(), width.toFloat(), gy() + 2 * d, p)
        // cacti
        for (o in obs) {
            cv.drawRect(o.x + o.w * 0.36f, gy() - o.h, o.x + o.w * 0.64f, gy(), p)
            cv.drawRect(o.x, gy() - o.h * 0.65f, o.x + o.w * 0.36f, gy() - o.h * 0.5f, p)
            cv.drawRect(o.x, gy() - o.h * 0.8f, o.x + o.w * 0.18f, gy() - o.h * 0.5f, p)
            cv.drawRect(o.x + o.w * 0.64f, gy() - o.h * 0.55f, o.x + o.w, gy() - o.h * 0.4f, p)
            cv.drawRect(o.x + o.w * 0.82f, gy() - o.h * 0.7f, o.x + o.w, gy() - o.h * 0.4f, p)
        }
        // dino
        val b = gy() - y; val top = b - dh
        cv.drawRect(dx + 8 * d, top + 14 * d, dx + 34 * d, b - 12 * d, p)
        cv.drawRect(dx + 20 * d, top, dx + 44 * d, top + 20 * d, p)
        cv.drawRect(dx, top + 16 * d, dx + 8 * d, top + 24 * d, p)
        cv.drawRect(dx + 34 * d, top + 20 * d, dx + 38 * d, top + 28 * d, p)
        val step = y > 0f || ((t * 10).toInt() % 2 == 0)
        cv.drawRect(dx + 12 * d, b - 12 * d, dx + 18 * d, if (step) b else b - 4 * d, p)
        cv.drawRect(dx + 24 * d, b - 12 * d, dx + 30 * d, if (!step || y > 0f) b else b - 4 * d, p)
        p.color = Color.WHITE
        cv.drawRect(dx + 34 * d, top + 4 * d, dx + 38 * d, top + 8 * d, p)
        // score + messages
        p.color = ink; p.textSize = 24 * d; p.textAlign = Paint.Align.RIGHT
        cv.drawText("%05d".format(score.toInt()), width - 20 * d, 150 * d, p)
        p.textAlign = Paint.Align.CENTER
        if (over) {
            p.textSize = 22 * d
            cv.drawText("G A M E   O V E R", width / 2f, height * 0.4f, p)
            p.textSize = 16 * d
            cv.drawText("Tap to try again", width / 2f, height * 0.4f + 32 * d, p)
        } else if (score < 3f) {
            p.textSize = 16 * d
            cv.drawText("Tap to jump", width / 2f, height * 0.4f, p)
        }
        postInvalidateOnAnimation()
    }
}
