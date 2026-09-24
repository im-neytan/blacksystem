package com.bl4ck.system;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ServidorManager {
    private static final String API_BASE_URL = BuildConfig.API_BASE_URL;
    private static final int TIMEOUT_MS = 10000;
    private static final ConcurrentLinkedQueue<Pedido> FILA_LOCAL = new ConcurrentLinkedQueue<>();
    private static final Set<String> IDS_NA_FILA = ConcurrentHashMap.newKeySet();

    public static class Pedido {
        public final String id, numero, megas;
        public Pedido(String id, String numero, String megas) { 
            this.id = id; 
            this.numero = numero; 
            this.megas = megas; 
        }
    }

    public interface FilaCallback { void onResultado(List<String> pedidosFormatados); }
    public interface HistoricoCallback { void onResultado(List<String> historicoFormatado); }
    public interface StatusCallback { void onStatus(String status); }

    private final Context context;
    private volatile boolean executando;
    private volatile StatusCallback statusCallback;

    public ServidorManager(Context context) { 
        this.context = context.getApplicationContext(); 
    }

    public String getDeviceId() { 
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID); 
    }

    public static int getTamanhoFila() { return FILA_LOCAL.size(); }
    
    public void setStatusCallback(StatusCallback callback) { this.statusCallback = callback; }

    private void publicarStatus(String status) { 
        if (statusCallback != null) statusCallback.onStatus(status); 
    }

    public static void buscarPedidosFila(FilaCallback callback) {
        List<String> lista = new ArrayList<>();
        for (Pedido p : FILA_LOCAL) {
            lista.add("📱 Destino: " + p.numero + "\n📦 Volume: " + p.megas + " MB\n🆔 ID: " + p.id);
        }
        callback.onResultado(lista);
    }

    public void buscarHistorico(HistoricoCallback callback) {
        new Thread(() -> {
            List<String> historico = new ArrayList<>();
            HttpURLConnection conn = null;
            try {
                String token = token();
                if (token.isEmpty()) { 
                    callback.onResultado(historico); 
                    return; 
                }

                Uri uri = Uri.parse(API_BASE_URL + "/api/pedidos/historico")
                        .buildUpon()
                        .appendQueryParameter("device_id", getDeviceId())
                        .build();

                conn = abrirConexao(uri.toString(), "GET", token);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        JSONArray itens = new JSONArray(lerResposta(br));
                        for (int i = 0; i < itens.length(); i++) {
                            JSONObject obj = itens.getJSONObject(i);
                            String status = obj.optString("status", "PENDENTE");
                            String emoji = status.equalsIgnoreCase("CONCLUIDO") ? "✅" : (status.equalsIgnoreCase("ERRO") ? "❌" : "⏳");
                            
                            historico.add(emoji + " Status: " + status + 
                                    "\n📱 Destino: " + obj.optString("numero", "N/A") + 
                                    " (" + obj.optString("megas", "0") + " MB)" + 
                                    "\n💬 Resposta: " + obj.optString("resposta_operadora", "Sem resposta gravada"));
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally { 
                if (conn != null) conn.disconnect(); 
            }
            callback.onResultado(historico);
        }, "historico-bl4ck").start();
    }

    public void iniciarSincronizacaoFila() {
        if (executando) return;
        executando = true;

        new Thread(() -> {
            publicarStatus("🟡 Conectando ao servidor...");
            while (executando) {
                try {
                    String token = token();
                    if (token.isEmpty()) { 
                        publicarStatus("🔴 Sessão expirada. Emparelhe novamente."); 
                        pararSincronizacao(); 
                        break; 
                    }

                    sincronizarPedidos(token);

                    if (!UssdAccessibilityService.isEmProcessamento()) {
                        Pedido proximo = FILA_LOCAL.poll();
                        if (proximo != null) {
                            IDS_NA_FILA.remove(proximo.id);
                            publicarStatus("🟡 Processando pedido #" + proximo.id);
                            UssdAccessibilityService.iniciarEnvio(context, proximo.id, proximo.numero, proximo.megas);
                        } else {
                            publicarStatus("🟢 Conectado · sem pedidos pendentes");
                        }
                    }

                    Thread.sleep(4000);
                } catch (InterruptedException e) { 
                    Thread.currentThread().interrupt(); 
                    break; 
                } catch (IllegalStateException e) {
                    publicarStatus("🔴 " + e.getMessage());
                    if ("Sessão expirada".equals(e.getMessage())) {
                        pararSincronizacao();
                        break;
                    }
                } catch (Exception e) { 
                    publicarStatus("🔴 Falha de conexão. Revisitando em 5s...");
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            }
        }, "sincronizacao-bl4ck").start();
    }

    private void sincronizarPedidos(String token) throws Exception {
        HttpURLConnection conn = null;
        try {
            Uri uri = Uri.parse(API_BASE_URL + "/api/pedidos/pendentes")
                    .buildUpon()
                    .appendQueryParameter("device_id", getDeviceId())
                    .build();

            conn = abrirConexao(uri.toString(), "GET", token);
            int codigo = conn.getResponseCode();

            if (codigo == HttpURLConnection.HTTP_UNAUTHORIZED || codigo == HttpURLConnection.HTTP_FORBIDDEN) {
                context.getSharedPreferences("BL4CK_CONFIG", Context.MODE_PRIVATE)
                        .edit()
                        .remove("SESSION_TOKEN")
                        .apply();
                throw new IllegalStateException("Sessão expirada");
            }

            if (codigo != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("Servidor HTTP " + codigo);
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                JSONArray pedidos = new JSONArray(lerResposta(br));
                for (int i = 0; i < pedidos.length(); i++) {
                    JSONObject obj = pedidos.getJSONObject(i);
                    // Uso de optString para evitar erros se o Node enviar números no JSON
                    String id = obj.optString("id", "");
                    String numero = obj.optString("numero", "");
                    String megas = obj.optString("megas", "0");

                    if (!id.isEmpty() && IDS_NA_FILA.add(id)) {
                        FILA_LOCAL.offer(new Pedido(id, numero, megas));
                    }
                }
            }
        } finally { 
            if (conn != null) conn.disconnect(); 
        }
    }

    public static void atualizarStatusPedido(Context context, String pedidoId, String status, String respostaOperadora) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String token = context.getSharedPreferences("BL4CK_CONFIG", Context.MODE_PRIVATE).getString("SESSION_TOKEN", "");
                if (token.isEmpty()) return;

                conn = (HttpURLConnection) new URL(API_BASE_URL + "/api/pedidos/status").openConnection();
                conn.setRequestMethod("POST"); 
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Authorization", "Bearer " + token); 
                conn.setConnectTimeout(TIMEOUT_MS); 
                conn.setReadTimeout(TIMEOUT_MS); 
                conn.setDoOutput(true);

                JSONObject body = new JSONObject(); 
                body.put("pedido_id", pedidoId); 
                body.put("status", status); 
                body.put("resposta_operadora", respostaOperadora != null ? respostaOperadora : "");

                try (OutputStream os = conn.getOutputStream()) { 
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8)); 
                }
                
                conn.getResponseCode();
            } catch (Exception ignored) { 
            } finally { 
                if (conn != null) conn.disconnect(); 
            }
        }, "status-pedido-bl4ck").start();
    }

    private String token() { 
        return context.getSharedPreferences("BL4CK_CONFIG", Context.MODE_PRIVATE).getString("SESSION_TOKEN", ""); 
    }

    private HttpURLConnection abrirConexao(String endereco, String metodo, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endereco).openConnection();
        conn.setRequestMethod(metodo); 
        conn.setRequestProperty("Authorization", "Bearer " + token); 
        conn.setConnectTimeout(TIMEOUT_MS); 
        conn.setReadTimeout(TIMEOUT_MS); 
        return conn;
    }

    private static String lerResposta(BufferedReader br) throws Exception { 
        StringBuilder resposta = new StringBuilder(); 
        String linha; 
        while ((linha = br.readLine()) != null) resposta.append(linha); 
        return resposta.toString(); 
    }

    public void pararSincronizacao() { 
        executando = false; 
    }
}
