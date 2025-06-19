package com.example.dermalyzeapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        TextView welcomeText = findViewById(R.id.welcomeText);
        ImageView logoImage = findViewById(R.id.logoImage);
        Button continueButton = findViewById(R.id.continueButton);

        welcomeText.setText("Welcome to the Skin Cancer Detection App");
        // logoImage.setImageResource(R.drawable.ic_logo); // Uncomment and set your logo if available

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(WelcomeActivity.this, HomeActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
} 