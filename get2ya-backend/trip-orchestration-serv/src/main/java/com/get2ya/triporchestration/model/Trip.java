package com.get2ya.triporchestration.model;
import com.get2ya.common.model.Location;
import java.time.Instant;

public class Trip {
        private final String id;
        private final String riderId;
        private String driverId;
        private final Location startLocation;
        private Location endLocation;
        private final Instant startTime;
        private Instant endTime;
        private double estimatedDuration;
        private double estimatedDistance;
        private double fare;
        private double surgeMultiplier = 1.0;
        private String status = "REQUESTED";
        private String routePolyline;
        
        public Trip(String id, String riderId, Location startLocation, Location endLocation) {
            this.id = id;
            this.riderId = riderId;
            this.driverId = "UNASSIGNED";
            this.startLocation = startLocation;
            this.endLocation = endLocation;
            this.startTime = Instant.now();
        }
        public String getId() { return id; }
        public String getRiderId() { return riderId; }
        public String getDriverId() { return driverId; }
        public Location getStartLocation() { return startLocation; }
        public Location getEndLocation() { return endLocation; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public double getEstimatedDuration() { return estimatedDuration; }
        public double getEstimatedDistance() { return estimatedDistance; }
        public double getFare() { return fare; }
        public double getSurgeMultiplier() { return surgeMultiplier; }
        public String getStatus() { return status; }
        public String getRoutePolyline() { return routePolyline; }
    }