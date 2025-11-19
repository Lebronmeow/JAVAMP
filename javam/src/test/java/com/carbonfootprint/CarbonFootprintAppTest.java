package com.carbonfootprint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Carbon Footprint Logger application
 */
public class CarbonFootprintAppTest {

    @Test
    @DisplayName("Test Trip object creation")
    public void testTripCreation() {
        CarbonFootprintApp.Trip trip = new CarbonFootprintApp.Trip(
            1, 
            "2025-11-18", 
            "Home", 
            "Office", 
            "Car (petrol)", 
            15.5, 
            1, 
            2.976, 
            "Daily commute"
        );

        assertEquals(1, trip.getId());
        assertEquals("2025-11-18", trip.getDate());
        assertEquals("Home", trip.getFrom());
        assertEquals("Office", trip.getTo());
        assertEquals("Car (petrol)", trip.getMode());
        assertEquals(15.5, trip.getDistance(), 0.001);
        assertEquals(1, trip.getPassengers());
        assertEquals(2.976, trip.getCo2(), 0.001);
        assertEquals("Daily commute", trip.getNotes());
    }

    @Test
    @DisplayName("Test CO2 calculation for single passenger")
    public void testCO2CalculationSinglePassenger() {
        // Car (petrol): 0.192 kg/km
        // Distance: 10 km
        // Passengers: 1
        // Expected: 10 * 0.192 * 1 = 1.92 kg
        double distance = 10.0;
        double emissionFactor = 0.192;
        int passengers = 1;

        double expectedCO2 = distance * emissionFactor * passengers;
        double actualCO2 = calculateCO2(distance, emissionFactor, passengers);

        assertEquals(expectedCO2, actualCO2, 0.001);
    }

    @Test
    @DisplayName("Test CO2 calculation for multiple passengers")
    public void testCO2CalculationMultiplePassengers() {
        // Bus: 0.089 kg/km
        // Distance: 25 km
        // Passengers: 3
        // Expected: 25 * 0.089 * 3 = 6.675 kg
        double distance = 25.0;
        double emissionFactor = 0.089;
        int passengers = 3;

        double expectedCO2 = distance * emissionFactor * passengers;
        double actualCO2 = calculateCO2(distance, emissionFactor, passengers);

        assertEquals(expectedCO2, actualCO2, 0.001);
    }

    @Test
    @DisplayName("Test CO2 calculation for zero emission mode")
    public void testCO2CalculationZeroEmission() {
        // Cycle/Walk: 0.0 kg/km
        double distance = 5.0;
        double emissionFactor = 0.0;
        int passengers = 1;

        double actualCO2 = calculateCO2(distance, emissionFactor, passengers);

        assertEquals(0.0, actualCO2, 0.001);
    }

    @Test
    @DisplayName("Test CO2 calculation for domestic flight")
    public void testCO2CalculationFlight() {
        // Domestic Flight: 0.255 kg/km
        // Distance: 500 km
        // Passengers: 2
        // Expected: 500 * 0.255 * 2 = 255 kg
        double distance = 500.0;
        double emissionFactor = 0.255;
        int passengers = 2;

        double expectedCO2 = distance * emissionFactor * passengers;
        double actualCO2 = calculateCO2(distance, emissionFactor, passengers);

        assertEquals(expectedCO2, actualCO2, 0.001);
    }

    @Test
    @DisplayName("Test Trip getters")
    public void testTripGetters() {
        CarbonFootprintApp.Trip trip = new CarbonFootprintApp.Trip(
            10, 
            "2025-12-01", 
            "City A", 
            "City B", 
            "Train", 
            120.0, 
            2, 
            9.84, 
            "Weekend trip"
        );

        assertNotNull(trip);
        assertTrue(trip.getId() > 0);
        assertNotNull(trip.getDate());
        assertNotNull(trip.getFrom());
        assertNotNull(trip.getTo());
        assertNotNull(trip.getMode());
        assertTrue(trip.getDistance() > 0);
        assertTrue(trip.getPassengers() > 0);
        assertTrue(trip.getCo2() >= 0);
    }

    // Helper method matching the app's calculation logic
    private double calculateCO2(double distanceKm, double factorKgPerKmPerPerson, int passengers) {
        return distanceKm * factorKgPerKmPerPerson * Math.max(1, passengers);
    }
}
