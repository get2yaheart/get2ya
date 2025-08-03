package com.get2ya.common.model;

import java.util.Objects;

public class Location {
    /* 1. BASICS */
    private final double lat;
    private final double lon;

    public Location(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public double getLat() { return lat; }
    public double getLon() { return lon; }

    /* 2. FALL BACK: CROW DISTANCE - Haversine formula */
    public double distanceMetersTo(Location other) {
        final int R = 6371000; // metres
        double φ1 = Math.toRadians(lat);
        double φ2 = Math.toRadians(other.lat);
        double Δφ = Math.toRadians(other.lat - lat);
        double Δλ = Math.toRadians(other.lon - lon);
        double a = Math.sin(Δφ/2) * Math.sin(Δφ/2) +
                   Math.cos(φ1) * Math.cos(φ2) *
                   Math.sin(Δλ/2) * Math.sin(Δλ/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    /**
     * FALL BACK: MANHATTAN DISTANCE
     * PRODUCTION: Road distance with traffic (Uber/Lyft style)
     * This would integrate with Google Maps/Mapbox APIs
     */
    public double roadDistanceMetersTo(Location other) {
        // TODO: Integrate with routing service
        // For now, use Manhattan distance approximation (better than crow-flies in cities)
        return manhattanDistanceMetersTo(other);
    }

    /**
     * Manhattan distance - better city approximation than Haversine
     */
    private double manhattanDistanceMetersTo(Location other) {
        double latDiff = Math.abs(other.lat - this.lat);
        double lonDiff = Math.abs(other.lon - this.lon);
        
        // Convert to meters (rough approximation)
        double latMeters = latDiff * 111000; // 1 degree lat ≈ 111km
        double lonMeters = lonDiff * 111000 * Math.cos(Math.toRadians(this.lat));
        
        // Fix: Use proper distance formula, not simple addition
        return Math.sqrt(latMeters * latMeters + lonMeters * lonMeters);
    }

    /* 3a. OVERRIDE equals METHOD */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location)) return false;
        Location loc = (Location) o;
        return Double.compare(loc.lat, lat) == 0 &&
               Double.compare(loc.lon, lon) == 0;
    }

    /* 3b. OVERRIDE hashCoe METHOD for 3a */
    @Override
    public int hashCode() { return Objects.hash(lat, lon); }

    /* 3c. misc */
    @Override
    public String toString() {
        return String.format("(%.6f, %.6f)", lat, lon);
    }
}

