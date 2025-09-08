package login;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.MouseInputListener;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.input.ZoomMouseWheelListenerCursor;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;
import org.jxmapviewer.viewer.DefaultWaypoint;

class SimpleWaypoint extends DefaultWaypoint {
    public SimpleWaypoint(GeoPosition geo) {
        super(geo);
    }
}

public class MapViewer extends JDialog {
    private final JXMapViewer mapViewer;
    private GeoPosition selectedPosition;

    public MapViewer(JFrame owner, Consumer<GeoPosition> onLocationSelect) {
        super(owner, "Select Location", true);
        
        setSize(800, 600);
        setLocationRelativeTo(owner);

        mapViewer = new JXMapViewer();
        TileFactoryInfo info = new OSMTileFactoryInfo();
        DefaultTileFactory tileFactory = new DefaultTileFactory(info);
        
        // Setup file-based tile cache
        File cacheDir = new File(System.getProperty("user.home") + File.separator + ".jxmapviewer2");
        cacheDir.mkdirs();
        mapViewer.setTileFactory(tileFactory);

        // Add interactions
        MouseInputListener mia = new PanMouseInputListener(mapViewer);
        mapViewer.addMouseListener(mia);
        mapViewer.addMouseMotionListener(mia);
        mapViewer.addMouseWheelListener(new ZoomMouseWheelListenerCursor(mapViewer));

        // Set initial focus to a default location and zoom
        GeoPosition initialPosition = new GeoPosition(0, 0);
        mapViewer.setZoom(17);
        mapViewer.setCenterPosition(initialPosition);
        this.selectedPosition = initialPosition;

        // Create a waypoint painter
        WaypointPainter<Waypoint> waypointPainter = new WaypointPainter<>();
        Set<Waypoint> waypoints = new HashSet<>();
        waypoints.add(new SimpleWaypoint(initialPosition));
        waypointPainter.setWaypoints(waypoints);
        mapViewer.setOverlayPainter(waypointPainter);

        mapViewer.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // Add a mouse listener to select a location
        mapViewer.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    Point p = e.getPoint();
                    selectedPosition = mapViewer.convertPointToGeoPosition(p);
                    
                    // Update the waypoint to the new location
                    waypoints.clear();
                    waypoints.add(new SimpleWaypoint(selectedPosition));
                    waypointPainter.setWaypoints(waypoints);
                    mapViewer.repaint();
                }
            }
        });

        // --- UI Components for Search and Zoom ---
        JTextField searchField = new JTextField(30);
        JButton searchButton = new JButton("Search");
        JButton zoomInButton = new JButton("+");
        JButton zoomOutButton = new JButton("-");

        // Top panel for search
        JPanel topPanel = new JPanel();
        topPanel.add(new JLabel("Location:"));
        topPanel.add(searchField);
        topPanel.add(searchButton);
        topPanel.add(zoomInButton);
        topPanel.add(zoomOutButton);

        JButton myLocationButton = new JButton("My Location");
        topPanel.add(myLocationButton);

        // --- Button Actions ---
        myLocationButton.addActionListener(e -> {
            findAndCenterOnMyLocation(waypointPainter, waypoints);
        });

        searchButton.addActionListener(e -> {
            String query = searchField.getText();
            if (query != null && !query.trim().isEmpty()) {
                searchAndCenter(query, waypointPainter, waypoints);
            }
        });

        zoomInButton.addActionListener(e -> mapViewer.setZoom(mapViewer.getZoom() - 1));
        zoomOutButton.addActionListener(e -> mapViewer.setZoom(mapViewer.getZoom() + 1));

        // Create a button to confirm the selection
        JButton selectButton = new JButton("Select this Location");
        selectButton.addActionListener(e -> {
            if (onLocationSelect != null) {
                onLocationSelect.accept(selectedPosition);
            }
            dispose();
        });

        // --- Layout ---
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(mapViewer, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(selectButton);
        
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private void findAndCenterOnMyLocation(WaypointPainter<Waypoint> waypointPainter, Set<Waypoint> waypoints) {
        try {
            URL url = new URL("http://ip-api.com/json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) {
                JOptionPane.showMessageDialog(this, "Could not determine location. Please check your internet connection.", "Location Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
            StringBuilder response = new StringBuilder();
            String output;
            while ((output = br.readLine()) != null) {
                response.append(output);
            }
            conn.disconnect();

            JSONObject jsonResponse = new JSONObject(response.toString());
            String status = jsonResponse.getString("status");

            if ("success".equals(status)) {
                double lat = jsonResponse.getDouble("lat");
                double lon = jsonResponse.getDouble("lon");

                selectedPosition = new GeoPosition(lat, lon);
                mapViewer.setCenterPosition(selectedPosition);
                mapViewer.setZoom(7); // Zoom to a reasonable level

                // Update waypoint
                waypoints.clear();
                waypoints.add(new SimpleWaypoint(selectedPosition));
                waypointPainter.setWaypoints(waypoints);
                mapViewer.repaint();
            } else {
                JOptionPane.showMessageDialog(this, "Could not determine location: " + jsonResponse.optString("message"), "Location Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "An error occurred while fetching your location.", "Location Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void searchAndCenter(String query, WaypointPainter<Waypoint> waypointPainter, Set<Waypoint> waypoints) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            URL url = new URL("https://nominatim.openstreetmap.org/search?q=" + encodedQuery + "&format=json&limit=1");
            
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "ThinkDeck/1.0");

            if (conn.getResponseCode() != 200) {
                System.err.println("Failed : HTTP error code : " + conn.getResponseCode());
                return;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
            String output;
            StringBuilder response = new StringBuilder();
            while ((output = br.readLine()) != null) {
                response.append(output);
            }
            conn.disconnect();

            JSONArray results = new JSONArray(response.toString());
            if (results.length() > 0) {
                JSONObject firstResult = results.getJSONObject(0);
                double lat = firstResult.getDouble("lat");
                double lon = firstResult.getDouble("lon");
                
                selectedPosition = new GeoPosition(lat, lon);
                mapViewer.setCenterPosition(selectedPosition);
                mapViewer.setZoom(7); // Zoom in to city level

                // Update waypoint
                waypoints.clear();
                waypoints.add(new SimpleWaypoint(selectedPosition));
                waypointPainter.setWaypoints(waypoints);
                mapViewer.repaint();
            } else {
                System.out.println("No results found for: " + query);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}