package login;

import Utils.*;
import component.HyperlinkText;
import component.Toaster;
import db.UserManager;
import db.FlashcardManager;
import db.TestMongo;

import org.bson.Document;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginUI extends JFrame {
    private final Toaster toaster;
    private boolean isDragging = false;
    private Point mouseOffset = new Point();
    private static final Logger LOGGER = Logger.getLogger(LoginUI.class.getName());

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                UserManager.migrateStringLocationsToGeoJSON(); // Migrate data first
                UserManager.ensureLocationIndex(); // Then ensure the geospatial index is created
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error during application startup", e);
            }
            new LoginUI();
        });
    }

    public LoginUI() {
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));

        JPanel mainPanel = createMainPanel();
        addWindowControls(mainPanel);
        addLogo(mainPanel);
        addSeparator(mainPanel);
        addUsernameTextField(mainPanel);
        addPasswordTextField(mainPanel);
        addLoginButton(mainPanel);
        addForgotPasswordButton(mainPanel);
        addRegisterButton(mainPanel);

        this.add(mainPanel);
        this.pack();
        this.setVisible(true);
        centerOnScreen();

        toaster = new Toaster(mainPanel);
    }

    private JPanel createMainPanel() {
        JPanel panel = createPanelWithRoundedBackground();
        configurePanelDragging(panel);
        return panel;
    }

    private JPanel createPanelWithRoundedBackground() {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = UIUtils.get2dGraphics(g);
                g2.setColor(UIUtils.COLOR_BACKGROUND);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(new Color(0, 0, 0, 50));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
            }
        };
    }

    private void configurePanelDragging(JPanel panel) {
        panel.setPreferredSize(new Dimension(800, 450));
        panel.setLayout(null);
        panel.setBackground(new Color(0, 0, 0, 0));

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                isDragging = true;
                mouseOffset = e.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isDragging = false;
            }
        });

        panel.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isDragging) {
                    Point newLocation = e.getLocationOnScreen();
                    newLocation.translate(-mouseOffset.x, -mouseOffset.y);
                    setLocation(newLocation);
                }
            }
        });
    }

    private void addWindowControls(JPanel panel) {
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        controlPanel.setOpaque(false);
        controlPanel.setBounds(0, 0, 800, 40);

        JButton minimizeButton = createControlButton("—", new Color(100, 100, 100));
        minimizeButton.addActionListener(e -> setState(Frame.ICONIFIED));

        JButton closeButton = createControlButton("×", new Color(200, 70, 70));
        closeButton.addActionListener(e -> System.exit(0));

        controlPanel.add(minimizeButton);
        controlPanel.add(closeButton);
        panel.add(controlPanel);
    }

    private JButton createControlButton(String text, Color bgColor) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = UIUtils.get2dGraphics(g);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (getModel().isPressed()) {
                    g2.setColor(bgColor.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(bgColor.brighter());
                } else {
                    g2.setColor(bgColor);
                }

                g2.fillOval(0, 0, getWidth(), getHeight());

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
                FontMetrics metrics = g2.getFontMetrics();
                int x = (getWidth() - metrics.stringWidth(text)) / 2;
                int y = ((getHeight() - metrics.getHeight()) / 2) + metrics.getAscent();
                g2.drawString(text, x, y);
            }
        };

        button.setPreferredSize(new Dimension(30, 30));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(false);

        return button;
    }

    private void centerOnScreen() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation(screenSize.width / 2 - getWidth() / 2, screenSize.height / 2 - getHeight() / 2);
    }

    private void addSeparator(JPanel panel) {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setForeground(UIUtils.COLOR_OUTLINE);
        separator.setBounds(310, 80, 1, 240);
        panel.add(separator);
    }

    private void addLogo(JPanel panel) {
        JLabel label = new JLabel();
        label.setFocusable(false);
        label.setIcon(
                new ImageIcon(Objects
                        .requireNonNull(
                                getClass().getClassLoader().getResource("images/logoo.png"))));
        label.setBounds(55, 146, 200, 110);
        panel.add(label);
    }

    private void addUsernameTextField(JPanel panel) {
        TextFieldUsername usernameField = new TextFieldUsername();
        usernameField.setBounds(423, 109, 250, 44);
        panel.add(usernameField);
    }

    private void addPasswordTextField(JPanel panel) {
        TextFieldPassword passwordField = new TextFieldPassword();
        passwordField.setBounds(423, 168, 250, 44);
        passwordField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (e.getKeyChar() == KeyEvent.VK_ENTER)
                    loginEventHandler();
            }
        });
        panel.add(passwordField);
    }

    private void addLoginButton(JPanel panel) {
        final Color[] loginButtonColors = { UIUtils.COLOR_INTERACTIVE, Color.white };

        JLabel loginButton = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = UIUtils.get2dGraphics(g);
                super.paintComponent(g2);

                Insets insets = getInsets();
                int w = getWidth() - insets.left - insets.right;
                int h = getHeight() - insets.top - insets.bottom;
                g2.setColor(loginButtonColors[0]);
                g2.fillRoundRect(insets.left, insets.top, w, h, UIUtils.ROUNDNESS, UIUtils.ROUNDNESS);

                FontMetrics metrics = g2.getFontMetrics(UIUtils.FONT_GENERAL_UI);
                int x2 = (getWidth() - metrics.stringWidth(UIUtils.BUTTON_TEXT_LOGIN)) / 2;
                int y2 = ((getHeight() - metrics.getHeight()) / 2) + metrics.getAscent();
                g2.setFont(UIUtils.FONT_GENERAL_UI);
                g2.setColor(loginButtonColors[1]);
                g2.drawString(UIUtils.BUTTON_TEXT_LOGIN, x2, y2);
            }
        };

        loginButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                loginEventHandler();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                loginButtonColors[0] = UIUtils.COLOR_INTERACTIVE_DARKER;
                loginButtonColors[1] = UIUtils.OFFWHITE;
                loginButton.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {

                loginButtonColors[0] = UIUtils.COLOR_INTERACTIVE;
                loginButtonColors[1] = Color.white;
                loginButton.repaint();

            }
        });

        loginButton.setBounds(423, 247, 250, 44);
        loginButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        panel.add(loginButton);
    }

    private void addForgotPasswordButton(JPanel panel) {
        panel.add(new HyperlinkText(UIUtils.BUTTON_TEXT_FORGOT_PASS, 423, 300,
                () -> toaster.error("Forgot password event")));
    }

    private void addRegisterButton(JPanel panel1) {
        panel1.add(new HyperlinkText(UIUtils.BUTTON_TEXT_REGISTER, 631, 300,
                () -> new RegisterUI()));
    }

    private void loginEventHandler() {
        Component[] components = this.getContentPane().getComponents();
        JPanel panel = (JPanel) components[0];

        TextFieldUsername usernameField = null;
        TextFieldPassword passwordField = null;

        for (Component c : panel.getComponents()) {

            if (c instanceof TextFieldUsername)
                usernameField = (TextFieldUsername) c;
            if (c instanceof TextFieldPassword)
                passwordField = (TextFieldPassword) c;

        }

        if (usernameField == null || passwordField == null) {
            toaster.error("Fields missing");
            return;
        }

        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());

        Document user = UserManager.loginUser(username, password);
        if (user != null) {
            toaster.success("Login successful");
            String userId = user.getObjectId("_id").toHexString();
            Timer fadeTimer = new Timer(10, e -> {
                float opacity = getOpacity();
                opacity -= 0.05f;
                if (opacity <= 0) {
                    ((Timer) e.getSource()).stop();
                    dispose();
                    new dashboard.Dashboard(userId);
                } else {
                    setOpacity(opacity);
                }
            });
            fadeTimer.start();
        } else {
            toaster.error("Invalid username or password");
        }
    }

}
