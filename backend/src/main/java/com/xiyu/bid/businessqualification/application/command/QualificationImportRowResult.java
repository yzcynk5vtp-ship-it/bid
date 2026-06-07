package com.xiyu.bid.businessqualification.application.command;

import lombok.Value;

/**
 * §4.1.3.4 资质批量导入单行结果
 *
 * 用于返回每行的成功 / 失败状态及行级错误信息。
 * - success=true 时携带 QualificationUpsertCommand 供调用方实际入库
 * - success=false 时携带行级失败原因
 */
@Value
public class QualificationImportRowResult {

    int rowNumber;
    String certificateNo;
    boolean success;
    String failureReason;
    QualificationUpsertCommand command;

    public static QualificationImportRowResult success(int rowNumber, String certificateNo, QualificationUpsertCommand command) {
        return new QualificationImportRowResult(rowNumber, certificateNo, true, null, command);
    }

    public static QualificationImportRowResult failure(int rowNumber, String certificateNo, String reason) {
        return new QualificationImportRowResult(rowNumber, certificateNo, false, reason, null);
    }
}
