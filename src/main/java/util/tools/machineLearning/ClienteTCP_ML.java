package util.tools.machineLearning;

import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.rmi.UnknownHostException;
import java.util.Scanner;

/**
 * This class is the client that connects to the server
 */
public class ClienteTCP_ML {

	private final static String server_host = "localhost";
	private final static int server_port = 7766;

	/**
	 * This method sends a message to a server and receives a response message.
	 *
	 * @param message String
	 * @return String
	 */
	public static String sendAndReceiveMessage(String message) {
		String responseMessage = "";

		try {
			//Create a socket
			Socket cliente = new Socket(server_host, server_port);

			PrintStream output = new PrintStream(cliente.getOutputStream());
			output.println(message); //Sending a message

			Scanner input = new Scanner(cliente.getInputStream());
			responseMessage = input.nextLine(); //Receiving a message

			//Close the connection
			input.close();
			output.close();
			cliente.close();

		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return responseMessage;
	}

}
