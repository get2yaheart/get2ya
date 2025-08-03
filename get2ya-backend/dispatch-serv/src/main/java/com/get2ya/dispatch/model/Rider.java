package com.get2ya.dispatch.model;

import com.get2ya.common.model.Location;
import java.util.List;
import java.util.ArrayList;

public class Rider {
    private final String id;
    private Location location;
    private double rating;
    private List<String> paymentMethods;
    private List<String> tripHistory;
    private boolean isPriority;

    public Rider(String id, Location location) {
        this.id = id;
        this.location = location;
        this.rating = 5.0;
        this.paymentMethods = new ArrayList<>();
        this.tripHistory = new ArrayList<>();
    }

    public String getId() { return id; }
    public Location getCurrentLocation() { return location; }
    public double getRating() { return rating; }
    public List<String> getPaymentMethods() { return paymentMethods; }
    public List<String> getTripHistory() { return tripHistory; }
    public boolean isPriority() { return isPriority; }
}