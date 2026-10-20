package services;

import entities.Group;
import entities.User;
import repositories.Repository;

import java.util.List;

public class GroupService {
    private final Repository<Group> groups = new Repository<>();

    public Group createGroup(String name) {
        return groups.create(id -> new Group(id, name));
    }

    public Group get(long groupId) {
        return groups.get(groupId);
    }

    public List<Group> groupsOf(User user) {
        return groups.getAll().stream().filter(group -> group.getMembers().contains(user)).toList();
    }

    public void addMember(Group group, User user) {
        group.addMember(user);
    }
}
