package net.ucucraft.worldevents.events.endstone.outro;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.ucucraft.worldevents.events.endstone.BlightSettings.OutroSettings;
import net.ucucraft.worldevents.lang.LangManager;
import net.ucucraft.worldevents.lang.Msg;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * One cancellable {@code runTaskTimer(0,1)} phase machine, not a chain of {@code runTaskLater}s.
 * Player UUIDs are captured once at the start and re-resolved to a {@link Player} every phase, since
 * holding live references across several seconds risks working with someone who has since logged out.
 */
public final class OutroSequence {

    private enum Phase { DARKNESS, PORTAL, TELEPORT, CLEANUP, DONE }

    private final Plugin plugin;
    private final LangManager lang;
    private final OutroSettings settings;
    private final List<UUID> audience;
    private final Runnable onComplete;

    private final long portalStartTick;
    private final long teleportTick;
    private final long cleanupTick;
    private final long doneTick;

    private BukkitTask task;
    private Phase phase = Phase.DARKNESS;
    private long tick;

    public OutroSequence(Plugin plugin, LangManager lang, OutroSettings settings, List<UUID> audience,
                          Runnable onComplete) {
        this.plugin = plugin;
        this.lang = lang;
        this.settings = settings;
        this.audience = List.copyOf(audience);
        this.onComplete = onComplete;

        long darknessDelay = ticks(settings.darknessDelay());
        long portalDuration = ticks(settings.portalDuration());
        this.portalStartTick = darknessDelay;
        this.teleportTick = portalStartTick + portalDuration;
        this.cleanupTick = teleportTick + ticks(settings.clearDelay());
        this.doneTick = cleanupTick + ticks(settings.restoreDelay());
    }

    private static long ticks(Duration duration) {
        return Math.max(0, duration.toMillis() / 50);
    }

    public void start() {
        onDarkness();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::run, 0L, 1L);
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void run() {
        tick++;
        if (phase == Phase.DARKNESS && tick >= portalStartTick) {
            phase = Phase.PORTAL;
            onPortalStart();
        }
        if (phase == Phase.PORTAL) {
            onPortalTick();
        }
        if (phase == Phase.PORTAL && tick >= teleportTick) {
            phase = Phase.TELEPORT;
            onTeleport();
        }
        if (phase == Phase.TELEPORT && tick >= cleanupTick) {
            phase = Phase.CLEANUP;
            onCleanup();
        }
        if (phase == Phase.CLEANUP && tick >= doneTick) {
            phase = Phase.DONE;
            cancel();
            onComplete.run();
        }
    }

    private void onDarkness() {
        for (Player player : players()) {
            lang.send(player, Msg.BLIGHT_OUTRO_DARKNESS);
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,
                    (int) ticks(settings.darknessDuration()), 0, false, false));
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 1.0f);
        }
    }

    private void onPortalStart() {
        for (Player player : players()) {
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 1.0f);
        }
    }

    private void onPortalTick() {
        for (Player player : players()) {
            Location loc = player.getLocation().add(0, 1, 0);
            World world = loc.getWorld();
            if (world == null) {
                continue;
            }
            world.spawnParticle(Particle.PORTAL, loc, settings.portalParticles(), 0.3, 0.5, 0.3, 0.05);
            world.spawnParticle(Particle.REVERSE_PORTAL, loc, Math.max(1, settings.portalParticles() / 4),
                    0.3, 0.5, 0.3, 0.02);
        }
    }

    private void onTeleport() {
        for (Player player : players()) {
            lang.send(player, Msg.BLIGHT_OUTRO_TELEPORT);
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 1.0f, 1.0f);
            player.leaveVehicle();
            player.teleportAsync(respawnFor(player));
        }
    }

    private void onCleanup() {
        if (!settings.clearEffects()) {
            return;
        }
        for (Player player : players()) {
            player.removePotionEffect(PotionEffectType.DARKNESS);
        }
    }

    private Location respawnFor(Player player) {
        Location respawn = player.getRespawnLocation();
        if (respawn != null) {
            return respawn;
        }
        Location worldSpawn = player.getWorld().getSpawnLocation();
        if (worldSpawn != null) {
            return worldSpawn;
        }
        return plugin.getServer().getWorlds().get(0).getSpawnLocation();
    }

    private List<Player> players() {
        List<Player> online = new ArrayList<>(audience.size());
        for (UUID id : audience) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }
}
