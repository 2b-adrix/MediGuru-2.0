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
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.mediguru.app.data.local.DiagnosisEntity
import com.mediguru.app.ui.*
import com.mediguru.app.ui.theme.MediGuruTheme
import com.mediguru.app.util.PdfGenerator
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
        val haptic = LocalHapticFeedback.current
        val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
        var showDeleteDialog by remember { mutableStateOf(false) }
        
        val isProcessing = state.status != DiagnosisStatus.IDLE && state.status != DiagnosisStatus.SUCCESS && state.status != DiagnosisStatus.ERROR

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri -> viewModel.onImageSelected(uri) }

        LaunchedEffect(state.doctorResponse) {
            if (state.doctorResponse.isNotEmpty() && state.status == DiagnosisStatus.SUCCESS) {
                tts?.speak(state.doctorResponse, TextToSpeech.QUEUE_FLUSH, null, null)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    BioTwinDashboard(
                        vitals = state.vitals, 
                        targetSystem = state.targetSystem,
                        isProcessing = isProcessing,
                        geneticScore = state.geneticRiskScore
                    )
                }

                item {
                    MedicalBoardVisualizer(
                        currentStatus = state.status,
                        specialists = state.activeSpecialists
                    )
                }

                if (state.boardLog.isNotEmpty() && isProcessing) {
                    item {
                        BoardDiscussionLog(log = state.boardLog)
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            if (state.selectedImageUri != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(state.selectedImageUri),
                                    contentDescription = stringResource(R.string.medical_image),
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                FilledTonalIconButton(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                                ) { Icon(Icons.Default.Edit, contentDescription = null) }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(8.dp))
                                    Text(stringResource(R.string.upload_image), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Symptoms Hub", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val symptoms = listOf("Fever", "Headache", "Cough", "Pain", "Rash", "Nausea")
                        items(symptoms) { symptom ->
                            FilterChip(
                                selected = state.transcription.contains(symptom),
                                onClick = { viewModel.onSymptomTapped(symptom) },
                                label = { Text(symptom) }
                            )
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                    ) {
                        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            RecordingIndicator(isRecording = state.isRecording) {
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
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.isRecording) "Listening..." else if (state.transcription.isEmpty()) "Tap mic to start" else "Analysis Ready",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (state.transcription.isNotEmpty()) {
                                    Text(state.transcription, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            
                            Box(contentAlignment = Alignment.Center) {
                                Button(
                                    onClick = { viewModel.processDiagnosis() },
                                    enabled = !isProcessing && (state.transcription.isNotEmpty() || state.selectedImageUri != null),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Analyze")
                                }
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.status == DiagnosisStatus.ERROR) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                text = state.error?.asString(context) ?: "Unknown Error",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                if (state.doctorResponse.isNotEmpty()) {
                    item {
                        DiagnosisResultCard(
                            response = state.doctorResponse,
                            transcription = state.transcription,
                            imageUri = state.selectedImageUri,
                            trajectory = state.trajectory,
                            onCopy = { copyToClipboard(context, state.doctorResponse) },
                            onShare = { shareDiagnosis(context, state.doctorResponse) },
                            onPdf = { transcription, response, imageUri ->
                                val file = PdfGenerator.generateMedicalReport(context, transcription, response, imageUri)
                                if (file != null) openPdf(context, file)
                            }
                        )
                    }
                }

                if (history.isNotEmpty()) {
                    item {
                        Text("Medical History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    items(history) { diagnosis ->
                        HistoryItem(diagnosis, context)
                    }
                }
            }
        }
    }

    @Composable
    fun BioTwinDashboard(vitals: BioVitals, targetSystem: BioSystem, isProcessing: Boolean, geneticScore: Float) {
        val infiniteTransition = rememberInfiniteTransition(label = "")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = ""
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(64.dp).scale(if (isProcessing) pulse else 1f), contentAlignment = Alignment.Center) {
                        Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {}
                        Icon(
                            imageVector = when(targetSystem) {
                                BioSystem.NERVOUS -> Icons.Default.Psychology
                                BioSystem.RESPIRATORY -> Icons.Default.Air
                                BioSystem.CARDIOVASCULAR -> Icons.Default.Favorite
                                else -> Icons.Default.AccessibilityNew
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Patient Digital Twin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isProcessing) "Live System Analysis..." else "Status: Optimal",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isProcessing) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        VitalBar("Neural Stress", vitals.painIntensity, MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        VitalBar("Genetic Risk", geneticScore, Color(0xFFFF9800))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        VitalBar("Inflammation", vitals.inflammation, MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(12.dp))
                        VitalBar("Homeostasis", vitals.recoveryPotential, Color(0xFF4CAF50))
                    }
                }
            }
        }
    }

    @Composable
    fun VitalBar(label: String, value: Float, color: Color) {
        val animatedValue by animateFloatAsState(targetValue = value, animationSpec = tween(1000), label = "")
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("${(animatedValue * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { animatedValue },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = color,
                trackColor = color.copy(alpha = 0.1f)
            )
        }
    }

    @Composable
    fun MedicalBoardVisualizer(currentStatus: DiagnosisStatus, specialists: List<Specialist>) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            specialists.forEachIndexed { index, specialist ->
                val isActive = when(index) {
                    0 -> currentStatus == DiagnosisStatus.RADIOLOGIST_REVIEW
                    1 -> currentStatus == DiagnosisStatus.SPECIALIST_CONSULT
                    2 -> currentStatus == DiagnosisStatus.PHARMACIST_CHECK
                    else -> false
                }
                
                val icon = when(specialist.icon) {
                    "Visibility" -> Icons.Default.Visibility
                    "Person" -> Icons.Default.Person
                    "Medication" -> Icons.Default.Medication
                    "Face" -> Icons.Default.Face
                    "Favorite" -> Icons.Default.Favorite
                    "Psychology" -> Icons.Default.Psychology
                    else -> Icons.Default.Groups
                }
                
                AgentAvatar(specialist.name, icon, isActive)
            }
        }
    }

    @Composable
    fun AgentAvatar(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isActive: Boolean) {
        val scale by animateFloatAsState(if (isActive) 1.2f else 1f, label = "")
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(56.dp).scale(scale),
                shape = CircleShape,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primaryContainer) else null
            ) {
                Icon(icon, null, modifier = Modifier.padding(12.dp), tint = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp), color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray)
        }
    }

    @Composable
    fun BoardDiscussionLog(log: List<BoardMessage>) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Forum, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Consensus Pipeline", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                log.takeLast(3).forEach { message ->
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("${message.agent}: ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(message.message, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    @Composable
    fun DiagnosisResultCard(
        response: String, 
        transcription: String,
        imageUri: android.net.Uri?,
        trajectory: List<RecoveryMilestone>, 
        onCopy: () -> Unit, 
        onShare: () -> Unit, 
        onPdf: (String, String, android.net.Uri?) -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Clinical Analysis", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Row {
                        IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, null) }
                        IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, null) }
                        IconButton(onClick = { onPdf(transcription, response, imageUri) }) { Icon(Icons.Outlined.PictureAsPdf, null) }
                    }
                }
                
                Text(text = response, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 16.dp))
                
                if (trajectory.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    Text("Recovery Trajectory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    trajectory.forEach { milestone ->
                        Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(modifier = Modifier.size(24.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(milestone.day.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(milestone.action, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(milestone.bioTarget, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun EliteDashboard(historyCount: Int) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardCard(Modifier.weight(1f), "Board Rating", "Gold", Icons.Default.Star, MaterialTheme.colorScheme.primary)
            DashboardCard(Modifier.weight(1f), "Case Load", historyCount.toString(), Icons.Default.Work, MaterialTheme.colorScheme.tertiary)
        }
    }

    @Composable
    fun DashboardCard(modifier: Modifier, title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
        Card(modifier = modifier, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = color)
                Text(title, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.7f))
            }
        }
    }

    @Composable
    fun RecordingIndicator(isRecording: Boolean, onClick: () -> Unit) {
        Box(contentAlignment = Alignment.Center) {
            FloatingActionButton(onClick = onClick, containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, shape = CircleShape) {
                Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, null)
            }
        }
    }

    @Composable
    fun HistoryItem(diagnosis: DiagnosisEntity, context: Context) {
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(diagnosis.timestamp)), style = MaterialTheme.typography.labelMedium)
                Text(diagnosis.doctorResponse, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("MediGuru", text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareDiagnosis(context: Context, text: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Share"))
    }

    private fun openPdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun startRecording() {
        mediaRecorder = MediaRecorder().apply { setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setOutputFile(File(externalCacheDir, "symptoms.m4a").absolutePath); prepare(); start() }
    }

    private fun stopRecording() { try { mediaRecorder?.apply { stop(); release() }; mediaRecorder = null } catch (e: Exception) {} }

    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}
