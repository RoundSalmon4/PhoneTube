package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Bottom sheet for the player "more" menu. Replaces the old AlertDialog.
 */
public class PhonePlayerMenuBottomSheet extends BottomSheetDialogFragment {

    interface OnMenuItemClickListener {
        void onMenuItemClick(int actionId);
    }

    private static final String ARG_ITEMS = "menu_items";
    private static final String ARG_ACTIONS = "menu_actions";

    private OnMenuItemClickListener mListener;

    static PhonePlayerMenuBottomSheet newInstance(String[] items, int[] actionIds) {
        PhonePlayerMenuBottomSheet sheet = new PhonePlayerMenuBottomSheet();
        Bundle args = new Bundle();
        args.putStringArray(ARG_ITEMS, items);
        args.putIntArray(ARG_ACTIONS, actionIds);
        sheet.setArguments(args);
        return sheet;
    }

    void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
        mListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_phone_player_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView list = view.findViewById(R.id.player_menu_list);
        if (list == null) return;

        String[] items = getArguments() != null ? getArguments().getStringArray(ARG_ITEMS) : null;
        int[] actions = getArguments() != null ? getArguments().getIntArray(ARG_ACTIONS) : null;
        if (items == null || actions == null) return;

        MenuAdapter adapter = new MenuAdapter(items, actions);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return super.onCreateDialog(savedInstanceState);
    }

    private class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.ViewHolder> {
        private final String[] mItems;
        private final int[] mActions;

        MenuAdapter(String[] items, int[] actions) {
            mItems = items;
            mActions = actions;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_phone_player_menu_option, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.title.setText(mItems[position]);
            holder.itemView.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onMenuItemClick(mActions[position]);
                }
                dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return mItems.length;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.menu_item_title);
            }
        }
    }
}
