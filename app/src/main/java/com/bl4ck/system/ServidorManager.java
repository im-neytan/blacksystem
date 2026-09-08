package com.bl4ck.system;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.Queue;

public class ServidorManager {

    // URL central padrão da sua API na Railway
    private static final String API_BASE_URL = "https://orion-2-production-5283.up.railway.app";

    public static class Pedido {
        public String id;
        public String numero;
        public String megas;

        public Pedido(String id, String numero, String megas) {
            this.id = id;
            this.numero = numero;
            this.megas = megas;
        }
    }

    private Context context;
    private boolean executando = false;
    private static Queue<Pedido> filaLocal = new LinkedList<>();

    public ServidorManager(Context context) {
        this.context = context;
    }

    public String getDeviceId() {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public static int getTamanhoFila() {
        return filaLocal.size();
    }

    public void iniciarSincronizacaoFila() {
        executando = true;
        new Thread(() -> {
            while (executando) {
                try {
                    SharedPreferences prefs = context.getSharedPreferences("BL4CK_CONFIG", Context.MODE_PRIVATE);
                    String sessionToken = prefs.getString("SESSION_TOKEN", "");

                    if (!sessionToken.isEmpty()) {
                        // 1. Sincroniza novos pedidos do servidor para a fila interna
                        sincronizarPedidos(sessionToken);

                        // 2. Se o serviço USSD não estiver ocupado e houver pedidos, executa o próximo
                        if (!UssdAccessibilityService.isEmProcessamento() && !filaLocal.isEmpty()) {
                            Pedido proximo = filaLocal.poll();
                            if (proximo != null) {
                                UssdAccessibilityService.iniciarEnvio(context, proximo.id, proximo.numero, proximo.megas);
                            }
                        }
                    }
                    Thread.sleep(4000); // Consulta a cada 4 segundos
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void sincronizarPedidos(String sessionToken) {
        try {
            URL url = new URL(API_BASE_URL + "/api/pedidos/pendentes?device_id=" + getDeviceId());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + sessionToken);
            conn.setConnectTimeout(5000);

            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);

                JSONArray jsonArray = new JSONArray(response.toString());
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    String id = obj.getString("id");
                    String numero = obj.getString("numero");
                    String megas = obj.getString("megas");

                    // Adiciona na fila se ainda não estiver inserido
                    boolean jaExiste = false;
                    for (Pedido p : filaLocal) {
                        if (p.id.equals(id)) {
                            jaExiste = true;
                            break;
                        }
                    }
                    if (!jaExiste) {
                        filaLocal.add(new Pedido(id, numero, megas));
                    }
                }
            } else if (conn.getResponseCode() == 401 || conn.getResponseCode() == 403) {
                // Se o token for revogado ou licença expirar, desconecta o dispositivo
                context.getSharedPreferences("BL4CK_CONFIG", Context.MODE_PRIVATE)
                        .edit()
                        .remove("SESSION_TOKEN")
                        .apply();
                pararSincronizacao();
            }
            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void atualizarStatusPedido(Context context, String pedidoId, String status) {
        new Thread(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences("BL4CK_CONFIG", Context.MODE_PRIVATE);
                String sessionToken = prefs.getString("SESSION_TOKEN", "");

                URL url = new URL(API_BASE_URL + "/api/pedidos/status");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Authorization", "Bearer " + sessionToken);
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
