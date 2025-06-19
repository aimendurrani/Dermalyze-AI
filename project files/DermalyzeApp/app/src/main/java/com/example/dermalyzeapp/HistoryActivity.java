package com.example.dermalyzeapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.dermalyzeapp.databinding.ActivityHistoryBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {
    private static final String TAG = "HistoryActivity";
    private ActivityHistoryBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private List<ScanResult> scanResults;
    private ScanHistoryAdapter adapter;
    private BroadcastReceiver scanSavedReceiver;
    private boolean isLoading = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long REFRESH_DELAY = 500; // 500ms delay

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Scan History");
        }

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        scanResults = new ArrayList<>();

        setupRecyclerView();
        setupSwipeRefresh();
        setupBroadcastReceiver();
        loadScanHistory();
    }

    private void setupRecyclerView() {
        adapter = new ScanHistoryAdapter(scanResults, this::onItemClick);

        binding.historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.historyRecyclerView.setAdapter(adapter);

        // Add scroll listener to detect when user reaches bottom
        binding.historyRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (!recyclerView.canScrollVertically(1) && !isLoading) {
                    // User has scrolled to the bottom
                    loadMoreResults();
                }
            }
        });

        // Set up start scan button
        binding.startScanButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScanActivity.class);
            startActivity(intent);
        });
    }

    private void setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener(this::refreshData);
    }

    private void refreshData() {
        scanResults.clear();
        adapter.notifyDataSetChanged();
        loadScanHistory();
    }

    private void setupBroadcastReceiver() {
        scanSavedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received scan saved broadcast");
                // Add delay before refreshing to allow Firebase to complete the write
                handler.postDelayed(() -> {
                    if (!isFinishing() && !isLoading) {
                        refreshData();
                    }
                }, REFRESH_DELAY);
            }
        };
        IntentFilter filter = new IntentFilter("com.example.dermalyzeapp.SCAN_SAVED");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanSavedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(scanSavedReceiver, filter);
        }
    }

    private void loadScanHistory() {
        if (isLoading) {
            Log.d(TAG, "Already loading data, skipping request");
            return;
        }

        if (mAuth.getCurrentUser() == null) {
            Log.e(TAG, "User not authenticated");
            Toast.makeText(this, "Please sign in to view history", Toast.LENGTH_SHORT).show();
            binding.swipeRefreshLayout.setRefreshing(false);
            showEmptyState("Please sign in to view history");
            return;
        }

        isLoading = true;
        String userId = mAuth.getCurrentUser().getUid();
        Log.d(TAG, "Loading scan history for user: " + userId);

        db.collection("users")
                .document(userId)
                .collection("scans")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)  // Limit to 20 items per page
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "Successfully loaded " + queryDocumentSnapshots.size() + " scan results");
                    scanResults.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            ScanResult scan = document.toObject(ScanResult.class);
                            scan.setId(document.getId());
                            scanResults.add(scan);
                            Log.d(TAG, "Added scan result: " + scan.getCondition() + " with confidence: " + scan.getConfidence());
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing scan result: " + document.getId(), e);
                        }
                    }
                    updateUI();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading scan history", e);
                    Toast.makeText(this, "Failed to load scan history: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    showEmptyState("Error loading scan history");
                })
                .addOnCompleteListener(task -> {
                    isLoading = false;
                    binding.swipeRefreshLayout.setRefreshing(false);
                });
    }

    private void loadMoreResults() {
        // Implement pagination if needed
        // This is a placeholder for future implementation
    }

    private void showEmptyState(String message) {
        TextView titleText = binding.emptyView.findViewById(R.id.emptyTitleText);
        TextView messageText = binding.emptyView.findViewById(R.id.emptyMessageText);
        
        if (titleText != null) {
            titleText.setText("No scan history available");
        }
        if (messageText != null) {
            messageText.setText(message);
        }
        
        binding.emptyView.setVisibility(View.VISIBLE);
        binding.historyRecyclerView.setVisibility(View.GONE);
    }

    private void updateUI() {
        Log.d(TAG, "Updating UI with " + scanResults.size() + " items");
        if (scanResults.isEmpty()) {
            showEmptyState("No scan history available");
        } else {
            binding.emptyView.setVisibility(View.GONE);
            binding.historyRecyclerView.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isLoading) {
            refreshData();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanSavedReceiver != null) {
            unregisterReceiver(scanSavedReceiver);
        }
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void onItemClick(ScanResult result) {
        // Handle item click - view detailed report
        Intent intent = new Intent(this, DiagnosisActivity.class);
        intent.putExtra("scanResult", result);
        startActivity(intent);
    }
} 