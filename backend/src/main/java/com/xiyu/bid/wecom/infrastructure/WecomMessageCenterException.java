package com.xiyu.bid.wecom.infrastructure;

/**
 * 企微消息中心 HTTP 调用异常。
 */
public class WecomMessageCenterException extends RuntimeException {

    public WecomMessageCenterException(String message) {
        super(message);
    }

    public WecomMessageCenterException(String message, Throwable cause) {
        super(message, cause);
    }
}
