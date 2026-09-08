package com.bl4ck.system;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class DashboardActivity extends AppCompatActivity {

    private ServidorManager servidorManager;
    private TextView txtDeviceId, txtServerStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Referência dos elementos de status
        txtDeviceId = findViewById(R.id.txtDeviceId);
        txtServerStatus = findViewById(R.id.txtServerStatus);

        // Inicializa o gerenciador da API
        servidorManager = new ServidorManager(this);

        // Exibe o ID do aparelho no painel
        String id = servidorManager.getDeviceId();
        txtDeviceId.setText("ID do Dispositivo: " + id);

        // Inicia a comunicação/polling com o bot
        servidorManager.iniciarSincronizacaoFila();
        txtServerStatus.setText("🟢 Conectado (Sincronizado)");

        // Configuração da Navegação por Abas
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        // Carrega a aba Fila como padrão ao abrir a tela
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new FilaFragment())
                    .commit();
        }

        // Listener elegante para alternar entre as abas
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            
            if (item.getItemId() == R.id.nav_fila) {
                selectedFragment = new FilaFragment();
            } else if (item.getItemId() == R.id.nav_historico) {
                selectedFragment = new HistoricoFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });
    }

    public ServidorManager getServidorManager() {
        return servidorManager;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (servidorManager != null) {
            servidorManager.pararSincronizacao();
        }
    }
}
