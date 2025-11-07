# 🏥 Rajasthan Medical Problem Assistant (RMPA)

**Rajasthan Medical Problem Assistant (RMPA)** is an intelligent AI-powered healthcare platform built to help people across **Rajasthan** access quick and reliable medical insights through **voice, image, and text inputs**.

With RMPA, users can **speak their symptoms in Hindi, Rajasthani, or English**, **upload medical images (like X-rays or prescriptions)**, and get **instant AI-based medical advice** — all in one simple, browser-based interface.

---

## 🌿 Mission

To make healthcare **accessible, multilingual, and AI-driven** for everyone in Rajasthan — especially for those in **rural and remote areas** — by bridging the gap between people and professional medical understanding.

---

## ✨ Key Features

- 🗣️ **Voice Input:** Speak your symptoms in Hindi, Rajasthani, or English.
- 🧠 **AI Medical Insights:** Get professional, concise, doctor-like responses.
- 🩻 **Image Upload:** Analyze X-rays, prescriptions, or lab reports instantly.
- 🔊 **Audio Output:** Listen to the doctor’s response in a natural local voice.
- 🌐 **Browser-Based:** No installation required — access via any device.
- 💊 **Prescription Analysis:** Understand prescriptions and medicines easily.

---

## 🖼️ Technical Architecture

![Technical Architecture](technical_architecture.png)

> *Place `technical_architecture.png` in the project root directory or update the path accordingly.*

---

## 🚀 Tech Stack

- **Python 3.10+**
- **Gradio** — For the interactive web interface
- **gTTS** — Google Text-to-Speech (Hindi & Rajasthani output)
- **Groq API** — For transcription and AI-based image understanding
- **pydub, scipy** — For audio processing
- **dotenv** — For managing environment variables
- **Render** — For cloud deployment

---

🩺 Usage

Speak your symptoms using the microphone.

Upload medical images or prescriptions (optional).

View and listen to AI-generated medical advice.y:

Rajasthan-Medical-Problem-Assistant/
├── brain_of_the_doctor.py         # AI logic for image & symptom analysis
├── gradio_app.py                  # Main Gradio web app
├── requirements.txt               # Project dependencies
├── render.yaml                    # Render deployment configuration
├── voice_of_the_doctor.py         # Text-to-speech system
├── voice_of_the_patient.py        # Voice transcription system
├── .env.example                   # Example environment variables
└── technical_architecture.png     # System architecture image

Rajasthan Health Focus

☀️ Heatstroke and Dehydration

💧 Waterborne Diseases (Typhoid, Cholera, Diarrhea)

🧒 Malnutrition and Anemia

🌫️ Dust and Respiratory Issues

🦵 Joint Pain and Arthritis

👤 Author

Aditya Kumar Mishra 
Adapted for Rajasthan’s Healthcare Context
