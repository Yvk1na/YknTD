package cn.ykntd.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Tracks the logical ownership of the fixed arena slots. A reservation counts
 * as in use so a re-entrant join cannot prepare the same Multiverse world.
 */
public final class DedicatedArenaPool<T> {
    private final int capacity;
    private final Map<Integer, T> active = new LinkedHashMap<>();
    private final Set<Integer> reservations = new LinkedHashSet<>();

    public DedicatedArenaPool(int capacity) {
        if (capacity < 1 || capacity > 10) {
            throw new IllegalArgumentException("实例容量必须在 1～10 之间");
        }
        this.capacity = capacity;
    }

    public OptionalInt reserveFirstAvailable() {
        return reserveFirstAvailable(slot -> true);
    }

    public OptionalInt reserveFirstAvailable(IntPredicate eligible) {
        Objects.requireNonNull(eligible, "eligible");
        for (int slot = 1; slot <= capacity; slot++) {
            if (!active.containsKey(slot) && !reservations.contains(slot) && eligible.test(slot)) {
                reservations.add(slot);
                return OptionalInt.of(slot);
            }
        }
        return OptionalInt.empty();
    }

    public boolean reserve(int slot) {
        validateSlot(slot);
        if (active.containsKey(slot) || reservations.contains(slot)) return false;
        reservations.add(slot);
        return true;
    }

    public boolean activate(int slot, T value) {
        validateSlot(slot);
        Objects.requireNonNull(value, "value");
        if (!reservations.remove(slot) || active.containsKey(slot)) return false;
        active.put(slot, value);
        return true;
    }

    public void cancelReservation(int slot) {
        validateSlot(slot);
        reservations.remove(slot);
    }

    public boolean release(int slot, T expected) {
        validateSlot(slot);
        if (active.get(slot) != expected) return false;
        active.remove(slot);
        return true;
    }

    public Optional<T> active(int slot) {
        validateSlot(slot);
        return Optional.ofNullable(active.get(slot));
    }

    public List<Slot<T>> activeSlots() {
        List<Slot<T>> result = new ArrayList<>(active.size());
        active.forEach((slot, value) -> result.add(new Slot<>(slot, value)));
        return List.copyOf(result);
    }

    public boolean inUse(int slot) {
        validateSlot(slot);
        return active.containsKey(slot) || reservations.contains(slot);
    }

    public int activeCount() {
        return active.size();
    }

    public int reservedCount() {
        return reservations.size();
    }

    public int capacity() {
        return capacity;
    }

    public void clear() {
        active.clear();
        reservations.clear();
    }

    private void validateSlot(int slot) {
        if (slot < 1 || slot > capacity) {
            throw new IllegalArgumentException("实例编号必须在 1～" + capacity + " 之间");
        }
    }

    public record Slot<T>(int number, T value) {
    }
}
