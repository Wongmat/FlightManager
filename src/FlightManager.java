import java.awt.GridLayout;
import java.awt.TextField;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

import javax.swing.*;

import java.util.ArrayList;
import java.util.Properties;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * This is a flight manager to support: (1) add a flight (2) delete a flight (by
 * flight_no) (3) print flight information (by flight_no) (4) select a flight
 * (by source, dest, stop_no = 0) (5) select a flight (by source, dest, stop_no
 * = 1)
 * 
 * @author comp1160/2016
 */

public class FlightManager {

	Scanner in = null;
	Connection conn = null;
	// Database Host
	final String databaseHost = "orasrv1.comp.hkbu.edu.hk";
	// Database Port
	final int databasePort = 1521;
	// Database name
	final String database = "pdborcl.orasrv1.comp.hkbu.edu.hk";
	final String proxyHost = "faith.comp.hkbu.edu.hk";
	final int proxyPort = 22;
	final String forwardHost = "localhost";
	int forwardPort;
	Session proxySession = null;
	boolean noException = true;

	// JDBC connecting host
	String jdbcHost;
	// JDBC connecting port
	int jdbcPort;

	String[] options = { // if you want to add an option, append to the end of
							// this array
			"add a flight", "print flight information (by flight_no)", "delete a flight (by flight_no)",
			"select or book a flight", "cancel a booking",
			"exit" };

	/**
	 * Get YES or NO. Do not change this function.
	 * 
	 * @return boolean
	 */
	boolean getYESorNO(String message) {
		JPanel panel = new JPanel();
		panel.add(new JLabel(message));
		JOptionPane pane = new JOptionPane(panel, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION);
		JDialog dialog = pane.createDialog(null, "Question");
		dialog.setVisible(true);
		boolean result = JOptionPane.YES_OPTION == (int) pane.getValue();
		dialog.dispose();
		return result;
	}

	/**
	 * Get username & password. Do not change this function.
	 * 
	 * @return username & password
	 */
	String[] getUsernamePassword(String title) {
		JPanel panel = new JPanel();
		final TextField usernameField = new TextField();
		final JPasswordField passwordField = new JPasswordField();
		panel.setLayout(new GridLayout(2, 2));
		panel.add(new JLabel("Username"));
		panel.add(usernameField);
		panel.add(new JLabel("Password"));
		panel.add(passwordField);
		JOptionPane pane = new JOptionPane(panel, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION) {
			private static final long serialVersionUID = 1L;

			@Override
			public void selectInitialValue() {
				usernameField.requestFocusInWindow();
			}
		};
		JDialog dialog = pane.createDialog(null, title);
		dialog.setVisible(true);
		dialog.dispose();
		return new String[] { usernameField.getText(), new String(passwordField.getPassword()) };
	}

	/**
	 * Login the proxy. Do not change this function.
	 * 
	 * @return boolean
	 */
	public boolean loginProxy() {
		if (getYESorNO("Using ssh tunnel or not?")) { // if using ssh tunnel
			String[] namePwd = getUsernamePassword("Login cs lab computer");
			String sshUser = namePwd[0];
			String sshPwd = namePwd[1];
			try {
				proxySession = new JSch().getSession(sshUser, proxyHost, proxyPort);
				proxySession.setPassword(sshPwd);
				Properties config = new Properties();
				config.put("StrictHostKeyChecking", "no");
				proxySession.setConfig(config);
				proxySession.connect();
				proxySession.setPortForwardingL(forwardHost, 0, databaseHost, databasePort);
				forwardPort = Integer.parseInt(proxySession.getPortForwardingL()[0].split(":")[0]);
			} catch (JSchException e) {
				e.printStackTrace();
				return false;
			}
			jdbcHost = forwardHost;
			jdbcPort = forwardPort;
		} else {
			jdbcHost = databaseHost;
			jdbcPort = databasePort;
		}
		return true;
	}

