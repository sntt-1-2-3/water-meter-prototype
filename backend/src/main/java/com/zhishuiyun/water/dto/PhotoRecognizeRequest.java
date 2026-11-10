package com.zhishuiyun.water.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;

public record PhotoRecognizeRequest(
        @JsonDeserialize(using = PhotoIntegerDeserializer.class) Integer rotation,
        @Valid PhotoReadingDto.Crop crop) {}
