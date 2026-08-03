package net.ucucraft.worldevents.events.endstone.loot;

import java.util.HashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

import net.ucucraft.worldevents.events.endstone.BlightRun;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Registered once for the plugin's lifetime; {@link #activate} / {@link #deactivate} toggle whether a
 * run is live. The hot path rejects almost every break in the server with a type check, a reference
 * compare and a bbox check before ever touching the sorted-array binary search in
 * {@link BlightRun#contains}.
 */
public final class BlightMineListener implements Listener {

    private final RandomGenerator random;

    private volatile BlightRun run;
    private volatile Material conversionMaterial = Material.END_STONE;
    private volatile LootTable table;
    private volatile DeliveryMode deliver = DeliveryMode.DROP;
    private volatile boolean dropEndstone;
    private volatile boolean requirePickaxe = true;
    private volatile boolean silkTouchBypasses = true;

    public BlightMineListener(RandomGenerator random) {
        this.random = random;
    }

    public void activate(BlightRun run, Material conversionMaterial, LootTable table, DeliveryMode deliver,
                          boolean dropEndstone, boolean requirePickaxe, boolean silkTouchBypasses) {
        this.conversionMaterial = conversionMaterial;
        this.table = table;
        this.deliver = deliver;
        this.dropEndstone = dropEndstone;
        this.requirePickaxe = requirePickaxe;
        this.silkTouchBypasses = silkTouchBypasses;
        this.run = run;
    }

    public void deactivate() {
        this.run = null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        BlightRun activeRun = run;
        if (activeRun == null || event.getBlock().getType() != conversionMaterial
                || event.getBlock().getWorld() != activeRun.world()) {
            return;
        }
        int chunkX = event.getBlock().getX() >> 4;
        int chunkZ = event.getBlock().getZ() >> 4;
        if (!activeRun.contains(chunkX, chunkZ)) {
            return;
        }

        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        event.setDropItems(dropEndstone);

        if (tool.containsEnchantment(Enchantment.SILK_TOUCH) && silkTouchBypasses) {
            return;
        }
        if (requirePickaxe && !Tag.ITEMS_PICKAXES.isTagged(tool.getType())) {
            return;
        }

        LootTable currentTable = table;
        if (currentTable == null) {
            return;
        }

        Map<String, Integer> enchantLevels = new HashMap<>();
        for (Map.Entry<Enchantment, Integer> entry : tool.getEnchantments().entrySet()) {
            enchantLevels.put(entry.getKey().getKey().toString(), entry.getValue());
        }

        Location location = event.getBlock().getLocation();
        for (LootTable.LootDrop drop : currentTable.roll(random, tool.getType().name(), enchantLevels)) {
            deliver(event.getPlayer(), location, drop);
        }
    }

    private void deliver(Player player, Location location, LootTable.LootDrop drop) {
        ItemStack stack = new ItemStack(drop.material(), drop.amount());
        if (deliver == DeliveryMode.INVENTORY) {
            for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
                location.getWorld().dropItemNaturally(location, leftover);
            }
        } else {
            location.getWorld().dropItemNaturally(location, stack);
        }
    }
}
