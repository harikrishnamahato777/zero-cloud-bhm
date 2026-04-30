package com.example.bhm;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.PrintWriter;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Browser History Manager
 * Zero-Cloud CI/CD Pipeline Project
 * Uses a Doubly Linked List for navigation + Stack for back/forward
 * Integrates VirusTotal API for URL safety scanning
 */
public class BrowserHistoryManager extends JFrame {

    // ── Doubly Linked List Node ──────────────────────────────────────────────
    static class Node {
        String url;
        String time;
        Node prev, next;

        Node(String url) {
            this.url  = url;
            this.time = LocalDateTime.now().toString();
        }
    }

    // ── Data Structures ──────────────────────────────────────────────────────
    private Node head    = null;
    private Node current = null;
    private final Stack<String>       backStack    = new Stack<>();
    private final Stack<String>       forwardStack = new Stack<>();
    private final HashMap<String, Integer> visitCount = new HashMap<>();

    // ── UI Components ────────────────────────────────────────────────────────
    private final JTextField urlField      = new JTextField(25);
    private final JTextArea  historyArea   = new JTextArea(15, 30);
    private final JButton    visitBtn      = new JButton("Visit");
    private final JButton    backBtn       = new JButton("Back");
    private final JButton    forwardBtn    = new JButton("Forward");
    private final JButton    showHistoryBtn= new JButton("Show History");
    private final JButton    deleteUrlBtn  = new JButton("Delete URL");
    private final JButton    exportBtn     = new JButton("Export CSV");
    private final JButton    analyticsBtn  = new JButton("Analytics");
    private final JButton    themeBtn      = new JButton("Dark Mode");

    private boolean darkMode = false;

    // ── VirusTotal API Key (replace with your key) ───────────────────────────
    private static final String VT_API_KEY = "7a551ec6ebe68b7d02eba5c847f1ca9a02fbea18f37fd6cef96c922c9a73e93e";

