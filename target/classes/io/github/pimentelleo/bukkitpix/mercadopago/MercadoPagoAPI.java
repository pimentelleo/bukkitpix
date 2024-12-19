package io.github.pimentelleo.bukkitpix.mercadopago;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.google.gson.Gson;
import java.io.IOException;

import io.github.pimentelleo.bukkitpix.BukkitPix;
import io.github.pimentelleo.bukkitpix.MSG;
import io.github.pimentelleo.bukkitpix.Order;
import io.github.pimentelleo.bukkitpix.OrderManager;
import io.github.pimentelleo.bukkitpix.OrderProduct;

public class MercadoPagoAPI {

	private static final String API_URL = "https://api.mercadopago.com/v1/payments";

	public static String createPixPayment(BukkitPix ap, Player p, OrderProduct product, float price) {	
		String jsonBody = """
		{
			"additional_info": {
				"items": [
					{
						"id": "MLBKKT22",
						"title": "Bukkit Pix",
						"description": "Test for Bukkit usage",
						"picture_url": "nullurl",
						"category_id": "electronics",
						"quantity": 1,
						"unit_price": 2,
						"type": "electronics",
						"event_date": "2023-12-31T09:37:52.000-04:00",
						"warranty": false,
						"category_descriptor": {
							"passenger": {},
							"route": {}
						}
					}
				]
			},
			"binary_mode": false,
			"campaign_id": null,
			"capture": true,
			"coupon_amount": null,
			"description": "Payment for product",
			"differential_pricing_id": null,
			"external_reference": "MP0001",
			"installments": 1,
			"metadata": null,
			"payer": {
				"email": "leonardociberxon@gmail.com"
			},
			"payment_method_id": "pix",
			"transaction_amount": 2
		}
				""";
		
		try {
			URL url = new URL(API_URL);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();

	        connection.setRequestMethod("POST");
	        connection.setDoOutput(true);
	        connection.setRequestProperty("Content-Type", "application/json");
	        connection.setRequestProperty("Accept", "application/json");
	        connection.setRequestProperty("Authorization", "Bearer " + ap.getConfig().getString("token-mp"));
	        try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                outputStream.writeBytes(jsonBody);
                outputStream.flush();
            }
	        
			int statusCode = connection.getResponseCode();
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
	        String line;
	        StringBuilder response = new StringBuilder();

	        while ((line = reader.readLine()) != null) {
	            response.append(line);
	        }
	        reader.close();
			
			if (statusCode != 201) {
				Bukkit.getConsoleSender().sendMessage("\u00a7b[AutoPix] \u00a7cErro ao validar PIX:\n" 
						+ statusCode + " - " + response.toString()
						+ "\nVerifique se configurou corretamente o token do MP.");
				MSG.sendMessage(p, "erro-validar");
				return null;
			}
			
			Order order = OrderManager.createOrder(p, product.getProduct(), product.getPrice());
			if (order == null) {
				MSG.sendMessage(p, "erro-validar");
				return null;
			}
			
			JSONObject json = (JSONObject) new JSONParser().parse(response.toString());
			JSONObject poi = (JSONObject) json.get("point_of_interaction");
			String qr = (String) ((JSONObject) poi.get("transaction_data"))
					        .get("qr_code");
			
			return qr;
			
		} catch (Exception e) {
			e.printStackTrace();
			MSG.sendMessage(p, "erro-validar");
		}
		return null;
	}

	public static Object[] getPayment(BukkitPix ap, String id) {
		String responseMP = "";
		try {
			URL url = new URL(API_URL + "/" + id);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();

			connection.setRequestMethod("GET");
			connection.setRequestProperty("Accept", "application/json");
			connection.setRequestProperty("Authorization", "Bearer " + ap.getConfig().getString("token-mp"));

			int statusCode = connection.getResponseCode();
			if (statusCode != 200) {
				return null;
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String line;
			StringBuilder response = new StringBuilder();

			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
			reader.close();

			responseMP = response.toString();

			JSONObject json = (JSONObject) new JSONParser().parse(response.toString());
			String status = (String) json.get("status");
			if (!(status.equals("approved")))
				return null;

			JSONObject details = (JSONObject) json.get("transaction_details");

			String pixId = ((String) details.get("transaction_id")).substring(3);
			double paid = (double) details.get("total_paid_amount");

			return new Object[] { pixId, paid };

		} catch (Exception e) {
			e.printStackTrace();
			Bukkit.getConsoleSender().sendMessage("\u00a7b========== \u00a7aDEBUG \u00a7b==========");
			Bukkit.getConsoleSender().sendMessage("\u00a7aPagamento: \u00a7f" + id);
			Bukkit.getConsoleSender().sendMessage("\u00a7aRetorno MP:");
			Bukkit.getConsoleSender().sendMessage(responseMP.toString());
			Bukkit.getConsoleSender().sendMessage("\u00a7b================================================");
		}
		return null;
	}

}
