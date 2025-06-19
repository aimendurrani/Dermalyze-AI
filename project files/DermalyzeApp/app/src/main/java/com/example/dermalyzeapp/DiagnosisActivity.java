package com.example.dermalyzeapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.dermalyzeapp.databinding.ActivityDiagnosisBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Image;
import com.itextpdf.io.image.ImageDataFactory;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;

public class DiagnosisActivity extends AppCompatActivity {
    private ActivityDiagnosisBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private String imageUriString;
    private String condition;
    private double confidence;
    private String recommendations;
    
    private Interpreter tflite;
    private static final int IMAGE_SIZE = 224;
    private static final float PROBABILITY_THRESHOLD = 0.5f;
    private static final String MODEL_PATH = "skin_model.tflite";

    private static final String[] SKIN_CONDITIONS = {
        "Actinic Keratoses / Bowen's Disease",
        "Basal Cell Carcinoma",
        "Benign Keratosis-like Lesions",
        "Dermatofibroma",
        "Melanoma",
        "Melanocytic Nevi (moles)",
        "Vascular Lesions"
    };

    private static final String[] CONDITION_DESCRIPTIONS = {
        // akiec
        "Actinic Keratoses are pre-cancerous skin growths that typically appear on sun-damaged skin. " +
        "They often present as rough, scaly patches that can be pink, red, or flesh-colored. " +
        "Bowen's Disease is an early form of skin cancer that appears as a persistent, scaly red patch.",

        // bcc
        "Basal Cell Carcinoma is the most common form of skin cancer. It typically appears as a " +
        "pearly, waxy bump; a flat, flesh-colored or brown scar-like lesion; or a bleeding or " +
        "scabbing sore that heals and returns. It's usually found on sun-exposed areas.",

        // bkl
        "Benign Keratosis-like Lesions are non-cancerous skin growths that often appear with age. " +
        "They can be flat or slightly raised, with colors ranging from light tan to dark brown. " +
        "These lesions are harmless but may resemble more serious conditions.",

        // df
        "Dermatofibroma is a common benign skin tumor that often appears as a firm, raised growth " +
        "that can be pink, gray, red, or brown. They're usually round, relatively small, and may " +
        "dimple when pressed from the sides.",

        // mel
        "Melanoma is the most dangerous form of skin cancer. It develops in melanocytes (pigment-producing cells) " +
        "and often resembles moles. Key warning signs include asymmetry, irregular borders, color " +
        "variations, diameter >6mm, and evolving size/shape/color.",

        // nv
        "Melanocytic Nevi (moles) are common, usually benign skin growths that develop from melanocytes. " +
        "They can be flat or raised, round or oval, and range in color from pink to dark brown. " +
        "While most are harmless, any changes should be monitored.",

        // vasc
        "Vascular Lesions are abnormalities of blood vessels appearing on or under the skin. " +
        "They can appear as red, purple, or blue marks and may be flat or raised. Types include " +
        "hemangiomas, port wine stains, and spider veins."
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("DiagnosisActivity", "onCreate called - DiagnosisActivity is starting!");
        if (getIntent() != null && getIntent().getExtras() != null) {
            for (String key : getIntent().getExtras().keySet()) {
                Object value = getIntent().getExtras().get(key);
                Log.d("DiagnosisActivity", "Intent extra: " + key + " = " + value);
            }
        } else {
            Log.d("DiagnosisActivity", "No extras in Intent!");
        }
        binding = ActivityDiagnosisBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Handle back button
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, R.string.error_user_not_logged_in, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            initializeTFLiteInterpreter();
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.error_initializing_tflite, e.getMessage()), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        imageUriString = getIntent().getStringExtra("imageUri");
        Log.d("DiagnosisActivity", "imageUriString from Intent: " + imageUriString);
        if (imageUriString != null) {
            displayImage();
            analyzeImage();
        } else {
            Log.e("DiagnosisActivity", "No imageUriString passed in Intent!");
        }

