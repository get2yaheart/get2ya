package com.get2ya.dispatch.model;
import com.get2ya.common.model.Location;
import java.util.List;
import java.time.Instant;
import java.util.ArrayList;

public class Driver {
    /* 1. BASIC ATTRIBUTES */
    public enum Status {
        AVAILABLE, ON_TRIP, OFFLINE
    }
    
    private final String id;
    private volatile Location currentLocation;
    private volatile Status status;
    private volatile Instant lastUpdate;
    private double rating;
    /* 2. MORE ATTRIBUTES */
    private final String vehicleType;
    private int capacity;
    private double earnings;
    private int onlineMinutes;
    private String currentTrip;
    private List<Location> preferredAreas;
    
    private volatile double heading = 0.0;  // Direction in degrees (0-360)
    private volatile double speed = 0.0;    // Current speed in km/h
    private Location previousLocation;      // For calculating heading/speed
    private Instant previousLocationTime;   // For calculating speed

    /* 3. METHODS */
    public Driver(String id, Location location, String vehicleType) {
        this.id = id;
        this.currentLocation = location;
        this.vehicleType = vehicleType;
        this.status = Status.AVAILABLE; // Set default status
        this.rating = 5.0;
        this.preferredAreas = new ArrayList<>();
        this.lastUpdate = Instant.now();
    }
    public String getId() { return id; }
    public Location getCurrentLocation() { return currentLocation; }
    public Status getStatus() { return status; }
    public Instant getLastUpdate() { return lastUpdate; }
    public double getRating() { return rating; }
    public String getVehicleType() { return vehicleType; }
    public int getCapacity() { return capacity; }
    public double getEarnings() { return earnings; }
    public int getOnlineMinutes() { return onlineMinutes; }
    public String getCurrentTrip() { return currentTrip; }
    public List<Location> getPreferredAreas() { return preferredAreas; }

    public void setStatus(Status newStatus) { this.status = newStatus; }

    // NEW METHODS - Industry-standard for ride-hailing apps
    
    /**
     * Get current heading/direction (0-360 degrees, where 0 = North)
     * Used for predictive driver positioning and ETA calculations
     */
    public double getHeading() { 
        return heading; 
    }
    
    /**
     * Get current speed in km/h
     * Used for ETA calculations and traffic analysis
     */
    public double getSpeed() { 
        return speed; 
    }
    
    /**
     * Get service type (UberX, UberXL, UberBlack, etc.)
     * Maps vehicle type to service offerings
     */
    public String getServiceType() {
        // Map vehicle types to service types (Uber/Lyft style)
        switch (vehicleType.toUpperCase()) {
            case "SEDAN":
            case "COMPACT":
                return "UberX";
            case "SUV":
            case "MINIVAN":
                return "UberXL";
            case "LUXURY":
            case "BMW":
            case "MERCEDES":
                return "UberBlack";
            case "TESLA":
                return "UberGreen";
            default:
                return "UberX"; // Default service
        }
    }
    
    /**
     * Update location with automatic heading and speed calculation
     * This is called every 5-30 seconds by the driver app
     */
    public void updateLocation(Location newLocation) {
        if (currentLocation != null && lastUpdate != null) {
            // Store previous location for calculations
            this.previousLocation = currentLocation;
            this.previousLocationTime = lastUpdate;
            
            // Calculate heading (direction of travel)
            this.heading = calculateHeading(currentLocation, newLocation);
            
            // Calculate speed
            this.speed = calculateSpeed(currentLocation, newLocation, 
                                      lastUpdate, Instant.now());
        }
        
        this.currentLocation = newLocation;
        this.lastUpdate = Instant.now();
    }
    
    /**
     * Calculate heading between two points (0-360 degrees)
     * 0 = North, 90 = East, 180 = South, 270 = West
     */
    private double calculateHeading(Location from, Location to) {
        double lat1 = Math.toRadians(from.getLat());
        double lat2 = Math.toRadians(to.getLat());
        double deltaLon = Math.toRadians(to.getLon() - from.getLon());
        
        double x = Math.sin(deltaLon) * Math.cos(lat2);
        double y = Math.cos(lat1) * Math.sin(lat2) - 
                   Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon);
        
        double bearing = Math.toDegrees(Math.atan2(x, y));
        return (bearing + 360) % 360; // Normalize to 0-360
    }
    
    /**
     * Calculate speed between two points in km/h
     */
    private double calculateSpeed(Location from, Location to, 
                                 Instant fromTime, Instant toTime) {
        double distanceMeters = from.distanceMetersTo(to);
        long timeDiffSeconds = java.time.Duration.between(fromTime, toTime).getSeconds();
        
        if (timeDiffSeconds <= 0) return 0.0;
        
        double speedMs = distanceMeters / timeDiffSeconds; // m/s
        return speedMs * 3.6; // Convert to km/h
    }
    
    // Add setters for new fields if needed
    public void setHeading(double heading) {
        this.heading = (heading + 360) % 360; // Normalize to 0-360
    }
    
    public void setSpeed(double speed) {
        this.speed = Math.max(0.0, speed); // Speed can't be negative
    }
}
