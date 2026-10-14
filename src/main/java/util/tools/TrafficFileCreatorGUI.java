package util.tools;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * Represents the TrafficFileCreatorGUI component.
 */
public class TrafficFileCreatorGUI extends JFrame {

    private JTextField caminhoArqField;
    private JTextField quantNodesField;
    private JTextField taxasField;
    private JTextField proporcoesField;
    private JTextField roField;
    private JTextField incrementoField;
    private JTextField muField;
    private JButton criarButton;
    private JButton selecionarArquivoButton;

    /**
     * Creates a new instance of TrafficFileCreatorGUI.
     */
    public TrafficFileCreatorGUI() {
        initComponents();
        setupLayout();
        setupListeners();
        setDefaultValues();
    }

    /**
     * Executes the init components operation.
     */
    private void initComponents() {
        setTitle("Traffic File Creator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);

        caminhoArqField = new JTextField(30);
        quantNodesField = new JTextField();
        taxasField = new JTextField();
        proporcoesField = new JTextField();
        roField = new JTextField();
        incrementoField = new JTextField();
        muField = new JTextField();

        criarButton = new JButton("Create Traffic File");
        selecionarArquivoButton = new JButton("Select...");
    }

    /**
     * Sets the default values.
     */
    private void setDefaultValues() {
        // Default/example values for each field
        quantNodesField.setText("14");
        taxasField.setText("100E+9, 200E+9, 300E+9, 400E+9, 500E+9");
        proporcoesField.setText("5, 4, 3, 2, 1");
        roField.setText("500");
        incrementoField.setText("200");
        muField.setText("1");

        // Suggest a default path for the file
        String userHome = System.getProperty("user.home");
        caminhoArqField.setText(userHome + File.separator + "traffic");
    }

    /**
     * Executes the setup layout operation.
     */
    private void setupLayout() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Configuration panel
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Traffic Configuration",
            TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Row 0 - File path
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        configPanel.add(new JLabel("Path of the file to be created:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        configPanel.add(caminhoArqField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        configPanel.add(selecionarArquivoButton, gbc);

        // Row 1 - Number of nodes
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        configPanel.add(new JLabel("Number of network nodes:"), gbc);

        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1;
        configPanel.add(quantNodesField, gbc);
        gbc.gridwidth = 1;

        // Row 2 - Rates (String[])
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        configPanel.add(new JLabel("Bit rates (comma-separated):"), gbc);

        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1;
        configPanel.add(taxasField, gbc);
        gbc.gridwidth = 1;

        // Row 3 - Proportions (double[])
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        configPanel.add(new JLabel("Proportions (comma-separated):"), gbc);

        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1;
        configPanel.add(proporcoesField, gbc);
        gbc.gridwidth = 1;

        // Linha 4 - Ro
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        configPanel.add(new JLabel("Ro (Carga inicial - Erlangs):"), gbc);

        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1;
        configPanel.add(roField, gbc);
        gbc.gridwidth = 1;

        // Row 5 - Load increment
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0;
        configPanel.add(new JLabel("Load increment (Erlangs):"), gbc);

        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1;
        configPanel.add(incrementoField, gbc);
        gbc.gridwidth = 1;

        // Linha 6 - Mu
        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 0;
        configPanel.add(new JLabel("Mu (Holding rate):"), gbc);

        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1;
        configPanel.add(muField, gbc);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(criarButton);

        mainPanel.add(configPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    /**
     * Executes the setup listeners operation.
     */
    private void setupListeners() {
        selecionarArquivoButton.addActionListener(new ActionListener() {
            /**
             * Handles the action event.
             * @param e the e.
             */
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Save traffic file");

                // Suggest the file name based on the current field
                String currentPath = caminhoArqField.getText();
                if (!currentPath.isEmpty()) {
                    fileChooser.setSelectedFile(new File(currentPath));
                }

                int userSelection = fileChooser.showSaveDialog(TrafficFileCreatorGUI.this);

                if (userSelection == JFileChooser.APPROVE_OPTION) {
                    File fileToSave = fileChooser.getSelectedFile();
                    caminhoArqField.setText(fileToSave.getAbsolutePath());
                }
            }
        });

        criarButton.addActionListener(new ActionListener() {
            /**
             * Handles the action event.
             * @param e the e.
             */
            @Override
            public void actionPerformed(ActionEvent e) {
                criarArquivoTrafico();
            }
        });

        // Adicionar tooltips para explicar cada campo
        quantNodesField.setToolTipText("Total number of nodes in the topology (e.g., 10)");
        taxasField.setToolTipText("Bit rates (e.g., 100E+9, 200E+9, 300E+9)");
        proporcoesField.setToolTipText("Proportions for each bit rate (e.g., 5, 4, 1)");
        roField.setToolTipText("Carga inicial da network em Erlangs (ex: 500)");
        incrementoField.setToolTipText("Increment value for the network load in Erlangs (e.g., 200)");
        muField.setToolTipText("Circuit holding rate (e.g., 1.0)");
    }

    /**
     * Executes the create traffic file operation.
     */
    private void criarArquivoTrafico() {
        try {
            // Validate and obtain the field values
            String caminhoArq = caminhoArqField.getText().trim();
            if (caminhoArq.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select a path for the file.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int quantNodes = Integer.parseInt(quantNodesField.getText().trim());

            // Process rates (String[])
            String[] taxasArray = taxasField.getText().split(",");
            String[] taxas = new String[taxasArray.length];
            for (int i = 0; i < taxasArray.length; i++) {
                taxas[i] = taxasArray[i].trim();
            }

            // Process proportions (double[])
            String[] proporcoesArray = proporcoesField.getText().split(",");
            double[] proporcoes = new double[proporcoesArray.length];
            for (int i = 0; i < proporcoesArray.length; i++) {
                proporcoes[i] = Double.parseDouble(proporcoesArray[i].trim());
            }

            // Validate whether the number of rates and proportions is the same
            if (taxas.length != proporcoes.length) {
                JOptionPane.showMessageDialog(this,
                    "The number of rates must match the number of proportions.",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            double ro = Double.parseDouble(roField.getText().trim());
            double incremento = Double.parseDouble(incrementoField.getText().trim());
            double mu = Double.parseDouble(muField.getText().trim());

            // Call the original method
            createProportionalTrafficFile(caminhoArq, quantNodes, taxas, proporcoes, ro, incremento, mu);

            JOptionPane.showMessageDialog(this,
                "Traffic file created successfully!\nLocation: " + caminhoArq,
                "Success", JOptionPane.INFORMATION_MESSAGE);

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                "Please check whether all numeric values are correct.",
                "Format Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Error creating file: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    /**
     * Method to create a traffic file
     */
    public static void createProportionalTrafficFile(String caminhoArq, int quantNodes, String[] taxas, double proporcoes[], double ro, double incremento, double mu) {

        double quantPares = quantNodes * (quantNodes - 1); //Number of node pairs
        double quantTaxas = taxas.length; //Number of bit-rate values

        double sumPropor = 0.0;
        for(int i = 0; i < quantTaxas; i++) {
            sumPropor += proporcoes[i];
        }

        double lambdaPorPar = (ro / quantPares) * mu;
        double lambdaPorTaxaTotal = lambdaPorPar / sumPropor;

        double lambdaPorTaxaPropor[] = new double[(int)quantTaxas];
        for(int i = 0; i < quantTaxas; i++) {
            lambdaPorTaxaPropor[i] = lambdaPorTaxaTotal * proporcoes[i];
        }

        double incPorPar = (incremento / quantPares) * mu;
        double incPorTaxaTotal = incPorPar / sumPropor;

        double incPorTaxaPropor[] = new double[(int)quantTaxas];
        for(int i = 0; i < quantTaxas; i++) {
            incPorTaxaPropor[i] = incPorTaxaTotal * proporcoes[i];
        }

        try {
            java.io.FileWriter fw = new java.io.FileWriter(caminhoArq);
            java.io.BufferedWriter out = new java.io.BufferedWriter(fw);

            StringBuilder sbaux = new StringBuilder();
            sbaux.append("{\"requestGenerators\":[\n");

            int cont = 0;
            for(int i = 1; i <= quantNodes; i++){
                StringBuilder sb = new StringBuilder();
                for(int j = 1; j <= quantNodes; j++){
                    if(i != j){
                        for(int t = 0; t < quantTaxas; t++) {
                            sb.append("{\"source\":\"" + i + "\",\"destination\":\"" + j + "\",\"arrivalRate\":" + lambdaPorTaxaPropor[t] + ",\"arrivalRateIncrease\":" + incPorTaxaPropor[t] + ",\"holdRate\":" + mu + ",\"bitRate\":" + taxas[t] + "}");
                            if(cont < ((quantPares * quantTaxas) - 1)){
                                sb.append(",\n");
                            }else{
                                sb.append("\n");
                            }
                            cont++;
                        }
                    }
                }
                sbaux.append(sb.toString());
            }

            sbaux.append("]}");
            out.append(sbaux.toString());
            //System.out.println(sbaux.toString());

            out.close();
            fw.close();

            System.out.println("Traffic file created.");

        } catch (java.io.IOException e) {
            e.printStackTrace();
            System.out.println("Error creating the traffic file.");
        }
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
                new TrafficFileCreatorGUI().setVisible(true);
            }
        });
    }
}

