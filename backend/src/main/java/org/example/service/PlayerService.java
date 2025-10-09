package org.example.service;

import org.example.entity.PlayerEntity;
import org.example.entity.RoomEntity;

import java.util.List;
import java.util.Optional;

public interface PlayerService {

    PlayerEntity createPlayer(String playerId, String playerName);

    Optional<PlayerEntity> getPlayerByPlayerId(String playerId);

    List<PlayerEntity> getPlayersByRoom(RoomEntity room);

    PlayerEntity updatePlayerReady(String playerId, boolean ready);

    void deletePlayer(String playerId);

    List<PlayerEntity> getAllPlayers();
}