package login;

import Utils.*;
import component.Toaster;
import db.UserManager;
import org.jxmapviewer.viewer.GeoPosition;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.logging.*;

public class RegisterUI extends JFrame {
    private final Toaster toaster;

    public RegisterUI() {
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setSize(600, 500);
        setLayout(null);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = UIUtils.get2dGraphics(g);
                g2.setColor(UIUtils.COLOR_BACKGROUND);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            }
        };
        panel.setBounds(0, 0, 600, 500);
        panel.setLayout(null);
        panel.setOpaque(false);
        add(panel);

        JLabel title = new JLabel("Register");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(Color.WHITE);
        title.setBounds(250, 70, 100, 30);
        panel.add(title);

        TextFieldUsername usernameField = new TextFieldUsername();
        usernameField.setBounds(170, 120, 260, 40);
        usernameField.setText("Username");
        usernameField.setForeground(UIUtils.COLOR_OUTLINE);
        usernameField.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (usernameField.getText().equals("Username")) {
                    usernameField.setText("");
                    usernameField.setForeground(Color.WHITE);
                }
            }

            public void focusLost(FocusEvent e) {
                if (usernameField.getText().isEmpty()) {
                    usernameField.setText("Username");
                    usernameField.setForeground(UIUtils.COLOR_OUTLINE);
                }
            }
        });
        panel.add(usernameField);

        TextFieldUsername emailField = new TextFieldUsername();
        emailField.setBounds(170, 170, 260, 40);
        emailField.setText("Email");
        emailField.setForeground(UIUtils.COLOR_OUTLINE);
        emailField.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (emailField.getText().equals("Email")) {
                    emailField.setText("");
                    emailField.setForeground(Color.WHITE);
                }
            }

            public void focusLost(FocusEvent e) {
                if (emailField.getText().isEmpty()) {
                    emailField.setText("Email");
                    emailField.setForeground(UIUtils.COLOR_OUTLINE);
                }
            }
        });
        panel.add(emailField);

        TextFieldPassword passwordField = new TextFieldPassword();
        passwordField.setBounds(170, 220, 260, 40);
        passwordField.setText("Password");
        passwordField.setForeground(UIUtils.COLOR_OUTLINE);
        passwordField.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (String.valueOf(passwordField.getPassword()).equals("Password")) {
                    passwordField.setText("");
                    passwordField.setForeground(Color.WHITE);
                }
            }

            public void focusLost(FocusEvent e) {
                if (String.valueOf(passwordField.getPassword()).isEmpty()) {
                    passwordField.setText("Password");
                    passwordField.setForeground(UIUtils.COLOR_OUTLINE);
                }
            }
        });
        panel.add(passwordField);

        TextFieldlocation locationField = new TextFieldlocation();
        locationField.setBounds(170, 285, 180, 40); // Shorten the field
        locationField.setText("Location");
        locationField.setForeground(UIUtils.COLOR_OUTLINE);
        locationField.setEditable(false); // Make it not editable by user
        panel.add(locationField);

        JButton selectLocationButton = new JButton("...");
        selectLocationButton.setBounds(360, 285, 70, 40); // Place button next to field
        panel.add(selectLocationButton);

        // Variable to hold the selected coordinates
        final GeoPosition[] selectedLocation = {null};

        // Action for the button
        selectLocationButton.addActionListener(e -> {
            MapViewer mapViewer = new MapViewer(this, (location) -> {
                selectedLocation[0] = location;
                locationField.setText(String.format("%.6f, %.6f", location.getLatitude(), location.getLongitude()));
                locationField.setForeground(Color.WHITE);
            });
            mapViewer.setVisible(true);
        });

        final Color[] registerButtonColors = { UIUtils.COLOR_INTERACTIVE, Color.WHITE };

        JLabel registerButton = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = UIUtils.get2dGraphics(g);
                super.paintComponent(g2);

                Insets insets = getInsets();
                int w = getWidth() - insets.left - insets.right;
                int h = getHeight() - insets.top - insets.bottom;
                g2.setColor(registerButtonColors[0]);
                g2.fillRoundRect(insets.left, insets.top, w, h, UIUtils.ROUNDNESS, UIUtils.ROUNDNESS);

                FontMetrics metrics = g2.getFontMetrics(UIUtils.FONT_GENERAL_UI);
                int x2 = (getWidth() - metrics.stringWidth("register")) / 2;
                int y2 = ((getHeight() - metrics.getHeight()) / 2) + metrics.getAscent();
                g2.setFont(UIUtils.FONT_GENERAL_UI);
                g2.setColor(registerButtonColors[1]);
                g2.drawString("Register", x2, y2);
            }
        };

        registerButton.setBounds(230, 350, 140, 44); // Centered the button
        registerButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        registerButton.setBackground(UIUtils.COLOR_BACKGROUND);

        registerButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                String username = usernameField.getText().trim();
                String email = emailField.getText().trim();
                String password = new String(passwordField.getPassword()).trim();
                String locationString = locationField.getText().trim();

                if (username.isEmpty() || email.isEmpty() || password.isEmpty() || locationString.isEmpty() ||
                        username.equals("Username") || email.equals("Email") || password.equals("Password")
                        || locationString.equals("Location")) {
                    toaster.error("All fields required!");
                    return;
                }

                if (selectedLocation[0] == null) {
                    toaster.error("Please select a location from the map.");
                    return;
                }

                // Use coordinates for registration
                String locationForDb = selectedLocation[0].getLatitude() + "," + selectedLocation[0].getLongitude();
                UserManager.insertUser(username, email, password, locationForDb);
                toaster.success("Registered: " + username);

                Timer closeTimer = new Timer(1300, ev -> dispose());
                closeTimer.setRepeats(false);
                closeTimer.start();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                registerButtonColors[0] = UIUtils.COLOR_INTERACTIVE_DARKER;
                registerButtonColors[1] = UIUtils.OFFWHITE;
                registerButton.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                registerButtonColors[0] = UIUtils.COLOR_INTERACTIVE;
                registerButtonColors[1] = Color.WHITE;
                registerButton.repaint();
            }
        });

        panel.add(registerButton);

        toaster = new Toaster(panel);

        setVisible(true);
    }
}
