-- Carbon Footprint Logger Database Schema
-- This file contains the SQL schema for the application
-- The application will auto-create these, but this file serves as documentation

-- Create database
CREATE DATABASE IF NOT EXISTS carbon_logger;

USE carbon_logger;

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create transport_modes table
CREATE TABLE IF NOT EXISTS transport_modes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    mode_name VARCHAR(128) NOT NULL,
    emission_factor DOUBLE NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed transport modes
INSERT INTO transport_modes (mode_name, emission_factor) VALUES
('Car (petrol)', 0.192),
('Car (diesel)', 0.171),
('Bus', 0.089),
('Train', 0.041),
('Domestic Flight', 0.255),
('Motorbike', 0.103),
('Cycle/Walk', 0.0)
ON DUPLICATE KEY UPDATE emission_factor=VALUES(emission_factor);

-- Create trips table
CREATE TABLE IF NOT EXISTS trips (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    trip_date DATE NOT NULL,
    from_location VARCHAR(255),
    to_location VARCHAR(255),
    mode_id INT NOT NULL,
    distance_km DOUBLE NOT NULL,
    passengers INT NOT NULL,
    co2_kg DOUBLE NOT NULL,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_trip_date (trip_date),
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at),
    CONSTRAINT fk_trips_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_trips_mode FOREIGN KEY (mode_id) REFERENCES transport_modes(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create fuel_usage table
CREATE TABLE IF NOT EXISTS fuel_usage (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    date DATE NOT NULL,
    fuel_type VARCHAR(50) NOT NULL,
    liters DOUBLE NOT NULL,
    co2_kg DOUBLE NOT NULL,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_date (date),
    CONSTRAINT fk_fuel_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create activity_logs table
CREATE TABLE IF NOT EXISTS activity_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    category VARCHAR(100) NOT NULL,
    activity_name VARCHAR(255) NOT NULL,
    value DOUBLE NOT NULL,
    unit VARCHAR(50) NOT NULL,
    co2_kg DOUBLE NOT NULL,
    date DATE NOT NULL,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_category (category),
    INDEX idx_date (date),
    CONSTRAINT fk_act_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create carbon_goals table
CREATE TABLE IF NOT EXISTS carbon_goals (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    goal_name VARCHAR(100) NOT NULL,
    target_co2 DOUBLE NOT NULL,
    period VARCHAR(16) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_period (period),
    CONSTRAINT fk_goal_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Sample data (optional)
-- INSERT INTO trips (trip_date, from_location, to_location, mode, distance_km, passengers, co2_kg, notes) 
-- VALUES 
-- ('2025-11-01', 'Home', 'Office', 'Car (petrol)', 15.5, 1, 2.976, 'Daily commute'),
-- ('2025-11-02', 'Office', 'Shopping Mall', 'Bus', 8.0, 1, 0.712, 'Shopping trip'),
-- ('2025-11-03', 'Home', 'Park', 'Cycle/Walk', 3.0, 1, 0.0, 'Morning exercise');
