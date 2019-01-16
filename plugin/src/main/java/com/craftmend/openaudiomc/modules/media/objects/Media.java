package com.craftmend.openaudiomc.modules.media.objects;

import com.craftmend.openaudiomc.modules.media.enums.MediaFlag;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
public class Media {

    //media tracker
    @Setter @Getter private String mediaId = UUID.randomUUID().toString();

    //media information
    private String source;
    private int startInstant;
    @Getter private transient int keepTimeout = -1;
    @Getter @Setter private boolean doPickup = true;
    @Getter @Setter private boolean loop = false;
    @Getter @Setter private boolean autoPlay = true;
    @Getter @Setter private int fadeTime = 0;
    @Getter @Setter private MediaFlag flag = MediaFlag.DEFAULT;

    public Media(String source) {
        this.source = source;
        this.startInstant = (int) (System.currentTimeMillis() / 1000L);
    }

    public Media applySettings(MediaOptions options) {
        this.loop = options.isLoop();
        this.keepTimeout = options.getExpirationTimeout();
        this.autoPlay = options.isAutoPlay();
        if (options.getId() != null) this.mediaId = options.getId();
        this.doPickup = options.isPickUp();
        this.setFadeTime(options.getFadeTime());
        return this;
    }

}
