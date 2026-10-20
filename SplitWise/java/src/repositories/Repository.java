package repositories;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;

public class Repository<T> {
    private final AtomicLong nextId = new AtomicLong();
    private final Map<Long, T> items = new ConcurrentHashMap<>();

    public T create(LongFunction<T> factory) {
        long id = nextId.incrementAndGet();
        T item = factory.apply(id);
        items.put(id, item);
        return item;
    }

    public T get(long id) {
        return items.get(id);
    }

    public List<T> getAll() {
        return List.copyOf(items.values());
    }
}
