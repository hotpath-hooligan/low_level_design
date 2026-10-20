package entities;

public record Payment(User from, User to, Money amount) {}
