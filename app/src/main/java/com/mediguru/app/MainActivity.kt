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
                            Column {
                                Text(if (state.isRecording) "Listening..." else "Tap to Speak", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(state.transcription.ifBlank { "Describe your condition" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { viewModel.processDiagnosis() },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        AnimatedContent(targetState = state.status, label = "") { status ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isProcessing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(when(status) {
                                        DiagnosisStatus.NEURAL_ANALYSIS -> "Neural Stress Scan..."
                                        DiagnosisStatus.GENOMIC_MAPPING -> "Mapping Genomes..."
                                        DiagnosisStatus.RADIOLOGIST_REVIEW -> "Radiologist Analyzing..."
                                        DiagnosisStatus.SPECIALIST_CONSULT -> "Specialist Review..."
                                        DiagnosisStatus.PHARMACIST_CHECK -> "Pharmacist Verifying..."
                                        else -> "Board Meeting..."
                                    })
                                } else {
                                    Icon(Icons.Default.Groups, null)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Summon Medical Board", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (state.doctorResponse.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Text("Official Board Consensus", fontWeight = FontWeight.Bold)
                                    }
                                    Row {
                                        IconButton(onClick = { 
                                            val file = PdfGenerator.generateMedicalReport(
                                                context, 
                                                state.transcription, 
                                                state.doctorResponse, 
                                                state.selectedImageUri
                                            )
                                            if (file != null) {
                                                openPdf(context, file)
                                            } else {
                                                Toast.makeText(context, "Failed to generate report", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Download Report", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { copyToClipboard(context, state.doctorResponse) }) {
                                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(state.doctorResponse, style = MaterialTheme.typography.bodyLarge, lineHeight = 28.sp)
                            }
                        }
                    }
                }

                if (state.trajectory.isNotEmpty()) {
                    item {
                        Text("Quantum Recovery Pathway", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        RecoveryTrajectoryView(trajectory = state.trajectory)
                    }
                }

                if (history.isNotEmpty()) {
                    item { Text("Board Records", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }
                    items(history) { diagnosis -> HistoryItem(diagnosis, context) }
                }
            }
        }
    }

    @Composable
    fun BoardDiscussionLog(log: List<BoardMessage>) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Board Discussion Log", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                log.forEach { msg ->
                    Text(
                        text = "[${msg.agent}]: ${msg.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun RecoveryTrajectoryView(trajectory: List<RecoveryMilestone>) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            trajectory.forEachIndexed { index, milestone ->
                Row(verticalAlignment = Alignment.Top) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = milestone.day.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (index < trajectory.size - 1) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(40.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(milestone.action, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(milestone.bioTarget, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun BioTwinDashboard(vitals: BioVitals, targetSystem: BioSystem, isProcessing: Boolean, geneticScore: Float) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessibilityNew, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Quantum Bio-Simulation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (targetSystem != BioSystem.NONE) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = targetSystem.name,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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
                    1 -> currentStatus == DiagnosisStatus.SPECIALIST_CONSULT || currentStatus == DiagnosisStatus.GP_CONSULTATION
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
