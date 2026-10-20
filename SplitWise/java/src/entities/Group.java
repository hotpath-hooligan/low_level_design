package entities;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Group {
    private final long id;
    private final String name;
    private final Set<User> members = ConcurrentHashMap.newKeySet();

    public Group(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public Set<User> getMembers() {
        return Set.copyOf(members);
    }

    public void addMember(User user) {
        if (!members.add(user)) {
            throw new IllegalStateException(user + " is already a member of group " + name);
        }
    }
}
