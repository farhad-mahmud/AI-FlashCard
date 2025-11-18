package login;

import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.input.ZoomMouseWheelListenerCursor;

import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
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

import com.mongodb.client.model.geojson.Position;

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

        // --- Map setup ---
       // --- Map setup ---
mapViewer = new JXMapViewer();

// Use HTTPS instead of HTTP for tiles
OSMTileFactoryInfo info = new OSMTileFactoryInfo() {
    @Override
    public String getBaseURL() {
        return "https://a.tile.openstreetmap.org/";
    }
};
DefaultTileFactory tileFactory = new DefaultTileFactory(info);
mapViewer.setTileFactory(tileFactory);

        // Cache folder
        File cacheDir = new File(System.getProperty("user.home"), ".jxmapviewer2");
        cacheDir.mkdirs();

        // Mouse interactions
        MouseInputListener mia = new PanMouseInputListener(mapViewer);
        mapViewer.addMouseListener(mia);
        mapViewer.addMouseMotionListener(mia);
        mapViewer.addMouseWheelListener(new ZoomMouseWheelListenerCursor(mapViewer));

        // Initial position & zoom (Dhaka)
        GeoPosition initialPosition = new GeoPosition(23.8103, 90.4125);
        mapViewer.setCenterPosition(initialPosition);
        mapViewer.setZoom(5); // city-level zoom
        selectedPosition = initialPosition;

        // Waypoint painter
        WaypointPainter<Waypoint> waypointPainter = new WaypointPainter<>();
        Set<Waypoint> waypoints = new HashSet<>();
        waypoints.add(new SimpleWaypoint(initialPosition));
        waypointPainter.setWaypoints(waypoints);
        mapViewer.setOverlayPainter(waypointPainter);
        mapViewer.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // --- Mouse click to select location ---
        mapViewer.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    Point clickPoint = e.getPoint();  // java.awt.Point
                    GeoPosition selected = mapViewer.convertPointToGeoPosition(clickPoint);

                    // Update waypoint
                    waypoints.clear();
                    waypoints.add(new SimpleWaypoint(selected));
                    waypointPainter.setWaypoints(waypoints);
                    mapViewer.repaint();

                    selectedPosition = selected;

                    com.mongodb.client.model.geojson.Point mongoPoint = 
                        new com.mongodb.client.model.geojson.Point(
                            new com.mongodb.client.model.geojson.Position(
                                selected.getLongitude(), // longitude first for MongoDB
                                selected.getLatitude()   // then latitude
                            )
                        );
                }
            }
        });

        // --- UI ---
        JTextField searchField = new JTextField(30);
        JButton searchButton = new JButton("Search");
        JButton zoomInButton = new JButton("+");
        JButton zoomOutButton = new JButton("-");
        JButton myLocationButton = new JButton("My Location");
        JButton selectButton = new JButton("Select this Location");

        // Top panel
        JPanel topPanel = new JPanel();
        topPanel.add(new JLabel("Location:"));
        topPanel.add(searchField);
        topPanel.add(searchButton);
        topPanel.add(zoomInButton);
        topPanel.add(zoomOutButton);
        topPanel.add(myLocationButton);

        // Bottom panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(selectButton);

        // Layout
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(mapViewer, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        // --- Button actions ---
        zoomInButton.addActionListener(e -> mapViewer.setZoom(mapViewer.getZoom() - 1));
        zoomOutButton.addActionListener(e -> mapViewer.setZoom(mapViewer.getZoom() + 1));

        myLocationButton.addActionListener(e -> findAndCenterOnMyLocation(waypointPainter, waypoints));

        searchButton.addActionListener(e -> {
            String query = searchField.getText();
            if (query != null && !query.trim().isEmpty()) {
                searchAndCenter(query, waypointPainter, waypoints);
            }
        });

        selectButton.addActionListener(e -> {
            if (onLocationSelect != null) onLocationSelect.accept(selectedPosition);
            dispose();
        });
    }

    // --- Methods for location search and current location ---
    private void findAndCenterOnMyLocation(WaypointPainter<Waypoint> waypointPainter, Set<Waypoint> waypoints) {
        try {
            URL url = new URL("https://ip-api.com/json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "ThinkDeck/1.0"); // optional but recommended

            if (conn.getResponseCode() != 200) {
                JOptionPane.showMessageDialog(this, "Could not determine location.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) response.append(line);
            conn.disconnect();

            JSONObject json = new JSONObject(response.toString());
            if ("success".equals(json.getString("status"))) {
                double lat = json.getDouble("lat");
                double lon = json.getDouble("lon");
                selectedPosition = new GeoPosition(lat, lon);
                mapViewer.setCenterPosition(selectedPosition);
                mapViewer.setZoom(7);
                waypoints.clear();
                waypoints.add(new SimpleWaypoint(selectedPosition));
                waypointPainter.setWaypoints(waypoints);
                mapViewer.repaint();
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error fetching location.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void searchAndCenter(String query, WaypointPainter<Waypoint> waypointPainter, Set<Waypoint> waypoints) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            URL url = new URL("https://nominatim.openstreetmap.org/search?q=" + encodedQuery + "&format=json&limit=1");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "ThinkDeck/1.0");

            if (conn.getResponseCode() != 200) return;

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) response.append(line);
            conn.disconnect();

            JSONArray results = new JSONArray(response.toString());
            if (results.length() > 0) {
                JSONObject first = results.getJSONObject(0);
                double lat = first.getDouble("lat");
                double lon = first.getDouble("lon");

                selectedPosition = new GeoPosition(lat, lon);
                mapViewer.setCenterPosition(selectedPosition);
                mapViewer.setZoom(7);

                waypoints.clear();
                waypoints.add(new SimpleWaypoint(selectedPosition));
                waypointPainter.setWaypoints(waypoints);
                mapViewer.repaint();
            } else {
                JOptionPane.showMessageDialog(this, "No results found for: " + query, "Search", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
