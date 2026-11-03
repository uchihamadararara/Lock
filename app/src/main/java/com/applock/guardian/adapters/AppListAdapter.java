package com.applock.guardian.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.applock.guardian.R;
import com.applock.guardian.models.AppInfo;
import com.applock.guardian.utils.AppPrefs;

import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {

    private List<AppInfo> appList;
    private AppPrefs prefs;
    private Context context;

    public AppListAdapter(List<AppInfo> appList, AppPrefs prefs, Context context) {
        this.appList = appList;
        this.prefs = prefs;
        this.context = context;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppInfo app = appList.get(position);
        holder.tvAppName.setText(app.getAppName());
        holder.tvPackage.setText(app.getPackageName());
        holder.ivIcon.setImageDrawable(app.getIcon());

        // Prevent switch listener firing during bind
        holder.switchLock.setOnCheckedChangeListener(null);
        holder.switchLock.setChecked(app.isLocked());

        holder.switchLock.setOnCheckedChangeListener((btn, checked) -> {
            app.setLocked(checked);
            if (checked) {
                prefs.addLockedApp(app.getPackageName());
            } else {
                prefs.removeLockedApp(app.getPackageName());
            }
        });

        // Row click also toggles
        holder.itemView.setOnClickListener(v -> {
            holder.switchLock.setChecked(!holder.switchLock.isChecked());
        });

        // Visual cue for locked state
        holder.ivLockIcon.setVisibility(app.isLocked() ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon, ivLockIcon;
        TextView tvAppName, tvPackage;
        Switch switchLock;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_app_icon);
            ivLockIcon = itemView.findViewById(R.id.iv_lock_icon);
            tvAppName = itemView.findViewById(R.id.tv_app_name);
            tvPackage = itemView.findViewById(R.id.tv_package_name);
            switchLock = itemView.findViewById(R.id.switch_lock);
        }
    }
}
