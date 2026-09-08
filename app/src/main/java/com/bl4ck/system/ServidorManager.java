package com.bl4ck.system;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ServidorManager {

    private static final String API_URL = "https://seu-bot.up.railway.app/api"; 
    private Context context;
    private boolean executando = false;

    public ServidorManager(Context context) {
        this.context = context;
    }

    public String getDeviceId() {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public void iniciarSincronizacaoFila() {
        executando = true;
        new Thread(() -> {
            while (executando) {
                try {
                    if (!UssdAccessibilityService.isEmProcessamento()) {
                        buscarProximoPedido();
                    }
                    Thread.sleep(5000); 
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void buscarProximoPedido() {
        try {
            URL url = new URL(API_URL + "/pedidos/proximo?device_id=" + getDeviceId());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);

            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);

                JSONObject json = new JSONObject(response.toString());
                if (json.has("id") && json.has("numero") && json.has("megas")) {
                    String pedidoId = json.getString("id");
                    String numero = json.getString("numero");
                    String megas = json.getString("megas");

                    UssdAccessibilityService.iniciarEnvio(context, pedidoId, numero, megas);
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void atualizarStatusPedido(String pedidoId, String status) {
        new Thread(() -> {
            try {
                URL url = new URL(API_URL + "/pedidos/status");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("pedido_id", pedidoId);
                body.put("status", status);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = body.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void pararSincronizacao() {
        executando = false;
    }
}
