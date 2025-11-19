package com.carbonfootprint;

// ========================= IMPORTS =========================
import com.carbonfootprint.CarbonFootprintApp.LoginScreen;
import com.carbonfootprint.CarbonFootprintApp.MainApp.ActivityLog;
import com.carbonfootprint.CarbonFootprintApp.MainApp.CarbonGoal;
import com.carbonfootprint.CarbonFootprintApp.MainApp.FuelUsage;
import com.carbonfootprint.CarbonFootprintApp.MainApp.Trip;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;



// ===========================================================
//                  FULL APPLICATION CLASS
// ===========================================================
public class CarbonFootprintApp extends Application {

    // ---- DB config ----
    private static final String DB_URL = "jdbc:mysql://localhost:3306/carbon_logger?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DB_USER = "root"; // your username
    private static final String DB_PASS = "Lebronryan1";


    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            DatabaseHelper.initSchema(); // create tables if needed
        } catch (SQLException e) {
            showFatal("Database initialization failed:\n" + e.getMessage());
            return;
        }

        LoginScreen login = new LoginScreen();
        login.start(primaryStage);
    }

    private void showFatal(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Fatal Error");
        a.setHeaderText("Application cannot start");
        a.setContentText(msg);
        a.showAndWait();
        System.exit(1);
    }

    // ======================================================
    // DATABASE HELPER (no shared connection)
    // ======================================================
    static class DatabaseHelper {

        public static Connection getConnection() throws SQLException {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                throw new SQLException("MySQL Driver not found", e);
            }
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        }

        public static void initSchema() throws SQLException {
            try (Connection conn = getConnection();
                 Statement st = conn.createStatement()) {

                // users
                st.execute("CREATE TABLE IF NOT EXISTS users (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY," +
                        "username VARCHAR(50) UNIQUE NOT NULL," +
                        "password_hash VARCHAR(255) NOT NULL," +
                        "email VARCHAR(255)," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")");

                // transport_modes
                st.execute("CREATE TABLE IF NOT EXISTS transport_modes (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY," +
                        "mode_name VARCHAR(128) NOT NULL," +
                        "emission_factor DOUBLE NOT NULL" +
                        ")");

                // seed modes if empty
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM transport_modes")) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        st.execute("INSERT INTO transport_modes (mode_name, emission_factor) VALUES " +
                                "('Car (petrol)', 0.192)," +
                                "('Car (diesel)', 0.171)," +
                                "('Bus', 0.089)," +
                                "('Train', 0.041)," +
                                "('Domestic Flight', 0.255)," +
                                "('Motorbike', 0.103)," +
                                "('Cycle/Walk', 0.0)");
                    }
                }

                // Check if old trips table exists and migrate
                try (ResultSet rs = conn.getMetaData().getTables(null, null, "trips", null)) {
                    if (rs.next()) {
                        // Check if user_id column exists
                        try (ResultSet cols = conn.getMetaData().getColumns(null, null, "trips", "user_id")) {
                            if (!cols.next()) {
                                // Old schema - drop and recreate
                                st.execute("DROP TABLE IF EXISTS trips");
                            }
                        }
                    }
                }

                // trips
                st.execute("CREATE TABLE IF NOT EXISTS trips (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY," +
                        "user_id INT NOT NULL," +
                        "trip_date DATE NOT NULL," +
                        "from_location VARCHAR(255)," +
                        "to_location VARCHAR(255)," +
                        "mode_id INT NOT NULL," +
                        "distance_km DOUBLE NOT NULL," +
                        "passengers INT NOT NULL," +
                        "co2_kg DOUBLE NOT NULL," +
                        "notes TEXT," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        "CONSTRAINT fk_trips_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE," +
                        "CONSTRAINT fk_trips_mode FOREIGN KEY (mode_id) REFERENCES transport_modes(id) ON DELETE RESTRICT" +
                        ")");

                // fuel_usage
                st.execute("CREATE TABLE IF NOT EXISTS fuel_usage (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY," +
                        "user_id INT NOT NULL," +
                        "date DATE NOT NULL," +
                        "fuel_type VARCHAR(50) NOT NULL," +
                        "liters DOUBLE NOT NULL," +
                        "co2_kg DOUBLE NOT NULL," +
                        "notes TEXT," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        "CONSTRAINT fk_fuel_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")");

                // activity_logs
                st.execute("CREATE TABLE IF NOT EXISTS activity_logs (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY," +
                        "user_id INT NOT NULL," +
                        "category VARCHAR(100) NOT NULL," +
                        "activity_name VARCHAR(255) NOT NULL," +
                        "value DOUBLE NOT NULL," +
                        "unit VARCHAR(50) NOT NULL," +
                        "co2_kg DOUBLE NOT NULL," +
                        "date DATE NOT NULL," +
                        "notes TEXT," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        "CONSTRAINT fk_act_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")");

                // carbon_goals
                st.execute("CREATE TABLE IF NOT EXISTS carbon_goals (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY," +
                        "user_id INT NOT NULL," +
                        "goal_name VARCHAR(100) NOT NULL," +
                        "target_co2 DOUBLE NOT NULL," +
                        "period VARCHAR(16) NOT NULL," + // daily/weekly/monthly/yearly
                        "start_date DATE NOT NULL," +
                        "end_date DATE NOT NULL," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        "CONSTRAINT fk_goal_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")");
            }
        }
    }

    // ======================================================
    // USER DAO
    // ======================================================
    static class UserDAO {

        public static boolean registerUser(String username, String password, String email) {
            String sql = "INSERT INTO users (username, password_hash, email) VALUES (?, ?, ?)";
            String hashed = BCrypt.hashpw(password, BCrypt.gensalt(12));

            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, username);
                ps.setString(2, hashed);
                ps.setString(3, email);
                ps.executeUpdate();
                return true;

            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        public static Integer authenticateUser(String username, String password) {
            String sql = "SELECT id, password_hash FROM users WHERE username = ?";

            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String stored = rs.getString("password_hash");
                        if (BCrypt.checkpw(password, stored)) {
                            return rs.getInt("id");
                        }
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        public static boolean usernameExists(String username) {
            String sql = "SELECT COUNT(*) FROM users WHERE username = ?";

            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }
    }

    // ======================================================
    // TRANSPORT MODE DAO
    // ======================================================
    static class TransportModeDAO {

        static class TransportMode {
            private final int id;
            private final String modeName;
            private final double emissionFactor;

            public TransportMode(int id, String modeName, double emissionFactor) {
                this.id = id;
                this.modeName = modeName;
                this.emissionFactor = emissionFactor;
            }

            public int getId() { return id; }
            public String getModeName() { return modeName; }
            public double getEmissionFactor() { return emissionFactor; }

            @Override
            public String toString() {
                return modeName;
            }
        }

        public static List<TransportMode> getAllModes() {
            List<TransportMode> list = new ArrayList<>();
            String sql = "SELECT id, mode_name, emission_factor FROM transport_modes ORDER BY id";

            try (Connection conn = DatabaseHelper.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {

                while (rs.next()) {
                    list.add(new TransportMode(
                            rs.getInt("id"),
                            rs.getString("mode_name"),
                            rs.getDouble("emission_factor")
                    ));
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }
    }

    // ======================================================
    // LOGIN SCREEN
    // ======================================================
    static class LoginScreen {

        private TextField usernameField;
        private PasswordField passwordField;
        private Stage stage;

        public void start(Stage stage) {
            this.stage = stage;
            stage.setTitle("Carbon Footprint Logger - Login");

            VBox root = createUI();
            Scene scene = new Scene(root, 420, 320);
            stage.setScene(scene);
            stage.show();
        }

        private VBox createUI() {
            VBox v = new VBox(12);
            v.setPadding(new Insets(24));
            v.setAlignment(Pos.CENTER);

            Label title = new Label("Carbon Footprint Logger");
            title.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:#2e7d32;");
            Label sub = new Label("Login to continue");
            sub.setStyle("-fx-text-fill:#666;");

            usernameField = new TextField();
            usernameField.setPromptText("Username");
            usernameField.setMaxWidth(280);

            passwordField = new PasswordField();
            passwordField.setPromptText("Password");
            passwordField.setMaxWidth(280);

            Button loginBtn = new Button("Login");
            loginBtn.setMaxWidth(280);
            loginBtn.setStyle("-fx-background-color:#4CAF50; -fx-text-fill:white;");
            loginBtn.setOnAction(e -> handleLogin());

            Button regBtn = new Button("Create Account");
            regBtn.setMaxWidth(280);
            regBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:#2196F3; -fx-border-color:#2196F3;");
            regBtn.setOnAction(e -> openRegister());

            v.getChildren().addAll(title, sub, usernameField, passwordField, loginBtn, regBtn);
            return v;
        }

        private void handleLogin() {
            String u = usernameField.getText().trim();
            String p = passwordField.getText();

            if (u.isEmpty() || p.isEmpty()) {
                alert(Alert.AlertType.WARNING, "Validation", "Enter username and password.");
                return;
            }

            Integer userId = UserDAO.authenticateUser(u, p);
            if (userId == null) {
                alert(Alert.AlertType.ERROR, "Login Failed", "Invalid username or password.");
            } else {
                openMainApp(userId, u);
            }
        }

        private void openRegister() {
            RegisterScreen reg = new RegisterScreen();
            Stage s = new Stage();
            reg.start(s);
            stage.close();
        }

        private void openMainApp(int userId, String username) {
            MainApp app = new MainApp(userId, username);
            Stage s = new Stage();
            app.start(s);
            stage.close();
        }

        private void alert(Alert.AlertType type, String title, String msg) {
            Alert a = new Alert(type);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(msg);
            a.showAndWait();
        }
    }

    // ======================================================
    // REGISTER SCREEN
    // ======================================================
    static class RegisterScreen {

        private TextField usernameField, emailField;
        private PasswordField passwordField, confirmField;
        private Stage stage;

        public void start(Stage stage) {
            this.stage = stage;
            stage.setTitle("Register - Carbon Footprint Logger");

            VBox root = createUI();
            Scene sc = new Scene(root, 420, 380);
            stage.setScene(sc);
            stage.show();
        }

        private VBox createUI() {
            VBox v = new VBox(12);
            v.setPadding(new Insets(24));
            v.setAlignment(Pos.CENTER);

            Label title = new Label("Create Account");
            title.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:#2e7d32;");

            usernameField = new TextField();
            usernameField.setPromptText("Username");
            usernameField.setMaxWidth(280);

            emailField = new TextField();
            emailField.setPromptText("Email (optional)");
            emailField.setMaxWidth(280);

            passwordField = new PasswordField();
            passwordField.setPromptText("Password (min 6 chars)");
            passwordField.setMaxWidth(280);

            confirmField = new PasswordField();
            confirmField.setPromptText("Confirm Password");
            confirmField.setMaxWidth(280);

            Button regBtn = new Button("Register");
            regBtn.setMaxWidth(280);
            regBtn.setStyle("-fx-background-color:#4CAF50; -fx-text-fill:white;");
            regBtn.setOnAction(e -> handleRegister());

            Button backBtn = new Button("Back to Login");
            backBtn.setMaxWidth(280);
            backBtn.setOnAction(e -> backToLogin());

            v.getChildren().addAll(title, usernameField, emailField,
                    passwordField, confirmField, regBtn, backBtn);
            return v;
        }

        private void handleRegister() {
            String u = usernameField.getText().trim();
            String e = emailField.getText().trim();
            String p1 = passwordField.getText();
            String p2 = confirmField.getText();

            if (u.isEmpty() || p1.isEmpty() || p2.isEmpty()) {
                alert(Alert.AlertType.WARNING, "Validation",
                        "Username and passwords are required.");
                return;
            }
            if (p1.length() < 6) {
                alert(Alert.AlertType.WARNING, "Validation",
                        "Password must be at least 6 characters.");
                return;
            }
            if (!p1.equals(p2)) {
                alert(Alert.AlertType.WARNING, "Validation",
                        "Passwords do not match.");
                confirmField.clear();
                return;
            }
            if (UserDAO.usernameExists(u)) {
                alert(Alert.AlertType.ERROR, "Error",
                        "Username already exists.");
                return;
            }

            boolean ok = UserDAO.registerUser(u, p1, e.isEmpty() ? null : e);
            if (ok) {
                alert(Alert.AlertType.INFORMATION, "Success",
                        "Account created. Please login.");
                backToLogin();
            } else {
                alert(Alert.AlertType.ERROR, "Error",
                        "Registration failed. Check console.");
            }
        }

        private void backToLogin() {
            LoginScreen login = new LoginScreen();
            Stage s = new Stage();
            login.start(s);
            stage.close();
        }

        private void alert(Alert.AlertType type, String title, String msg) {
            Alert a = new Alert(type);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(msg);
            a.showAndWait();
        }
    }

    // ======================================================
    // MAIN APP WITH MULTIPLE PAGES
    // ======================================================
    static class MainApp {

        private final int userId;
        private final String username;

        private BorderPane root;
        private VBox tripsPage;
        private VBox fuelPage;
        private VBox activityPage;
        private VBox goalsPage;

        // Trips page UI
        private DatePicker tripDatePicker;
        private TextField tripFromField, tripToField, tripDistanceField, tripPassengersField;
        private ComboBox<TransportModeDAO.TransportMode> tripModeCombo;
        private TextArea tripNotesArea;
        private Label tripCo2Label;
        private TableView<Trip> tripTable;
        private ObservableList<Trip> tripList = FXCollections.observableArrayList();
        private PieChart tripPieChart;
        private BarChart<String, Number> tripBarChart;

        // Fuel page UI
        private DatePicker fuelDatePicker;
        private ComboBox<String> fuelTypeCombo;
        private TextField fuelLitersField;
        private TextArea fuelNotesArea;
        private Label fuelCo2Label;
        private TableView<FuelUsage> fuelTable;
        private ObservableList<FuelUsage> fuelList = FXCollections.observableArrayList();
        private PieChart fuelPieChart;
        private BarChart<String, Number> fuelBarChart;

        // Activity logs UI
        private DatePicker actDatePicker;
        private TextField actCategoryField, actNameField, actValueField, actUnitField;
        private TextArea actNotesArea;
        private Label actCo2Label;
        private TableView<ActivityLog> actTable;
        private ObservableList<ActivityLog> actList = FXCollections.observableArrayList();
        private PieChart actPieChart;
        private BarChart<String, Number> actBarChart;

        // Goals UI
        private TextField goalNameField, goalTargetField;
        private ComboBox<String> goalPeriodCombo;
        private DatePicker goalStartPicker, goalEndPicker;
        private TableView<CarbonGoal> goalTable;
        private ObservableList<CarbonGoal> goalList = FXCollections.observableArrayList();
        private Label goalsSummaryLabel;

        public MainApp(int userId, String username) {
            this.userId = userId;
            this.username = username;
        }

        public void start(Stage stage) {
            root = new BorderPane();

            HBox topNav = createNavBar(stage);
            root.setTop(topNav);

            // default page: trips
            showTripsPage();

            Scene sc = new Scene(root, 1280, 780);
            stage.setScene(sc);
            stage.setTitle("Carbon Footprint Dashboard - " + username);
            stage.show();
        }

        private HBox createNavBar(Stage stage) {
            HBox nav = new HBox(10);
            nav.setPadding(new Insets(10));
            nav.setAlignment(Pos.CENTER_LEFT);
            nav.setStyle("-fx-background-color:#eeeeee;");

            Button tripsBtn = new Button("Trips");
            Button fuelBtn = new Button("Fuel Usage");
            Button actBtn = new Button("Activity Logs");
            Button goalsBtn = new Button("Carbon Goals");
            Button logoutBtn = new Button("Logout");

            for (Button b : Arrays.asList(tripsBtn, fuelBtn, actBtn, goalsBtn, logoutBtn)) {
                b.setStyle("-fx-background-radius:20; -fx-padding:6 14 6 14;");
            }

            tripsBtn.setOnAction(e -> showTripsPage());
            fuelBtn.setOnAction(e -> showFuelPage());
            actBtn.setOnAction(e -> showActivityPage());
            goalsBtn.setOnAction(e -> showGoalsPage());
            logoutBtn.setOnAction(e -> {
                LoginScreen login = new LoginScreen();
                Stage s = new Stage();
                login.start(s);
                stage.close();
            });

            Label title = new Label("Carbon Tracker");
            title.setStyle("-fx-font-size:18px; -fx-font-weight:bold;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            nav.getChildren().addAll(title, spacer, tripsBtn, fuelBtn, actBtn, goalsBtn, logoutBtn);
            return nav;
        }

        // -------------------- TRIPS PAGE --------------------
        private void showTripsPage() {
            if (tripsPage == null) {
                tripsPage = buildTripsPage();
            }
            loadTrips();
            updateTripCharts();
            root.setCenter(tripsPage);
        }

        private VBox buildTripsPage() {
            GridPane form = new GridPane();
            form.setPadding(new Insets(10));
            form.setVgap(8);
            form.setHgap(8);
            form.setStyle("-fx-background-color:#fafafa; -fx-border-color:#ddd;");

            Label title = new Label("Trip Logger");
            title.setStyle("-fx-font-size:18px; -fx-font-weight:bold;");
            form.add(title, 0, 0, 4, 1);

            form.add(new Label("Date:"), 0, 1);
            tripDatePicker = new DatePicker(LocalDate.now());
            form.add(tripDatePicker, 1, 1);

            form.add(new Label("From:"), 0, 2);
            tripFromField = new TextField();
            tripFromField.setPromptText("Origin");
            form.add(tripFromField, 1, 2);

            form.add(new Label("To:"), 2, 2);
            tripToField = new TextField();
            tripToField.setPromptText("Destination");
            form.add(tripToField, 3, 2);

            form.add(new Label("Mode:"), 0, 3);
            tripModeCombo = new ComboBox<>(FXCollections.observableArrayList(TransportModeDAO.getAllModes()));
            if (!tripModeCombo.getItems().isEmpty()) tripModeCombo.getSelectionModel().selectFirst();
            form.add(tripModeCombo, 1, 3);

            form.add(new Label("Distance (km):"), 2, 3);
            tripDistanceField = new TextField();
            form.add(tripDistanceField, 3, 3);

            form.add(new Label("Passengers:"), 0, 4);
            tripPassengersField = new TextField("1");
            form.add(tripPassengersField, 1, 4);

            form.add(new Label("Notes:"), 0, 5);
            tripNotesArea = new TextArea();
            tripNotesArea.setPrefRowCount(2);
            form.add(tripNotesArea, 1, 5, 3, 1);

            Button calcBtn = new Button("Calculate CO₂");
            Button saveBtn = new Button("Save Trip");
            Button refreshBtn = new Button("Refresh");
            HBox buttons = new HBox(8, calcBtn, saveBtn, refreshBtn);
            buttons.setAlignment(Pos.CENTER_LEFT);
            form.add(buttons, 1, 6);

            tripCo2Label = new Label("Estimated CO₂: - kg");
            tripCo2Label.setStyle("-fx-font-weight:bold;");
            form.add(tripCo2Label, 1, 7);

            calcBtn.setOnAction(e -> calculateTripCO2());
            saveBtn.setOnAction(e -> saveTrip());
            refreshBtn.setOnAction(e -> {
                loadTrips();
                updateTripCharts();
            });

            tripTable = new TableView<>();
            tripTable.setItems(tripList);
            tripTable.setPrefHeight(260);

            TableColumn<Trip, String> dateCol = new TableColumn<>("Date");
            dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));

            TableColumn<Trip, String> fromCol = new TableColumn<>("From");
            fromCol.setCellValueFactory(new PropertyValueFactory<>("from"));

            TableColumn<Trip, String> toCol = new TableColumn<>("To");
            toCol.setCellValueFactory(new PropertyValueFactory<>("to"));

            TableColumn<Trip, String> modeCol = new TableColumn<>("Mode");
            modeCol.setCellValueFactory(new PropertyValueFactory<>("mode"));

            TableColumn<Trip, Double> distCol = new TableColumn<>("Distance");
            distCol.setCellValueFactory(new PropertyValueFactory<>("distance"));

            TableColumn<Trip, Integer> paxCol = new TableColumn<>("Pax");
            paxCol.setCellValueFactory(new PropertyValueFactory<>("passengers"));

            TableColumn<Trip, Double> co2Col = new TableColumn<>("CO₂ (kg)");
            co2Col.setCellValueFactory(new PropertyValueFactory<>("co2"));

            tripTable.getColumns().addAll(dateCol, fromCol, toCol, modeCol, distCol, paxCol, co2Col);

            MenuItem deleteItem = new MenuItem("Delete selected trip");
            deleteItem.setOnAction(e -> {
                Trip t = tripTable.getSelectionModel().getSelectedItem();
                if (t != null) {
                    deleteTrip(t.getId());
                    loadTrips();
                    updateTripCharts();
                }
            });
            tripTable.setContextMenu(new ContextMenu(deleteItem));

            // charts
            tripPieChart = new PieChart();
            tripPieChart.setTitle("Trips by Mode");

            CategoryAxis xAxis = new CategoryAxis();
            NumberAxis yAxis = new NumberAxis();
            tripBarChart = new BarChart<>(xAxis, yAxis);
            xAxis.setLabel("Month");
            yAxis.setLabel("CO₂ (kg)");
            tripBarChart.setTitle("Trip CO₂ per Month");

            HBox charts = new HBox(10, tripPieChart, tripBarChart);
            charts.setPadding(new Insets(10));
            charts.setPrefHeight(300);

            VBox v = new VBox(10, form, tripTable, charts);
            v.setPadding(new Insets(10));
            return v;
        }

        private void calculateTripCO2() {
            String distText = tripDistanceField.getText().trim();
            String paxText = tripPassengersField.getText().trim();
            TransportModeDAO.TransportMode mode = tripModeCombo.getValue();

            if (distText.isEmpty() || paxText.isEmpty() || mode == null) {
                showAlert("Validation", "Enter distance, passengers and mode.");
                return;
            }

            try {
                double distance = Double.parseDouble(distText);
                int pax = Integer.parseInt(paxText);
                if (distance <= 0 || pax <= 0) throw new NumberFormatException();
                double co2 = distance * mode.getEmissionFactor() * pax;
                tripCo2Label.setText(String.format("Estimated CO₂: %.3f kg", co2));
            } catch (NumberFormatException ex) {
                showAlert("Validation", "Distance and passengers must be positive numbers.");
            }
        }

        private void saveTrip() {
            LocalDate date = tripDatePicker.getValue();
            String from = tripFromField.getText().trim();
            String to = tripToField.getText().trim();
            TransportModeDAO.TransportMode mode = tripModeCombo.getValue();
            String distText = tripDistanceField.getText().trim();
            String paxText = tripPassengersField.getText().trim();
            String notes = tripNotesArea.getText().trim();

            if (date == null || mode == null || distText.isEmpty() || paxText.isEmpty()) {
                showAlert("Validation", "Please fill date, mode, distance and passengers.");
                return;
            }

            double distance;
            int pax;
            try {
                distance = Double.parseDouble(distText);
                pax = Integer.parseInt(paxText);
                if (distance <= 0 || pax <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                showAlert("Validation", "Distance and passengers must be positive numbers.");
                return;
            }

            double factor = mode.getEmissionFactor();
            double co2 = distance * factor * pax;

            String sql = "INSERT INTO trips (user_id, trip_date, from_location, to_location, mode_id, distance_km, passengers, co2_kg, notes) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setDate(2, java.sql.Date.valueOf(date));
                ps.setString(3, from);
                ps.setString(4, to);
                ps.setInt(5, mode.getId());
                ps.setDouble(6, distance);
                ps.setInt(7, pax);
                ps.setDouble(8, co2);
                ps.setString(9, notes);
                ps.executeUpdate();
            } catch (SQLException e) {
                showAlert("Database error", "Failed to save trip: " + e.getMessage());
                return;
            }

            tripDatePicker.setValue(LocalDate.now());
            tripFromField.clear();
            tripToField.clear();
            tripDistanceField.clear();
            tripPassengersField.setText("1");
            tripNotesArea.clear();
            tripCo2Label.setText("Estimated CO₂: - kg");

            loadTrips();
            updateTripCharts();
            showAlert("Saved", String.format("Trip saved — CO₂: %.3f kg", co2));
        }

        private void loadTrips() {
            tripList.clear();
            String q = "SELECT t.id, t.trip_date, t.from_location, t.to_location, m.mode_name, t.distance_km, t.passengers, t.co2_kg, t.notes " +
                    "FROM trips t JOIN transport_modes m ON t.mode_id = m.id " +
                    "WHERE t.user_id = ? ORDER BY t.trip_date DESC, t.id DESC";

            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();

                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                while (rs.next()) {
                    int id = rs.getInt("id");
                    java.sql.Date d = rs.getDate("trip_date");
                    String dateStr = d != null ? d.toLocalDate().format(fmt) : "";
                    String from = rs.getString("from_location");
                    String to = rs.getString("to_location");
                    String mode = rs.getString("mode_name");
                    double distance = rs.getDouble("distance_km");
                    int pax = rs.getInt("passengers");
                    double co2 = rs.getDouble("co2_kg");
                    String notes = rs.getString("notes");
                    tripList.add(new Trip(id, dateStr, from, to, mode, distance, pax, co2, notes));
                }
            } catch (SQLException e) {
                showAlert("Database error", "Failed to load trips: " + e.getMessage());
            }
        }

        private void deleteTrip(int id) {
            String sql = "DELETE FROM trips WHERE id = ? AND user_id = ?";
            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.setInt(2, userId);
                ps.executeUpdate();
            } catch (SQLException e) {
                showAlert("Database error", "Failed to delete trip: " + e.getMessage());
            }
        }

        private void updateTripCharts() {
            // Pie: trips by mode
            Map<String, Integer> modeCounts = new LinkedHashMap<>();
            String q1 = "SELECT m.mode_name, COUNT(*) AS c FROM trips t " +
                    "JOIN transport_modes m ON t.mode_id = m.id " +
                    "WHERE t.user_id = ? GROUP BY m.mode_name";

            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(q1)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    modeCounts.put(rs.getString("mode_name"), rs.getInt("c"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
            for (Map.Entry<String, Integer> en : modeCounts.entrySet()) {
                if (en.getValue() > 0) {
                    pieData.add(new PieChart.Data(en.getKey(), en.getValue()));
                }
            }
            tripPieChart.setData(pieData);

            // Bar: CO2 by month
            Map<String, Double> co2ByMonth = new TreeMap<>();
            String q2 = "SELECT DATE_FORMAT(trip_date, '%Y-%m') AS ym, SUM(co2_kg) AS total " +
                    "FROM trips WHERE user_id = ? GROUP BY ym ORDER BY ym";

            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(q2)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    co2ByMonth.put(rs.getString("ym"), rs.getDouble("total"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            tripBarChart.getData().clear();
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Trip CO₂ (kg)");
            for (Map.Entry<String, Double> en : co2ByMonth.entrySet()) {
                series.getData().add(new XYChart.Data<>(en.getKey(), en.getValue()));
            }
            tripBarChart.getData().add(series);
        }

        // -------------------- FUEL PAGE --------------------
        private void showFuelPage() {
            if (fuelPage == null) {
                fuelPage = buildFuelPage();
            }
            loadFuelUsage();
            updateFuelCharts();
            root.setCenter(fuelPage);
        }

        private VBox buildFuelPage() {
            GridPane form = new GridPane();
            form.setPadding(new Insets(10));
            form.setVgap(8);
            form.setHgap(8);
            form.setStyle("-fx-background-color:#fafafa; -fx-border-color:#ddd;");

            Label title = new Label("Fuel Usage");
            title.setStyle("-fx-font-size:18px; -fx-font-weight:bold;");
            form.add(title, 0, 0, 4, 1);

            form.add(new Label("Date:"), 0, 1);
            fuelDatePicker = new DatePicker(LocalDate.now());
            form.add(fuelDatePicker, 1, 1);

            form.add(new Label("Fuel Type:"), 0, 2);
            fuelTypeCombo = new ComboBox<>();
            fuelTypeCombo.getItems().addAll("Petrol", "Diesel", "CNG", "LPG", "Aviation Fuel");
            fuelTypeCombo.getSelectionModel().selectFirst();
            form.add(fuelTypeCombo, 1, 2);

            form.add(new Label("Liters:"), 0, 3);
            fuelLitersField = new TextField();
            form.add(fuelLitersField, 1, 3);

            form.add(new Label("Notes:"), 0, 4);
            fuelNotesArea = new TextArea();
            fuelNotesArea.setPrefRowCount(2);
            form.add(fuelNotesArea, 1, 4, 3, 1);

            Button calcBtn = new Button("Estimate CO₂");
            Button saveBtn = new Button("Save Record");
            Button refreshBtn = new Button("Refresh");
            HBox buttons = new HBox(8, calcBtn, saveBtn, refreshBtn);
            buttons.setAlignment(Pos.CENTER_LEFT);
            form.add(buttons, 1, 5);

            fuelCo2Label = new Label("Estimated CO₂: - kg");
            fuelCo2Label.setStyle("-fx-font-weight:bold;");
            form.add(fuelCo2Label, 1, 6);

            calcBtn.setOnAction(e -> calculateFuelCO2());
            saveBtn.setOnAction(e -> saveFuelUsage());
            refreshBtn.setOnAction(e -> {
                loadFuelUsage();
                updateFuelCharts();
            });

            fuelTable = new TableView<>();
            fuelTable.setItems(fuelList);
            fuelTable.setPrefHeight(260);

            TableColumn<FuelUsage, String> dCol = new TableColumn<>("Date");
            dCol.setCellValueFactory(new PropertyValueFactory<>("date"));

            TableColumn<FuelUsage, String> fCol = new TableColumn<>("Fuel");
            fCol.setCellValueFactory(new PropertyValueFactory<>("fuelType"));

            TableColumn<FuelUsage, Double> lCol = new TableColumn<>("Liters");
            lCol.setCellValueFactory(new PropertyValueFactory<>("liters"));

            TableColumn<FuelUsage, Double> cCol = new TableColumn<>("CO₂ (kg)");
            cCol.setCellValueFactory(new PropertyValueFactory<>("co2"));

            fuelTable.getColumns().addAll(dCol, fCol, lCol, cCol);

            MenuItem del = new MenuItem("Delete selected");
            del.setOnAction(e -> {
                FuelUsage fu = fuelTable.getSelectionModel().getSelectedItem();
                if (fu != null) {
                    deleteFuelUsage(fu.getId());
                    loadFuelUsage();
                    updateFuelCharts();
                }
            });
            fuelTable.setContextMenu(new ContextMenu(del));

            fuelPieChart = new PieChart();
            fuelPieChart.setTitle("CO₂ by Fuel Type");

            CategoryAxis xAxis = new CategoryAxis();
            NumberAxis yAxis = new NumberAxis();
            fuelBarChart = new BarChart<>(xAxis, yAxis);
            xAxis.setLabel("Month");
            yAxis.setLabel("CO₂ (kg)");
            fuelBarChart.setTitle("Fuel CO₂ per Month");

            HBox charts = new HBox(10, fuelPieChart, fuelBarChart);
            charts.setPadding(new Insets(10));

            VBox v = new VBox(10, form, fuelTable, charts);
            v.setPadding(new Insets(10));
            return v;
        }

        private double getFuelFactor(String type) {
            switch (type) {
                case "Petrol": return 2.31;      // approx kg CO2 per liter
                case "Diesel": return 2.68;
                case "CNG": return 2.75;         // per kg; approx equivalence
                case "LPG": return 1.51;
                case "Aviation Fuel": return 2.54;
                default: return 2.0;
            }
        }

        private void calculateFuelCO2() {
            String litersText = fuelLitersField.getText().trim();
            String fuelType = fuelTypeCombo.getValue();
            if (litersText.isEmpty() || fuelType == null) {
                showAlert("Validation", "Enter liters and select fuel type.");
                return;
            }
            try {
                double liters = Double.parseDouble(litersText);
                if (liters <= 0) throw new NumberFormatException();
                double co2 = liters * getFuelFactor(fuelType);
                fuelCo2Label.setText(String.format("Estimated CO₂: %.3f kg", co2));
            } catch (NumberFormatException ex) {
                showAlert("Validation", "Liters must be a positive number.");
            }
        }

        private void saveFuelUsage() {
            LocalDate date = fuelDatePicker.getValue();
            String fuelType = fuelTypeCombo.getValue();
            String litersText = fuelLitersField.getText().trim();
            String notes = fuelNotesArea.getText().trim();

            if (date == null || fuelType == null || litersText.isEmpty()) {
                showAlert("Validation", "Fill date, fuel type and liters.");
                return;
            }

            double liters;
            try {
                liters = Double.parseDouble(litersText);
                if (liters <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                showAlert("Validation", "Liters must be a positive number.");
                return;
            }

            double factor = getFuelFactor(fuelType);
            double co2 = liters * factor;

            String sql = "INSERT INTO fuel_usage (user_id, date, fuel_type, liters, co2_kg, notes) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setDate(2, java.sql.Date.valueOf(date));
                ps.setString(3, fuelType);
                ps.setDouble(4, liters);
                ps.setDouble(5, co2);
                ps.setString(6, notes);
                ps.executeUpdate();
            } catch (SQLException e) {
                showAlert("Database error", "Failed to save fuel usage: " + e.getMessage());
                return;
            }

            fuelDatePicker.setValue(LocalDate.now());
            fuelLitersField.clear();
            fuelNotesArea.clear();
            fuelCo2Label.setText("Estimated CO₂: - kg");

            loadFuelUsage();
            updateFuelCharts();
            showAlert("Saved", String.format("Fuel record saved — CO₂: %.3f kg", co2));
        }

        private void loadFuelUsage() {
            fuelList.clear();
            String q = "SELECT id, date, fuel_type, liters, co2_kg, notes FROM fuel_usage " +
                    "WHERE user_id = ? ORDER BY date DESC, id DESC";

            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                while (rs.next()) {
                    int id = rs.getInt("id");
                    java.sql.Date d = rs.getDate("date");
                    String dateStr = d != null ? d.toLocalDate().format(fmt) : "";
                    String fuel = rs.getString("fuel_type");
                    double liters = rs.getDouble("liters");
                    double co2 = rs.getDouble("co2_kg");
                    String notes = rs.getString("notes");
                    fuelList.add(new FuelUsage(id, dateStr, fuel, liters, co2, notes));
                }
            } catch (SQLException e) {
                showAlert("Database error", "Failed to load fuel usage: " + e.getMessage());
            }
        }

        private void deleteFuelUsage(int id) {
            String sql = "DELETE FROM fuel_usage WHERE id = ? AND user_id = ?";
            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.setInt(2, userId);
                ps.executeUpdate();
            } catch (SQLException e) {
                showAlert("Database error", "Failed to delete fuel record: " + e.getMessage());
            }
        }

        private void updateFuelCharts() {
            // Pie: CO2 by fuel type
            Map<String, Double> byFuel = new LinkedHashMap<>();
            String q1 = "SELECT fuel_type, SUM(co2_kg) AS total FROM fuel_usage " +
                    "WHERE user_id = ? GROUP BY fuel_type";

            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(q1)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    byFuel.put(rs.getString("fuel_type"), rs.getDouble("total"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
            for (Map.Entry<String, Double> en : byFuel.entrySet()) {
                if (en.getValue() > 0) {
                    pieData.add(new PieChart.Data(en.getKey(), en.getValue()));
                }
            }
            fuelPieChart.setData(pieData);

            // Bar: CO2 by month
            Map<String, Double> byMonth = new TreeMap<>();
            String q2 = "SELECT DATE_FORMAT(date, '%Y-%m') AS ym, SUM(co2_kg) AS total " +
                    "FROM fuel_usage WHERE user_id = ? GROUP BY ym ORDER BY ym";

            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(q2)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    byMonth.put(rs.getString("ym"), rs.getDouble("total"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            fuelBarChart.getData().clear();
            XYChart.Series<String, Number> s = new XYChart.Series<>();
            s.setName("Fuel CO₂ (kg)");
            for (Map.Entry<String, Double> en : byMonth.entrySet()) {
                s.getData().add(new XYChart.Data<>(en.getKey(), en.getValue()));
            }
            fuelBarChart.getData().add(s);
        }

        // -------------------- ACTIVITY PAGE --------------------
        private void showActivityPage() {
            if (activityPage == null) {
                activityPage = buildActivityPage();
            }
            loadActivityLogs();
            updateActivityCharts();
            root.setCenter(activityPage);
        }

        private VBox buildActivityPage() {
            GridPane form = new GridPane();
            form.setPadding(new Insets(10));
            form.setVgap(8);
            form.setHgap(8);
            form.setStyle("-fx-background-color:#fafafa; -fx-border-color:#ddd;");

            Label title = new Label("Activity Logs");
            title.setStyle("-fx-font-size:18px; -fx-font-weight:bold;");
            form.add(title, 0, 0, 4, 1);

            form.add(new Label("Date:"), 0, 1);
            actDatePicker = new DatePicker(LocalDate.now());
            form.add(actDatePicker, 1, 1);

            form.add(new Label("Category:"), 0, 2);
            actCategoryField = new TextField();
            actCategoryField.setPromptText("e.g. Electricity, Cooking, Waste");
            form.add(actCategoryField, 1, 2);

            form.add(new Label("Activity:"), 0, 3);
            actNameField = new TextField();
            actNameField.setPromptText("e.g. AC usage, LPG stove");
            form.add(actNameField, 1, 3);

            form.add(new Label("Value:"), 0, 4);
            actValueField = new TextField();
            form.add(actValueField, 1, 4);

            form.add(new Label("Unit:"), 2, 4);
            actUnitField = new TextField();
            actUnitField.setPromptText("kWh, kg, etc.");
            form.add(actUnitField, 3, 4);

            form.add(new Label("Notes:"), 0, 5);
            actNotesArea = new TextArea();
            actNotesArea.setPrefRowCount(2);
            form.add(actNotesArea, 1, 5, 3, 1);

            Button calcBtn = new Button("Estimate CO₂ (rough)");
            Button saveBtn = new Button("Save Activity");
            Button refreshBtn = new Button("Refresh");
            HBox buttons = new HBox(8, calcBtn, saveBtn, refreshBtn);
            buttons.setAlignment(Pos.CENTER_LEFT);
            form.add(buttons, 1, 6);

            actCo2Label = new Label("Estimated CO₂: - kg");
            actCo2Label.setStyle("-fx-font-weight:bold;");
            form.add(actCo2Label, 1, 7);

            calcBtn.setOnAction(e -> estimateActivityCO2());
            saveBtn.setOnAction(e -> saveActivityLog());
            refreshBtn.setOnAction(e -> {
                loadActivityLogs();
                updateActivityCharts();
            });

            actTable = new TableView<>();
            actTable.setItems(actList);
            actTable.setPrefHeight(260);

            TableColumn<ActivityLog, String> dc = new TableColumn<>("Date");
            dc.setCellValueFactory(new PropertyValueFactory<>("date"));

            TableColumn<ActivityLog, String> catCol = new TableColumn<>("Category");
            catCol.setCellValueFactory(new PropertyValueFactory<>("category"));

            TableColumn<ActivityLog, String> nameCol = new TableColumn<>("activityName");
            nameCol.setText("Activity");
            nameCol.setCellValueFactory(new PropertyValueFactory<>("activityName"));

            TableColumn<ActivityLog, Double> valCol = new TableColumn<>("Value");
            valCol.setCellValueFactory(new PropertyValueFactory<>("value"));

            TableColumn<ActivityLog, String> unitCol = new TableColumn<>("Unit");
            unitCol.setCellValueFactory(new PropertyValueFactory<>("unit"));

            TableColumn<ActivityLog, Double> co2Col = new TableColumn<>("CO₂ (kg)");
            co2Col.setCellValueFactory(new PropertyValueFactory<>("co2"));

            actTable.getColumns().addAll(dc, catCol, nameCol, valCol, unitCol, co2Col);

            MenuItem del = new MenuItem("Delete selected");
            del.setOnAction(e -> {
                ActivityLog al = actTable.getSelectionModel().getSelectedItem();
                if (al != null) {
                    deleteActivityLog(al.getId());
                    loadActivityLogs();
                    updateActivityCharts();
                }
            });
            actTable.setContextMenu(new ContextMenu(del));

            actPieChart = new PieChart();
            actPieChart.setTitle("CO₂ by Category");

            CategoryAxis xAxis = new CategoryAxis();
            NumberAxis yAxis = new NumberAxis();
            actBarChart = new BarChart<>(xAxis, yAxis);
            xAxis.setLabel("Month");
            yAxis.setLabel("CO₂ (kg)");
            actBarChart.setTitle("Activity CO₂ per Month");

            HBox charts = new HBox(10, actPieChart, actBarChart);
            charts.setPadding(new Insets(10));

            VBox v = new VBox(10, form, actTable, charts);
            v.setPadding(new Insets(10));
            return v;
        }

        private void estimateActivityCO2() {
            String valText = actValueField.getText().trim();
            String unit = actUnitField.getText().trim().toLowerCase();

            if (valText.isEmpty() || unit.isEmpty()) {
                showAlert("Validation", "Enter value and unit.");
                return;
            }
            try {
                double value = Double.parseDouble(valText);
                if (value <= 0) throw new NumberFormatException();

                // super rough factors just for demonstration
                double factor;
                if (unit.contains("kwh")) factor = 0.82; // grid electricity
                else if (unit.contains("kg")) factor = 1.0;
                else if (unit.contains("litre") || unit.contains("l")) factor = 2.0;
                else factor = 0.5;

                double co2 = value * factor;
                actCo2Label.setText(String.format("Estimated CO₂: %.3f kg", co2));
            } catch (NumberFormatException ex) {
                showAlert("Validation", "Value must be a positive number.");
            }
        }

        private void saveActivityLog() {
            LocalDate date = actDatePicker.getValue();
            String cat = actCategoryField.getText().trim();
            String name = actNameField.getText().trim();
            String valText = actValueField.getText().trim();
            String unit = actUnitField.getText().trim();
            String notes = actNotesArea.getText().trim();

            if (date == null || cat.isEmpty() || name.isEmpty() || valText.isEmpty() || unit.isEmpty()) {
                showAlert("Validation", "Fill date, category, activity, value, unit.");
                return;
            }

            double value;
            try {
                value = Double.parseDouble(valText);
                if (value <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                showAlert("Validation", "Value must be a positive number.");
                return;
            }

            double factor;
            String unitLower = unit.toLowerCase();
            if (unitLower.contains("kwh")) factor = 0.82;
            else if (unitLower.contains("kg")) factor = 1.0;
            else if (unitLower.contains("litre") || unitLower.contains("l")) factor = 2.0;
            else factor = 0.5;

            double co2 = value * factor;

            String sql = "INSERT INTO activity_logs (user_id, category, activity_name, value, unit, co2_kg, date, notes) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setString(2, cat);
                ps.setString(3, name);
                ps.setDouble(4, value);
                ps.setString(5, unit);
                ps.setDouble(6, co2);
                ps.setDate(7, java.sql.Date.valueOf(date));
                ps.setString(8, notes);
                ps.executeUpdate();
            } catch (SQLException e) {
                showAlert("Database error", "Failed to save activity: " + e.getMessage());
                return;
            }

            actDatePicker.setValue(LocalDate.now());
            actCategoryField.clear();
            actNameField.clear();
            actValueField.clear();
            actUnitField.clear();
            actNotesArea.clear();
            actCo2Label.setText("Estimated CO₂: - kg");

            loadActivityLogs();
            updateActivityCharts();
            showAlert("Saved", String.format("Activity saved — CO₂: %.3f kg", co2));
        }

        private void loadActivityLogs() {
            actList.clear();
            String q = "SELECT id, category, activity_name, value, unit, co2_kg, date, notes " +
                    "FROM activity_logs WHERE user_id = ? ORDER BY date DESC, id DESC";

            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String cat = rs.getString("category");
                    String name = rs.getString("activity_name");
                    double value = rs.getDouble("value");
                    String unit = rs.getString("unit");
                    double co2 = rs.getDouble("co2_kg");
                    java.sql.Date d = rs.getDate("date");
                    String dateStr = d != null ? d.toLocalDate().format(fmt) : "";
                    String notes = rs.getString("notes");
                    actList.add(new ActivityLog(id, dateStr, cat, name, value, unit, co2, notes));
                }
            } catch (SQLException e) {
                showAlert("Database error", "Failed to load activity logs: " + e.getMessage());
            }
        }

        private void deleteActivityLog(int id) {
            String sql = "DELETE FROM activity_logs WHERE id = ? AND user_id = ?";
            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.setInt(2, userId);
                ps.executeUpdate();
            } catch (SQLException e) {
                showAlert("Database error", "Failed to delete activity: " + e.getMessage());
            }
        }

        private void updateActivityCharts() {
            // Pie: CO2 by category
            Map<String, Double> byCat = new LinkedHashMap<>();
            String q1 = "SELECT category, SUM(co2_kg) AS total FROM activity_logs " +
                    "WHERE user_id = ? GROUP BY category";

            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(q1)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    byCat.put(rs.getString("category"), rs.getDouble("total"));
                }
            } catch (SQLException e) {
            }

            ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
            for (Map.Entry<String, Double> en : byCat.entrySet()) {
                if (en.getValue() > 0) {
                    pieData.add(new PieChart.Data(en.getKey(), en.getValue()));
                }
            }
            actPieChart.setData(pieData);

            // Bar: CO2 by month
            Map<String, Double> byMonth = new TreeMap<>();
            String q2 = "SELECT DATE_FORMAT(date, '%Y-%m') AS ym, SUM(co2_kg) AS total " +
                    "FROM activity_logs WHERE user_id = ? GROUP BY ym ORDER BY ym";

            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(q2)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    byMonth.put(rs.getString("ym"), rs.getDouble("total"));
                }
            } catch (SQLException e) {
            }

            actBarChart.getData().clear();
            XYChart.Series<String, Number> s = new XYChart.Series<>();
            s.setName("Activity CO₂ (kg)");
            for (Map.Entry<String, Double> en : byMonth.entrySet()) {
                s.getData().add(new XYChart.Data<>(en.getKey(), en.getValue()));
            }
            actBarChart.getData().add(s);
        }

        // -------------------- GOALS PAGE --------------------
        private void showGoalsPage() {
            if (goalsPage == null) {
                goalsPage = buildGoalsPage();
            }
            loadGoals();
            updateGoalsSummary();
            root.setCenter(goalsPage);
        }

        private VBox buildGoalsPage() {
            GridPane form = new GridPane();
            form.setPadding(new Insets(10));
            form.setVgap(8);
            form.setHgap(8);
            form.setStyle("-fx-background-color:#fafafa; -fx-border-color:#ddd;");

            Label title = new Label("Carbon Goals");
            title.setStyle("-fx-font-size:18px; -fx-font-weight:bold;");
            form.add(title, 0, 0, 4, 1);

            form.add(new Label("Goal Name:"), 0, 1);
            goalNameField = new TextField();
            goalNameField.setPromptText("e.g. Keep monthly CO₂ under 100 kg");
            form.add(goalNameField, 1, 1, 3, 1);

            form.add(new Label("Target CO₂ (kg):"), 0, 2);
            goalTargetField = new TextField();
            form.add(goalTargetField, 1, 2);

            form.add(new Label("Period:"), 2, 2);
            goalPeriodCombo = new ComboBox<>();
            goalPeriodCombo.getItems().addAll("daily", "weekly", "monthly", "yearly");
            goalPeriodCombo.getSelectionModel().select("monthly");
            form.add(goalPeriodCombo, 3, 2);

            form.add(new Label("Start Date:"), 0, 3);
            goalStartPicker = new DatePicker(LocalDate.now());
            form.add(goalStartPicker, 1, 3);

            form.add(new Label("End Date:"), 2, 3);
            goalEndPicker = new DatePicker(LocalDate.now().plusMonths(1));
            form.add(goalEndPicker, 3, 3);

            Button saveBtn = new Button("Save Goal");
            Button refreshBtn = new Button("Refresh");
            HBox buttons = new HBox(8, saveBtn, refreshBtn);
            buttons.setAlignment(Pos.CENTER_LEFT);
            form.add(buttons, 1, 4);

            saveBtn.setOnAction(e -> saveGoal());
            refreshBtn.setOnAction(e -> {
                loadGoals();
                updateGoalsSummary();
            });

            goalTable = new TableView<>();
            goalTable.setItems(goalList);
            goalTable.setPrefHeight(260);

            TableColumn<CarbonGoal, String> nameCol = new TableColumn<>("Goal");
            nameCol.setCellValueFactory(new PropertyValueFactory<>("goalName"));

            TableColumn<CarbonGoal, Double> tgtCol = new TableColumn<>("Target CO₂ (kg)");
            tgtCol.setCellValueFactory(new PropertyValueFactory<>("targetCo2"));

            TableColumn<CarbonGoal, String> perCol = new TableColumn<>("Period");
            perCol.setCellValueFactory(new PropertyValueFactory<>("period"));

            TableColumn<CarbonGoal, String> startCol = new TableColumn<>("Start");
            startCol.setCellValueFactory(new PropertyValueFactory<>("startDate"));

            TableColumn<CarbonGoal, String> endCol = new TableColumn<>("End");
            endCol.setCellValueFactory(new PropertyValueFactory<>("endDate"));

            goalTable.getColumns().addAll(nameCol, tgtCol, perCol, startCol, endCol);

            MenuItem del = new MenuItem("Delete selected goal");
            del.setOnAction(e -> {
                CarbonGoal g = goalTable.getSelectionModel().getSelectedItem();
                if (g != null) {
                    deleteGoal(g.getId());
                    loadGoals();
                    updateGoalsSummary();
                }
            });
            goalTable.setContextMenu(new ContextMenu(del));

            goalsSummaryLabel = new Label("Goals summary will appear here.");
            goalsSummaryLabel.setStyle("-fx-font-weight:bold; -fx-padding:8;");

            VBox v = new VBox(10, form, goalTable, goalsSummaryLabel);
            v.setPadding(new Insets(10));
            return v;
        }

        private void saveGoal() {
            String name = goalNameField.getText().trim();
            String targetText = goalTargetField.getText().trim();
            String period = goalPeriodCombo.getValue();
            LocalDate start = goalStartPicker.getValue();
            LocalDate end = goalEndPicker.getValue();

            if (name.isEmpty() || targetText.isEmpty() || period == null || start == null || end == null) {
                showAlert("Validation", "Fill goal name, target, period, dates.");
                return;
            }
            if (end.isBefore(start)) {
                showAlert("Validation", "End date cannot be before start date.");
                return;
            }

            double target;
            try {
                target = Double.parseDouble(targetText);
                if (target <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                showAlert("Validation", "Target CO₂ must be a positive number.");
                return;
            }

            String sql = "INSERT INTO carbon_goals (user_id, goal_name, target_co2, period, start_date, end_date) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";

            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setString(2, name);
                ps.setDouble(3, target);
                ps.setString(4, period);
                ps.setDate(5, java.sql.Date.valueOf(start));
                ps.setDate(6, java.sql.Date.valueOf(end));
                ps.executeUpdate();
            } catch (SQLException e) {
                showAlert("Database error", "Failed to save goal: " + e.getMessage());
                return;
            }

            goalNameField.clear();
            goalTargetField.clear();
            goalPeriodCombo.getSelectionModel().select("monthly");
            goalStartPicker.setValue(LocalDate.now());
            goalEndPicker.setValue(LocalDate.now().plusMonths(1));

            loadGoals();
            updateGoalsSummary();
            showAlert("Saved", "Goal saved successfully.");
        }

        private void loadGoals() {
            goalList.clear();
            String q = "SELECT id, goal_name, target_co2, period, start_date, end_date " +
                    "FROM carbon_goals WHERE user_id = ? ORDER BY created_at DESC";

            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(q)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("goal_name");
                    double target = rs.getDouble("target_co2");
                    String period = rs.getString("period");
                    java.sql.Date sd = rs.getDate("start_date");
                    java.sql.Date ed = rs.getDate("end_date");
                    String startStr = sd != null ? sd.toLocalDate().format(fmt) : "";
                    String endStr = ed != null ? ed.toLocalDate().format(fmt) : "";
                    goalList.add(new CarbonGoal(id, name, target, period, startStr, endStr));
                }
            } catch (SQLException e) {
                showAlert("Database error", "Failed to load goals: " + e.getMessage());
            }
        }

        private void deleteGoal(int id) {
            String sql = "DELETE FROM carbon_goals WHERE id = ? AND user_id = ?";
            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.setInt(2, userId);
                ps.executeUpdate();
            } catch (SQLException e) {
                showAlert("Database error", "Failed to delete goal: " + e.getMessage());
            }
        }

        // Simple summary: total CO2 vs sum of targets (for all goals)
        private void updateGoalsSummary() {
            double totalTrip = 0;
            double totalFuel = 0;
            double totalAct = 0;

            String q1 = "SELECT SUM(co2_kg) FROM trips WHERE user_id = ?";
            String q2 = "SELECT SUM(co2_kg) FROM fuel_usage WHERE user_id = ?";
            String q3 = "SELECT SUM(co2_kg) FROM activity_logs WHERE user_id = ?";

            try (Connection conn = DatabaseHelper.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(q1)) {
                    ps.setInt(1, userId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) totalTrip = rs.getDouble(1);
                }
                try (PreparedStatement ps = conn.prepareStatement(q2)) {
                    ps.setInt(1, userId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) totalFuel = rs.getDouble(1);
                }
                try (PreparedStatement ps = conn.prepareStatement(q3)) {
                    ps.setInt(1, userId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) totalAct = rs.getDouble(1);
                }
            } catch (SQLException e) {
                // ignore
            }

            double totalCo2 = totalTrip + totalFuel + totalAct;

            double totalTarget = 0;
            for (CarbonGoal g : goalList) {
                totalTarget += g.getTargetCo2();
            }

            String msg = String.format(
                    "Total CO₂ so far: %.2f kg | Combined goals target: %.2f kg.",
                    totalCo2, totalTarget
            );

            if (totalTarget > 0) {
                double pct = (totalCo2 / totalTarget) * 100.0;
                msg += String.format(" You are at %.1f%% of your total target.", pct);
            } else {
                msg += " No goals set yet.";
            }

            goalsSummaryLabel.setText(msg);
        }

        // --------------- Utility ---------------
        private void showAlert(String title, String msg) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(msg);
            a.showAndWait();
        }

        // --------------- Models ---------------
        public static class Trip {
            private final int id;
            private final String date;
            private final String from;
            private final String to;
            private final String mode;
            private final double distance;
            private final int passengers;
            private final double co2;
            private final String notes;

            public Trip(int id, String date, String from, String to, String mode,
                        double distance, int passengers, double co2, String notes) {
                this.id = id;
                this.date = date;
                this.from = from;
                this.to = to;
                this.mode = mode;
                this.distance = distance;
                this.passengers = passengers;
                this.co2 = co2;
                this.notes = notes;
            }

            public int getId() { return id; }
            public String getDate() { return date; }
            public String getFrom() { return from; }
            public String getTo() { return to; }
            public String getMode() { return mode; }
            public double getDistance() { return distance; }
            public int getPassengers() { return passengers; }
            public double getCo2() { return co2; }
            public String getNotes() { return notes; }
        }

        public static class FuelUsage {
            private final int id;
            private final String date;
            private final String fuelType;
            private final double liters;
            private final double co2;
            private final String notes;

            public FuelUsage(int id, String date, String fuelType, double liters,
                             double co2, String notes) {
                this.id = id;
                this.date = date;
                this.fuelType = fuelType;
                this.liters = liters;
                this.co2 = co2;
                this.notes = notes;
            }

            public int getId() { return id; }
            public String getDate() { return date; }
            public String getFuelType() { return fuelType; }
            public double getLiters() { return liters; }
            public double getCo2() { return co2; }
            public String getNotes() { return notes; }
        }

        public static class ActivityLog {
            private final int id;
            private final String date;
            private final String category;
            private final String activityName;
            private final double value;
            private final String unit;
            private final double co2;
            private final String notes;

            public ActivityLog(int id, String date, String category, String activityName,
                               double value, String unit, double co2, String notes) {
                this.id = id;
                this.date = date;
                this.category = category;
                this.activityName = activityName;
                this.value = value;
                this.unit = unit;
                this.co2 = co2;
                this.notes = notes;
            }

            public int getId() { return id; }
            public String getDate() { return date; }
            public String getCategory() { return category; }
            public String getActivityName() { return activityName; }
            public double getValue() { return value; }
            public String getUnit() { return unit; }
            public double getCo2() { return co2; }
            public String getNotes() { return notes; }
        }

        public static class CarbonGoal {
            private final int id;
            private final String goalName;
            private final double targetCo2;
            private final String period;
            private final String startDate;
            private final String endDate;

            public CarbonGoal(int id, String goalName, double targetCo2,
                              String period, String startDate, String endDate) {
                this.id = id;
                this.goalName = goalName;
                this.targetCo2 = targetCo2;
                this.period = period;
                this.startDate = startDate;
                this.endDate = endDate;
            }

            public int getId() { return id; }
            public String getGoalName() { return goalName; }
            public double getTargetCo2() { return targetCo2; }
            public String getPeriod() { return period; }
            public String getStartDate() { return startDate; }
            public String getEndDate() { return endDate; }
        }
    }
}


