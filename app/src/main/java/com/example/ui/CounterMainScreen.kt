package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.CountRecord
import com.example.ui.components.DetectionsOverlayImage
import com.example.ui.utils.ImageUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CounterMainScreen(
    viewModel: CountViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sampleBitmap by viewModel.sampleBitmap.collectAsStateWithLifecycle()
    val sceneBitmap by viewModel.sceneBitmap.collectAsStateWithLifecycle()
    val analysisState by viewModel.analysisState.collectAsStateWithLifecycle()
    val apiKeyOverride by viewModel.apiKeyOverride.collectAsStateWithLifecycle()
    val history by viewModel.historyList.collectAsStateWithLifecycle()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var tempApiKey by remember { mutableStateOf(apiKeyOverride) }

    // Uri cache for taking camera shots
    var latestCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isCapturingSample by remember { mutableStateOf(true) } // true for sample, false for scene

    // Camera launcher contract
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && latestCameraUri != null) {
            if (isCapturingSample) {
                viewModel.setSampleImage(latestCameraUri)
            } else {
                viewModel.setSceneImage(latestCameraUri)
            }
        }
    }

    // Gallery launcher contracts
    val pickSampleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setSampleImage(uri)
        }
    }

    val pickSceneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setSceneImage(uri)
        }
    }

    // Helper to request Camera permissions using Accompanist
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    fun launchCamera(forSample: Boolean) {
        isCapturingSample = forSample
        if (cameraPermissionState.status.isGranted) {
            val tmpFile = File(context.cacheDir, "counting_capture_${if (forSample) "sample" else "scene"}.jpg")
            try {
                if (tmpFile.exists()) tmpFile.delete()
                tmpFile.createNewFile()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, tmpFile)
            latestCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionState.launchPermissionRequest()
            Toast.makeText(context, "Camera permission required to capture photos.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Camera,
                            contentDescription = "App Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Fast Counter",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            tempApiKey = viewModel.getActiveApiKey()
                            showSettingsDialog = true
                        },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "API Settings",
                            tint = if (viewModel.getActiveApiKey().isEmpty()) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                )
            )
        },
        containerColor = Color(0xFF12141C), // Deep premium dark slate background
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
            // API Warning Banner if not configured
            if (viewModel.getActiveApiKey().isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1E1E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error Key",
                                tint = Color(0xFFFF5252)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "API Key Required",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Please enter your Gemini API key in settings to enable counts.",
                                    color = Color(0xFFE0E0E0),
                                    fontSize = 12.sp
                                )
                            }
                            Button(
                                onClick = {
                                    tempApiKey = viewModel.getActiveApiKey()
                                    showSettingsDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Set Key", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Quick instruction guide
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Counting objects is as simple as 1, 2:",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("1", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Text(
                                text = "Isolate one sample item of interest inside the Sample box.",
                                color = Color(0xFFC5C7DB),
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("2", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Text(
                                text = "Take a full scene photo featuring all instances of that item.",
                                color = Color(0xFFC5C7DB),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Reference / Sample Selection View (1 Item)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "1. Reference Sample (Exactly 1 item)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    
                    if (sampleBitmap == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E2130))
                                .border(1.dp, Color(0xFF333850), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterFrames,
                                    contentDescription = "Sample placeholder",
                                    tint = Color(0xFF5A6084),
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "No reference photo provided",
                                    color = Color(0xFF8E95BE),
                                    fontSize = 12.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = { launchCamera(forSample = true) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("sample_camera_btn")
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Take Photo", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            pickSampleLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8E95BE)),
                                        modifier = Modifier.testTag("sample_gallery_btn")
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Gallery", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        // Display loaded reference image
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E2130))
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        ) {
                            Image(
                                bitmap = sampleBitmap!!.asImageBitmap(),
                                contentDescription = "Reference Item",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Remove image button
                            IconButton(
                                onClick = { viewModel.setSampleImageBitmap(null) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(28.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Target Full Scene selection view (multiple items)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "2. Target Photo containing items to count",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    if (sceneBitmap == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E2130))
                                .border(1.dp, Color(0xFF333850), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Grid4x4,
                                    contentDescription = "Scene placeholder",
                                    tint = Color(0xFF5A6084),
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "No target photo provided",
                                    color = Color(0xFF8E95BE),
                                    fontSize = 12.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = { launchCamera(forSample = false) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("scene_camera_btn")
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Take Photo", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            pickSceneLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8E95BE)),
                                        modifier = Modifier.testTag("scene_gallery_btn")
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Gallery", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        // Display loaded scene image
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E2130))
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        ) {
                            Image(
                                bitmap = sceneBitmap!!.asImageBitmap(),
                                contentDescription = "Scene Image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Remove image button
                            IconButton(
                                onClick = { viewModel.setSceneImageBitmap(null) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(28.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Calculation/Count main trigger action
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { viewModel.countItems() },
                    enabled = sampleBitmap != null && sceneBitmap != null && analysisState !is AnalysisState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("count_items_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = Color(0xFF22263A),
                        disabledContentColor = Color(0xFF525774)
                    )
                ) {
                    Icon(imageVector = Icons.Default.Done, contentDescription = "Trigger")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Count Items Now",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // Active Counting Analysis State Container
            item {
                AnimatedVisibility(
                    visible = analysisState !is AnalysisState.Idle,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    when (val state = analysisState) {
                        AnalysisState.Idle -> {}
                        is AnalysisState.Loading -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = state.message,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Please keep the app open; our model is scanning both images contextually.",
                                        color = Color(0xFF8E95BE),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        is AnalysisState.Error -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF331B1F)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = "Analysis error",
                                        tint = Color(0xFFFF5252)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Counting Failed",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = state.message,
                                            color = Color(0xFFE5B4B4),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                        is AnalysisState.Success -> {
                            val result = state.result
                            val detections = result.detections ?: emptyList()
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D2D)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFF3A3E5C), RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Counting Success",
                                            color = Color(0xFF4CAF50),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        if (state.isHistorical) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier
                                                    .background(Color(0xFF2E324A), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Icon(Icons.Default.History, contentDescription = "history", tint = Color(0xFF8E95BE), modifier = Modifier.size(12.dp))
                                                Text("Restored", color = Color(0xFF8E95BE), fontSize = 10.sp)
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Display big count results badge
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Item Name & count
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = result.itemName.replaceFirstChar { it.titlecase() },
                                                color = Color.White,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            Text(
                                                text = result.description,
                                                color = Color(0xFFC5C7DB),
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }

                                        // Total items found circles
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = result.count.toString(),
                                                    color = Color.White,
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                                Text(
                                                    text = "items",
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    // Render full photo with overlay highlights if detections exist!
                                    if (detections.isNotEmpty() && sceneBitmap != null) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Locations highlight scene:",
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, Color(0xFF3E4366), RoundedCornerShape(8.dp))
                                        ) {
                                            DetectionsOverlayImage(
                                                bitmap = sceneBitmap!!,
                                                detections = detections,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Historical counts section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "History Logs",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (history.isNotEmpty()) {
                        Text(
                            text = "Clear All",
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable { viewModel.clearAllHistory() }
                                .padding(4.dp)
                        )
                    }
                }
            }

            if (history.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No saved counts yet. Run a count to see it here.",
                            color = Color(0xFF5A6084),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(history, key = { it.id }) { log ->
                    CountRecordRow(
                        record = log,
                        onSelect = { viewModel.selectHistoricalRecord(log) },
                        onDelete = { viewModel.deleteHistoryRecord(log.id) }
                    )
                }
            }
        }
    }

    // Custom API Configuration Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Key, contentDescription = "API key icon", tint = MaterialTheme.colorScheme.primary)
                    Text("API Settings")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The counts run on public Gemini Vision API model structures. Since Android API requests securely consume user scopes, please paste your Gemini API Key directly below to execute real-time counting.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_field")
                    )
                    Text(
                        text = "Note: Stored securely inside local SharedPreferences. You can retrieve an API key inside Google AI Studio under the Keys section.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setApiKeyOverride(tempApiKey)
                        showSettingsDialog = false
                    },
                    modifier = Modifier.testTag("save_api_key")
                ) {
                    Text("Save Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CountRecordRow(
    record: CountRecord,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()) }
    val recordDate = remember(record.timestamp) { formatter.format(Date(record.timestamp)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Little thumbnail circle representing the scan
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF2E324A),CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.InsertPhoto,
                    contentDescription = "Image tag",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = record.itemName.replaceFirstChar { it.titlecase() },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${record.count} count",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = recordDate,
                    color = Color(0xFF8E95BE),
                    fontSize = 11.sp
                )
            }

            // Quick loading trigger arrow and delete
            IconButton(
                onClick = onSelect,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Quick view",
                    tint = Color(0xFF8E95BE),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete log",
                    tint = Color(0xFFFF5252).copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
