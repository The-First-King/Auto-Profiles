package com.mine.autoprofile.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.mine.autoprofile.R;
import com.mine.autoprofile.models.FullRule;
import java.util.ArrayList;
import java.util.List;

public class RuleAdapter extends RecyclerView.Adapter<RuleAdapter.RuleViewHolder> {
    private List<FullRule> rules = new ArrayList<>();

    public void setRules(List<FullRule> rules) {
        this.rules = rules;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_rule, parent, false);
        return new RuleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RuleViewHolder holder, int position) {
        FullRule fullRule = rules.get(position);
        
        // Assuming your Profile model has a getName() method
        holder.profileName.setText("Profile ID: " + fullRule.profile.getId()); 
        holder.triggerInfo.setText(fullRule.trigger.getType() + " - " + fullRule.trigger.getValue());
    }

    @Override
    public int getItemCount() {
        return rules.size();
    }

    static class RuleViewHolder extends RecyclerView.ViewHolder {
        TextView profileName, triggerInfo;

        public RuleViewHolder(@NonNull View itemView) {
            super(itemView);
            profileName = itemView.findViewById(R.id.text_profile_name);
            triggerInfo = itemView.findViewById(R.id.text_trigger_info);
        }
    }
}
