# 🏥 MediGuru AI - Industrial Grade Medical Assistant

**MediGuru AI** is a professional-grade Android application that leverages state-of-the-art AI models to provide instant medical insights. By combining **Voice Transcription (Whisper)** and **Vision Analysis (Llama 3.2)**, MediGuru helps users understand symptoms and medical documents with ease.

---

## ✨ Key Features

- 🎙️ **Voice Symptom Analysis:** Speak your symptoms and get them transcribed and analyzed instantly using Groq's Whisper-large-v3.
- 📸 **Medical Image Vision:** Upload X-Rays, prescriptions, or skin conditions for AI-powered visual analysis.
- 💾 **Offline History:** All consultations are saved locally using **Room Database** for future reference.
- 🔊 **Text-to-Speech:** AI responses are read aloud for better accessibility.
- 📤 **Share & Export:** Easily copy or share diagnosis results with healthcare professionals.
- 🎨 **Modern Material 3 UI:** A clean, intuitive interface with edge-to-edge support and dynamic themes.

---

## 🚀 Tech Stack & Architecture

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Architecture:** MVVM with Clean Architecture principles
- **Dependency Injection:** Hilt (Dagger)
- **Networking:** Retrofit & OkHttp
- **Database:** Room (for persistent history)
- **AI Backend:** Groq Cloud API (Whisper-large-v3 & Llama-3.2-11b-vision)
- **Image Handling:** Coil
- **Logging:** Timber
- **Utilities:** Google Accompanist (Permissions), Splashscreen API

---

## 🛠️ Getting Started

### Prerequisites
1. Get a **Groq API Key** from [Groq Console](https://console.groq.com/).
2. Add the key to your `local.properties`:
   ```properties
   GROQ_API_KEY=your_api_key_here
   ```

### Installation
1. Clone the repository.
2. Open in **Android Studio Jellyfish** or newer.
3. Sync Gradle and Run on an emulator or physical device.

---

## 📂 Project Structure

```text
com.mediguru.app/
├── data/
│   ├── api/          # Retrofit Interfaces
│   ├── local/        # Room Database, DAOs, Entities
│   ├── model/        # Data Transfer Objects (DTOs)
│   └── repository/   # Business Logic & Data Coordination
├── di/               # Hilt Dependency Injection Modules
├── ui/
│   ├── theme/        # Material 3 Design System
│   └── DiagnosisViewModel.kt
└── MainActivity.kt   # Main Entry Point & Compose UI
```

---

## 🛡️ Disclaimer
*MediGuru AI is an educational tool and does not provide professional medical advice, diagnosis, or treatment. Always seek the advice of your physician or other qualified health provider with any questions you may have regarding a medical condition.*

---

👤 **Developed by Aditya Kumar Mishra**
*Targeting the highest standards of Android Development.*
