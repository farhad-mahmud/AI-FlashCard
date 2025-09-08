package dashboard;

import db.UserManager;
import org.bson.Document;
import org.apache.commons.lang3.tuple.Pair;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Advanced nearby user search with adjustable radius slider, live updates,
 * optional min/max distance filter and sorting controls.
 */
public class NearbyUsersExplorer extends JDialog {
    private final String currentUserId;
    private final Document currentUser;
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JSlider radiusSlider;
    private final JCheckBox autoRefresh;
    private final JComboBox<String> sortMode;
    private final JTextField minDistanceField;
    private final JTextField maxDistanceField;
    private final Timer refreshTimer;

    public NearbyUsersExplorer(JFrame owner, String currentUserId, Document currentUser) {
        super(owner, "Nearby Users", false);
        this.currentUserId = currentUserId;
        this.currentUser = currentUser;
        setSize(480, 560);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10,10));

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4,4,4,4);
        gbc.gridx=0; gbc.gridy=0; top.add(new JLabel("Radius (km)"), gbc);
        radiusSlider = new JSlider(1, 100, 5); // 1..100 km
        radiusSlider.setPaintTicks(true); radiusSlider.setPaintLabels(true); radiusSlider.setMajorTickSpacing(25); radiusSlider.setMinorTickSpacing(5);
        gbc.gridx=1; gbc.gridy=0; gbc.gridwidth=3; gbc.fill=GridBagConstraints.HORIZONTAL; top.add(radiusSlider, gbc);

        gbc.gridwidth=1; gbc.fill=GridBagConstraints.NONE;
        gbc.gridx=0; gbc.gridy=1; top.add(new JLabel("Min km"), gbc);
        minDistanceField = new JTextField(5); gbc.gridx=1; top.add(minDistanceField, gbc);
        gbc.gridx=2; top.add(new JLabel("Max km"), gbc);
        maxDistanceField = new JTextField(5); gbc.gridx=3; top.add(maxDistanceField, gbc);

        gbc.gridx=0; gbc.gridy=2; top.add(new JLabel("Sort"), gbc);
        sortMode = new JComboBox<>(new String[]{"Distance Asc","Distance Desc","Name A-Z","Name Z-A"});
        gbc.gridx=1; gbc.gridy=2; gbc.gridwidth=2; top.add(sortMode, gbc);
        gbc.gridwidth=1;

        autoRefresh = new JCheckBox("Auto-refresh (3s)");
        gbc.gridx=3; gbc.gridy=2; top.add(autoRefresh, gbc);

        JButton searchBtn = new JButton("Search Now");
        gbc.gridx=0; gbc.gridy=3; gbc.gridwidth=4; gbc.fill=GridBagConstraints.HORIZONTAL; top.add(searchBtn, gbc);

        add(top, BorderLayout.NORTH);

        JList<String> results = new JList<>(listModel);
        results.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        add(new JScrollPane(results), BorderLayout.CENTER);

        JLabel status = new JLabel("Ready");
        add(status, BorderLayout.SOUTH);

        searchBtn.addActionListener(e -> performSearch(status));
        radiusSlider.addChangeListener(e -> { if (!radiusSlider.getValueIsAdjusting() && autoRefresh.isSelected()) performSearch(status); });
        sortMode.addActionListener(e -> { if (autoRefresh.isSelected()) performSearch(status); });

        refreshTimer = new Timer(3000, e -> { if (autoRefresh.isSelected()) performSearch(status); });
        refreshTimer.start();

        performSearch(status);
    }

    private void performSearch(JLabel status) {
        listModel.clear();
        if (currentUser == null) { status.setText("No current user"); return; }
        Object locObj = currentUser.get("location");
        if (!(locObj instanceof Document)) { status.setText("Set your location first"); return; }
        @SuppressWarnings("unchecked") List<Double> coords = (List<Double>) ((Document)locObj).get("coordinates");
        if (coords == null || coords.size() != 2) { status.setText("Invalid location data"); return; }
        double lon = coords.get(0); double lat = coords.get(1);
        double radius = radiusSlider.getValue();
        List<Pair<Document, Double>> users = UserManager.findUsersWithinRadius(lat, lon, radius);
        // Filter by min/max if provided
        Double min = parseNullableDouble(minDistanceField.getText());
        Double max = parseNullableDouble(maxDistanceField.getText());
        users.removeIf(p -> (min != null && p.getRight() < min) || (max != null && p.getRight() > max));
        // Sort
        users.sort((a,b) -> {
            switch (String.valueOf(sortMode.getSelectedItem())) {
                case "Distance Desc": return Double.compare(b.getRight(), a.getRight());
                case "Name A-Z": return a.getLeft().getString("username").compareToIgnoreCase(b.getLeft().getString("username"));
                case "Name Z-A": return b.getLeft().getString("username").compareToIgnoreCase(a.getLeft().getString("username"));
                default: return Double.compare(a.getRight(), b.getRight());
            }
        });
        for (Pair<Document, Double> p : users) {
            Document u = p.getLeft();
            if (u.getObjectId("_id").toHexString().equals(currentUserId)) continue;
            listModel.addElement(String.format("%s  |  %.2f km", u.getString("username"), p.getRight()));
        }
        status.setText("Results: " + listModel.size());
    }

    private Double parseNullableDouble(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
