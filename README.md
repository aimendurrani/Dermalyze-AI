# **Dermalyze – AI-Powered Skin Lesion Detection App**

Dermalyze is a mobile application designed to help users detect skin lesions using artificial intelligence. By capturing a photo of a lesion, users can instantly receive predictions about the type of skin condition. The app uses a deep learning model trained on the HAM10000 dataset and is optimized for real-world use through focal loss and transfer learning with MobileNetV2.

**Features**
Image capture and lesion classification directly within the app

AI-powered predictions using a trained convolutional neural network

PDF report generation for each scan

Lesion history tracking and cloud storage

Offline model prediction using TensorFlow Lite

Firebase-based user authentication and image/report storage

**Supported Lesion Classes**
The model is trained to detect the following seven types of skin lesions:

Melanocytic nevi (nv)

Melanoma (mel)

Benign keratosis-like lesions (bkl)

Basal cell carcinoma (bcc)

Actinic keratoses / Intraepithelial carcinoma (akiec)

Vascular lesions (vasc)

Dermatofibroma (df)

**AI Model Overview**
Dataset: HAM10000

Model Base: MobileNetV2 (pretrained on ImageNet)

Modifications: Custom classification head added, base layers frozen

Loss Function: Focal Loss (added to address class imbalance and improve accuracy)

Optimizer: Adam (learning rate = 0.001)

Evaluation Metrics: Accuracy, Precision, Recall, F1-score, Top-2 and Top-3 Accuracy

Deployment Format: .tflite model used in Android app

**Source Code and Training**
The core AI code was adapted from the Skin-Lesion-Analyzer project.
Modifications include:

Added Focal Loss to handle class imbalance

Tuned hyperparameters for improved accuracy

Exported trained model to .tflite for mobile deployment

You can find the training notebook in this repository under ai-project.ipynb.

**App Development**
Built in Android Studio using Kotlin

Integrated the TFLite model using TensorFlow Lite

Firebase used for:

User authentication

Image and prediction result storage

Report generation and saving

UI designed to match a clean, user-friendly experience

**Results**
Previous baseline accuracy: ~37%

Improved accuracy after model updates and focal loss: ~94% categorical accuracy

High performance on underrepresented classes as well

**Getting Started**
Clone this repository

Open the Android app folder in Android Studio

Replace skin_model.tflite with your own trained model (optional)

Add your Firebase configuration

Run the app on a physical Android device or emulator

**License**
This project is released under the MIT License.
