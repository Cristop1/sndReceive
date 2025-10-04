package com.cuibluetooth.bleeconomy.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.View
import com.cuibluetooth.bleeconomy.R
import com.cuibluetooth.bleeconomy.model.Coordinates
import com.cuibluetooth.bleeconomy.model.Person
import com.cuibluetooth.bleeconomy.model.Student

class MapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var mapBitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
    }

    private var persons: List<Person> = emptyList()

    // Reference pixel for coordinates(0,0) Default center
    private var refPixelX = 0f
    private var refPixelY = 0f

    // Map Extent in meters, assumes symmetric area
    private var mapExtentmeters = 10f // Map covers from -10f to 10f in both x y

    private var scaleX = 0f
    private var scaleY = 0f

    init {
        // Load Bitmap
        mapBitmap = BitmapFactory.decodeResource(resources, R.drawable.background1)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        refPixelX = w / 2f // center by default
        refPixelY = h / 2f
        scaleX = w / (2 * mapExtentmeters) // Pixels per meter
        scaleY = scaleX // Assuming square map
    }

    fun setPersons(newPersons: List<Person>) {
        persons = newPersons
        invalidate()
    }

    // Method to change reference
    fun setReferencePixel(pixelX: Float, pixelY: Float) {
        refPixelX = pixelX
        refPixelY = pixelY
        invalidate()
    }

    fun setMapExtent(meters: Float) {
        mapExtentmeters = meters
        scaleX = width / (2 * mapExtentmeters)
        scaleY = scaleX // Assuming square map
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw map background, scaled to fit with aspect ratio preserved
        mapBitmap?.let { bitmap ->
            val bitmapWidth = bitmap.width.toFloat()
            val bitmapHeight = bitmap.height.toFloat()
            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()

            val scaleFactorX = viewWidth / bitmapWidth
            val scaleFactorY = viewHeight / bitmapHeight
            val scale = minOf(scaleFactorX, scaleFactorY) // Preserve aspect ratio

            val matrix = Matrix().apply {
                postScale(scale, scale)
                postTranslate((viewWidth - bitmapWidth * scale) / 2, (viewHeight - bitmapHeight * scale) / 2) // Center
            }

            canvas.drawBitmap(bitmap, matrix, null)
        }

        // Draw positions (Students by now)
        persons.forEach { person ->
            if (person is Student) {
                drawPosition(canvas, person.actual, Color.BLUE, person.username)
                person.stand?.let { drawPosition(canvas, it, Color.RED, "${person.username}'s Stand") }
            }
        }
    }

    private fun drawPosition(canvas: Canvas, coords: Coordinates, color: Int, label: String) {
        val pixelX = refPixelX + (coords.x.toFloat() * scaleX)
        val pixelY = refPixelY - (coords.y.toFloat() * scaleY) // assuming y+ up
        paint.color = color
        canvas.drawCircle(pixelX, pixelY, 10f, paint)
        paint.color = Color.BLACK
        canvas.drawText(label, pixelX + 12f, pixelY + 12f, paint) // Label
    }
}