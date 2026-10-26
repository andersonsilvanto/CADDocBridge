package com.andersonlopes.caddocbridge.ui.history;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.andersonlopes.caddocbridge.R;
import com.andersonlopes.caddocbridge.model.ConversionHistory;
import com.andersonlopes.caddocbridge.utils.FileStorageManager;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HistoryAdapter extends ListAdapter<ConversionHistory, HistoryAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(@NonNull ConversionHistory item);
        void onItemLongClick(@NonNull ConversionHistory item);
    }

    private static final Object PAYLOAD_SELECTION = new Object();
    private static final DiffUtil.ItemCallback<ConversionHistory> DIFF = new DiffUtil.ItemCallback<ConversionHistory>() {
        @Override
        public boolean areItemsTheSame(@NonNull ConversionHistory a, @NonNull ConversionHistory b) {
            return a.getId() == b.getId();
        }
        @Override
        public boolean areContentsTheSame(@NonNull ConversionHistory a, @NonNull ConversionHistory b) {
            return a.getFileName().equals(b.getFileName()) && a.getFormat().equals(b.getFormat())
                    && a.getSizeBytes() == b.getSizeBytes() && a.getCreatedAt() == b.getCreatedAt();
        }
    };

    private final Listener listener;
    private Set<Long> selectedIds = Collections.emptySet();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt", "BR"));

    public HistoryAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void setSelectedIds(@NonNull Set<Long> ids) {
        this.selectedIds = ids;
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.bindSelection(getItem(position));
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView card;
        private final TextView textFormat;
        private final TextView textName;
        private final TextView textInfo;
        private final CheckBox checkSelected;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card);
            textFormat = itemView.findViewById(R.id.textFormat);
            textName = itemView.findViewById(R.id.textName);
            textInfo = itemView.findViewById(R.id.textInfo);
            checkSelected = itemView.findViewById(R.id.checkSelected);
        }

        void bind(@NonNull final ConversionHistory item) {
            textFormat.setText(item.getFormat());
            textName.setText(item.getFileName());
            textInfo.setText(FileStorageManager.formatSize(item.getSizeBytes()) + " • " + dateFormat.format(new Date(item.getCreatedAt())));

            card.setOnClickListener(v -> listener.onItemClick(item));
            card.setOnLongClickListener(v -> {
                listener.onItemLongClick(item);
                return true;
            });
            bindSelection(item);
        }

        void bindSelection(@NonNull ConversionHistory item) {
            boolean selected = selectedIds.contains(item.getId());
            boolean selectionMode = !selectedIds.isEmpty();

            checkSelected.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
            checkSelected.setChecked(selected);

            float density = itemView.getResources().getDisplayMetrics().density;
            card.setStrokeWidth((int) ((selected ? 2 : 1) * density));
            card.setStrokeColor(Color.parseColor(selected ? "#38BDF8" : "#334155"));
        }
    }
}