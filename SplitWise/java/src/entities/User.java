package entities;

import java.util.Comparator;

public record User(long id, String name) {
    public static final Comparator<User> BY_ID = Comparator.comparingLong(User::id);
}
