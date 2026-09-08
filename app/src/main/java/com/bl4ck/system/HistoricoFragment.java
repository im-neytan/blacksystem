package com.bl4ck.system;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.ArrayList;

public class HistoricoFragment extends Fragment {

    private ListView listView;
    private ArrayAdapter<String> adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_lista, container, false);
        listView = view.findViewById(R.id.listView);

        adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter);

        carregarHistoricoServidor();

        return view;
    }

    private void carregarHistoricoServidor() {
        if (getActivity() instanceof DashboardActivity) {
            DashboardActivity activity = (DashboardActivity) getActivity();
            ServidorManager servidorManager = activity.getServidorManager();

            if (servidorManager != null) {
                servidorManager.buscarHistorico(historico -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            adapter.clear();
                            if (historico.isEmpty()) {
                                adapter.add("📜 NENHUM REGISTRO DE ENVIO ENCONTRADO");
                            } else {
                                adapter.addAll(historico);
                            }
                            adapter.notifyDataSetChanged();
                        });
                    }
                });
            }
        }
    }
}
