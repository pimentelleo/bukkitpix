package io.github.pimentelleo.bukkitpix.commands;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.github.pimentelleo.bukkitpix.BukkitPix;
import io.github.pimentelleo.bukkitpix.DonorInfo;
import io.github.pimentelleo.bukkitpix.MSG;
import io.github.pimentelleo.bukkitpix.Order;
import io.github.pimentelleo.bukkitpix.OrderManager;
import io.github.pimentelleo.bukkitpix.TimeManager;
import io.github.pimentelleo.bukkitpix.inventory.InventoryManager;
import io.github.pimentelleo.bukkitpix.mercadopago.MPValidator;
import io.github.pimentelleo.bukkitpix.mercadopago.MercadoPagoAPI;

public class AutoPixCommand implements CommandExecutor {
	
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm");
	BukkitPix ap;

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String command, String[] args) {
		if (args.length >= 1) {
			/* Checando os subcommands */
			if (args[0].equalsIgnoreCase("info")) {
				if (!(sender instanceof Player)) {
					MSG.sendMessage(sender, "in-game");
					return false;
				}
				InventoryManager.openInfo((Player) sender);
				return true;
			} else if (args[0].equalsIgnoreCase("lista")) {
				if (args.length == 1) {
					if (!(sender instanceof Player)) {
						MSG.sendMessage(sender, "ajuda-lista");
						return false;
					}
					if (!TimeManager.canExecute(BukkitPix.getInstance(), (Player) sender, "list")) {
						return false;
					}
					sendOrderList(sender, sender.getName());
					return true;
				}
				if (!sender.hasPermission("autopix.admin")) {
					MSG.sendMessage(sender, "sem-permissao");
					return false;
				}
				sendOrderList(sender, args[1]);
				return true;
			} else if (args[0].equalsIgnoreCase("validar")) {
				if (!(sender instanceof Player)) {
					MSG.sendMessage(sender, "in-game");
					return false;
				}
				Player player = (Player) sender;
				if (BukkitPix.getInstance().getConfig().getBoolean("automatico.ativado")) {
					return false;
				}
				if (args.length == 1) {
					MSG.sendMessage(player, "ajuda-validar");
					return false;
				}
				String pixId = args[1];
				if (pixId.length() != 32 || pixId.charAt(0) != 'E') {
					MSG.sendMessage(player, "pix-invalido");
					return false;
				}
				if (!TimeManager.canExecute(BukkitPix.getInstance(), player, "validate")) {
					return false;
				}
				if (OrderManager.isTransactionValidated(pixId)) {
					MSG.sendMessage(player, "ja-validado");
					return false;
				}
				MSG.sendMessage(player, "validando");
				new Thread(() -> {
					MPValidator.validateTransaction(BukkitPix.getInstance(), pixId, player);
				}).start();
				return true;
			}
			else if (args[0].equalsIgnoreCase("cancelar")) {
				if (args.length == 1) {
					MSG.sendMessage(sender, "ajuda-cancelar");
					return false;
				}
			}
			else if (args[0].equalsIgnoreCase("check")) {
				String payId = args[1];
				OrderManager.validateOrder(sender.getName(), Integer.parseInt(payId));
			}
			else if (args[0].equalsIgnoreCase("reload")) {
				if (!sender.hasPermission("autopix.admin")) {
					MSG.sendMessage(sender, "sem-permissao");
					return false;
				}
				BukkitPix.reloadPlugin();
				MSG.sendMessage(sender, "reload-executado");
				return false;
			}
			else if (args[0].equalsIgnoreCase("top")) {
				if (sender instanceof Player 
						&& !TimeManager.canExecute(BukkitPix.getInstance(), (Player) sender, "list"))
					return false;
				
				
				new Thread(() -> {
					List<DonorInfo> topDonors = OrderManager.getTopDonors();
					
					if (topDonors.size() == 0) {
						MSG.sendMessage(sender, "sem-doadores");
						return;
					}
					
					StringBuilder message = new StringBuilder();
					message.append(MSG.getMessage("cabecalho-top"));
					message.append("\n");
					
					for (DonorInfo info : topDonors) {
						message.append(MSG.getMessage("corpo-top")
								.replace("{doador}", info.getDonor())
								.replace("{total}", String.format("%.2f", info.getTotal()).replace('.', ','))
								);
						message.append("\n");
					}
					message.append("\n");
					
					sender.sendMessage(message.toString());
					
				}).start();
				return false;
			}
		}

		// Caso nenhum subcomando seja identificado, envia a mensagem de ajuda.
		MSG.sendMessage(sender, "ajuda-autopix");
		return false;
	}
	
	private void sendOrderList(final CommandSender sender, final String player) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				List<Order> orders = OrderManager.listOrders(player);
				if (orders.isEmpty()) {
					MSG.sendMessage(sender, "sem-ordens");
					return;
				}
				StringBuilder message = new StringBuilder();
				message.append(MSG.getMessage("cabecalho"));
				message.append("\n");
				for (Order ord : orders) {
					message.append(MSG.getMessage("corpo")
							.replace("{id}", Integer.toString(ord.getId()))
							.replace("{data}", DATE_FORMAT.format(new Date(ord.getCreated().getTime())))
							.replace("{preco}", String.format("%.2f", ord.getPrice()).replace('.', ','))
							.replace("{produto}", ord.getProduct())
							.replace("{status}", ord.isValidated() ? "\u00a7aAPROVADO" : "\u00a7ePENDENTE"));
					message.append("\n");
				}
				
				message.append("\n");
				sender.sendMessage(message.toString());
			}
		}).start();
	}

}
