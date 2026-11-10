package com.zhishuiyun.water.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PhotoConfirmRequest(
        @JsonDeserialize(using = PhotoDecimalStringDeserializer.class)
        @NotBlank @Pattern(regexp = "^[0-9]{1,9}(\\.[0-9]{1,3})?$", message = "读数须为最多9位整数和3位小数的非负十进制字符串") String reading,
        @JsonDeserialize(using = PhotoDecimalStringDeserializer.class)
        @NotBlank @Pattern(regexp = "^[0-9]{1,9}(\\.[0-9]{1,3})?$", message = "上次读数格式错误，请刷新照片详情") String expectedPreviousReading,
        @Size(max = 2000) String note) {}
