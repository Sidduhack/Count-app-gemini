package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.BoxDetection

@Composable
fun DetectionsOverlayImage(
    bitmap: Bitmap,
    detections: List<BoxDetection>,
    modifier: Modifier = Modifier
) {
    if (bitmap.width <= 0 || bitmap.height <= 0) return

    val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Scene with detections",
            modifier = Modifier.fillMaxSize()
        )

        detections.forEachIndexed { index, detection ->
            val box = detection.box_2d
            if (box.size == 4) {
                val ymin = (box[0] / 1000f).coerceIn(0f, 1f)
                val xmin = (box[1] / 1000f).coerceIn(0f, 1f)
                val ymax = (box[2] / 1000f).coerceIn(0f, 1f)
                val xmax = (box[3] / 1000f).coerceIn(0f, 1f)

                val leftDp = (xmin * containerWidth.value).dp
                val topDp = (ymin * containerHeight.value).dp
                val boxWidthDp = ((xmax - xmin) * containerWidth.value).dp
                val boxHeightDp = ((ymax - ymin) * containerHeight.value).dp

                // Drawing bounding box element
                Box(
                    modifier = Modifier
                        .offset(x = leftDp, y = topDp)
                        .size(width = boxWidthDp, height = boxHeightDp)
                        .border(2.dp, Color(0xFFFF5252))
                        .background(Color(0xFFFF5252).copy(alpha = 0.12f))
                ) {
                    // Sequence Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(16.dp)
                            .background(Color(0xFFFF5252), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            color = Color.White,
                            fontSize = 8.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
