package com.craftmend.openaudiomc.services.authentication;

import com.craftmend.openaudiomc.OpenAudioMc;
import com.craftmend.openaudiomc.services.authentication.objects.Key;
import com.craftmend.openaudiomc.services.authentication.objects.RequestResponse;
import com.craftmend.openaudiomc.services.authentication.objects.ServerKeySet;
import lombok.Getter;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class AuthenticationService {

    @Getter
    private ServerKeySet serverKeySet = new ServerKeySet();

    public AuthenticationService() {
        System.out.println(OpenAudioMc.getLOG_PREFIX() + "Starting authentication module");
        loadData();
    }

    private void loadData() {
        if (!OpenAudioMc.getInstance().getConfigurationModule().getDataConfig().getString("keyset.private").equals("not-set")) {
            serverKeySet.setPrivateKey(new Key(OpenAudioMc.getInstance().getConfigurationModule().getDataConfig().getString("keyset.private")));
            serverKeySet.setPublicKey(new Key(OpenAudioMc.getInstance().getConfigurationModule().getDataConfig().getString("keyset.public")));
            return;
        }

        //setup process
        try {
            RequestResponse requestResponse = OpenAudioMc.getGson().fromJson(readHttp(OpenAudioMc.getInstance().getConfigurationModule().getServer() + "/genid"), RequestResponse.class);

            if (!requestResponse.isSuccess()) {
                System.out.println(OpenAudioMc.getLOG_PREFIX() + "Failed to request token.");
                return;
            }

            serverKeySet.setPrivateKey(new Key(requestResponse.getPrivateKey().toString()));
            serverKeySet.setPublicKey(new Key(requestResponse.getPublicKey().toString()));
            OpenAudioMc.getInstance().getConfigurationModule().getDataConfig().set("keyset.private", serverKeySet.getPrivateKey().getValue());
            OpenAudioMc.getInstance().getConfigurationModule().getDataConfig().set("keyset.public", serverKeySet.getPublicKey().getValue());
        } catch (IOException exception) {
            System.out.println(OpenAudioMc.getLOG_PREFIX() + "Failed to request token.");
            exception.printStackTrace();
        }
    }

    private String readHttp(String url) throws IOException {
        try (Scanner scanner = new Scanner(new URL(url).openStream(),
                StandardCharsets.UTF_8.toString())) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

}
