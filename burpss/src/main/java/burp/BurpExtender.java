package burp;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class BurpExtender implements IBurpExtender, ITab {

    // --- Configuration ---
    private static final int CORNER_RADIUS = 12; 
    private static final int DELAY_SECONDS = 10;
    
    private File saveDir;
    private IBurpExtenderCallbacks callbacks;
    private PrintWriter stdout;
    private PrintWriter stderr;
    private JPanel mainPanel;
    private JButton btnScreenshot;
    private JLabel statusLabel;
    private JTextArea logArea;

    // --- JNA INTERFACE (Windows Only) ---
    public interface Dwmapi extends StdCallLibrary {
        Dwmapi INSTANCE = Native.load("dwmapi", Dwmapi.class);
        int DwmGetWindowAttribute(WinDef.HWND hwnd, int dwAttribute, WinDef.RECT pvAttribute, int cbAttribute);
    }

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
        this.stdout = new PrintWriter(callbacks.getStdout(), true);
        this.stderr = new PrintWriter(callbacks.getStderr(), true);
        callbacks.setExtensionName("burpss");
        this.saveDir = resolveSafeStorage();
        SwingUtilities.invokeLater(this::initUI);
    }

    private File resolveSafeStorage() {
        String[] candidates = {
            System.getProperty("user.home") + File.separator + "burpss_captures",
            System.getProperty("user.home") + File.separator + "Documents" + File.separator + "burpss_captures",
            System.getProperty("java.io.tmpdir") + File.separator + "burpss_captures"
        };
        for (String path : candidates) {
            File f = new File(path);
            if (!f.exists()) f.mkdirs();
            if (f.canWrite()) return f;
        }
        return new File(System.getProperty("java.io.tmpdir"));
    }

    // --- MODERN CENTERED UI ---
    private void initUI() {
        // 1. Root Container (BorderLayout)
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Color.WHITE); // Or Burp default, usually fine to leave null, but let's be safe.

        // 2. Center Panel (GridBag for perfect centering)
        JPanel centerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(10, 10, 10, 10);

        // -- LOGO / TITLE --
        JLabel title = new JLabel("burpss");
        title.setFont(new Font("Segoe UI", Font.BOLD, 42));
        title.setForeground(new Color(255, 140, 0)); // Burp Orange
        c.gridy = 0;
        centerPanel.add(title, c);

        // -- STATUS --
        statusLabel = new JLabel("");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        statusLabel.setForeground(Color.GRAY);
        c.gridy = 1;
        centerPanel.add(statusLabel, c);

        // -- ACTION BUTTON --
        btnScreenshot = new JButton("START CAPTURE (10s)");
        btnScreenshot.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btnScreenshot.setBackground(new Color(34, 139, 34)); // Modern Green
        btnScreenshot.setForeground(Color.WHITE);
        btnScreenshot.setFocusPainted(false);
        btnScreenshot.setBorderPainted(false);
        btnScreenshot.setOpaque(true);
        btnScreenshot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // Add padding to button
        btnScreenshot.setBorder(new EmptyBorder(15, 40, 15, 40));
        
        btnScreenshot.addActionListener(e -> startCaptureSequence());
        
        c.gridy = 2;
        c.insets = new Insets(30, 10, 30, 10); // Extra space around button
        centerPanel.add(btnScreenshot, c);

        // -- INSTRUCTIONS --
        JLabel help = new JLabel("");
        help.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        help.setForeground(Color.LIGHT_GRAY);
        c.gridy = 3;
        centerPanel.add(help, c);

        // Add Center Panel to Root
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // 3. Bottom Logs (Clean Console)
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setPreferredSize(new Dimension(800, 150));
        bottomPanel.setBorder(new EmptyBorder(10, 20, 20, 20));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(245, 245, 245));
        logArea.setForeground(Color.DARK_GRAY);
        
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Activity Log"));
        
        bottomPanel.add(scroll, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Finalize
        callbacks.customizeUiComponent(mainPanel);
        callbacks.addSuiteTab(this);
        log("Ready. Storage: " + saveDir.getAbsolutePath());
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        stdout.println(msg);
    }

    private void startCaptureSequence() {
        btnScreenshot.setEnabled(false);
        btnScreenshot.setBackground(Color.GRAY);
        
        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                for (int i = DELAY_SECONDS; i > 0; i--) {
                    publish("SWITCH WINDOW (" + i + "s)");
                    Thread.sleep(1000);
                }
                return null;
            }
            @Override
            protected void process(List<String> chunks) {
                btnScreenshot.setText(chunks.get(chunks.size() - 1));
                statusLabel.setText("Acquiring target...");
            }
            @Override
            protected void done() {
                try {
                    performSmartCapture();
                } catch (Exception e) {
                    log("Error: " + e.getMessage());
                    e.printStackTrace(stderr);
                } finally {
                    btnScreenshot.setText("START CAPTURE (" + DELAY_SECONDS + "s)");
                    btnScreenshot.setBackground(new Color(34, 139, 34)); // Reset Green
                    btnScreenshot.setEnabled(true);
                    statusLabel.setText("Ready to Capture");
                }
            }
        };
        worker.execute();
    }

    private void performSmartCapture() throws Exception {
        Thread.sleep(300); // Settle
        Rectangle bounds = resolveActiveBounds();
        
        if (bounds == null || bounds.width < 50 || bounds.height < 50) {
            log("⚠️ Warning: Bounds too small. Capturing full screen.");
            bounds = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        }

        log("📸 Capturing: " + bounds.width + "x" + bounds.height + " @ " + bounds.x + "," + bounds.y);
        
        Robot robot = new Robot();
        BufferedImage raw = robot.createScreenCapture(bounds);
        BufferedImage processed = makeTransparentCorners(raw, CORNER_RADIUS);
        
        saveImage(processed);
    }

    private Rectangle resolveActiveBounds() {
        // 1. Internal Burp
        Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (activeWindow != null && activeWindow.isFocused()) {
            return getInternalComponentBounds();
        }

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return getWindowsBounds();
        } else if (os.contains("mac")) {
            return getMacBounds();
        } else {
            return getLinuxBounds();
        }
    }

    // --- LINUX HANDLER ---
    private Rectangle getLinuxBounds() {
        try {
            String idCmd = "xprop -root _NET_ACTIVE_WINDOW | awk '{print $NF}'";
            Process pId = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", idCmd});
            BufferedReader rId = new BufferedReader(new InputStreamReader(pId.getInputStream()));
            String winId = rId.readLine();
            
            if (winId != null && !winId.trim().isEmpty()) {
                winId = winId.trim();
                String geoCmd = "xwininfo -id " + winId + " -frame";
                Process pGeo = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", geoCmd});
                BufferedReader rGeo = new BufferedReader(new InputStreamReader(pGeo.getInputStream()));
                String line;
                int x=0, y=0, w=0, h=0;
                while((line = rGeo.readLine()) != null) {
                    line = line.trim();
                    if(line.startsWith("Absolute upper-left X:")) x = Integer.parseInt(line.split(":")[1].trim());
                    if(line.startsWith("Absolute upper-left Y:")) y = Integer.parseInt(line.split(":")[1].trim());
                    if(line.startsWith("Width:")) w = Integer.parseInt(line.split(":")[1].trim());
                    if(line.startsWith("Height:")) h = Integer.parseInt(line.split(":")[1].trim());
                }
                if (w > 0 && h > 0) return new Rectangle(x, y, w, h);
            }
        } catch (Exception e) {}
        return new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
    }

    // --- MAC HANDLER ---
    private Rectangle getMacBounds() {
        try {
            String script = "tell application \"System Events\" to get bounds of first window of (first application process whose frontmost is true)";
            Process p = Runtime.getRuntime().exec(new String[]{"osascript", "-e", script});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            if (line != null && !line.trim().isEmpty()) {
                String[] parts = line.split(",");
                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                int w = Integer.parseInt(parts[2].trim()) - x;
                int h = Integer.parseInt(parts[3].trim()) - y;
                return new Rectangle(x, y, w, h);
            }
        } catch (Exception e) {}
        return new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
    }

    // --- WINDOWS HANDLER ---
    private Rectangle getWindowsBounds() {
        try {
            WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
            WinDef.RECT rect = new WinDef.RECT();
            if (Dwmapi.INSTANCE.DwmGetWindowAttribute(hwnd, 9, rect, rect.size()) != 0) {
                User32.INSTANCE.GetWindowRect(hwnd, rect);
            }
            return new Rectangle(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top);
        } catch (Exception e) {}
        return new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
    }

    // --- INTERNAL HANDLER ---
    private Rectangle getInternalComponentBounds() {
        Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        while (c != null) {
            if (c instanceof Window) break;
            if ((c instanceof JTabbedPane || c instanceof JScrollPane) && c.getWidth() > 300) {
                 Point p = c.getLocationOnScreen();
                 return new Rectangle(p.x, p.y, c.getWidth(), c.getHeight());
            }
            c = c.getParent();
        }
        Window w = SwingUtilities.getWindowAncestor(mainPanel);
        return (w != null) ? w.getBounds() : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
    }

    private BufferedImage makeTransparentCorners(BufferedImage image, int radius) {
        BufferedImage output = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = output.createGraphics();
        g2.setComposite(AlphaComposite.Src);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(0, 0, image.getWidth(), image.getHeight(), radius, radius));
        g2.setComposite(AlphaComposite.SrcAtop);
        g2.drawImage(image, 0, 0, null);
        g2.dispose();
        return output;
    }

    private void saveImage(BufferedImage img) {
        try {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File file = new File(saveDir, "burpss_" + ts + ".png");
            ImageIO.write(img, "png", file);
            log("✅ SAVED: " + file.getName());
            Toolkit.getDefaultToolkit().beep();
        } catch (Exception e) {
            log("❌ Save Error: " + e.getMessage());
        }
    }

    @Override public String getTabCaption() { return "burpss"; }
    @Override public Component getUiComponent() { return mainPanel; }
}