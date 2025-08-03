package com.get2ya.dispatch.service;

import com.uber.h3core.H3Core;
import com.uber.h3core.LengthUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.get2ya.dispatch.model.Driver;
import com.get2ya.common.model.Location;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Service
public class H3DriverPool {
    // Multi-resolution indexing (Uber's approach)
    private static final int FINE_RES = 11;  // ~25m precision
    private static final int COARSE_RES = 9; // ~350m precision
    
    private final H3Core h3;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Multi-level spatial index
    private final Map<String, Set<DriverNode>> fineIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<DriverNode>> coarseIndex = new ConcurrentHashMap<>();
    
    // Driver state tracking
    private final Map<String, DriverNode> activeDrivers = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastUpdateTimes = new ConcurrentHashMap<>();
    
    // Performance monitoring
    private final Map<String, Integer> hexagonLoad = new ConcurrentHashMap<>();

    public H3DriverPool() throws IOException {
        this.h3 = H3Core.newInstance();
    }

    // Enriched driver node (Uber-style)
    private static class DriverNode {
        final Driver driver;
        final Location location;
        final Instant lastUpdate;
        final String fineH3;
        final String coarseH3;
        final double heading;  // Direction in degrees
        final double speed;    // km/h
        final Set<String> serviceTypes;

        DriverNode(Driver driver, String fineH3, String coarseH3) {
            this.driver = driver;
            this.location = driver.getCurrentLocation();
            this.lastUpdate = Instant.now();
            this.fineH3 = fineH3;
            this.coarseH3 = coarseH3;
            this.heading = driver.getHeading();
            this.speed = driver.getSpeed();
            this.serviceTypes = Set.of(driver.getServiceType());
        }
    }

    /**
     * Add/update driver (call every 5-30s)
     */
    public void updateDriver(Driver driver) {
        lock.writeLock().lock();
        try {
            // Remove old position if exists
            removeDriverFromIndices(driver.getId());
            
            Location loc = driver.getCurrentLocation();
            String fineH3 = h3.latLngToCellAddress(loc.getLat(), loc.getLon(), FINE_RES);
            String coarseH3 = h3.latLngToCellAddress(loc.getLat(), loc.getLon(), COARSE_RES);
            
            // Create enriched node
            DriverNode node = new DriverNode(driver, fineH3, coarseH3);
            
            // Update indices
            fineIndex.computeIfAbsent(fineH3, k -> ConcurrentHashMap.newKeySet()).add(node);
            coarseIndex.computeIfAbsent(coarseH3, k -> ConcurrentHashMap.newKeySet()).add(node);
            activeDrivers.put(driver.getId(), node);
            lastUpdateTimes.put(driver.getId(), Instant.now());
            
            // Update load metrics
            hexagonLoad.merge(coarseH3, 1, Integer::sum);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Find nearby drivers with Uber-like ranking
     * 
     * @param serviceType UberX, UberBlack, etc (null for any)
     */
    public List<Driver> findNearbyDrivers(Location riderLocation, double radiusKm, 
                                         int maxResults, String serviceType) {
        lock.readLock().lock();
        try {
            String centerH3 = h3.latLngToCellAddress(
                riderLocation.getLat(), 
                riderLocation.getLon(), 
                COARSE_RES
            );
            
            // Calculate search area
            int ringSize = calculateRingSize(radiusKm);
            Set<String> searchArea = new HashSet<>(h3.gridDisk(centerH3, ringSize));
            
            // Collect candidates
            List<DriverNode> candidates = searchArea.stream()
                .flatMap(h3Index -> coarseIndex.getOrDefault(h3Index, Collections.emptySet()).stream())
                .filter(node -> isDriverEligible(node, serviceType))
                .filter(node -> isWithinRadius(node.location, riderLocation, radiusKm))
                .collect(Collectors.toList());
            
            // Apply Uber's ranking algorithm
            return candidates.stream()
                .sorted((a, b) -> compareDrivers(a, b, riderLocation))
                .map(node -> node.driver)
                .limit(maxResults)
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Remove driver immediately
     */
    public void removeDriver(String driverId) {
        lock.writeLock().lock();
        try {
            removeDriverFromIndices(driverId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    //----- Internal Methods -----//
    private void removeDriverFromIndices(String driverId) {
        DriverNode node = activeDrivers.remove(driverId);
        if (node != null) {
            fineIndex.getOrDefault(node.fineH3, Collections.emptySet()).remove(node);
            coarseIndex.getOrDefault(node.coarseH3, Collections.emptySet()).remove(node);
            hexagonLoad.merge(node.coarseH3, -1, Integer::sum);
            lastUpdateTimes.remove(driverId);
        }
    }

    @Scheduled(fixedRate = 60_000)  // Clean every minute
    private void cleanupInactiveDrivers() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(5));
        lock.writeLock().lock();
        try {
            lastUpdateTimes.entrySet().stream()
                .filter(e -> e.getValue().isBefore(cutoff))
                .map(Map.Entry::getKey)
                .forEach(this::removeDriverFromIndices);
        } finally {
            lock.writeLock().unlock();
        }
    }

    //----- Helper Methods -----//
    private int calculateRingSize(double radiusKm) {
        double hexEdgeKm = h3.getHexagonEdgeLengthAvg(COARSE_RES, LengthUnit.km);
        return Math.max(1, (int) Math.ceil(radiusKm / hexEdgeKm));
    }

    private int compareDrivers(DriverNode a, DriverNode b, Location riderLoc) {
        // 1. Status priority (AVAILABLE first)
        int statusCompare = Boolean.compare(
            b.driver.getStatus() == Driver.Status.AVAILABLE,
            a.driver.getStatus() == Driver.Status.AVAILABLE
        );
        if (statusCompare != 0) return statusCompare;
        
        // 2. Significant rating difference (>0.3)
        double ratingDiff = b.driver.getRating() - a.driver.getRating();
        if (Math.abs(ratingDiff) > 0.3) {
            return ratingDiff > 0 ? 1 : -1;
        }
        
        // 3. Estimated pickup time (with traffic)
        return Double.compare(
            estimatePickupTime(a, riderLoc),
            estimatePickupTime(b, riderLoc)
        );
    }
    
    private double estimatePickupTime(DriverNode node, Location riderLoc) {
        double distKm = node.location.roadDistanceMetersTo(riderLoc) / 1000.0;
        double trafficFactor = 1.0 + (hexagonLoad.getOrDefault(node.coarseH3, 0) * 0.05);
        double speed = Math.max(node.speed, 20.0);  // Min 20km/h in traffic
        return (distKm / speed) * trafficFactor * 60;  // Minutes
    }
    
    private boolean isDriverEligible(DriverNode node, String serviceType) {
        return node.driver.getStatus() == Driver.Status.AVAILABLE &&
               (serviceType == null || node.serviceTypes.contains(serviceType)) &&
               Duration.between(node.lastUpdate, Instant.now()).toMinutes() < 2;
    }
    
    private boolean isWithinRadius(Location a, Location b, double radiusKm) {
        return a.distanceMetersTo(b) <= radiusKm * 1000;
    }
    
    //----- Monitoring -----//
    public Map<String, Object> getPerformanceStats() {
        return Map.of(
            "activeDrivers", activeDrivers.size(),
            "coarseHexagons", coarseIndex.size(),
            "avgLoad", hexagonLoad.values().stream()
                       .mapToInt(v -> v).average().orElse(0.0)
        );
    }
}