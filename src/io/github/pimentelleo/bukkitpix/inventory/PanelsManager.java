package io.github.pimentelleo.bukkitpix.inventory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;

import io.github.pimentelleo.bukkitpix.BukkitPix;
import io.github.pimentelleo.bukkitpix.MSG;
import io.github.pimentelleo.bukkitpix.Order;
import io.github.pimentelleo.bukkitpix.OrderProduct;
import me.rockyhawk.commandpanels.api.Panel;

    public class PanelsManager {
        File file = new File("panel.yml");
    String panelName = "Bukkit Pix Menu";
    Panel panel = new Panel(file, panelName);
        
}
