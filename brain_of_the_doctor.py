from dotenv import load_dotenv
load_dotenv()

import os
import base64
from groq import Groq

# Load Groq API Key
GROQ_API_KEY = os.environ.get("GROQ_API_KEY")

def encode_image(image_path):   
    with open(image_path, "rb") as image_file:
        return base64.b64encode(image_file.read()).decode('utf-8')

# Default values
query = "Is there something wrong with my face?"
# Using llama-3.2-11b-vision-preview as it is the current stable vision model on Groq.
model = "llama-3.2-11b-vision-preview"

def analyze_image_with_query(query, model, encoded_image):
    client = Groq(api_key=GROQ_API_KEY)  
    messages = [
        {
            "role": "user",
            "content": [
                {
                    "type": "text", 
                    "text": query
                },
                {
                    "type": "image_url",
                    "image_url": {
                        "url": f"data:image/jpeg;base64,{encoded_image}",
                    },
                },
            ],
        }]
    chat_completion = client.chat.completions.create(
        messages=messages,
        model=model
    )

    return chat_completion.choices[0].message.content
