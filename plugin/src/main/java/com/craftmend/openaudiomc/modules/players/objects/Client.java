package com.craftmend.openaudiomc.modules.players.objects;

import com.craftmend.openaudiomc.OpenAudioMc;
import com.craftmend.openaudiomc.modules.configuration.objects.ClientSettings;
import com.craftmend.openaudiomc.modules.media.objects.Media;
import com.craftmend.openaudiomc.modules.media.objects.MediaUpdate;
import com.craftmend.openaudiomc.modules.players.events.ClientConnectEvent;
import com.craftmend.openaudiomc.modules.players.events.ClientDisconnectEvent;
import com.craftmend.openaudiomc.modules.players.interfaces.ClientConnection;
import com.craftmend.openaudiomc.modules.regions.objects.IRegion;
import com.craftmend.openaudiomc.modules.speakers.objects.ApplicableSpeaker;
import com.craftmend.openaudiomc.services.networking.NetworkingService;
import com.craftmend.openaudiomc.services.networking.packets.*;
import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Client implements ClientConnection {

    //spigot
    @Getter private Player player;

    //socket
    @Getter private boolean isConnected = false;
    @Getter private String pin = "1234";

    //optional regions and speakers
    private List<IRegion> currentRegions = new ArrayList<>();
    private List<ApplicableSpeaker> currentSpeakers = new ArrayList<>();

    //ongoing sounds
    private List<Media> ongoingMedia = new ArrayList<>();

    //plugin data
    @Setter @Getter private String selectedSpeakerSource = null;


    public Client(Player player) {
        this.player = player;
        if (OpenAudioMc.getInstance().getConfig().getBoolean("options.send-on-join")) publishUrl();
    }

    public void publishUrl() {
        NetworkingService service = OpenAudioMc.getInstance().getNetworkingService();
        if (service.isConnecting()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', OpenAudioMc.getInstance().getConfig().getString("messages.api-starting-up")));
            return;
        }

        if (isConnected) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', OpenAudioMc.getInstance().getConfig().getString("messages.client-already-connected")));
            return;
        }

        try {
            OpenAudioMc.getInstance().getNetworkingService().connectIfDown();
        } catch (URISyntaxException exception) {
            player.sendMessage(OpenAudioMc.getLOG_PREFIX() + "Failed to execute goal.");
            exception.printStackTrace();
        }
        this.pin = UUID.randomUUID().toString().subSequence(0, 3).toString();
        TextComponent message = new TextComponent(ChatColor.translateAlternateColorCodes('&', OpenAudioMc.getInstance().getConfig().getString("messages.click-to-connect")));
        message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,
                OpenAudioMc.getInstance().getConfigurationModule().getDataConfig().getString("keyset.base-url") + new TokenFactory().build(this)));
        player.spigot().sendMessage(message);
    }

    public void onConnect() {
        this.isConnected = true;
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', OpenAudioMc.getInstance().getConfig().getString("messages.client-opened")));
        currentRegions.clear();
        currentSpeakers.clear();
        Bukkit.getScheduler().scheduleAsyncDelayedTask(OpenAudioMc.getInstance(), () -> {
            ongoingMedia.forEach(this::sendMedia);
            ClientSettings settings = new ClientSettings().load();
            if (!settings.equals(new ClientSettings())) {
                OpenAudioMc.getInstance().getNetworkingService().send(this, new PacketClientPushSettings(settings));
            }
        }, 20);
        Bukkit.getServer().getPluginManager().callEvent(new ClientConnectEvent(player, this));
    }

    public void onDisconnect() {
        this.isConnected = false;
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', OpenAudioMc.getInstance().getConfig().getString("messages.client-closed")));
        Bukkit.getServer().getPluginManager().callEvent(new ClientDisconnectEvent(player));
    }

    @Override
    public void setVolume(int volume) {
        if (volume < 0 || volume > 100) {
            throw new IllegalArgumentException("Volume must be between 0 and 100");
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', OpenAudioMc.getInstance().getConfig().getString("messages.client-volume-change").replaceAll("__amount__", volume + "")));
        OpenAudioMc.getInstance().getNetworkingService().send(this, new PacketClientSetVolume(volume));
    }

    public void onQuit() {
        kick();
    }

    private void kick() {
        OpenAudioMc.getInstance().getNetworkingService().send(this, new PacketSocketKickClient());
    }

    public void sendMedia(Media media) {
        if (media.getKeepTimeout() != -1 && !ongoingMedia.contains(media)) {
            ongoingMedia.add(media);
            Bukkit.getScheduler().scheduleAsyncDelayedTask(OpenAudioMc.getInstance(), () -> ongoingMedia.remove(media), 20 * media.getKeepTimeout());
        }
        if (isConnected) OpenAudioMc.getInstance().getNetworkingService().send(this, new PacketClientCreateMedia(media));
    }

    public void tickSpeakers() {
        List<ApplicableSpeaker> applicableSpeakers = new ArrayList<>(OpenAudioMc.getInstance().getSpeakerModule().getApplicableSpeakers(player.getLocation()));

        List<ApplicableSpeaker> enteredSpeakers = new ArrayList<>(applicableSpeakers);
        enteredSpeakers.removeIf(speaker -> containsSpeaker(currentSpeakers, speaker));

        List<ApplicableSpeaker> leftSpeakers = new ArrayList<>(currentSpeakers);
        leftSpeakers.removeIf(speaker -> containsSpeaker(applicableSpeakers, speaker));

        enteredSpeakers.forEach(entered -> {
            if (!isPlayingSpeaker(entered)) {
                OpenAudioMc.getInstance().getNetworkingService().send(this, new PacketClientCreateMedia(entered.getSpeaker().getMedia(), entered.getDistance(), entered.getSpeaker().getRadius()));
            }
        });

        currentSpeakers.forEach(current -> {
            if (containsSpeaker(applicableSpeakers, current)) {
                Optional<ApplicableSpeaker> selector = filterSpeaker(applicableSpeakers, current);
                if (selector.isPresent() && (current.getDistance() != selector.get().getDistance())) {
                    ApplicableSpeaker currentSelector = selector.get();
                    MediaUpdate mediaUpdate = new MediaUpdate(currentSelector.getDistance(), currentSelector.getSpeaker().getRadius(), 450, current.getSpeaker().getMedia().getMediaId());
                    OpenAudioMc.getInstance().getNetworkingService().send(this, new PacketClientUpdateMedia(mediaUpdate));
                }
            }
        });

        leftSpeakers.forEach(left -> OpenAudioMc.getInstance().getNetworkingService().send(this, new PacketClientDestroyMedia(left.getSpeaker().getMedia().getMediaId())));

        currentSpeakers = applicableSpeakers;
    }

    public void tickRegions() {
        if (OpenAudioMc.getInstance().getRegionModule() != null) {
            //regions are enabled
            List<IRegion> detectedRegions = OpenAudioMc.getInstance().getRegionModule().getRegions(player.getLocation());

            List<IRegion> enteredRegions = new ArrayList<>(detectedRegions);
            enteredRegions.removeIf(t -> containsRegion(currentRegions, t));

            List<IRegion> leftRegions = new ArrayList<>(currentRegions);
            leftRegions.removeIf(t -> containsRegion(detectedRegions, t));

            List<IRegion> takeOverMedia = new ArrayList<>();
            enteredRegions.forEach(entered -> {
                if (!isPlayingRegion(entered)) {
                    sendMedia(entered.getMedia());
                } else {
                    takeOverMedia.add(entered);
                }
            });

            leftRegions.stream()
                    .filter(exited -> !containsRegion(takeOverMedia, exited))
                    .forEach(exited -> OpenAudioMc.getInstance().getNetworkingService().send(this, new PacketClientDestroyMedia(exited.getMedia().getMediaId())));

            currentRegions = detectedRegions;
        }
    }

    private boolean isPlayingRegion(IRegion region) {
        return currentRegions.stream().anyMatch(currentRegion -> currentRegion.getMedia().getSource().equals(region.getMedia().getSource()));
    }

    private boolean isPlayingSpeaker(ApplicableSpeaker speaker) {
        return currentSpeakers.stream().anyMatch(currentSpeaker -> currentSpeaker.getSpeaker().getSource().equals(speaker.getSpeaker().getSource()));
    }

    private Optional<ApplicableSpeaker> filterSpeaker(List<ApplicableSpeaker> speakers, ApplicableSpeaker query) {
        return speakers.stream().filter(applicableSpeaker -> applicableSpeaker.getSpeaker() == query.getSpeaker()).findFirst();
    }

    private boolean containsSpeaker(List<ApplicableSpeaker> speakers, ApplicableSpeaker speaker) {
        return speakers.stream().anyMatch(currentSpeaker -> speaker.getSpeaker().getSource().equals(currentSpeaker.getSpeaker().getSource()));
    }

    private boolean containsRegion(List<IRegion> regions, IRegion query) {
        return regions.stream().anyMatch(region -> query.getMedia().getSource().equals(region.getMedia().getSource()));
    }

    @Override
    public boolean isConnected() {
        return this.isConnected;
    }

    @Override
    public List<Media> getOngoingMedia() {
        return this.ongoingMedia;
    }

    @Override
    public void playMedia(Media media) {
        sendMedia(media);
    }
}
