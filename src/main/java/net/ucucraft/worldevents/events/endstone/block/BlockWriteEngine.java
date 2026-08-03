package net.ucucraft.worldevents.events.endstone.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Budgeted block writer shared by conversion and restore. Runs on its own 1-tick task, started lazily
 * on the first submit and cancelled once the queue drains — it cannot ride the plugin's shared
 * {@code core.tick-interval} timer, which would make every burst up to 20x larger.
 * <p>
 * Two budgets bound each tick: {@code blocksPerTick} caps packet/update volume, and
 * {@code maxNanosPerTick} is the real TPS guard, checked every 256 blocks since checking every block
 * costs 5-10% on its own.
 */
public final class BlockWriteEngine {

    private static final int CHECK_INTERVAL = 256;

    private final Plugin plugin;
    private final Deque<WriteJob> queue = new ArrayDeque<>();

    private int blocksPerTick = 3000;
    private long maxNanosPerTick = 3_000_000;
    private BukkitTask task;
    private final List<Runnable> onDrainedCallbacks = new ArrayList<>();

    public BlockWriteEngine(Plugin plugin) {
        this.plugin = plugin;
    }

    public void configure(int blocksPerTick, double maxMsPerTick) {
        this.blocksPerTick = Math.max(1, blocksPerTick);
        this.maxNanosPerTick = Math.max(1, (long) (maxMsPerTick * 1_000_000));
    }

    /** Fired exactly once, the moment every job queued so far has finished applying. Multiple callbacks
     *  may be pending at once (e.g. crash recovery restoring several leftover runs together). */
    public void onDrained(Runnable callback) {
        if (callback == null) {
            return;
        }
        // Callers always submit their jobs before registering, so an idle engine here means there was
        // nothing to drain (e.g. restoring a run that converted no blocks). tick() would never fire the
        // callback in that case, wedging the run, so run it now.
        if (idle()) {
            callback.run();
        } else {
            onDrainedCallbacks.add(callback);
        }
    }

    public void submit(WriteJob job) {
        queue.add(job);
        start();
    }

    public boolean idle() {
        return task == null && queue.isEmpty();
    }

    public int pendingJobs() {
        return queue.size();
    }

    private void start() {
        if (task != null) {
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        long deadline = System.nanoTime() + maxNanosPerTick;
        int remaining = blocksPerTick;
        while (remaining > 0 && !queue.isEmpty()) {
            WriteJob job = queue.peek();
            int step = Math.min(remaining, CHECK_INTERVAL);
            remaining -= job.apply(step);
            if (job.done()) {
                queue.poll();
            }
            if (System.nanoTime() >= deadline) {
                break;
            }
        }
        if (queue.isEmpty()) {
            task.cancel();
            task = null;
            List<Runnable> callbacks = List.copyOf(onDrainedCallbacks);
            onDrainedCallbacks.clear();
            callbacks.forEach(Runnable::run);
        }
    }

    /** Blocking drain used only from {@code onDisable}, where the scheduler no longer accepts tasks. */
    public void drainBlocking(long budgetMs) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        long deadline = System.nanoTime() + budgetMs * 1_000_000L;
        while (!queue.isEmpty() && System.nanoTime() < deadline) {
            WriteJob job = queue.peek();
            job.apply(4096);
            if (job.done()) {
                queue.poll();
            }
        }
    }
}
