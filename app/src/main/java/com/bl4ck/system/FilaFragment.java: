package com.bl4ck.system;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.ArrayList;

public class FilaFragment extends Fragment {

    private ListView listView;
    private ArrayAdapter<String> adapter;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_lista, container, false);
        listView = view.findViewById(R.id.listView);

        adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter);

        // Atualização contínua da lista visual da fila local
        iniciarAtualizacaoFila();

        return view;
    }

    private void iniciarAtualizacaoFila() {
        runnable = new Runnable() {
            @Override
            public void run() {
                ServidorManager.buscarPedidosFila(pedidos -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            adapter.clear();
                            if (pedidos.isEmpty()) {
                                adapter.add("⌛ NENHUM PEDIDO NA FILA LOCAL");
                            } else {
                                adapter.addAll(pedidos);
                            }
                            adapter.notifyDataSetChanged();
                        });
                    }
                });
                handler.postDelayed(this, 3000); // Atualiza a tela a cada 3 segundos
            }
        };
        handler.post(runnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }
}
