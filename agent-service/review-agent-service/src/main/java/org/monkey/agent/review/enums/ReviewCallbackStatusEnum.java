package org.monkey.agent.review.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * '回调状态：0=待回调,1=成功,2=失败,3=放弃'
 */

@Getter
@AllArgsConstructor
public enum ReviewCallbackStatusEnum {
    PENDING(0, "待回调"),
    SUCCESS(1, "成功"),
    FAILURE(2, "失败"),
    ABANDONED(3, "放弃");

    private final int code;
    private final String desc;
}
