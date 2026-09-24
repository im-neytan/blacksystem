package com.bl4ck.system;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class LoginActivity extends AppCompatActivity {
    private static final int REQUEST_CALL_PHONE = 1001;
    // Garanta que BuildConfig.API_BASE_URL não termine com barra extra
    private static final String API_CENTRAL_URL = BuildConfig.API_BASE_URL + "/api/emparelhar";

    private TextInputEditText edtCodigoEmparelhamento;
    private MaterialButton btnEmparelhar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences("BL4CK_CONFIG", MODE_PRIVATE);
        if (!prefs.getString("SESSION_TOKEN", "").isEmpty() && temPermissaoChamada()) {
            abrirDashboard();
            return;
        }

        setContentView(R.layout.activity_login);
        edtCodigoEmparelhamento = findViewById(R.id.edtCodigoEmparelhamento);
        btnEmparelhar = findViewById(R.id.btnEmparelhar);
        btnEmparelhar.setOnClickListener(v -> tentarEmparelhar());
    }

    private void tentarEmparelhar() {
        if (!temPermissaoChamada()) {
            requestPermissions(new String[]{android.Manifest.permission.CALL_PHONE}, REQUEST_CALL_PHONE);
            return;
        }
        String codigo = edtCodigoEmparelhamento.getText() == null ? "" : edtCodigoEmparelhamento.getText().toString().trim().toUpperCase();
        if (codigo.isEmpty()) {
            Toast.makeText(this, "Digite o código de emparelhamento.", Toast.LENGTH_SHORT).show();
            return;
        }
        btnEmparelhar.setEnabled(false);
        btnEmparelhar.setText("Validando...");
        enviarEmparelhamento(codigo);
    }

    private boolean temPermissaoChamada() {
        return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M
                || checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CALL_PHONE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissão concedida. Informe o código para continuar.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "A permissão de chamada é necessária para executar pedidos USSD.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void enviarEmparelhamento(String codigo) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                conn = (HttpURLConnection) new URL(API_CENTRAL_URL).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(12000);
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("codigo", codigo);
                body.put("device_id", deviceId);
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) response.append(line);
                    }
                    
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String token = jsonResponse.optString("token", jsonResponse.optString("session_token", ""));
                    
                    if (token.isEmpty()) {
                        mostrarErro("Servidor não retornou um token válido.");
                        return;
                    }

                    getSharedPreferences("BL4CK_CONFIG", MODE_PRIVATE).edit().putString("SESSION_TOKEN", token).apply();
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Dispositivo emparelhado com sucesso.", Toast.LENGTH_SHORT).show();
                        abrirDashboard();
                    });
                } else {
                    // Tenta ler a mensagem de erro retornada pela API
                    StringBuilder errorResponse = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(
                            conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) errorResponse.append(line);
                    } catch (Exception ignored) {}

                    String msgErro = "Código inválido ou serviço indisponível (" + responseCode + ")";
                    if (errorResponse.length() > 0) {
                        try {
                            JSONObject errJson = new JSONObject(errorResponse.toString());
                            msgErro = errJson.optString("message", errJson.optString("error", msgErro));
                        } catch (Exception ignored) {}
                    }
                    mostrarErro(msgErro);
                }
            } catch (Exception e) {
                mostrarErro("Erro de conexão: " + e.getLocalizedMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void mostrarErro(String mensagem) {
        runOnUiThread(() -> {
            if (isFinishing()) return;
            btnEmparelhar.setEnabled(true);
            btnEmparelhar.setText("Conectar Dispositivo");
            Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show();
        });
    }

    private void abrirDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}
