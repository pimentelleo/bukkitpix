package io.github.pimentelleo.bukkitpix.mercadopago;

import java.util.UUID;

import javax.print.attribute.standard.Media;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.io.IOException;

import io.github.pimentelleo.bukkitpix.BukkitPix;
import io.github.pimentelleo.bukkitpix.MSG;
import io.github.pimentelleo.bukkitpix.Order;
import io.github.pimentelleo.bukkitpix.OrderManager;
import io.github.pimentelleo.bukkitpix.OrderProduct;

public class MercadoPagoAPI {

	private static final String API_URL = "https://api.mercadopago.com/v1/payments";

	public static Object[] createPixPayment(BukkitPix ap, Player p, OrderProduct product, float price) {	
		UUID indepotencyKey = UUID.randomUUID();

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
						"unit_price": %s,
						"type": "electronics",
						"warranty": false,
						"category_descriptor":" {
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
				"email": "%s@gmail.com"
			},
			"payment_method_id": "pix",
			"transaction_amount": %s
		}
			""".formatted(price, p.getName(), price);
		String formattedBody = jsonBody
			.replaceAll("\\s+", "")        // Remove whitespace
			.replace("\\", "")             // Remove backslashes
			.replace("\"{\"", "{\"")       // Fix object start
			.replace("\"}\"", "\"}")       // Fix object end
			.trim();
		// MSG.sendMessage(p, formattedBody);
		
		OkHttpClient client = new OkHttpClient();
		RequestBody body = RequestBody.create(formattedBody, MediaType.get("application/json; charset=utf-8"));
		Request request = new Request.Builder()
			.url(API_URL)
			.post(body)
			.addHeader("Content-Type", "application/json")
			.addHeader("Accept", "application/json")
			.addHeader("X-Idempotency-Key", indepotencyKey.toString())
			.addHeader("Authorization", "Bearer " + ap.getConfig().getString("token-mp"))
			.build();

		try (Response response = client.newCall(request).execute()) {
			Gson gson = new Gson();
			if (!response.isSuccessful()) {
			Bukkit.getConsoleSender().sendMessage("\u00a7b[BukkitPix] \u00a7cErro ao validar PIX:\n" 
				+ response.code() + " - " + response.body().string()
				+ "\nVerifique se configurou corretamente o token do MP.");
			MSG.sendMessage(p, "erro-validar");
			return null;
			}
			
			JsonObject responseObject = gson.fromJson(response.body().charStream(), JsonObject.class);
			Bukkit.getConsoleSender().sendMessage("\u00a7b[BukkitPix] \u00a7aResponse: " + gson.toJson(responseObject));

			long paymentId = responseObject.get("id").getAsLong();
			Bukkit.getConsoleSender().sendMessage("\u00a7b[BukkitPix] \u00a7aPayment ID: " + gson.toJson(paymentId));

			String qr = responseObject.getAsJsonObject("point_of_interaction").getAsJsonObject("transaction_data").get("qr_code").getAsString();
			Bukkit.getConsoleSender().sendMessage("\u00a7b[BukkitPix] \u00a7aQR DATA: " + gson.toJson(qr));


			// MSG.sendMessage(p, "erro-validar");

			// String responseBody = response.body().string();
			
			// JsonObject json = gson.fromJson(responseBody, JsonObject.class);
			// JsonObject poi = json.getAsJsonObject("point_of_interaction");
			// String qr = poi.getAsJsonObject("transaction_data").get("qr_code").getAsString();
			Object[] paymentData = {
				paymentId,
				qr
			};
			return paymentData;
		} catch (IOException e) {
			e.printStackTrace();
			MSG.sendMessage(p, "erro-validar");
			return null;
		}
	}


	public static Boolean checkPayment(BukkitPix ap, String p, long paymentId) {	

		OkHttpClient client = new OkHttpClient();
		Request request = new Request.Builder()
			.url(API_URL + "/" + paymentId)
			.get()
			.addHeader("Content-Type", "application/json")
			.addHeader("Accept", "application/json")
			.addHeader("Authorization", "Bearer " + ap.getConfig().getString("token-mp"))
			.build();

		try (Response response = client.newCall(request).execute()) {
			if (!response.isSuccessful()) {
			Bukkit.getConsoleSender().sendMessage("\u00a7b[AutoPix] \u00a7cErro ao validar PIX:\n" 
				+ response.code() + " - " + response.body().string()
				+ "\nVerifique se configurou corretamente o token do MP.");
			// MSG.sendMessage(p, "erro-validar");
			return null;
			}

			// Order order = OrderManager.createOrder(p, product.getProduct(), product.getPrice());
			// if (order == null) {
			// 	MSG.sendMessage(p, "erro-validar");
			// 	return null;
			// }

			Gson gson = new Gson();

			JsonObject responseObject = gson.fromJson(response.body().charStream(), JsonObject.class);
			
			Bukkit.getConsoleSender().sendMessage("\u00a7b[BukkitPix] \u00a7aResponse (check payment): " + gson.toJson(responseObject));

			String status = responseObject.get("status").getAsString();
			Bukkit.getConsoleSender().sendMessage("\u00a7b[BukkitPix] \u00a7aResponse (payment status): " + status);
			if (status.trim().equals("approved")) {
				Bukkit.getConsoleSender().sendMessage("\u00a7b[BukkitPix] \u00a7aPayment approved: " + status);

				// order.setTransaction(responseObject.get("id").getAsString());
				// OrderManager.updateOrder(order);
				// MSG.sendMessage(p, "pix-validado");
				return true;
			} else {
				Bukkit.getConsoleSender().sendMessage("\u00a7b[BukkitPix] \u00a7aPayment pending: " + status);
				return false;
			}
		} catch (IOException e) {
			e.printStackTrace();
			// MSG.sendMessage(p, "erro-validar");
			return null;
		}
	}

	// public static Object[] getPayment(BukkitPix ap, String id) {
	// 	String responseMP = "";
	// 	try {
	// 		URL url = new URL(API_URL + "/" + id);
	// 		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

	// 		connection.setRequestMethod("GET");
	// 		connection.setRequestProperty("Accept", "application/json");
	// 		connection.setRequestProperty("Authorization", "Bearer " + ap.getConfig().getString("token-mp"));

	// 		int statusCode = connection.getResponseCode();
	// 		if (statusCode != 200) {
	// 			return null;
	// 		}
	// 		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
	// 		String line;
	// 		StringBuilder response = new StringBuilder();

	// 		while ((line = reader.readLine()) != null) {
	// 			response.append(line);
	// 		}
	// 		reader.close();

	// 		responseMP = response.toString();
	// 		Gson gson = new Gson();

	// 		JsonObject json = (JSONObject) new JSONParser().parse(response.toString());
	// 		JSONObject json = new JSONObject(new JSONTokener(response.toString()));
	// 		Gson gson = new Gson();
	// 		JsonObject json = gson.fromJson(response.toString(), JsonObject.class);
	// 		String status = json.get("status").getAsString();
			
	// 		JSONObject details = json.getJSONObject("transaction_details");
			
	// 		String pixId = details.getString("transaction_id").substring(3);
	// 		double paid = details.getDouble("total_paid_amount");
	// 		return new Object[] { pixId, paid };

	// 	} catch (Exception e) {
	// 		e.printStackTrace();
	// 		Bukkit.getConsoleSender().sendMessage("\u00a7b========== \u00a7aDEBUG \u00a7b==========");
	// 		Bukkit.getConsoleSender().sendMessage("\u00a7aPagamento: \u00a7f" + id);
	// 		Bukkit.getConsoleSender().sendMessage("\u00a7aRetorno MP:");
	// 		Bukkit.getConsoleSender().sendMessage(responseMP.toString());
	// 		Bukkit.getConsoleSender().sendMessage("\u00a7b================================================");
	// 	}
	// 	return null;
	// }

}
