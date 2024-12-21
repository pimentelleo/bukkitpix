package io.github.pimentelleo.bukkitpix;

import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import io.github.pimentelleo.bukkitpix.inventory.InventoryManager;
import io.github.pimentelleo.bukkitpix.mercadopago.MercadoPagoAPI;
import io.github.pimentelleo.bukkitpix.qrcode.ImageCreator;

public class OrderManager {
	
	private static Connection conn;
	
	protected static boolean startOrderManager(BukkitPix ap) throws SQLException {
		FileConfiguration cfg = ap.getConfig();
		
		String type = cfg.getString("database.type");
		String autoIncrement = null;
		
		if (type.equalsIgnoreCase("mysql")) {
			String host = cfg.getString("database.host").trim(), user = cfg.getString("database.user").trim(), 
				   pass = cfg.getString("database.pass").trim(), db = cfg.getString("database.db").trim();
			int port = cfg.getInt("database.port");
			autoIncrement = "AUTO_INCREMENT";
			
			String url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?autoReconnect=true&characterEncoding=utf8";
			conn = DriverManager.getConnection(url, user, pass);
		}
		else if (type.equalsIgnoreCase("sqlite")) {
			autoIncrement = "AUTOINCREMENT";
			File flatFile = new File(ap.getDataFolder(), "autopix.db");
			conn = DriverManager.getConnection("jdbc:sqlite:" + flatFile.getAbsolutePath());
		}
		else {
			MSG.sendMessage(Bukkit.getConsoleSender(), "db-invalido");
			return false;
		}
		
		conn.prepareStatement("CREATE TABLE IF NOT EXISTS autopix_orders "
				+ "(id INTEGER PRIMARY KEY " + autoIncrement + ", player VARCHAR(16) NOT NULL,"
				+ "product VARCHAR(16) NOT NULL, price DECIMAL(10, 2) NOT NULL, "
				+ "created TIMESTAMP NOT NULL, qrData VARCHAR(255) NOT NULL, paymentId BIGINT NOT NULL,  isPaid BOOL NOT NULL);").executeUpdate();
		
		conn.prepareStatement("CREATE TABLE IF NOT EXISTS autopix_pendings " 
				+ "(id VARCHAR(32) PRIMARY KEY, player VARCHAR(16) NOT NULL);").executeUpdate();
		
		return true;
	}
	
