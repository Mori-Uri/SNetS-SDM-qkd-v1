package util.tools;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

/**
 * Represents the TrafficFileCreator component.
 */
public class TrafficFileCreator {

	/**
	 * Runs the application entry point.
	 * @param args the args.
	 */
	public static void main(String[] args) throws Exception {

		Scanner sc = new Scanner(System.in);
        System.out.println("=== Create the traffic file ===");

        while (true) {
            System.out.println("\nEscolha a topologia:");
            System.out.println("1) NSFNet");
            System.out.println("2) Cost239");
            System.out.println("3) USA");
            System.out.println("4) Japan");
            System.out.println("5) N8E13");
            System.out.println("0) Sair");
            System.out.print("Option: ");

            int opcao = sc.nextInt();

            if (opcao == 0) {
                System.out.println("Encerrando o programa...");
                break;
            }

            switch (opcao) {
                case 1:
                    System.out.println("Topologia NSFNet");
                    createNSFNetTrafficFile();
                    esperarTecla(sc);
                    break;

                case 2:
                	System.out.println("Topologia Cost239");
                	createCost239TrafficFile();
                    esperarTecla(sc);
                    break;

                case 3:
                	System.out.println("Topologia USA");
                	createUSATrafficFile();
                    esperarTecla(sc);
                    break;

                case 4:
                	System.out.println("Topologia Japan");
                	createJapanTrafficFile();
                    esperarTecla(sc);
                    break;

                case 5:
                	System.out.println("Topologia N8E13");
                	createN8E13TrafficFile();
                    esperarTecla(sc);
                    break;

                default:
                    System.out.println("Invalid option!");
            }
        }

        sc.close();
	}

	/**
     * Waits for the user to press Enter to continue.
     */
    public static void esperarTecla(Scanner sc) {
        System.out.print("\nPress ENTER to continue...");
        sc.nextLine(); // consome o Enter pendente anterior
        sc.nextLine(); // Wait for the next Enter key press
    }

	/**
	 * Creates the nsf net traffic file.
	 */
	public static void createNSFNetTrafficFile() {
		String caminhoArq = "C:\\Users\\alexa\\OneDrive\\Desktop\\traffic_NSFNet";

		int quantNodes = 14; //Number of nodes in the topology

		//String[] taxas = {"100E+9", "200E+9", "300E+9", "400E+9", "500E+9", "600E+9"};
		//String[] taxas = {"500E+9", "600E+9", "700E+9", "800E+9", "900E+9", "1000E+9"};
		//String[] taxas = {"100E+9", "200E+9", "400E+9", "600E+9", "800E+9", "1000E+9"};
		//String[] taxas = {"200E+9", "400E+9", "600E+9", "800E+9", "1000E+9"};
		String[] taxas = {"100E+9", "200E+9", "300E+9", "400E+9", "500E+9"};
		//String[] taxas = {"100E+9", "150E+9", "200E+9", "250E+9", "300E+9", "350E+9", "400E+9"};
		//String[] taxas = {"10E+9", "40E+9", "80E+9", "100E+9", "160E+9", "200E+9", "400E+9"};

		//double proportions[] = {6.0, 5.0, 4.0, 3.0, 2.0, 1.0};
		double proporcoes[] = {5.0, 4.0, 3.0, 2.0, 1.0};
		//double proportions[] = {7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0};

		double ro = 2000.0; // Erlangs, initial load
		double incremento = 500.0; // Erlangs, load increment
		double mu = 1.0;

		createProportionalTrafficFile(caminhoArq, quantNodes, taxas, proporcoes, ro, incremento, mu);
	}