        binding.generatePdfButton.setOnClickListener(v -> generatePdfReport());
    }

    private void initializeTFLiteInterpreter() throws IOException {
        MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(this, MODEL_PATH);
        tflite = new Interpreter(tfliteModel);
    }

    private void displayImage() {
        try {
            Uri imageUri = Uri.parse(imageUriString);
            File imageFile = new File(imageUri.getPath());
            Log.d("DiagnosisActivity", "Trying to load image from: " + imageFile.getAbsolutePath() + ", exists: " + imageFile.exists());
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
            binding.scannedImage.setImageBitmap(bitmap);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_loading_image, e.getMessage()), Toast.LENGTH_SHORT).show();
            Log.e("DiagnosisActivity", "Error loading image: " + e.getMessage(), e);
        }
    }

    private void analyzeImage() {
        try {
            binding.statusTextView.setText("Analyzing image...");
            binding.statusTextView.setTextColor(getResources().getColor(android.R.color.black, null));

            Uri imageUri = Uri.parse(imageUriString);
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
            if (bitmap == null) {
                binding.statusTextView.setText("Error: Could not decode image.");
                binding.statusTextView.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
                Toast.makeText(this, "Error: Could not decode image.", Toast.LENGTH_LONG).show();
                return;
            }

            // Preprocess the image
            TensorImage tensorImage = preprocessImage(bitmap);

            // Run inference
            float[][] outputArray = new float[1][SKIN_CONDITIONS.length];
            tflite.run(tensorImage.getBuffer(), outputArray);

            // Process results
            int maxIndex = 0;
            float maxConfidence = outputArray[0][0];

            for (int i = 1; i < outputArray[0].length; i++) {
                if (outputArray[0][i] > maxConfidence) {
                    maxConfidence = outputArray[0][i];
                    maxIndex = i;
                }
            }

            if (maxConfidence >= PROBABILITY_THRESHOLD) {
                condition = SKIN_CONDITIONS[maxIndex];
                confidence = maxConfidence;
                recommendations = generateRecommendations(condition);

                updateUI();
                saveResults();
            } else {
                binding.statusTextView.setText("Unable to confidently diagnose.");
                binding.statusTextView.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
                Toast.makeText(this, "Unable to confidently diagnose.", Toast.LENGTH_LONG).show();
            }

            Log.d("DiagnosisActivity", "Model output: " + java.util.Arrays.toString(outputArray[0]));
        } catch (Exception e) {
            binding.statusTextView.setText("Error analyzing image: " + e.getMessage());
            binding.statusTextView.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
            Toast.makeText(this, "Error analyzing image: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private TensorImage preprocessImage(Bitmap bitmap) {
        // Initialize tensor image
        TensorImage tensorImage = new TensorImage();
        tensorImage.load(bitmap);
        
        // Create image processor
        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(IMAGE_SIZE, IMAGE_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(new NormalizeOp(0f, 255f)) // Normalize to [0,1]
                .build();
        
        // Process the image
        return imageProcessor.process(tensorImage);
    }

    private void updateUI() {
        binding.conditionTextView.setText(condition);
        binding.confidenceTextView.setText(String.format(Locale.US, "Confidence: %.1f%%", confidence * 100));
        binding.conditionDescriptionTextView.setText(CONDITION_DESCRIPTIONS[getConditionIndex(condition)]);
        binding.recommendationsTextView.setText(recommendations);
        binding.statusTextView.setText("Analysis complete - Saving results...");
        binding.statusTextView.setTextColor(getResources().getColor(android.R.color.black, null));
    }

    private int getConditionIndex(String condition) {
        for (int i = 0; i < SKIN_CONDITIONS.length; i++) {
            if (SKIN_CONDITIONS[i].equals(condition)) {
                return i;
            }
        }
        return 0;
    }

    private String generateRecommendations(String condition) {
        switch (condition) {
            case "Actinic Keratoses / Bowen's Disease":
                return "ATTENTION: Actinic Keratoses detected, which may develop into skin cancer.\n\n" +
                       "Recommended actions:\n" +
                       "1. Schedule an appointment with a dermatologist\n" +
                       "2. Protect the affected area from sun exposure\n" +
                       "3. Use prescribed sunscreen regularly\n" +
                       "4. Monitor for any changes in size or appearance\n\n" +
                       "Note: This is an AI-assisted analysis and should not be considered a final diagnosis.";

            case "Basal Cell Carcinoma":
                return "URGENT: The AI model suggests this may be Basal Cell Carcinoma, a type of skin cancer.\n\n" +
                       "Recommended actions:\n" +
                       "1. Schedule an immediate appointment with a dermatologist\n" +
                       "2. Do not delay seeking professional medical attention\n" +
                       "3. Bring this image and analysis to your appointment\n" +
                       "4. Avoid sun exposure to the affected area\n\n" +
                       "Note: This is an AI-assisted analysis and should not be considered a final diagnosis.";

            case "Benign Keratosis-like Lesions":
                return "The AI model suggests these are benign (non-cancerous) growths.\n\n" +
                       "Recommended actions:\n" +
                       "1. Monitor the lesions for any changes\n" +
                       "2. Schedule a routine check-up with a dermatologist\n" +
                       "3. Protect your skin from sun damage\n" +
                       "4. Document any changes in size or appearance\n\n" +
                       "Note: This is an AI-assisted analysis and should not be considered a final diagnosis.";

            case "Dermatofibroma":
                return "The AI model suggests this may be a Dermatofibroma, which is typically benign.\n\n" +
                       "Recommended actions:\n" +
                       "1. No immediate action required if stable\n" +
                       "2. Monitor for any changes in size or color\n" +
                       "3. Consider a routine dermatologist check-up\n" +
                       "4. Document any changes or symptoms\n\n" +
                       "Note: This is an AI-assisted analysis and should not be considered a final diagnosis.";

            case "Melanoma":
                return "URGENT: The AI model suggests this may be Melanoma, a serious form of skin cancer.\n\n" +
                       "Recommended actions:\n" +
                       "1. Seek IMMEDIATE medical attention\n" +
                       "2. Schedule an emergency appointment with a dermatologist\n" +
                       "3. Bring this image and analysis to your appointment\n" +
                       "4. Do not delay - early treatment is crucial\n\n" +
                       "Note: This is an AI-assisted analysis and should not be considered a final diagnosis.";

            case "Melanocytic Nevi (moles)":
                return "The AI model suggests this is a common mole (Melanocytic Nevus).\n\n" +
                       "Recommended actions:\n" +
                       "1. Regular self-monitoring using the ABCDE rule:\n" +
                       "   - Asymmetry\n" +
                       "   - Border irregularity\n" +
                       "   - Color variation\n" +
                       "   - Diameter > 6mm\n" +
                       "   - Evolving size/shape\n" +
                       "2. Annual skin check with a dermatologist\n" +
                       "3. Protect from sun exposure\n\n" +
                       "Note: This is an AI-assisted analysis and should not be considered a final diagnosis.";

            case "Vascular Lesions":
                return "The AI model suggests this may be a vascular lesion.\n\n" +
                       "Recommended actions:\n" +
                       "1. Schedule a consultation with a dermatologist\n" +
                       "2. Monitor for any changes in size or color\n" +
                       "3. Document any associated symptoms\n" +
                       "4. Protect the area from injury\n\n" +
                       "Note: This is an AI-assisted analysis and should not be considered a final diagnosis.";

            default:
                return "Please consult with a healthcare professional for proper diagnosis and treatment.\n\n" +
                       "Note: This is an AI-assisted analysis and should not be considered a final diagnosis.";
        }
    }

    private void saveResults() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Error: User not logged in", Toast.LENGTH_LONG).show();
            binding.statusTextView.setText("Error: User not logged in");
            binding.statusTextView.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        com.google.firebase.Timestamp firestoreTimestamp = com.google.firebase.Timestamp.now();

        try {
            Toast.makeText(this, "Saving results...", Toast.LENGTH_SHORT).show();
            Log.d("DiagnosisActivity", "Starting save process for user: " + userId);
            
            // Validate image URI
            if (imageUriString == null || imageUriString.isEmpty()) {
                throw new IllegalArgumentException("Image URI is null or empty");
            }

            Uri imageUri = Uri.parse(imageUriString);
            if (imageUri == null) {
                throw new IllegalArgumentException("Invalid image URI");
            }

            // Load and validate bitmap
            Bitmap originalBitmap = null;
            try {
                originalBitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
            } catch (Exception e) {
                throw new IllegalArgumentException("Could not load image: " + e.getMessage());
            }

            if (originalBitmap == null) {
                throw new IllegalArgumentException("Could not decode image");
            }

            // Create directory if it doesn't exist
            File storageDir = new File(getExternalFilesDir(null), "scans");
            if (!storageDir.exists()) {
                if (!storageDir.mkdirs()) {
                    throw new IOException("Failed to create storage directory");
                }
            }

            // Save bitmap to external storage
            File imageFile = new File(storageDir, "scan_" + timestamp + ".jpg");
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(imageFile);
                if (!originalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)) {
                    throw new IOException("Failed to compress image");
                }
                fos.flush();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            // Verify the file was created
            if (!imageFile.exists() || imageFile.length() == 0) {
                throw new IOException("Failed to save image file");
            }

            String localImagePath = imageFile.getAbsolutePath();
            Log.d("DiagnosisActivity", "Image saved to: " + localImagePath);

            // Create scan result object
            ScanResult scanResult = new ScanResult(
                    timestamp,
                    localImagePath,
                    condition,
                    confidence,
                    firestoreTimestamp,
                    recommendations
            );

            // Save to Firestore
            Log.d("DiagnosisActivity", "Saving to Firestore...");
            db.collection("users")
                    .document(userId)
                    .collection("scans")
                    .document(timestamp)
                    .set(scanResult)
                    .addOnSuccessListener(aVoid -> {
                        Log.d("DiagnosisActivity", "Successfully saved to Firestore");
                        binding.statusTextView.setText("Analysis complete - Results saved");
                        binding.statusTextView.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
                        Toast.makeText(this, "Scan results saved successfully", Toast.LENGTH_SHORT).show();

                        // Notify HistoryActivity to refresh
                        Intent refreshIntent = new Intent("com.example.dermalyzeapp.SCAN_SAVED");
                        refreshIntent.setPackage(getPackageName());
                        sendBroadcast(refreshIntent);
                        Log.d("DiagnosisActivity", "Sent broadcast to refresh history");

                        // Return to HistoryActivity
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e("DiagnosisActivity", "Failed to save to Firestore", e);
                        // Delete the saved image if Firestore save fails
                        if (imageFile.exists()) {
                            imageFile.delete();
                        }
                        
                        String errorMessage = "Failed to save results: " + e.getMessage();
                        binding.statusTextView.setText(errorMessage);
                        binding.statusTextView.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                    });

        } catch (Exception e) {
            Log.e("DiagnosisActivity", "Error in saveResults", e);
            String errorMessage = "Error processing image: " + e.getMessage();
            binding.statusTextView.setText(errorMessage);
            binding.statusTextView.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
        }
    }

    private void generatePdfReport() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File pdfFile = new File(getExternalFilesDir(null), "skin_scan_" + timestamp + ".pdf");

            PdfWriter writer = new PdfWriter(pdfFile);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // Add title
            document.add(new Paragraph(getString(R.string.pdf_title))
                    .setFontSize(20)
                    .setBold());

            // Add date
            document.add(new Paragraph(getString(R.string.pdf_date_format, 
                new SimpleDateFormat("MMMM dd, yyyy HH:mm:ss", Locale.US).format(new Date()))));

            // Add image
            try {
                Uri imageUri = Uri.parse(imageUriString);
                Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                Image pdfImg = new Image(ImageDataFactory.create(stream.toByteArray()));
                pdfImg.setWidth(300);
                document.add(pdfImg);
            } catch (Exception e) {
                // Handle image error
            }

            // Add diagnosis details
            document.add(new Paragraph(getString(R.string.pdf_diagnosis_details))
                    .setFontSize(16)
                    .setBold());
            document.add(new Paragraph(getString(R.string.condition_format, condition)));
            document.add(new Paragraph(getString(R.string.confidence_format, confidence * 100)));

            // Add description
            document.add(new Paragraph(getString(R.string.pdf_condition_description))
                    .setFontSize(16)
                    .setBold());
            document.add(new Paragraph(CONDITION_DESCRIPTIONS[getConditionIndex(condition)]));

            // Add recommendations
            document.add(new Paragraph(getString(R.string.pdf_recommendations))
                    .setFontSize(16)
                    .setBold());
            document.add(new Paragraph(recommendations));

            // Add disclaimer
            document.add(new Paragraph(getString(R.string.pdf_disclaimer))
                    .setFontSize(14)
                    .setBold());
            document.add(new Paragraph(getString(R.string.pdf_disclaimer_text))
                    .setFontSize(10)
                    .setItalic());

            document.close();

            // Share PDF
            sharePdfReport(pdfFile);

        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_generating_pdf, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void sharePdfReport(File pdfFile) {
        Uri pdfUri = FileProvider.getUriForFile(this,
                getApplicationContext().getPackageName() + ".provider", pdfFile);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, pdfUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.share_pdf)));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tflite != null) {
            tflite.close();
        }
    }
} 