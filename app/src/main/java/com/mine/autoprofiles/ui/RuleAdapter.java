package com.mine.autoprofiles.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.mine.autoprofiles.R;
import com.mine.autoprofiles.models.FullRule;
import java.util.ArrayList;
import java.util.List;

public class RuleAdapter extends RecyclerView.Adapter<RuleAdapter.RuleViewHolder> {
    
    private List<FullRule> rules = new ArrayList<>();
    private final OnRuleClickListener listener;

    // Interface to handle clicks on the items and buttons
    public interface OnRuleClickListener {
        void onToggleRule(FullRule rule, boolean isChecked);
        void onEditRule(FullRule rule);
        void onDeleteRule(FullRule rule);
    }

    // Constructor requires the listener so MainActivity can handle the clicks
    public RuleAdapter(OnRuleClickListener listener) {
        this.listener = listener;
    }

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

        // 1. Set Rule Name (Since your Rule model doesn't have a name string, we can use the ID or trigger type)
        // A user-given name wins (location rules); otherwise show a positional
        // number (1..n) so the numbering has no gaps after deletions. The id
        // remains the stable internal key for alarms and edits.
        String userName = fullRule.rule != null ? fullRule.rule.getName() : null;
        if (userName != null && !userName.trim().isEmpty()) {
            holder.ruleName.setText(userName);
        } else {
            holder.ruleName.setText("Rule #" + (position + 1) + " (" + fullRule.trigger.getType() + ")");
        }

        // 2. Set Profile Name
        // NOTE: Assuming your Profile.java model has a getName() method. If not, use String.valueOf(fullRule.profile.getId())
        if (fullRule.profile != null) {
            holder.ruleProfile.setText("Applies to: " + fullRule.profile.getName());
        } else {
            holder.ruleProfile.setText("Applies to: Unknown Profile");
        }

        // 3. Set Trigger Info (human readable for TIME triggers)
        holder.ruleTriggerInfo.setText(formatTrigger(fullRule));

        // 4. Set Switch State
        // Remove listener temporarily so we don't trigger it while recycling views
        holder.ruleSwitch.setOnCheckedChangeListener(null); 
        holder.ruleSwitch.setChecked(fullRule.rule.isEnabled());

        // 5. Re-attach Listeners
        holder.ruleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) listener.onToggleRule(fullRule, isChecked);
        });

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEditRule(fullRule);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteRule(fullRule);
        });
    }

    /** Human-readable schedule summary (supports both JSON and legacy trigger values). */
    private static String formatTrigger(FullRule fullRule) {
        if (fullRule.trigger == null) return "Trigger: ?";
        String value = fullRule.trigger.getValue();
        if ("CELL".equals(fullRule.trigger.getType())) {
            int n = com.mine.autoprofiles.utils.CellUtils.fromTriggerValue(value).size();
            return "Location: " + n + " cell tower" + (n == 1 ? "" : "s");
        }
        if (!"TIME".equals(fullRule.trigger.getType())) return "Trigger: " + value;
        com.mine.autoprofiles.models.Schedule schedule =
                com.mine.autoprofiles.models.Schedule.parse(value);
        return schedule != null ? schedule.describe() : "Trigger: " + value;
    }

    @Override
    public int getItemCount() {
        return rules.size();
    }

    static class RuleViewHolder extends RecyclerView.ViewHolder {
        TextView ruleName;
        TextView ruleProfile;
        TextView ruleTriggerInfo;
        SwitchCompat ruleSwitch;
        Button btnEdit;
        Button btnDelete;

        public RuleViewHolder(@NonNull View itemView) {
            super(itemView);
            // Mappings updated to match your new item_rule.xml exactly
            ruleName = itemView.findViewById(R.id.rule_name);
            ruleProfile = itemView.findViewById(R.id.rule_profile);
            ruleTriggerInfo = itemView.findViewById(R.id.rule_trigger_info);
            ruleSwitch = itemView.findViewById(R.id.rule_switch);
            btnEdit = itemView.findViewById(R.id.btn_edit_rule);
            btnDelete = itemView.findViewById(R.id.btn_delete_rule);
        }
    }
}
