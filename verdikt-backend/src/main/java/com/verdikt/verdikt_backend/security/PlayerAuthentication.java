package com.verdikt.verdikt_backend.security;

import com.verdikt.verdikt_backend.model.Player;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

public class PlayerAuthentication implements Authentication {

    private final Player player;
    private boolean authenticated = true;

    public PlayerAuthentication(Player player) {
        this.player = player;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Player getDetails() {
        return player;
    }

    @Override
    public Object getPrincipal() {
        return player;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return player != null ? player.getName() : null;
    }

    public Player getPlayer() {
        return player;
    }
}
