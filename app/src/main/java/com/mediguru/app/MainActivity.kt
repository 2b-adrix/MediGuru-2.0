package com.mediguru.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.mediguru.app.data.local.DiagnosisEntity
import com.mediguru.app.ui.DiagnosisViewModel
import com.mediguru.app.ui.theme.MediGuruTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var mediaRecorder: MediaRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tts = TextToSpeech(this, this)
        setContent {
            MediGuruTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MediGuruScreen()
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    @OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun MediGuruScreen(viewModel: DiagnosisViewModel = hiltViewModel()) {
        val state by viewModel.uiState.collectAsState()
        val history by viewModel.diagnosisHistory.collectAsState()
        val context = LocalContext.current
        val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
        var showDeleteDialog by remember { mutableStateOf(false) }
        
        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri -> viewModel.onImageSelected(uri) }

        LaunchedEffect(state.doctorResponse) {
            if (state.doctorResponse.isNotEmpty()) {
                tts?.speak(state.doctorResponse, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(R.string.delete_confirm_title)) },
                text = { Text(stringResource(R.string.delete_confirm_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearHistory()
                        showDeleteDialog = false
                    }) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HealthAndSafety, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.app_name), fontWeight = FontWeight.ExtraBold)
                        }
                    },
                    actions = {
                        if (history.isNotEmpty()) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.clear_history))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    // Image Upload Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.selectedImageUri != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(state.selectedImageUri),
                                    contentDescription = stringResource(R.string.medical_image),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                FilledTonalIconButton(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.change_image))
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.AddPhotoAlternate, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(56.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        stringResource(R.string.upload_image), 
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                                        Text(stringResource(R.string.tap_to_select), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    // Voice Symptoms Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (state.isRecording) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(72.dp),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                FloatingActionButton(
                                    onClick = {
                                        if (micPermissionState.status.isGranted) {
                                            if (state.isRecording) {
                                                stopRecording()
                                                viewModel.onRecordingStateChanged(false, File(externalCacheDir, "symptoms.m4a"))
                                            } else {
                                                startRecording()
                                                viewModel.onRecordingStateChanged(true, null)
                                            }
                                        } else {
                                            micPermissionState.launchPermissionRequest()
                                        }
                                    },
                                    containerColor = if (state.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(20.dp),
                                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                                ) {
                                    Icon(
                                        if (state.isRecording) Icons.Default.Stop else Icons.Default.Mic, 
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    if (state.isRecording) stringResource(R.string.recording) else stringResource(R.string.tap_to_speak),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (state.isRecording) "Tap to stop" else "Tap to record",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                item {
                    // Analysis Button
                    Button(
                        onClick = { viewModel.processDiagnosis() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        enabled = !state.isLoading && (state.selectedImageUri != null || state.audioFile != null),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.consulting_ai))
                        } else {
                            Icon(Icons.Default.Analytics, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.get_diagnosis), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (state.doctorResponse.isNotEmpty()) {
                    item {
                        // AI Response Section
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Text(stringResource(R.string.ai_doctor_response), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Row {
                                        IconButton(onClick = { copyToClipboard(context, state.doctorResponse) }) {
                                            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.copy_diagnosis), modifier = Modifier.size(20.dp))
                                        }
                                        IconButton(onClick = { shareDiagnosis(context, state.doctorResponse) }) {
                                            Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.share_diagnosis), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(state.doctorResponse, style = MaterialTheme.typography.bodyLarge, lineHeight = 26.sp)
                            }
                        }
                    }
                }

                if (history.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.history),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(history) { diagnosis ->
                        HistoryItem(diagnosis, context)
                    }
                }
            }
        }
    }

    @Composable
    fun HistoryItem(diagnosis: DiagnosisEntity, context: Context) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
                    Text(
                        sdf.format(Date(diagnosis.timestamp)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row {
                        IconButton(onClick = { copyToClipboard(context, diagnosis.doctorResponse) }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    diagnosis.doctorResponse,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("MediGuru Diagnosis", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    private fun shareDiagnosis(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.sharing_subject))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_diagnosis)))
    }

    private fun startRecording() {
        try {
            val audioFile = File(externalCacheDir, "symptoms.m4a")
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
        } catch (e: Exception) {
            // Handle edge cases where stop is called too soon
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
