package util.tools;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.prefs.Preferences;

/**
 * Represents the ConfigDirectorySelectorGUI component.
 */
public class ConfigDirectorySelectorGUI extends JDialog {

	private JTextField directoryField;
    private JButton browseButton;
    private JButton confirmButton;
    private JLabel statusLabel;

    private String configDirectory;

    // --------------------------------------------------
    // Preferences (persistence of the last directory)
    // --------------------------------------------------
    private static final String PREF_NODE = "simulator/config";
    private static final String LAST_DIR_KEY = "lastConfigDirectory";
    private final Preferences prefs = Preferences.userRoot().node(PREF_NODE);

    // --------------------------------------------------
    // Construtor
    // --------------------------------------------------
    /**
     * Creates a new instance of ConfigDirectorySelectorGUI.
     * @param parent the parent.
     */
    public ConfigDirectorySelectorGUI(Frame parent) {
        super(parent, "Select Simulation Configuration Directory", true);
        initComponents();
        setupLayout();
        setupListeners();
    }

    // --------------------------------------------------
    // Component initialization
    // --------------------------------------------------
    /**
     * Executes the init components operation.
     */
    private void initComponents() {
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setSize(650, 180);
        setLocationRelativeTo(getParent());

        directoryField = new JTextField(35);
        directoryField.setEditable(false);

        browseButton = new JButton("Search");
        confirmButton = new JButton("Confirm");
        confirmButton.setEnabled(false);

        statusLabel = new JLabel("Select the directory that contains the configuration files");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // ----------------------------------------------
        // Load last saved directory (if it exists)
        // ----------------------------------------------
        String lastDir = prefs.get(LAST_DIR_KEY, null);
        if (lastDir != null && new File(lastDir).exists()) {
            configDirectory = lastDir;
            directoryField.setText(lastDir);
            confirmButton.setEnabled(true);
            statusLabel.setText("Last used directory loaded");
        }
    }

    // --------------------------------------------------
    // Layout
    // --------------------------------------------------
    /**
     * Executes the setup layout operation.
     */
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));

        JPanel directoryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        directoryPanel.setBorder(
                BorderFactory.createTitledBorder("Configuration Directory")
        );

        directoryPanel.add(new JLabel("Directory: "));
        directoryPanel.add(directoryField);
        directoryPanel.add(browseButton);
        directoryPanel.add(confirmButton);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(statusLabel);

        add(directoryPanel, BorderLayout.NORTH);
        add(statusPanel, BorderLayout.SOUTH);

        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    // --------------------------------------------------
    // Listeners
    // --------------------------------------------------
    /**
     * Executes the setup listeners operation.
     */
    private void setupListeners() {

        browseButton.addActionListener(e -> browseForDirectory());

        confirmButton.addActionListener(e -> {
            if (configDirectory != null) {
                // Save again for safety
                prefs.put(LAST_DIR_KEY, configDirectory);
                statusLabel.setText("Directory confirmed.");
            }
            dispose();
        });
    }

    // --------------------------------------------------
    // JFileChooser (directory mode)
    // --------------------------------------------------
    /**
     * Executes the browse for directory operation.
     */
    private void browseForDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select the configuration directory.");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        // Open in the last used directory, if one exists.
        String lastDir = prefs.get(LAST_DIR_KEY, null);
        if (lastDir != null && new File(lastDir).exists()) {
            chooser.setCurrentDirectory(new File(lastDir));
        }

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File dir = chooser.getSelectedFile();
            configDirectory = dir.getAbsolutePath();
            directoryField.setText(configDirectory);
            confirmButton.setEnabled(true);
            statusLabel.setText("Selected directory: " + dir.getName());

            // Save preference immediately
            prefs.put(LAST_DIR_KEY, configDirectory);
        }
    }

    // --------------------------------------------------
    // Getter of the result
    // --------------------------------------------------
    /**
     * @return directory selected by the user or null if not confirmed
     */
    public String getConfigDirectory() {
        return configDirectory;
    }
}
