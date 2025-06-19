package com.example.dermalyzeapp;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.firebase.Timestamp;

public class ScanResult implements Parcelable {
    private String id;
    private String imageUrl;
    private String condition;
    private double confidence;
    private Timestamp timestamp;
    private String recommendations;

    public ScanResult() {
        // Required empty constructor for Firestore
    }

    public ScanResult(String id, String imageUrl, String condition, double confidence, Timestamp timestamp, String recommendations) {
        this.id = id;
        this.imageUrl = imageUrl;
        this.condition = condition;
        this.confidence = confidence;
        this.timestamp = timestamp;
        this.recommendations = recommendations;
    }

    protected ScanResult(Parcel in) {
        id = in.readString();
        imageUrl = in.readString();
        condition = in.readString();
        confidence = in.readDouble();
        timestamp = new Timestamp(in.readLong(), 0);
        recommendations = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(imageUrl);
        dest.writeString(condition);
        dest.writeDouble(confidence);
        dest.writeLong(timestamp.getSeconds());
        dest.writeString(recommendations);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ScanResult> CREATOR = new Creator<ScanResult>() {
        @Override
        public ScanResult createFromParcel(Parcel in) {
            return new ScanResult(in);
        }

        @Override
        public ScanResult[] newArray(int size) {
            return new ScanResult[size];
        }
    };

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public String getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(String recommendations) {
        this.recommendations = recommendations;
    }
} 