package com.example.dermalyzeapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.dermalyzeapp.databinding.ActivityHomeBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {
    private static final String TAG = "HomeActivity";
    private ActivityHomeBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private List<ScanResult> recentScans;
    private ScanHistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        recentScans = new ArrayList<>();

        setupRecyclerView();
        setupButtons();
        loadRecentScans();
    }

    private void setupRecyclerView() {
        adapter = new ScanHistoryAdapter(recentScans, result -> {
            Intent intent = new Intent(this, DiagnosisActivity.class);
            intent.putExtra("scanResult", result);
            startActivity(intent);
        });
        binding.recentScansRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recentScansRecyclerView.setAdapter(adapter);
    }

    private void setupButtons() {
        binding.scanButton.setOnClickListener(v -> startScan());
        binding.fabNewScan.setOnClickListener(v -> startScan());
        binding.viewHistoryButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecentScans();
    }

    private void loadRecentScans() {
        if (mAuth.getCurrentUser() == null) {
            Log.e(TAG, "User not authenticated");
            Toast.makeText(this, "Please sign in to view scans", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        Log.d(TAG, "Loading recent scans for user: " + userId);

        db.collection("users")
                .document(userId)
                .collection("scans")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "Successfully loaded " + queryDocumentSnapshots.size() + " recent scans");
                    recentScans.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            ScanResult scan = document.toObject(ScanResult.class);
                            scan.setId(document.getId());
                            recentScans.add(scan);
                            Log.d(TAG, "Added recent scan: " + scan.getCondition());
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing scan result", e);
                        }
                    }
                    updateUI();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading recent scans", e);
                    Toast.makeText(this, "Failed to load recent scans", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateUI() {
        if (recentScans.isEmpty()) {
            binding.noScansText.setVisibility(View.VISIBLE);
            binding.recentScansRecyclerView.setVisibility(View.GONE);
        } else {
            binding.noScansText.setVisibility(View.GONE);
            binding.recentScansRecyclerView.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    private void startScan() {
        Intent intent = new Intent(this, ScanActivity.class);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.home_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_history) {
            Intent intent = new Intent(this, HistoryActivity.class);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.action_logout) {
            mAuth.signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 