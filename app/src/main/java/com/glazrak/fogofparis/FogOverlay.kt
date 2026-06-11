package com.glazrak.fogofparis

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class FogOverlay(
    private var visitedCells: Set<CellId>
) : Overlay() {

    // Pinceau pour le voile sombre.
    private val fogPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)  // noir semi-opaque (180/255)
    }

    // Pinceau pour percer les trous : mode CLEAR.
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    // Permet de mettre à jour les cellules depuis l'extérieur.
    fun updateCells(cells: Set<CellId>) {
        visitedCells = cells
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return  // on ne dessine pas dans la passe "ombre"

        val projection = mapView.projection

        // Calque temporaire pour isoler le CLEAR de la carte en dessous.
        val saveCount = canvas.saveLayer(null, null)

        // 1. Voile sombre sur tout l'écran.
        canvas.drawRect(
            0f, 0f,
            canvas.width.toFloat(), canvas.height.toFloat(),
            fogPaint
        )

        // 2. Percer un trou par cellule visitée.
        for (cell in visitedCells) {
            val bounds = cellToBounds(cell)

            // Coins géographiques -> pixels écran.
            val topLeft = projection.toPixels(
                org.osmdroid.util.GeoPoint(bounds.latNorth, bounds.lonWest), null
            )
            val bottomRight = projection.toPixels(
                org.osmdroid.util.GeoPoint(bounds.latSouth, bounds.lonEast), null
            )

            canvas.drawRect(
                topLeft.x.toFloat(), topLeft.y.toFloat(),
                bottomRight.x.toFloat(), bottomRight.y.toFloat(),
                clearPaint
            )
        }

        // Rabattre le calque sur la carte.
        canvas.restoreToCount(saveCount)
    }
}