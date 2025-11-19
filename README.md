# Carbon Footprint Logger

A comprehensive JavaFX desktop application for tracking and analyzing carbon footprint across multiple categories with user authentication.

## Features

### User Management
- **User Authentication**: Secure login and registration system with BCrypt password hashing
- **Multi-user Support**: Each user has their own isolated data

### Trip Logging
- **Trip Tracking**: Record travel details including date, origin, destination, mode of transport, distance, and passengers
- **CO₂ Calculation**: Automatic calculation of carbon emissions based on transport mode and distance
- **Data Visualization**: 
  - Pie chart showing mode share distribution
  - Bar chart displaying CO₂ emissions by month

### Fuel Usage Tracking
- **Fuel Records**: Log fuel consumption by type (Petrol, Diesel, CNG, LPG, Aviation Fuel)
- **Emission Tracking**: Calculate CO₂ emissions from fuel consumption
- **Visual Analytics**: Charts showing fuel usage patterns and emissions

### Activity Logs
- **Lifestyle Tracking**: Record carbon footprint from daily activities (electricity, cooking, waste, etc.)
- **Custom Categories**: Flexible categorization system for different activity types
- **Unit Flexibility**: Support for various units (kWh, kg, liters, etc.)

### Carbon Goals
- **Goal Setting**: Set personal carbon reduction targets
- **Period Tracking**: Daily, weekly, monthly, or yearly goal periods
- **Progress Monitoring**: Track your progress against set goals

### General Features
- **Database Storage**: Persistent storage using MySQL database with proper foreign key relationships
- **Interactive UI**: User-friendly multi-page interface with navigation
- **Real-time Updates**: Automatic chart and table updates after data changes

## Transport Modes & Emission Factors

| Mode | CO₂ (kg per km per person) |
|------|---------------------------|
| Car (petrol) | 0.192 |
| Car (diesel) | 0.171 |
| Bus | 0.089 |
| Train | 0.041 |
| Domestic Flight | 0.255 |
| Motorbike | 0.103 |
| Cycle/Walk | 0.0 |

## Prerequisites

- **Java**: JDK 11 or higher
- **Maven**: 3.6 or higher
- **MySQL**: 5.7 or higher
- **MySQL Server**: Running on localhost:3306

## Database Setup

1. Start your MySQL server
2. Update database credentials in `CarbonFootprintApp.java` if needed:
   ```java
   private static final String DB_USER = "root";
   private static final String DB_PASS = "password";
   ```
3. The application will automatically create the database and table on first run

## Building the Project

```bash
# Clean and compile
mvn clean compile

# Run the application
mvn javafx:run

# Package as JAR
mvn clean package
```

## Running the Application

### Option 1: Using Maven
```bash
mvn javafx:run
```

### Option 2: Using the JAR file
```bash
java -jar target/carbon-footprint-logger-1.0-SNAPSHOT.jar
```

## Project Structure

```
javam/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── carbonfootprint/
│   │   │           └── CarbonFootprintApp.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/
│           └── com/
│               └── carbonfootprint/
│                   └── CarbonFootprintAppTest.java
├── pom.xml
└── README.md
```

## Usage

1. **Add a Trip**:
   - Select date from the date picker
   - Enter origin and destination
   - Choose transport mode
   - Enter distance in kilometers
   - Specify number of passengers
   - Optionally add notes
   - Click "Calculate CO₂" to preview emissions
   - Click "Save Trip" to store in database

2. **View Trips**:
   - All trips are displayed in the table view
   - Right-click on a trip to delete it

3. **Analyze Data**:
   - View mode share distribution in the pie chart
   - Monitor monthly CO₂ emissions in the bar chart
   - Click "Refresh" to update visualizations

## Configuration

Edit `src/main/resources/application.properties` to customize database settings:

```properties
db.url=jdbc:mysql://localhost:3306/carbon_logger?useSSL=false&serverTimezone=UTC
db.username=root
db.password=password
```

## Dependencies

- **JavaFX**: 17.0.2 (UI framework)
- **MySQL Connector/J**: 8.0.33 (Database connectivity)
- **jBCrypt**: 0.4 (Password hashing)
- **JUnit Jupiter**: 5.9.3 (Testing framework)

## Troubleshooting

### Database Connection Issues
- Ensure MySQL server is running
- Verify database credentials
- Check that port 3306 is accessible

### JavaFX Runtime Issues
- Ensure Java 11+ is installed
- Verify JAVA_HOME is set correctly

## License

This project is open source and available for educational purposes.

## Author

Carbon Footprint Logger - JavaFX Application

