package services;

import entities.User;
import repositories.Repository;

public class UserService {
    private final Repository<User> users = new Repository<>();

    public User createUser(String name) {
        return users.create(id -> new User(id, name));
    }

    public User get(long userId) {
        return users.get(userId);
    }
}
