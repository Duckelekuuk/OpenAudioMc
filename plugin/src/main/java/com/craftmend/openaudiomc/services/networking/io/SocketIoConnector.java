package com.craftmend.openaudiomc.services.networking.io;

import com.craftmend.openaudiomc.OpenAudioMc;
import com.craftmend.openaudiomc.modules.players.objects.Client;
import com.craftmend.openaudiomc.services.networking.abstracts.AbstractPacket;
import com.craftmend.openaudiomc.services.networking.payloads.AcknowledgeClientPayload;
import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import lombok.Getter;
import okhttp3.OkHttpClient;
import org.bukkit.Bukkit;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

public class SocketIoConnector {

    private Socket socket;
    @Getter private boolean isConnected = false;
    @Getter private boolean isConnecting = false;
    private SSLHelper sslHelper;

    public SocketIoConnector() throws KeyManagementException, NoSuchAlgorithmException {
        sslHelper = new SSLHelper();
    }

    public void setupConnection() throws URISyntaxException {
        if (!canConnect()) return;
        System.out.println(OpenAudioMc.getLOG_PREFIX() + "Setting up Socket.IO connection.");

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .hostnameVerifier(sslHelper.getHostnameVerifier())
                .sslSocketFactory(sslHelper.getSslSocketFactory(), sslHelper.getTrustManager())
                .build();

        IO.Options opts = new IO.Options();
        opts.callFactory = okHttpClient;
        opts.webSocketFactory = okHttpClient;
        opts.query = "type=server&" +
                "secret=" + OpenAudioMc.getInstance().getAuthenticationService().getServerKeySet().getPrivateKey().getValue() + "&" +
                "public=" + OpenAudioMc.getInstance().getAuthenticationService().getServerKeySet().getPublicKey().getValue();

        socket = IO.socket(OpenAudioMc.getInstance().getConfigurationModule().getServer(), opts);

        isConnecting = true;
        registerEvents();
        socket.connect();
    }

    private void registerEvents() {
        socket.on(Socket.EVENT_CONNECT, args -> {
            //connected
            isConnected = true;
            isConnecting = false;
            System.out.println(OpenAudioMc.getLOG_PREFIX() + "Socket: Opened.");
        });

        socket.on(Socket.EVENT_DISCONNECT, args -> {
            //disconnected
            isConnected = false;
            isConnecting = false;
            System.out.println(OpenAudioMc.getLOG_PREFIX() + "Socket: closed.");
        });

        socket.on(Socket.EVENT_CONNECT_TIMEOUT, args -> {
            isConnecting = false;
        });

        socket.on("acknowledgeClient", args -> {
            AcknowledgeClientPayload payload = (AcknowledgeClientPayload) OpenAudioMc.getGson().fromJson(args[0].toString(), AbstractPacket.class).getData();
            Client client = OpenAudioMc.getInstance().getPlayerModule().getClient(payload.getUuid());

            Ack callback = (Ack) args[1];

            if (client == null) {
                callback.call(false);
            } else if (client.getPin().equals(payload.getToken())) {
                client.onConnect();
                callback.call(true);
            } else {
                callback.call(false);
            }
        });

        socket.on("data", args -> {
            AbstractPacket abstractPacket = OpenAudioMc.getGson().fromJson(args[0].toString(), AbstractPacket.class);
            OpenAudioMc.getInstance().getNetworkingService().triggerPacket(abstractPacket);
        });
    }

    private boolean canConnect() {
        return (!isConnecting && !isConnected);
    }

    public void send(Client client, AbstractPacket packet) {
        if (isConnected && client.isConnected()) {
            //check if the player is real, fake players aren't cool
            if (Bukkit.getPlayer(client.getPlayer().getUniqueId()) == null) return;
            packet.setClient(client.getPlayer().getUniqueId());
            socket.emit("data", OpenAudioMc.getGson().toJson(packet));
        }
    }
}