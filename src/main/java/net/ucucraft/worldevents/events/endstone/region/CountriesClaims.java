package net.ucucraft.worldevents.events.endstone.region;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ucucraft.countries.api.ChunkPos;
import com.ucucraft.countries.api.CountriesAPI;
import com.ucucraft.countries.api.CountriesProvider;
import com.ucucraft.countries.api.CountryView;
import org.bukkit.Bukkit;

/**
 * The only class allowed to name {@code com.ucucraft.countries.api.*}. Every entry point is guarded
 * by a plugin-presence check and wrapped in {@code catch (Throwable)}: a missing dependency jar makes
 * any class merely naming a Countries type throw {@link NoClassDefFoundError} at first use, and that
 * is an {@link Error}, not an {@link Exception}.
 */
public final class CountriesClaims implements ClaimSource {

    @Override
    public boolean available() {
        try {
            return Bukkit.getPluginManager().getPlugin("Countries") != null && CountriesProvider.get() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public Map<String, Set<ChunkCoord>> claimsByWorld() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Countries") == null) {
                return Map.of();
            }
            CountriesAPI api = CountriesProvider.get();
            if (api == null) {
                return Map.of();
            }
            Map<String, Set<ChunkCoord>> result = new HashMap<>();
            for (CountryView country : api.getCountries()) {
                for (ChunkPos pos : copyClaims(country)) {
                    result.computeIfAbsent(pos.world(), w -> new HashSet<>()).add(new ChunkCoord(pos.x(), pos.z()));
                }
            }
            return result;
        } catch (Throwable t) {
            return Map.of();
        }
    }

    /** {@link CountryView#claims()} is a live view over a mutable set; copy defensively, retrying once on CME. */
    private Set<ChunkPos> copyClaims(CountryView country) {
        try {
            return Set.copyOf(country.claims());
        } catch (RuntimeException e) {
            return Set.copyOf(country.claims());
        }
    }
}
