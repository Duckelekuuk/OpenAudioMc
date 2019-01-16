package com.craftmend.openaudiomc.modules.media.objects;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MediaOptions {

    private boolean loop = false;
    private String id;
    private int expirationTimeout = -1;
    private boolean autoPlay = true;
    private boolean pickUp = true;
    private int fadeTime = 0;

}
