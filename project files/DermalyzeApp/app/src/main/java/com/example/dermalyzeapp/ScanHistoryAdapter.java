package com.example.dermalyzeapp;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.dermalyzeapp.databinding.ItemScanHistoryBinding;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ScanHistoryAdapter extends RecyclerView.Adapter<ScanHistoryAdapter.ViewHolder> {
    private final List<ScanResult> scanResults;
    private final OnItemClickListener listener;
    private final SimpleDateFormat dateFormat;

    public interface OnItemClickListener {
        void onItemClick(ScanResult result);
    }

    public ScanHistoryAdapter(List<ScanResult> scanResults, OnItemClickListener listener) {
        this.scanResults = scanResults;
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemScanHistoryBinding binding = ItemScanHistoryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScanResult result = scanResults.get(position);
        holder.bind(result, listener);
    }

    @Override
    public int getItemCount() {
        return scanResults.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemScanHistoryBinding binding;

        ViewHolder(ItemScanHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ScanResult result, OnItemClickListener listener) {
            binding.conditionTextView.setText(result.getCondition());
            binding.confidenceTextView.setText(
                    String.format(Locale.US, "Confidence: %.1f%%", result.getConfidence() * 100));
            binding.dateTextView.setText(new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    .format(result.getTimestamp().toDate()));

            // Load image using Glide
            String imagePath = result.getImageUrl();
            if (imagePath != null) {
                try {
                    File imageFile = new File(imagePath);
                    if (imageFile.exists()) {
                        Glide.with(binding.scanImageView.getContext())
                                .load(imageFile)
                                .centerCrop()
                                .error(R.drawable.ic_error_image) // Add this drawable to your resources
                                .into(binding.scanImageView);
                    } else {
                        Log.e("ScanHistoryAdapter", "Image file does not exist: " + imagePath);
                        binding.scanImageView.setImageResource(R.drawable.ic_error_image);
                    }
                } catch (Exception e) {
                    Log.e("ScanHistoryAdapter", "Error loading image: " + imagePath, e);
                    binding.scanImageView.setImageResource(R.drawable.ic_error_image);
                }
            } else {
                binding.scanImageView.setImageResource(R.drawable.ic_error_image);
            }

            // Set click listeners
            binding.getRoot().setOnClickListener(v -> listener.onItemClick(result));
            binding.viewReportButton.setOnClickListener(v -> listener.onItemClick(result));
        }
    }
} 