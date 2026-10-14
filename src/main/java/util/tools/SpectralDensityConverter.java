package util.tools;

import java.util.Scanner;

/**
 * Represents the SpectralDensityConverter component.
 */
public class SpectralDensityConverter {

	/**
     * Converts power from dBm to watts (W).
     * Formula: P(W) = 10^(P_dBm/10) / 1000
     */
    public static double dbmToWatts(double pdBm) {
        return Math.pow(10.0, pdBm / 10.0) / 1000.0;
    }

    /**
     * Converts power from watts (W) to dBm.
     * Inverse formula: P_dBm = 10 * log10(P(W) * 1000)
     */
    public static double wattsToDbm(double watts) {
        return 10.0 * Math.log10(watts * 1000.0);
    }

    /**
     * Converts frequency from GHz to Hz.
     */
    public static double ghzToHz(double freqGHz) {
        return freqGHz * 1e9;
    }

    /**
     * Converts frequency from Hz to GHz.
     */
    public static double hzToGHz(double freqHz) {
        return freqHz / 1e9;
    }

    /**
     * Converts dBm/GHz to W/Hz.
     * General formula:
     * P(W/Hz) = (10^(P_dBm/10) / 1000) / (freqGHz * 1e9)
     */
    public static double convertDbmPerGHzToWPerHz(double xDbmPerGHz, double freqGHz) {
        double powerW = dbmToWatts(xDbmPerGHz);
        double freqHz = ghzToHz(freqGHz);
        return powerW / freqHz;
    }

    /**
     * Converts W/Hz to dBm/GHz.
     * Inverse formula:
     * P_dBm/GHz = 10 * log10(P(W/Hz) * 1000 * (freqGHz * 1e9))
     */
    public static double convertWPerHzToDbmPerGHz(double wPerHz, double freqGHz) {
        double freqHz = ghzToHz(freqGHz);
        double powerW = wPerHz * freqHz;
        return wattsToDbm(powerW);
    }

    /**
     * Calcula dBm/GHz a partir de uma power total em dBm e bandwidth em GHz.
     * Formula: dBm/GHz = dBm_total - 10 * log10(B_GHz)
     */
    public static double convertDbmToDbmPerGHz(double pdBmTotal, double bandwidthGHz) {
        return pdBmTotal - 10.0 * Math.log10(bandwidthGHz);
    }

    /**
     * Calcula power total em dBm a partir de dBm/GHz e bandwidth em GHz.
     * Inverse formula: dBm_total = dBm/GHz + 10 * log10(B_GHz)
     */
    public static double convertDbmPerGHzToDbm(double pdBmPerGHz, double bandwidthGHz) {
        return pdBmPerGHz + 10.0 * Math.log10(bandwidthGHz);
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
     * Displays the main menu and manages the user interaction.
     */
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("=== Conversor entre dBm, dBm/GHz e W/Hz ===");

        while (true) {
            System.out.println("\nChoose the desired conversion:");
            System.out.println("1) dBm/GHz → W/Hz");
            System.out.println("2) W/Hz → dBm/GHz");
            System.out.println("3) Converter dBm + largura de banda (GHz) → dBm/GHz");
            System.out.println("4) Converter dBm/GHz + largura de banda (GHz) → dBm total");
            System.out.println("0) Sair");
            System.out.print("Option: ");

            int opcao = sc.nextInt();

            if (opcao == 0) {
                System.out.println("Encerrando o programa...");
                break;
            }

            switch (opcao) {
                case 1:
                    System.out.print("Digite o valor em dBm/GHz: ");
                    double dbmPerGHz = sc.nextDouble();
                    System.out.print("Enter the bandwidth in GHz (e.g., 1): ");
                    double freqGHz1 = sc.nextDouble();
                    double resultWPerHz = convertDbmPerGHzToWPerHz(dbmPerGHz, freqGHz1);
                    System.out.printf("Result: %.6e W/Hz%n", resultWPerHz);
                    esperarTecla(sc);
                    break;

                case 2:
                    System.out.print("Digite o valor em W/Hz: ");
                    double wPerHz = sc.nextDouble();
                    System.out.print("Enter the bandwidth in GHz (e.g., 1): ");
                    double freqGHz2 = sc.nextDouble();
                    double resultDbmPerGHz = convertWPerHzToDbmPerGHz(wPerHz, freqGHz2);
                    System.out.printf("Result: %.6f dBm/GHz%n", resultDbmPerGHz);
                    esperarTecla(sc);
                    break;

                case 3:
                    System.out.print("Informe a power total (dBm): ");
                    double pdBm = sc.nextDouble();
                    System.out.print("Informe a largura de banda (GHz): ");
                    double bwGHz1 = sc.nextDouble();
                    double dbmPerGHz1 = convertDbmToDbmPerGHz(pdBm, bwGHz1);
                    System.out.printf("Result: %.6f dBm/GHz%n", dbmPerGHz1);
                    esperarTecla(sc);
                    break;

                case 4:
                    System.out.print("Informe o valor em dBm/GHz: ");
                    double dbmPerGHz2 = sc.nextDouble();
                    System.out.print("Informe a largura de banda (GHz): ");
                    double bwGHz2 = sc.nextDouble();
                    double dbmTotal = convertDbmPerGHzToDbm(dbmPerGHz2, bwGHz2);
                    System.out.printf("Resultado: %.6f dBm total%n", dbmTotal);
                    esperarTecla(sc);
                    break;

                default:
                    System.out.println("Invalid option!");
            }
        }

        sc.close();
    }

}
