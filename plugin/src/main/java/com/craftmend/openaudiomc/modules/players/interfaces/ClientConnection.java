package com.craftmend.openaudiomc.modules.players.interfaces;

import com.craftmend.openaudiomc.modules.media.objects.Media;

import java.util.List;

public interface ClientConnection {

    boolean isConnected();
    String getPin();
    List<Media> getOngoingMedia();
    void playMedia(Media media);
    void setVolume(int volume);

}
