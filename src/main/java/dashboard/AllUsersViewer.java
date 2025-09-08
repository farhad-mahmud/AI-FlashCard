package dashboard;

import com.mongodb.client.*;
import org.bson.Document;
import db.TestMongo;
import db.UserManager;
import Utils.*;
import component.Toaster;
import org.apache.commons.lang3.tuple.Pair;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class AllUsersViewer extends JFrame {

    private final String currentUserId;
    private JPanel userListPanel;
    private final Document currentUser;

    public AllUsersViewer(String currentUserId) {
        this.currentUserId = currentUserId;
        this.currentUser = UserManager.getUserById(new org.bson.types.ObjectId(currentUserId));
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setSize(450, 500);
        setLayout(null);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel backgroundPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = UIUtils.get2dGraphics(g);
                g2.setColor(UIUtils.COLOR_BACKGROUND);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            }
        };
        backgroundPanel.setBounds(0, 0, 450, 500);
        backgroundPanel.setLayout(null);
        backgroundPanel.setOpaque(false);
        add(backgroundPanel);

        JLabel title = new JLabel("All Registered Users", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        title.setBounds(0, 20, 450, 40);
        backgroundPanel.add(title);

        JTextField radiusField = new JTextField("5"); // Default 5km
        radiusField.setBounds(25, 60, 100, 30);
        backgroundPanel.add(radiusField);

        JButton searchButton = new JButton("Search by km");
        searchButton.setBounds(135, 60, 120, 30);
        backgroundPanel.add(searchButton);

        JButton advancedButton = new JButton("Advanced ▶");
        advancedButton.setBounds(300, 60, 110, 30);
        backgroundPanel.add(advancedButton);
        advancedButton.addActionListener(e -> new NearbyUsersExplorer(this, currentUserId, currentUser).setVisible(true));

        userListPanel = new JPanel();
        userListPanel.setLayout(new BoxLayout(userListPanel, BoxLayout.Y_AXIS));
        userListPanel.setBackground(new Color(0, 0, 0, 0));

        JButton exitButton = new JButton("X");
        exitButton.setBounds(410, 10, 30, 30);
        exitButton.setBackground(new Color(58, 64, 77));
        exitButton.setForeground(Color.BLACK);
        exitButton.setBorder(BorderFactory.createEmptyBorder());
        exitButton.setFocusPainted(false);
        exitButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        exitButton.addActionListener(e -> dispose());
        backgroundPanel.add(exitButton);

        searchButton.addActionListener(e -> {
            if (currentUser == null) {
                new Toaster(backgroundPanel).error("Could not load your user data.");
                return;
            }
            try {
                double radius = Double.parseDouble(radiusField.getText());
                Object locationObj = currentUser.get("location");

                if (!(locationObj instanceof Document)) {
                    new Toaster(backgroundPanel).error("Your location is not set. Please update it in your profile.");
                    return;
                }
                
                Document location = (Document) locationObj;
                @SuppressWarnings("unchecked")
                List<Double> coordinates = (List<Double>) location.get("coordinates");

                if (coordinates == null || coordinates.size() != 2) {
                    new Toaster(backgroundPanel).error("Your location data is invalid. Please update it in your profile.");
                    return;
                }

                double longitude = coordinates.get(0);
                double latitude = coordinates.get(1);
                List<Pair<Document, Double>> users = UserManager.findUsersWithinRadius(latitude, longitude, radius);
                updateUsersList(users);

            } catch (NumberFormatException ex) {
                new Toaster(backgroundPanel).error("Invalid radius. Please enter a number.");
            } catch (Exception ex) {
                new Toaster(backgroundPanel).error("An unexpected error occurred during search.");
                ex.printStackTrace();
            }
        });

        loadAllUsers();


        JScrollPane scrollPane = new JScrollPane(userListPanel);
        scrollPane.setBounds(25, 100, 400, 360);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        backgroundPanel.add(scrollPane);

        setVisible(true);
    }

    private void loadAllUsers() {
        MongoDatabase db = TestMongo.connect();
        MongoCollection<Document> users = db.getCollection("users");
        FindIterable<Document> docs = users.find(com.mongodb.client.model.Filters.ne("isHidden", true));
        userListPanel.removeAll();
        for (Document user : docs) {
            if (user.getObjectId("_id").toHexString().equals(currentUserId)) continue;
            addUserCard(user, null);
        }
        userListPanel.revalidate();
        userListPanel.repaint();
    }

    private void updateUsersList(List<Pair<Document, Double>> usersWithDistance) {
        userListPanel.removeAll();
        if (usersWithDistance.isEmpty()) {
            JLabel noUsersLabel = new JLabel("No users found within the specified radius.");
            noUsersLabel.setForeground(Color.WHITE);
            userListPanel.add(noUsersLabel);
        } else {
            for (Pair<Document, Double> userPair : usersWithDistance) {
                Document user = userPair.getLeft();
                if (user.getObjectId("_id").toHexString().equals(currentUserId)) continue;
                Double distance = userPair.getRight();
                addUserCard(user, distance);
            }
        }
        userListPanel.revalidate();
        userListPanel.repaint();
    }

    private void addUserCard(Document user, Double distance) {
        String username = user.getString("username");
        String email = user.getString("email");
        boolean canReceiveMessages = user.getBoolean("canReceiveMessages", true);

        JPanel userCard = new JPanel();
        userCard.setLayout(new BorderLayout());
        userCard.setBackground(new Color(58, 64, 77));
        userCard.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel nameLabel = new JLabel("User: " + username);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));

        JLabel emailLabel = new JLabel("Email: " + email);
        emailLabel.setForeground(new Color(180, 180, 180));
        emailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        textPanel.add(nameLabel);
        textPanel.add(emailLabel);

        if (distance != null) {
            JLabel distanceLabel = new JLabel(String.format("Distance: %.2f km", distance));
            distanceLabel.setForeground(new Color(180, 180, 180));
            distanceLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            textPanel.add(distanceLabel);
        } else {
            Object locationObj = user.get("location");
            String locationString = "Not available";
            if (locationObj instanceof Document) {
                Document locationDoc = (Document) locationObj;
                @SuppressWarnings("unchecked")
                List<Double> coords = (List<Double>) locationDoc.get("coordinates");
                if (coords != null && coords.size() == 2) {
                    locationString = String.format("%.4f, %.4f", coords.get(1), coords.get(0)); // lat, lon
                }
            } else if (locationObj instanceof String) {
                locationString = (String) locationObj;
            }
            
            JLabel locationLabel = new JLabel("Location: " + locationString);
            locationLabel.setForeground(new Color(180, 180, 180));
            locationLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            textPanel.add(locationLabel);
        }

        userCard.add(textPanel, BorderLayout.CENTER);

        if (canReceiveMessages) {
            JButton messageButton = new JButton("Message");
            messageButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
            messageButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            String receiverId = user.getObjectId("_id").toHexString();
            messageButton.addActionListener(e -> {
                new MessagingUI(currentUserId, receiverId).setVisible(true);
            });
            userCard.add(messageButton, BorderLayout.EAST);
        }

        userCard.setMaximumSize(new Dimension(380, 100));

        userListPanel.add(userCard);
        userListPanel.add(Box.createVerticalStrut(10));
    }
}
