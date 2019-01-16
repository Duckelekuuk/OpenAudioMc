package com.craftmend.openaudiomc.services.authentication.objects;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
public class RequestResponse {

    private boolean success;
    private UUID publicKey;
    private UUID privateKey;

}