	public static Order createOrder(Player p, String product, float price, Object[] paymentData) {
		try {

			Long paymentId = Long.valueOf(paymentData[0].toString());
			String qrData = (String) paymentData[1];
			PreparedStatement ps = conn.prepareStatement("INSERT INTO autopix_orders "
					+ "(player, product, price, created, paymentId, qrData, isPaid) VALUES (?, ?, ?, ?, ?, ?, ?);", PreparedStatement.RETURN_GENERATED_KEYS);
			ps.setString(1, p.getName());
			ps.setString(2, product);
			ps.setFloat(3, price);
			ps.setTimestamp(4, Timestamp.from(Instant.now()));
			ps.setLong(5, paymentId);
			ps.setString(6, qrData);
			ps.setBoolean(7, false);
			
			ps.executeUpdate();
			
			Order ord = new Order(p.getName(), product, price, 0L);

			ResultSet generatedKeys = ps.getGeneratedKeys();
			final Integer orderId;
			if (generatedKeys.next()) {
				orderId = generatedKeys.getInt(1);
			} else {
				orderId = null;
			}
			
			// return ord;

			BukkitRunnable task = new BukkitRunnable() {
				@Override
				public void run() {
					Bukkit.getConsoleSender().sendMessage("\u00a7b[BukkitPix] \u00a7aBuscando pagamento: " + paymentId + "da ordem" + ord.getId());
					String result = validateOrder(BukkitPix.getInstance(), p.getName(), orderId);
					Bukkit.getConsoleSender().sendMessage("\u00a7b[BukkitPix] \u00a7aValidateOrder result: " + result);

					if ("success".equals(result) || "alreadyPaid".equals(result)) {
						Bukkit.getConsoleSender().sendMessage("\u00a7b[BukkitPix] \u00a7aPagamento " + paymentId + " validado!");

						this.cancel();
					}
				}
			};
			task.runTaskTimer(BukkitPix.getInstance(), 0L, 100L);
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	public static List<Order> listOrders(String player) {
		List<Order> orders = new ArrayList<>();
		
		try {
			PreparedStatement ps = conn.prepareStatement("SELECT * FROM autopix_orders WHERE player = ?;");
			ps.setString(1, player);
			
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				int id = rs.getInt(1);
				String player_real = rs.getString("player");
				String product = rs.getString("product");
				float price = rs.getFloat("price");
				Timestamp created = rs.getTimestamp("created");
				String transaction = rs.getString("qrData");
				
				Order ord = new Order(player_real, product, price, created.getTime());
				ord.setId(id);
				ord.setTransaction(transaction);
				
				orders.add(ord);
			}
			
			return orders;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return orders;
	}

	public static String validateOrder(BukkitPix ap, String player, Integer orderId) {
		try {
			PreparedStatement ps = conn.prepareStatement("SELECT * FROM autopix_orders WHERE player = ? AND id = ?;");
			ps.setString(1, player);
			ps.setInt(2, orderId);
			
			ResultSet rs = ps.executeQuery();

			if (rs.next()) {
				long paymentId = rs.getLong("paymentId");
				Bukkit.getConsoleSender().sendMessage("\u00a7b[BukkitPix] \u00a7aSQL query: " + paymentId);

				String product = rs.getString("product");
				Boolean isPaid = rs.getBoolean("isPaid");
				Boolean isOrderPaid = MercadoPagoAPI.checkPayment(BukkitPix.getInstance(), player, paymentId);
				Bukkit.getConsoleSender().sendMessage("\u00a7b[BukkitPix] \u00a7aResponse (check payment): " + isOrderPaid);

				if (isPaid == true) {
					Bukkit.getConsoleSender().sendMessage("\u00a7b[BukkitPix] \u00a7aPagamento já validado");
					return "alreadyPaid";
				} else if (isOrderPaid == true && isPaid == false) {
					Bukkit.getConsoleSender().sendMessage("\u00a7b[BukkitPix] \u00a7aStarting player reward" + Bukkit.getPlayer(player));

					// MSG.sendMessage(Bukkit.getPlayer(player), "pix-validado");

					// String executecommand = ap.getConfig().getString("commands.VIP1");
					List<String> commands = ap.getConfig().getStringList("menu.principal.produtos." + product + ".comandos");

					for (String command : commands) {
						Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player));
					}

					PreparedStatement updatePs = conn.prepareStatement("UPDATE autopix_orders SET isPaid = ? WHERE id = ?;");
					updatePs.setBoolean(1, true);
					updatePs.setInt(2, orderId);
					updatePs.executeUpdate();
					InventoryManager.removePaidMap(Bukkit.getPlayer(player));
					return "success";

				} else {
					Bukkit.getConsoleSender().sendMessage("\u00a7b[BukkitPix] \u00a7aPagamento Pendente");
					return "pending";
				}
			}
			
			return null;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	public static List<Order> getOrders(String player) {
		return getOrders(player, 10);
	}
	
	public static List<Order> getOrders(String player, int limit) {
		List<Order> orders = new ArrayList<>();
		
		try {
			PreparedStatement ps = conn.prepareStatement("SELECT * FROM autopix_orders WHERE player = ? " + 
														 "ORDER BY created DESC LIMIT ?;");
			ps.setString(1, player);
			ps.setInt(2, limit);
			
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				int id = rs.getInt(1);
				String player_real = rs.getString("player");
				String product = rs.getString("product");
				float price = rs.getFloat("price");
				Timestamp created = rs.getTimestamp("created");
				String transaction = rs.getString("qrData");
				
				Order ord = new Order(player_real, product, price, created.getTime());
				ord.setId(id);
				ord.setTransaction(transaction);
				
				orders.add(ord);
			}
			
			return orders;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return orders;
	}
	
	public static Order getLastOrder(String player) {
		List<Order> orders = getOrders(player, 1);
		return orders.isEmpty() ? null : orders.get(0);
	}
	
	public static boolean isTransactionValidated(String transactionId) {
		try {
			PreparedStatement ps = conn.prepareStatement("SELECT id FROM autopix_orders WHERE pix = ?;");
			ps.setString(1, transactionId);
			return ps.executeQuery().next();
		} catch (SQLException e) {
			e.printStackTrace();
			return true;
		}
	}

	public static boolean validateOrders(String transactionId) {
		try {
			PreparedStatement ps = conn.prepareStatement("SELECT id FROM autopix_orders WHERE pix = ?;");
			ps.setString(1, transactionId);
			return ps.executeQuery().next();
		} catch (SQLException e) {
			e.printStackTrace();
			return true;
		}
	}
	
	public static boolean setTransaction(Order ord, String transactionId) {
		try {
			PreparedStatement ps = conn.prepareStatement("UPDATE autopix_orders SET pix = ? WHERE id = ?;");
			ps.setString(1, transactionId);
			ps.setInt(2, ord.getId());
			ps.executeUpdate();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	protected static void validatePendings(BukkitPix ap) {
		try {
			PreparedStatement st = conn.prepareStatement("SELECT * FROM autopix_pendings;");
			ResultSet rs = st.executeQuery();
			
			while (rs.next()) {
				String player = rs.getString("player");
				if (Bukkit.getPlayerExact(player) == null) continue;
				
				String id = rs.getString("id");
				
				// Object[] data = MercadoPagoAPI.checkPayment(ap, id);
				// if (data == null) continue;
				
				// String pixId = (String) data[0];
				// double paid = (double) data[1];
				
				for (Order order : getOrders(player)) {
					OrderProduct op = InventoryManager.getProductByOrder(order);
					if (op == null) continue;
					if (order.isValidated()) continue;
					// if (Math.abs(order.getPrice() - paid) > 0.001) continue;
					
					// if (!(OrderManager.setTransaction(order, pixId))) continue;
					
					deletePending(id);
					
					new BukkitRunnable() {
						@SuppressWarnings("deprecation")
						@Override
						public void run() {
							Player p = Bukkit.getPlayerExact(player);
							
							try {
								String mapMaterial = BukkitPix.getRunningVersion() >= 1013 ? "FILLED_MAP" : "MAP";
								if (p.getItemInHand().getType().name() == mapMaterial) {
									BufferedImage gif = ImageIO.read(BukkitPix.getInstance().getResource("success.png"));
									ImageCreator.generateMap(gif, p, null);
								}
							} catch (Exception e) {}
							
							if (ap.getConfig().getBoolean("som.ativar")) {
								try {
									Sound sound = Sound.valueOf(
											ap.getConfig().getString("som.efeito").toUpperCase());
									p.playSound(p.getLocation(), sound, 1, 1);
								} catch (Exception e) {}
							}
							
							for (String cmd : op.getCommands())
								Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
										cmd.replace("{player}", p.getName()).replace('&', '\u00a7'));
						}
					}.runTask(ap);
					break;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	private static void deletePending(String id) throws SQLException {
		PreparedStatement st = conn.prepareStatement("DELETE FROM autopix_pendings WHERE id = ?;");
		st.setString(1, id);
		st.executeUpdate();
	}
	
	public static List<DonorInfo> getTopDonors(){
		ArrayList<DonorInfo> topDonors = new ArrayList<>();
		
		try {
			PreparedStatement st = conn.prepareStatement(
					"SELECT DISTINCT player AS donor, "
					+ "(SELECT SUM(price) FROM autopix_orders WHERE player = donor AND pix != 'NULL') "
					+ "AS total FROM autopix_orders WHERE pix != 'NULL' ORDER BY total DESC LIMIT 5;");
			ResultSet rs = st.executeQuery();
			while (rs.next()) {
				topDonors.add(new DonorInfo(rs.getString("donor"), rs.getFloat("total")));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return topDonors;
	}

}
