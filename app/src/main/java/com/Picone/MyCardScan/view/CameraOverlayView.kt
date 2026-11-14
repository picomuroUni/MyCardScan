package com.Picone.MyCardScan.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.Picone.MyCardScan.R

class CameraOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val overlayPaint = Paint().apply {
        color = Color.argb(150, 0, 0, 0) // sfondo scuro semitrasparente
    }

    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val cornerPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    var rectLeftPercent   = 0f
    var rectTopPercent    = 0f
    var rectWidthPercent  = 0f
    var rectHeightPercent = 0f
    private val hintText = context.getString(R.string.photo_hint)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        // Dimensioni del rettangolo guida (proporzioni carte Pokemon 63x88mm)
        val rectWidth = width * 0.75f
        val rectHeight = rectWidth * (88f / 63f)
        val left = (width - rectWidth) / 2f
        val top = (height - rectHeight) / 2f
        val right = left + rectWidth
        val bottom = top + rectHeight


        //  percentuali per il crop
        rectLeftPercent   = left / width
        rectTopPercent    = top / height
        rectWidthPercent  = rectWidth / width
        rectHeightPercent = rectHeight / height

        val rect = RectF(left, top, right, bottom)

        canvas.drawRect(0f, 0f, width, top, overlayPaint)
        canvas.drawRect(0f, bottom, width, height, overlayPaint)
        canvas.drawRect(0f, top, left, bottom, overlayPaint)
        canvas.drawRect(right, top, width, bottom, overlayPaint)
        canvas.drawRect(rect, borderPaint)

        // Angoli evidenziati
        val cornerLen = 40f


        // Top-lef
        canvas.drawLine(left, top, left + cornerLen, top,  cornerPaint)
        canvas.drawLine(left, top, left,  top + cornerLen, cornerPaint)
        // Top-right
        canvas.drawLine(right,  top, right - cornerLen, top, cornerPaint)
        canvas.drawLine(right , top, right, top + cornerLen, cornerPaint)

        // Bottom-left
        canvas.drawLine(left, bottom, left + cornerLen, bottom, cornerPaint)
        canvas.drawLine(left ,  bottom, left, bottom -  cornerLen, cornerPaint)
        // Bottom-right
        canvas.drawLine(right , bottom, right -  cornerLen, bottom, cornerPaint)
        canvas.drawLine(right, bottom, right, bottom -  cornerLen, cornerPaint)

        // Testo guida
        canvas.drawText(
            hintText,
            width / 2f,
            top - 20f,
            textPaint
        )
    }
}