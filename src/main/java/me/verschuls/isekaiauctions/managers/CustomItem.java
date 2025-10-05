package me.verschuls.isekaiauctions.managers;

import lombok.Getter;
import me.verschuls.isekaiauctions.IsekaiAuctions;
import me.verschuls.isekaiauctions.others.Utils;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

@Getter
public class CustomItem {
    private String name = "";
    private Integer model = 0;
    private List<String> lore = List.of();
    private String material = "";
    private String type = "";

    public CustomItem(String name) {
        ConfigurationSection section = IsekaiAuctions.getInstance().itemsFile.getConfigurationSection("custom_items." + name);
        if (section == null)
            return;

        this.material = section.getString("material", "");
        this.model = section.getInt("model", 0);
        this.name = Utils.colorize(section.getString("name", ""));
        this.type = section.getString("type", "");

        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
            List<String> newLore = new ArrayList<>(lore.size());
            for (String line : lore)
                newLore.add(Utils.colorize(line));

            this.lore = newLore;
        }

        IsekaiAuctions.getInstance().customItems.put(name, this);
    }
}