	/**
	 * Login the oracle system. Do not change this function.
	 * 
	 * @return boolean
	 */
	public boolean loginDB() {
		String[] namePwd = getUsernamePassword("Login sqlplus");
		String username = namePwd[0];
		String password = namePwd[1];
		String URL = "jdbc:oracle:thin:@" + jdbcHost + ":" + jdbcPort + "/" + database;

		try {
			System.out.println("Logging " + URL + " ...");
			conn = DriverManager.getConnection(URL, username, password);
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Show the options. If you want to add one more option, put into the
	 * options array above.
	 */
	public void showOptions() {
		System.out.println("Please choose following option:");
		for (int i = 0; i < options.length; ++i) {
			System.out.println("(" + (i + 1) + ") " + options[i]);
		}
	}

	/**
	 * Run the manager
	 */
	public void run() {
		while (noException) {
			showOptions();
			String line = in.nextLine();
			if (line.equalsIgnoreCase("exit"))
				return;
			int choice = -1;
			try {
				choice = Integer.parseInt(line);
			} catch (Exception e) {
				System.out.println("This option is not available");
				continue;
			}
			if (!(choice >= 1 && choice <= options.length)) {
				System.out.println("This option is not available");
				continue;
			}
			if (options[choice - 1].equals("add a flight")) {
				addFlight();
			} else if (options[choice - 1].equals("delete a flight (by flight_no)")) {
				deleteFlight();
			} else if (options[choice - 1].equals("print flight information (by flight_no)")) {
				printFlightByNo();
			} else if (options[choice - 1].equals("select or book a flight")) {
				selectFlight();
			} else if (options[choice - 1].equals("cancel a booking")) {
				cancelBooking();
			} else if (options[choice - 1].equals("exit")) {
				break;
			}
		}
	}

	/**
	 * Print out the infomation of a flight given a flight_no
	 * 
	 * @param flight_no
	 */
	private void printFlightInfo(String flight_no) {
		try {
			Statement stm = conn.createStatement();
			String sql = "SELECT * FROM FLIGHTS WHERE Flight_no = '" + flight_no + "'";
			ResultSet rs = stm.executeQuery(sql);
			if (!rs.next())
				return;
			String[] heads = { "Flight_no", "Depart_Time", "Arrive_Time", "Fare", "Seat Limit", "Source", "Dest" };
			for (int i = 0; i < 7; ++i) { // flight table 6 attributes
				try {
					System.out.println(heads[i] + " : " + rs.getString(i + 1)); // attribute
																				// id
																				// starts
																				// with
																				// 1
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		} catch (SQLException e1) {
			e1.printStackTrace();
			noException = false;
		}
	}

	/**
	 * List all flights in the database.
	 */
	private void listAllFlights() {
		System.out.println("All flights in the database now:");
		try {
			Statement stm = conn.createStatement();
			String sql = "SELECT Flight_no FROM FLIGHTS";
			ResultSet rs = stm.executeQuery(sql);

			int resultCount = 0;
			while (rs.next()) {
				System.out.println(rs.getString(1));
				++resultCount;
			}
			System.out.println("Total " + resultCount + " flight(s).");
			rs.close();
			stm.close();
		} catch (SQLException e) {
			e.printStackTrace();
			noException = false;
		}
	}

	/**
	 * Select out a flight according to the flight_no.
	 */
	private void printFlightByNo() {
		listAllFlights();
		System.out.println("Please input the flight_no to print info:");
		String line = in.nextLine();
		line = line.trim();
		if (line.equalsIgnoreCase("exit"))
			return;

		printFlightInfo(line);
	}
	
	/**
	 * Pulls the fare for the flight_no provided from the sql database
	 * @param flight_no identifier for sql database
	 * @return amount of the fare for flight_no provided
	 */
	private int calculateFare(String flight_no) {
		try {
			Statement stm = conn.createStatement();
			String sql = "SELECT fare FROM FLIGHTS WHERE Flight_no = '" + flight_no + "'";
			ResultSet rs = stm.executeQuery(sql);
			if (!rs.next())
				return -1;
			
			return Integer.parseInt(rs.getString(1));
		} catch (SQLException e1) {
			e1.printStackTrace();
			noException = false;
			return -1;
		}
	}
	
	/**
	 * Asks user where they would like to go and proceeds to see if they would like to book
	 * one of the possible flights to the location.
	 */
	private void selectFlight() {
		System.out.println("Please input source, dest, max connections, max travel hours:");

		String line = in.nextLine();

		if (line.equalsIgnoreCase("exit"))
			return;

		String[] values = line.split(",");
		for (int i = 0; i < values.length; ++i)
			values[i] = values[i].trim();
		
		String str = ""; // Used to store possible options to display at the end
		int resultCount = 0; // Tracks total number of options
		
		// Used to pass information into Booking if customer books one of the flights
		ArrayList<ArrayList<String>> posFlights = new ArrayList<ArrayList<String>>();
		ArrayList<Integer> fares = new ArrayList<Integer>();

		try {
			/**
			 * Create the statement and sql
			 */
			Statement stm = conn.createStatement();
			
			if(Integer.parseInt(values[2]) >= 0) {
				String sql = "SELECT Flight_No FROM FLIGHTS "
						+ "WHERE SOURCE = '" + values[0] + "'" + " AND "
						+ "DEST = '" + values[1] + "' AND "
						+ "arrive_time - depart_time <= " + (Integer.parseInt(values[3]) / 24.0);
				
				ResultSet rs = stm.executeQuery(sql);
				
				while (rs.next()) {
					resultCount++;
					int fare = calculateFare(rs.getString(1));
					str += "(" + resultCount + ") " + rs.getString(1) + " fare: " + fare + "\n";
					ArrayList<String> arr = new ArrayList<String>();
					arr.add(rs.getString(1));
					posFlights.add(arr);
					fares.add(fare);
				}
			}
			if(Integer.parseInt(values[2]) >= 1) {
				String sql = "SELECT F1.Flight_No, F2.Flight_No FROM FLIGHTS F1, FLIGHTS F2 "
						+ "WHERE F1.source = '" + values[0] + "'" + " AND "
						+ "F1.dest = F2.source AND "
						+ "F2.dest = '" + values[1] + "' AND "
						+ "F1.arrive_time < F2.depart_time AND "
						+ "F2.arrive_time - F1.depart_time <= " + (Integer.parseInt(values[3]) / 24.0);
				
				ResultSet rs = stm.executeQuery(sql);
				
				while (rs.next()) {
					resultCount++;
					int fare = (int)((calculateFare(rs.getString(1)) + calculateFare(rs.getString(2)) * 0.9));
					str += "(" + resultCount + ") " 
							+ rs.getString(1) + "->"
							+ rs.getString(2) 
							+ " fare: " + fare + "\n";
					ArrayList<String> arr = new ArrayList<String>();
					arr.add(rs.getString(1));
					arr.add(rs.getString(2));
					posFlights.add(arr);
					fares.add(fare);
				}
			}

			if(Integer.parseInt(values[2]) >= 2) {
				String sql = "SELECT F1.Flight_No, F2.Flight_No, F3.Flight_No "
						+ "FROM FLIGHTS F1, FLIGHTS F2, FLIGHTS F3 "
						+ "WHERE F1.source = '" + values[0] + "'" + " AND "
						+ "F1.dest = F2.source AND "
						+ "F1.arrive_time < F2.depart_time AND "
						+ "F2.dest = F3.source AND "
						+ "F3.dest = '" + values[1] +"' AND "
						+ "F2.arrive_time < F3.depart_time AND "
						+ "F3.arrive_time - F1.depart_time <= " + (Integer.parseInt(values[3]) / 24.0);
				
				ResultSet rs = stm.executeQuery(sql);
				
				while (rs.next()) {
					resultCount++;
					int fare = (int)(((calculateFare(rs.getString(1)) + calculateFare(rs.getString(2)) + calculateFare(rs.getString(3))) * 0.75));
					str += "(" + resultCount + ") " 
							+ rs.getString(1) + "->"
							+ rs.getString(2) + "->"
							+ rs.getString(3) 
							+ " fare: " + fare + "\n";
					ArrayList<String> arr = new ArrayList<String>();
					arr.add(rs.getString(1));
					arr.add(rs.getString(2));
					arr.add(rs.getString(3));
					posFlights.add(arr);
					fares.add(fare);
				}
			}
			System.out.println("Total " + resultCount + " choice(s).");
			System.out.println(str);
			stm.close();
		} catch (SQLException e) {
			e.printStackTrace();
			noException = false;
		}
		
		if(resultCount == 0)
			return;
		
		
		// Asks customer if they would like to book one of the flights
		System.out.println("Input option number to book, cust_id or type exit:");
		line = in.nextLine();
		line = line.trim();
		if (line.equalsIgnoreCase("exit"))
			return;

		String[] val = line.split(",");
		if(val.length != 2) {
			System.out.println("Incorrect number of inputs!!!");
			return;
		}
		for (int i = 0; i < val.length; ++i)
			val[i] = val[i].trim();

		int chosen = Integer.parseInt(val[0]) - 1;
		
		Booking(val[1], posFlights.get(chosen), fares.get(chosen));
	}

	
	/**
	 * Checks the flights have seats open and proceeds to book the flight if available
	 * @param cust_id identifier for customer booking
	 * @param flights the flights the customer would like to book
	 * @param fare the total cost of the flights
	 * @return
	 */
	public boolean Booking(String cust_id, ArrayList<String> flights, int fare) {
		int bookingNum = 0;
		try {
			Statement stm = conn.createStatement();
			
			// Checks if all flights have open seats
			for (int i = 0; i < flights.size(); i++) {
				String sql = "SELECT SEAT_LIMIT FROM FLIGHTS WHERE FLIGHT_NO = '" + flights.get(i) + "'";
				ResultSet rs = stm.executeQuery(sql);
				while (rs.next()) {
					if (Integer.parseInt(rs.getString(1)) == 0) {
						System.out.println("Failed to book flight. Seat limit for a flight is full.");
						return false;
					}
				}
			}
			
			// Creates a new unique booking_id for new booking
			String sql = "SELECT MAX(BOOKING_ID) FROM BOOKINGS";
			ResultSet rs = stm.executeQuery(sql);
			
			while(rs.next()) {
				if(rs.getString(1) == null) {
					bookingNum = 1;
				} else {
					bookingNum = Integer.parseInt(rs.getString(1)) + 1;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			System.out.println("fail to delete flight ");
		}
		
		
		//create booking:
		try {
			Statement stm = conn.createStatement();

			// First inserts a new booking
			String sql = "INSERT INTO BOOKINGS VALUES(" + bookingNum + ", '" + cust_id + "', " + fare + ")";
			stm.executeUpdate(sql);
			
			// Loops through flights being booked and adds them to table
			for (int i = 0; i < flights.size(); i++) {
				sql = "INSERT INTO BOOKING_HAS_FLIGHTS VALUES(" + bookingNum  + ", '" + flights.get(i) + "')";
				// SQL Trigger auto updates the seat_limit for flights
				stm.executeUpdate(sql);
			}
			
			System.out.println("Suceeded to book, booking_id: " + bookingNum);
			stm.close();
		
		
			
		} catch (SQLException e) {
			e.printStackTrace();
			System.out.println("fail to delete flight ");
			noException = false;
		}
		return true;
	}
	
	/**
	 * Cancels an existing booking.
	 */
	private void cancelBooking() {
		try {
			Statement stm = conn.createStatement();

			// Finds possible bookings to be cancelled
			String sql = "SELECT Booking_id FROM BOOKINGS";
			ResultSet rs = stm.executeQuery(sql);
			System.out.println("\nBooking_ids:");
			while(rs.next()) {
				System.out.println(rs.getString(1));
			}
			
			stm.close();
		} catch (SQLException e) {
			e.printStackTrace();
			System.out.println("fail to cancel booking");
			noException = false;
		}
		
		System.out.println("Input booking_id to cancel or exit:");
		String line = in.nextLine();
		line = line.trim();
		if (line.equalsIgnoreCase("exit"))
			return;
		
		try {
			Statement stm = conn.createStatement();

			String sql = "DELETE FROM BOOKINGS WHERE Booking_id = " + line;
			
			// Prints whether or not a booking was cancelled
			if(stm.executeUpdate(sql) != 0) {
				System.out.println("Booking " + line + " cancelled");
			} else {
				System.out.println("Failed to cancel booking");
			}
			
			stm.close();
		
		
			
		} catch (SQLException e) {
			e.printStackTrace();
			System.out.println("fail to cancel booking");
			noException = false;
		}
	}
	
	/**
	 * Inserts data into database
	 * 
	 * @return
	 */
	private void addFlight() {
		/**
		 * A sample input is: CX109, 2015/03/15/13:00:00, 2015/03/15/19:00:00,
		 * 2000, 4, Beijing, Tokyo
		 */
		System.out.println("Please input the flight_no, depart_time, arrive_time, fare, seat_limit, source, dest:"); // Added seat limit
		String line = in.nextLine();

		if (line.equalsIgnoreCase("exit"))
			return;
		String[] values = line.split(",");

		if (values.length < 7) {
			System.out.println("The value number is expected to be 7");
			return;
		}
		for (int i = 0; i < values.length; ++i)
			values[i] = values[i].trim();

		try {
			Statement stm = conn.createStatement();
			String sql = "INSERT INTO FLIGHTS VALUES(" + "'" + values[0] + "', " + // this
																					// is
																					// flight
																					// no
			"to_date('" + values[1] + "', 'yyyy/mm/dd/hh24:mi:ss'), " + // this
																		// is
																		// depart_time
			"to_date('" + values[2] + "', 'yyyy/mm/dd/hh24:mi:ss'), " + // this
																		// is
																		// arrive_time
			values[3] + ", " + // this is fare
			values[4] + ", " +
				"'" + values[5] + "', " + // this is source
					"'" + values[6] + "'" + // this is dest
					")";
			stm.executeUpdate(sql);
			stm.close();
			System.out.println("Succeeded to add flight " + values[0]);
			printFlightInfo(values[0]);
		} catch (SQLException e) {
			e.printStackTrace();
			System.out.println("fail to add a flight " + line);
			noException = false;
		}
	}

	/**
	 * First deletes all bookings the flight is in. Then deletes the flight
	 */
	public void deleteFlight() {
		listAllFlights();
		System.out.println("Please input the flight_no to delete:");
		String line = in.nextLine();

		if (line.equalsIgnoreCase("exit"))
			return;
		line = line.trim();

		try {
			Statement stm = conn.createStatement();
			
			// Find all bookings that include the flight being deleted
			String sql = "SELECT Booking_id FROM BOOKING_HAS_FLIGHTS WHERE Flight_No = '" + line + "'";
			ResultSet rs = stm.executeQuery(sql);
			int bookingid = -1;
			
			while(rs.next()) {
				if(rs.getString(1) != null) {
					bookingid = Integer.parseInt(rs.getString(1));
				}
			}
				
			
			// Delete the bookings for the flight being deleted if found
			if(bookingid != -1) {
				sql = "DELETE FROM BOOKINGS WHERE Booking_id = " + bookingid;
				stm.executeUpdate(sql);
	
				System.out.println("Booking_id " + bookingid + " deleted.");
			}

			
			// Lastly delete the flight requested
			sql = "DELETE FROM FLIGHTS WHERE FLIGHT_NO = '" + line + "'";

			stm.executeUpdate(sql); // please pay attention that we use
									// executeUpdate to update the database
			
			System.out.println("Succeeded to delete flight " + line);
			
			stm.close();
		} catch (SQLException e) {
			e.printStackTrace();
			System.out.println("fail to delete flight " + line);
			noException = false;
		}
	}

	/**
	 * Close the manager. Do not change this function.
	 */
	public void close() {
		System.out.println("Thanks for using this manager! Bye...");
		try {
			if (conn != null)
				conn.close();
			if (proxySession != null) {
				proxySession.disconnect();
			}
			in.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Constructor of flight manager Do not change this function.
	 */
	public FlightManager() {
		System.out.println("Welcome to use this manager!");
		in = new Scanner(System.in);
	}

	/**
	 * Main function
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		FlightManager manager = new FlightManager();
		if (!manager.loginProxy()) {
			System.out.println("Login proxy failed, please re-examine your username and password!");
			return;
		}
		if (!manager.loginDB()) {
			System.out.println("Login database failed, please re-examine your username and password!");
			return;
		}
		System.out.println("Login succeed!");
		try {
			manager.run();
		} finally {
			manager.close();
		}
	}
}
