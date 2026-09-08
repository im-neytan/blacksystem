package com.bl4ck.system;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText edtUrlServidor, edtApiKey;
    private Button btnConectar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("BL4CK_CONFIG", MODE_PRIVATE);
        String urlSalva = prefs.getString("API_URL", "");
        String tokenSalvo = prefs.getString("API_KEY", "");

        // Se já configurou anteriormente, entra direto no Dashboard
        if (!urlSalva.isEmpty() && !tokenSalvo.isEmpty()) {
            abrirDashboard();
            return;
        }

        setContentView(R.layout.activity_login);

        edtUrlServidor = findViewById(R.id.edtUrlServidor);
        edtApiKey = findViewById(R.id.edtApiKey);
        btnConectar = findViewById(R.id.btnConectar);

        btnConectar.setOnClickListener(v -> {
            String url = edtUrlServidor.getText().toString().trim();
            String key = edtApiKey.getText().toString().trim();

            if (url.isEmpty() || key.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos para conectar!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            // Salva as credenciais no armazenamento seguro local (SharedPreferences)
            prefs.edit()
                    .putString("API_URL", url)
                    .putString("API_KEY", key)
                    .apply();

            Toast.makeText(this, "Servidor salvo com sucesso!", Toast.LENGTH_SHORT).show();
            abrirDashboard();
        });
    }

    private void abrirDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}
