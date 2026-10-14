package util.tools;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Represents the TrafficParameterGUI component.
 */
public class TrafficParameterGUI extends JFrame {

    private JTextField filePathField;
    private JButton browseButton;
    private JButton analyzeButton;
    private JTextArea resultArea;
    private JLabel statusLabel;

    /**
     * Creates a new instance of TrafficParameterGUI.
     */
    public TrafficParameterGUI() {
        initComponents();
        setupLayout();
        setupListeners();
    }

    /**
     * Executes the init components operation.
     */
    private void initComponents() {
        setTitle("Traffic Parameter Analyzer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(650, 500);
        setLocationRelativeTo(null);

        filePathField = new JTextField(30);
        filePathField.setEditable(false);

        browseButton = new JButton("Browse");
        analyzeButton = new JButton("Analyze File");
        analyzeButton.setEnabled(false);

        resultArea = new JTextArea(20, 50);
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resultArea.setBackground(new Color(240, 240, 240));

        statusLabel = new JLabel("Select a traffic file to analyze");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    }

    /**
     * Executes the setup layout operation.
     */
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));

        // Top panel - file selection
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filePanel.setBorder(BorderFactory.createTitledBorder("File Selection"));
        filePanel.add(new JLabel("File:"));
        filePanel.add(filePathField);
        filePanel.add(browseButton);
        filePanel.add(analyzeButton);

        // Center panel - results
        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.setBorder(BorderFactory.createTitledBorder("Identified Parameters"));
        resultPanel.add(new JScrollPane(resultArea), BorderLayout.CENTER);

        // Bottom panel - status
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(statusLabel);

        // Adicionar painis ao frame
        add(filePanel, BorderLayout.NORTH);
        add(resultPanel, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);

        // Add margins
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    /**
     * Executes the setup listeners operation.
     */
    private void setupListeners() {
        browseButton.addActionListener(new ActionListener() {
            /**
             * Handles the action event.
             * @param e the e.
             */
            @Override
            public void actionPerformed(ActionEvent e) {
                browseForFile();
            }
        });

        analyzeButton.addActionListener(new ActionListener() {
            /**
             * Handles the action event.
             * @param e the e.
             */
            @Override
            public void actionPerformed(ActionEvent e) {
                analyzeFile();
            }
        });
    }

    /**
     * Executes the browse for file operation.
     */
    private void browseForFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select the traffic file");

        // Accept any file type
        fileChooser.setAcceptAllFileFilterUsed(true);

        // Optional: still show JSON as the preference, but accept all
        fileChooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
        //fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("All files", "*"));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            String filePath = fileChooser.getSelectedFile().getAbsolutePath();
            filePathField.setText(filePath);
            analyzeButton.setEnabled(true);
            statusLabel.setText("Selected file: " + fileChooser.getSelectedFile().getName());
            resultArea.setText(""); // Clear previous results
        }
    }

    /**
     * Executes the analyze file operation.
     */
    private void analyzeFile() {
        String filePath = filePathField.getText();
        if (filePath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a file first.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Show loading indicator
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        analyzeButton.setEnabled(false);
        statusLabel.setText("Analyzing file...");

        // Run analysis in a separate thread to avoid freezing the interface
        SwingWorker<TrafficParameters, Void> worker = new SwingWorker<TrafficParameters, Void>() {
            /**
             * Returns the do in background.
             * @return the result of the operation.
             */
            @Override
            protected TrafficParameters doInBackground() throws Exception {
                return extractParametersFromTrafficFile(filePath);
            }

            /**
             * Executes the done operation.
             */
            @Override
            protected void done() {
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                analyzeButton.setEnabled(true);

                try {
                    TrafficParameters params = get();
                    if (params != null) {
                        displayResults(params);
                        statusLabel.setText("Analysis completed successfully!");
                    } else {
                        resultArea.setText("Error: Could not extract the file parameters.\n\n"
                                + "Possveis causas:\n"
                                + "- Unsupported file format\n"
                                + "- Estrutura JSON invlida\n"
                                + "- Corrupted file\n"
                                + "- Incorrect encoding");
                        statusLabel.setText("Error while analyzing the file");
                    }
                } catch (Exception ex) {
                    resultArea.setText("Error during analysis:\n" + ex.getMessage() + "\n\n"
                            + "Check whether the file:\n"
                            + "- Tem formato JSON vlido\n"
                            + "- Contm a estrutura 'requestGenerators'\n"
                            + "- Is not corrupted");
                    statusLabel.setText("Error while analyzing the file");
                    ex.printStackTrace();
                }
            }
        };

        worker.execute();
    }

    /**
     * Executes the display results operation.
     * @param params the params.
     */
    private void displayResults(TrafficParameters params) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== IDENTIFIED PARAMETERS ===\n\n");

        sb.append("Number of nodes: ").append(params.getQuantNodes()).append("\n\n");

        sb.append("Transmission rates:\n");
        double[] taxas = params.getTaxas();
        for (int i = 0; i < taxas.length; i++) {
            sb.append("  - ").append(formatBitRate(taxas[i])).append("\n");
        }
        sb.append("\n");

        sb.append("Proportions (normalized):\n");
        double[] proporcoes = params.getProporcoes();
        for (int i = 0; i < proporcoes.length; i++) {
            sb.append("  - ").append(formatBitRate(taxas[i])).append(": ").append(String.format("%.2f", proporcoes[i])).append("\n");
        }
        sb.append("\n");

        sb.append("Parmetros de Trfego:\n");
        sb.append("  - Ro (Initial load - Erlangs): ").append(String.format("%.2f", params.getRo())).append("\n");
        sb.append("  - Load increment (Erlangs): ").append(String.format("%.2f", params.getIncremento())).append("\n");
        sb.append("  - Mu (Holding rate): ").append(params.getMu()).append("\n");

        sb.append("\n=== RESUMO ===\n");
        sb.append("Analyzed file: ").append(filePathField.getText()).append("\n");
        sb.append("Total number of node pairs: ").append(params.getQuantNodes() * (params.getQuantNodes() - 1)).append("\n");
        sb.append("Number of different rates: ").append(taxas.length).append("\n");

        resultArea.setText(sb.toString());
    }

    /**
     * Returns the format bit rate.
     * @param bitRate the bitRate.
     * @return the result of the operation.
     */
    private String formatBitRate(double bitRate) {
        if (bitRate >= 1e9) {
            return String.format("%.0f Gbps", bitRate / 1e9);
        } else if (bitRate >= 1e6) {
            return String.format("%.0f Mbps", bitRate / 1e6);
        } else if (bitRate >= 1e3) {
            return String.format("%.0f Kbps", bitRate / 1e3);
        } else {
            return String.format("%.0f bps", bitRate);
        }
    }

    // Updated parameter extraction method to automatically detect the format
    /**
     * Returns the extract parameters from traffic file.
     * @param caminhoArq the caminhoArq.
     * @return the result of the operation.
     */
    public static TrafficParameters extractParametersFromTrafficFile(String caminhoArq) {
        try {
            FileReader fr = new FileReader(caminhoArq);
            BufferedReader br = new BufferedReader(fr);

            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                jsonContent.append(line);
            }

            br.close();
            fr.close();

            String content = jsonContent.toString().trim();

            // Detectar automaticamente se  JSON
            if (isValidJSON(content)) {
                return parseJSONContent(content);
            } else {
                // Try other formats in the future
                throw new IOException("Unsupported file format. Only JSON is currently accepted.");
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Method to check whether the content is valid JSON
    /**
     * Checks whether valid json.
     * @param content the content.
     * @return true if the condition is met; false otherwise.
     */
    private static boolean isValidJSON(String content) {
        try {
            new JSONObject(content);
            return true;
        } catch (Exception e1) {
            try {
                new JSONArray(content);
                return true;
            } catch (Exception e2) {
                return false;
            }
        }
    }

    // Method to parse JSON content
    /**
     * Returns the parse json content.
     * @param content the content.
     * @return the result of the operation.
     */
    private static TrafficParameters parseJSONContent(String content) {
        try {
            JSONObject jsonObject = new JSONObject(content);
            JSONArray requestGenerators = jsonObject.getJSONArray("requestGenerators");

            Set<Integer> nodes = new HashSet<>();
            Set<Double> taxasSet = new HashSet<>();

            List<Double> lambdaValues = new ArrayList<>();
            List<Double> incValues = new ArrayList<>();
            List<Double> taxasList = new ArrayList<>();

            double mu = 0;
            boolean muSet = false;

            for (int i = 0; i < requestGenerators.length(); i++) {
                JSONObject generator = requestGenerators.getJSONObject(i);

                int source = generator.getInt("source");
                int destination = generator.getInt("destination");
                double arrivalRate = generator.getDouble("arrivalRate");
                double arrivalRateIncrease = generator.getDouble("arrivalRateIncrease");
                double holdRate = generator.getDouble("holdRate");
                double bitRate = generator.getDouble("bitRate");

                nodes.add(source);
                nodes.add(destination);
                taxasSet.add(bitRate);

                if (!muSet) {
                    mu = holdRate;
                    muSet = true;
                }

                lambdaValues.add(arrivalRate);
                incValues.add(arrivalRateIncrease);
                taxasList.add(bitRate);
            }

            int quantNodes = nodes.size();
            double quantPares = quantNodes * (quantNodes - 1);

            List<Double> taxasUnicas = new ArrayList<>(taxasSet);
            Collections.sort(taxasUnicas);

            List<Double> somaPorTaxa = new ArrayList<>();

            for (Double taxa : taxasUnicas) {
                double sumLambdaForTaxa = 0;
                for (int i = 0; i < taxasList.size(); i++) {
                    if (taxasList.get(i).equals(taxa)) {
                        sumLambdaForTaxa += lambdaValues.get(i);
                    }
                }
                somaPorTaxa.add(sumLambdaForTaxa);
            }

            double minSoma = Double.MAX_VALUE;
            for (double soma : somaPorTaxa) {
                if (soma > 0 && soma < minSoma) {
                    minSoma = soma;
                }
            }

            if (minSoma == Double.MAX_VALUE) {
                minSoma = 1.0;
            }

            double[] proporcoes = new double[somaPorTaxa.size()];
            for (int i = 0; i < somaPorTaxa.size(); i++) {
                proporcoes[i] = somaPorTaxa.get(i) / minSoma;
            }

            double sumPropor = 0;
            for (double prop : proporcoes) {
                sumPropor += prop;
            }

            double[] taxas = new double[taxasUnicas.size()];
            for (int i = 0; i < taxasUnicas.size(); i++) {
                taxas[i] = taxasUnicas.get(i);
            }

            double lambdaRef = 0;
            double incRef = 0;
            for (int i = 0; i < requestGenerators.length(); i++) {
                JSONObject generator = requestGenerators.getJSONObject(i);
                double bitRate = generator.getDouble("bitRate");

                if (bitRate == taxas[0]) {
                    lambdaRef = generator.getDouble("arrivalRate");
                    incRef = generator.getDouble("arrivalRateIncrease");
                    break;
                }
            }

            double lambdaPorTaxaTotal = lambdaRef / proporcoes[0];
            double lambdaPorPar = lambdaPorTaxaTotal * sumPropor;
            double ro = (lambdaPorPar * quantPares) / mu;

            double incPorTaxaTotal = incRef / proporcoes[0];
            double incPorPar = incPorTaxaTotal * sumPropor;
            double incremento = (incPorPar * quantPares) / mu;

            return new TrafficParameters(quantNodes, taxas, proporcoes, ro, incremento, mu);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Robust method to parse bitRate from different formats
    /**
     * Returns the parse bit rate.
     * @param bitRateStr the bitRateStr.
     * @return the result of the operation.
     */
    private static double parseBitRate(String bitRateStr) {
        try {
            // Remove spaces and convert to uppercase
            String cleaned = bitRateStr.trim().toUpperCase();

            // Try direct parsing
            try {
                return Double.parseDouble(cleaned);
            } catch (NumberFormatException e) {
                // Ignore and try other formats
            }

            // Try scientific notation
            if (cleaned.contains("E")) {
                return Double.parseDouble(cleaned.replace("E+", "E"));
            }

            // Try common suffixes
            if (cleaned.endsWith("G") || cleaned.endsWith("GBPS")) {
                String numStr = cleaned.replace("G", "").replace("BPS", "").replace("GBPS", "");
                return Double.parseDouble(numStr) * 1e9;
            } else if (cleaned.endsWith("M") || cleaned.endsWith("MBPS")) {
                String numStr = cleaned.replace("M", "").replace("BPS", "").replace("MBPS", "");
                return Double.parseDouble(numStr) * 1e6;
            } else if (cleaned.endsWith("K") || cleaned.endsWith("KBPS")) {
                String numStr = cleaned.replace("K", "").replace("BPS", "").replace("KBPS", "");
                return Double.parseDouble(numStr) * 1e3;
            }

            // Last attempt - remove non-numeric characters
            String numericOnly = cleaned.replaceAll("[^0-9.E+-]", "");
            if (!numericOnly.isEmpty()) {
                return Double.parseDouble(numericOnly);
            }

            throw new NumberFormatException("Could not convert: " + bitRateStr);

        } catch (Exception e) {
            System.err.println("Error converting bitRate: " + bitRateStr + " - " + e.getMessage());
            throw new NumberFormatException("Unsupported bitRate format: " + bitRateStr);
        }
    }

    // TrafficParameters class
    public static class TrafficParameters {
        private int quantNodes;
        private double[] taxas;
        private double[] proporcoes;
        private double ro;
        private double incremento;
        private double mu;

        public TrafficParameters(int quantNodes, double[] taxas, double[] proporcoes, double ro, double incremento, double mu) {
            this.quantNodes = quantNodes;
            this.taxas = taxas;
            this.proporcoes = proporcoes;
            this.ro = ro;
            this.incremento = incremento;
            this.mu = mu;
        }

        public int getQuantNodes() { return quantNodes; }
        public double[] getTaxas() { return taxas; }
        public double[] getProporcoes() { return proporcoes; }
        public double getRo() { return ro; }
        public double getIncremento() { return incremento; }
        public double getMu() { return mu; }
    }

    /**
     * Runs the application entry point.
     * @param args the args.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            /**
             * Runs the operation.
             */
            @Override
            public void run() {
                new TrafficParameterGUI().setVisible(true);
            }
        });
    }
}