	/**
	 * Creates the cost239 traffic file.
	 */
	public static void createCost239TrafficFile() {
		String caminhoArq = "C:\\Users\\alexa\\OneDrive\\Desktop\\traffic_Cost239";

		int quantNodes = 11; //Number of nodes in the topology

		//String[] taxas = {"100E+9", "200E+9", "300E+9", "400E+9", "500E+9", "600E+9"};
		String[] taxas = {"100E+9", "200E+9", "300E+9", "400E+9", "500E+9"};
		//String[] taxas = {"100E+9", "150E+9", "200E+9", "250E+9", "300E+9", "350E+9", "400E+9"};
		//String[] taxas = {"10E+9", "40E+9", "80E+9", "100E+9", "160E+9", "200E+9", "400E+9"};

		//double proportions[] = {6.0, 5.0, 4.0, 3.0, 2.0, 1.0};
		double proporcoes[] = {5.0, 4.0, 3.0, 2.0, 1.0};
		//double proportions[] = {7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0};

		double ro = 500.0; // Erlangs, initial load
		double incremento = 200.0; // Erlangs, load increment
		double mu = 1.0;

		createProportionalTrafficFile(caminhoArq, quantNodes, taxas, proporcoes, ro, incremento, mu);
	}

	/**
	 * Creates the japan traffic file.
	 */
	public static void createJapanTrafficFile() {
		String caminhoArq = "C:\\Users\\Alexandre Cardoso\\eclipse-workspace\\SNetS_SDM\\simulations\\JPN12_KSP-RC-RF\\traffic";

		int quantNodes = 12; //Number of nodes in the topology

		String[] taxas = {"100E+9", "200E+9", "300E+9", "400E+9", "500E+9"};
		//String[] taxas = {"100E+9", "150E+9", "200E+9", "250E+9", "300E+9", "350E+9", "400E+9"};
		//String[] taxas = {"10E+9", "40E+9", "80E+9", "100E+9", "160E+9", "200E+9", "400E+9"};

		double proporcoes[] = {5.0, 4.0, 3.0, 2.0, 1.0};
		//double proportions[] = {7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0};

		double ro = 500.0; // Erlangs, initial load
		double incremento = 250.0; // Erlangs, load increment
		double mu = 1.0;

		createProportionalTrafficFile(caminhoArq, quantNodes, taxas, proporcoes, ro, incremento, mu);
	}

	/**
	 * Creates the usa traffic file.
	 */
	public static void createUSATrafficFile() {
		String caminhoArq = "C:\\Users\\Alexandre Cardoso\\eclipse-workspace\\SNetS_SDM\\simulations\\USA_KSP-AIDEN_c7\\traffic";

		int quantNodes = 24; //Number of nodes in the topology

		String[] taxas = {"100E+9", "200E+9", "300E+9", "400E+9", "500E+9"};
		//String[] taxas = {"100E+9", "150E+9", "200E+9", "250E+9", "300E+9", "350E+9", "400E+9"};
		//String[] taxas = {"10E+9", "40E+9", "80E+9", "100E+9", "160E+9", "200E+9", "400E+9"};

		double proporcoes[] = {5.0, 4.0, 3.0, 2.0, 1.0};
		//double proportions[] = {7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0};

		double ro = 500.0; // Erlangs, initial load
		double incremento = 250.0; // Erlangs, load increment
		double mu = 1.0;

		createProportionalTrafficFile(caminhoArq, quantNodes, taxas, proporcoes, ro, incremento, mu);
	}

	/**
	 * Creates the n8 e13 traffic file.
	 */
	public static void createN8E13TrafficFile() {
		String caminhoArq = "C:\\Users\\alexa\\OneDrive\\Desktop\\traffic_N8E13";

		int quantNodes = 8; //Number of nodes in the topology

		String[] taxas = {"100E+9", "200E+9", "300E+9", "400E+9", "500E+9"};

		double proporcoes[] = {5.0, 4.0, 3.0, 2.0, 1.0};

		double ro = 300.0; // Erlangs, initial load
		double incremento = 100.0; // Erlangs, load increment
		double mu = 1.0;

		createProportionalTrafficFile(caminhoArq, quantNodes, taxas, proporcoes, ro, incremento, mu);
	}

	/**
	 * This method creates the traffic file for the given topology according to the passed parameters.
	 *
	 * @param caminhoArq String
	 * @param quantNodes int
	 * @param taxas String[]
	 * @param proporcoes double
	 * @param ro double
	 * @param incremento double
	 * @param mu double
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
			FileWriter fw = new FileWriter(caminhoArq);
			BufferedWriter out = new BufferedWriter(fw);

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

		} catch (IOException e) {
			e.printStackTrace();

			System.out.println("Error creating the traffic file.");
		}
	}

}
