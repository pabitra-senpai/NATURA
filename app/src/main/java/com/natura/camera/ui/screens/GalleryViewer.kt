package com.natura.camera.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.natura.camera.camera.NaturaMediaItem
import com.natura.camera.ui.theme.*

@Composable
fun GalleryViewer(
    items: List<NaturaMediaItem>,
    onClose: () -> Unit,
    onItemDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { items.size })

    // MediaStore deletion contract for Android 10+
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onItemDeleted()
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(NaturaBlack)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close gallery",
                        tint = NaturaTextPrimary
                    )
                }

                // Center Index & Format badge
                if (items.isNotEmpty()) {
                    val currentItem = items.getOrNull(pagerState.currentPage)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} of ${items.size}",
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = NaturaTextMuted
                        )
                        val badge = if (currentItem?.isVideo == true) "MP4" else if (currentItem?.hasDng == true) "JPG + DNG" else "JPG"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100))
                                .border(1.dp, NaturaAmber, RoundedCornerShape(100))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badge,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                color = NaturaAmber
                            )
                        }
                    }
                }

                // Actions: Share & Delete
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val currentItem = items.getOrNull(pagerState.currentPage)

                    // Share button
                    IconButton(
                        onClick = {
                            if (currentItem != null) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (currentItem.isVideo) "video/mp4" else "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, currentItem.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share NATURA file"))
                            }
                        },
                        enabled = currentItem != null,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share media",
                            tint = NaturaTextPrimary
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = {
                            if (currentItem != null) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val pendingIntent = MediaStore.createDeleteRequest(
                                        context.contentResolver,
                                        listOf(currentItem.uri)
                                    )
                                    deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                                } else {
                                    try {
                                        context.contentResolver.delete(currentItem.uri, null, null)
                                        onItemDeleted()
                                    } catch (_: Exception) {}
                                }
                            }
                        },
                        enabled = currentItem != null,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete media",
                            tint = NaturaTextPrimary
                        )
                    }
                }
            }
        },
        containerColor = NaturaBlack,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No NATURA photos yet",
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = NaturaTextMuted
                )
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) { page ->
                val media = items[page]
                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = media.uri,
                        contentDescription = media.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    )

                    if (media.isVideo) {
                        Icon(
                            imageVector = Icons.Outlined.PlayCircle,
                            contentDescription = "Video file",
                            tint = NaturaTextPrimary.copy(alpha = 0.8f),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }
        }
    }
}