    // ────────────────────────────────────────────────────────────────────────
    public BrowserHistoryManager() {
        setTitle("Browser History Manager");
        setSize(1200, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topPanel.add(urlField);
        topPanel.add(visitBtn);
        topPanel.add(backBtn);
        topPanel.add(forwardBtn);
        topPanel.add(showHistoryBtn);
        topPanel.add(deleteUrlBtn);
        topPanel.add(exportBtn);
        topPanel.add(analyticsBtn);
        topPanel.add(themeBtn);

        historyArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(historyArea);

        add(topPanel, BorderLayout.NORTH);
        add(scroll,   BorderLayout.CENTER);

        // Button listeners
        visitBtn      .addActionListener(e -> visitPage());
        backBtn       .addActionListener(e -> goBack());
        forwardBtn    .addActionListener(e -> goForward());
        showHistoryBtn.addActionListener(e -> showHistory());
        deleteUrlBtn  .addActionListener(e -> deleteUrl());
        exportBtn     .addActionListener(e -> exportCSV());
        analyticsBtn  .addActionListener(e -> showAnalytics());
        themeBtn      .addActionListener(e -> toggleTheme(topPanel));

        setShortcuts(topPanel);
        applyLightTheme(topPanel);
        setVisible(true);
    }

    // ── Keyboard Shortcuts ───────────────────────────────────────────────────
    private void setShortcuts(JPanel panel) {
        // Ctrl+L → focus URL field
        urlField.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK), "focusUrl");
        urlField.getActionMap().put("focusUrl", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { urlField.requestFocus(); }
        });
        // Ctrl+E → export CSV
        historyArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                   .put(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_DOWN_MASK), "exportCsv");
        historyArea.getActionMap().put("exportCsv", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { exportCSV(); }
        });
        // Ctrl+A → analytics
        historyArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                   .put(KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK), "analytics");
        historyArea.getActionMap().put("analytics", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { showAnalytics(); }
        });
        // Ctrl+D → toggle theme
        historyArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                   .put(KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_DOWN_MASK), "toggleTheme");
        historyArea.getActionMap().put("toggleTheme", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { toggleTheme(panel); }
        });
    }

    // ── Visit a URL ──────────────────────────────────────────────────────────
    private void visitPage() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) return;
        if (!url.startsWith("http")) url = "https://" + url;

        // VirusTotal safety check
        if (!isUrlSafeVirusTotal(url)) {
            JOptionPane.showMessageDialog(this,
                "⚠ Malicious URL detected!", "Security Alert",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (current != null) backStack.push(current.url);
        forwardStack.clear();

        visitCount.put(url, visitCount.getOrDefault(url, 0) + 1);

        Node node = new Node(url);
        if (head == null) {
            head = current = node;
        } else {
            current.next = node;
            node.prev    = current;
            current      = node;
        }

        updateHistory();
        openBrowser(url);
        urlField.setText("");
    }

    // ── Back Navigation ──────────────────────────────────────────────────────
    private void goBack() {
        if (current != null && current.prev != null) {
            forwardStack.push(current.url);
            current = current.prev;
            urlField.setText(current.url);
            updateHistory();
            openBrowser(current.url);
        } else {
            JOptionPane.showMessageDialog(this, "No more back history.");
        }
    }

    // ── Forward Navigation ───────────────────────────────────────────────────
    private void goForward() {
        if (!forwardStack.isEmpty()) {
            String forwardUrl = forwardStack.pop();
            backStack.push(current.url);
            current = findNode(forwardUrl);
            if (current != null) {
                urlField.setText(current.url);
                updateHistory();
                openBrowser(current.url);
            }
        } else {
            JOptionPane.showMessageDialog(this, "No more forward history.");
        }
    }

    // ── Find Node in Linked List ─────────────────────────────────────────────
    private Node findNode(String url) {
        Node temp = head;
        while (temp != null) {
            if (temp.url.equals(url)) return temp;
            temp = temp.next;
        }
        return null;
    }

    // ── Show Full History ────────────────────────────────────────────────────
    private void showHistory() {
        historyArea.setText("");
        Node temp = head;
        while (temp != null) {
            historyArea.append(temp.url + " | " + temp.time
                               + " | Visits: " + visitCount.get(temp.url) + "\n");
            temp = temp.next;
        }
    }

    // ── Delete a URL ─────────────────────────────────────────────────────────
    private void deleteUrl() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter URL to delete.");
            return;
        }
        Node node = findNode(url);
        if (node != null) {
            if (node.prev != null) node.prev.next = node.next; else head = node.next;
            if (node.next != null) node.next.prev = node.prev;
            if (current == node)
                current = (node.prev != null) ? node.prev : node.next;
            backStack.remove(url);
            forwardStack.remove(url);
            visitCount.remove(url);
            JOptionPane.showMessageDialog(this, "Deleted: " + url);
            updateHistory();
        } else {
            JOptionPane.showMessageDialog(this, "URL not found.");
        }
    }

    // ── Update History Display ───────────────────────────────────────────────
    private void updateHistory() { showHistory(); }

    // ── Export to CSV ────────────────────────────────────────────────────────
    private void exportCSV() {
        try {
            String path = System.getProperty("user.home") + "\\Desktop\\history.csv";
            PrintWriter pw = new PrintWriter(path);
            pw.println("URL,Timestamp,VisitCount");
            Node temp = head;
            while (temp != null) {
                pw.println(temp.url + "," + temp.time + "," + visitCount.get(temp.url));
                temp = temp.next;
            }
            pw.close();
            JOptionPane.showMessageDialog(this, "Exported to Desktop as history.csv");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage());
        }
    }

    // ── Analytics ────────────────────────────────────────────────────────────
    private void showAnalytics() {
        StringBuilder sb = new StringBuilder("Most Visited Sites\n\n");
        for (Map.Entry<String, Integer> e : visitCount.entrySet())
            sb.append(e.getKey()).append(" → ").append(e.getValue()).append("\n");
        JOptionPane.showMessageDialog(this, sb.toString());
    }

    // ── Theme Toggle ─────────────────────────────────────────────────────────
    private void toggleTheme(JPanel panel) {
        if (darkMode) { applyLightTheme(panel); themeBtn.setText("Dark Mode"); }
        else          { applyDarkTheme(panel);  themeBtn.setText("Light Mode"); }
        darkMode = !darkMode;
    }

    private void applyLightTheme(JPanel panel) {
        panel.setBackground(Color.WHITE);
        historyArea.setBackground(Color.WHITE);
        historyArea.setForeground(Color.BLACK);
        urlField.setBackground(Color.WHITE);
        urlField.setForeground(Color.BLACK);
    }

    private void applyDarkTheme(JPanel panel) {
        Color darkBg = new Color(45, 45, 45);
        panel.setBackground(darkBg);
        historyArea.setBackground(darkBg);
        historyArea.setForeground(Color.WHITE);
        urlField.setBackground(Color.DARK_GRAY);
        urlField.setForeground(Color.WHITE);
    }

    // ── Open in System Browser ───────────────────────────────────────────────
    private void openBrowser(String url) {
        try { Desktop.getDesktop().browse(new URI(url)); }
        catch (Exception ignored) {}
    }

    // ── VirusTotal URL Safety Check ──────────────────────────────────────────
    private boolean isUrlSafeVirusTotal(String url) {
        if (VT_API_KEY.equals("YOUR_VIRUSTOTAL_API_KEY_HERE")) return true; // skip if no key
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            String encoded = java.util.Base64.getUrlEncoder()
                .withoutPadding().encodeToString(url.getBytes());
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(new URI("https://www.virustotal.com/api/v3/urls/" + encoded))
                .header("x-apikey", VT_API_KEY)
                .GET().build();
            java.net.http.HttpResponse<String> response = client
                .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            return !response.body().contains("\"malicious\"");
        } catch (Exception e) {
            return true; // allow on network error
        }
    }

    // ── Entry Point ──────────────────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(BrowserHistoryManager::new);
    }
}
