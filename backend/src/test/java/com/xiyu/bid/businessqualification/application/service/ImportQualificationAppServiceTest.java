package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.businessqualification.application.command.QualificationImportRowResult;
import com.xiyu.bid.businessqualification.application.command.QualificationUpsertCommand;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * §4.1.3.4 ImportQualificationAppService 单元测试
 * 覆盖：parse 跳过表头 / 必填校验 / 联系方式正则 / 日期顺序 / 附件命名 / 证书编号查重 / 空文件
 */
@ExtendWith(MockitoExtension.class)
class ImportQualificationAppServiceTest {

    @Mock private CreateQualificationAppService createQualificationAppService;
    @Mock private BusinessQualificationJpaRepository qualificationJpaRepository;

    @InjectMocks private ImportQualificationAppService importService;

    private static final String[] HEADERS = {
            "证书名称", "等级", "认证机构", "证书编号", "发证日期", "证书有效期",
            "代理机构", "代理联系方式", "认证范围", "证书审核提醒", "附件文件名"
    };

    /** 构造包含表头 + 数据行的最小 xlsx，返回 multipart file */
    private MultipartFile buildExcel(String[][] body) throws Exception {
        try (var wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("资质证书");
            Row hr = sh.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) hr.createCell(i).setCellValue(HEADERS[i]);
            for (int r = 0; r < body.length; r++) {
                Row row = sh.createRow(r + 1);
                for (int c = 0; c < HEADERS.length; c++) {
                    row.createCell(c).setCellValue(body[r][c] == null ? "" : body[r][c]);
                }
            }
            wb.write(out);
            return new MockMultipartFile("file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    @Test
    void importFromExcel_OnlyHeader_ShouldReturnEmpty() throws Exception {
        MultipartFile file = buildExcel(new String[0][]);

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.total()).isZero();
        assertThat(summary.success()).isZero();
        assertThat(summary.failed()).isZero();
        assertThat(summary.results()).isEmpty();
        verify(createQualificationAppService, never()).create(any());
    }

    @Test
    void importFromExcel_OneValidRow_ShouldImport() throws Exception {
        String certNo = "IMP-VALID-1";
        MultipartFile file = buildExcel(new String[][]{{
                "E2E 导入测试", "FIRST", "中国计量认证中心", certNo,
                "2024-01-15", "2027-12-31", "代理A", "13800138000",
                "范围A", "提醒A", "QUAL_" + certNo + "_01_x.pdf"
        }});
        when(qualificationJpaRepository.existsByCertificateNo(certNo)).thenReturn(false);

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.total()).isEqualTo(1);
        assertThat(summary.success()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        verify(createQualificationAppService, times(1)).create(any(QualificationUpsertCommand.class));
    }

    @Test
    void importFromExcel_DuplicateCertificateNo_ShouldSkipRow() throws Exception {
        String certNo = "IMP-DUP-1";
        MultipartFile file = buildExcel(new String[][]{{
                "测试", "FIRST", "科技局", certNo, "2024-01-15", "2027-12-31",
                "代理A", "13800138000", "范围", "提醒", "QUAL_" + certNo + "_01_x.pdf"
        }});
        when(qualificationJpaRepository.existsByCertificateNo(certNo)).thenReturn(true);

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.total()).isEqualTo(1);
        assertThat(summary.success()).isZero();
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.results().get(0).getFailureReason()).contains("已存在");
        verify(createQualificationAppService, never()).create(any());
    }

    @Test
    void importFromExcel_MissingRequiredField_ShouldReportRowFailure() throws Exception {
        String certNo = "IMP-MISS-1";
        MultipartFile file = buildExcel(new String[][]{{
                "",                                // 证书名称空
                "FIRST", "科技局", certNo, "2024-01-15", "2027-12-31",
                "代理A", "13800138000", "范围", "提醒", "QUAL_" + certNo + "_01_x.pdf"
        }});

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.total()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.results().get(0).getFailureReason()).contains("不能为空");
        verify(createQualificationAppService, never()).create(any());
    }

    @Test
    void importFromExcel_InvalidContactFormat_ShouldReportRowFailure() throws Exception {
        String certNo = "IMP-CONT-1";
        MultipartFile file = buildExcel(new String[][]{{
                "测试", "FIRST", "科技局", certNo, "2024-01-15", "2027-12-31",
                "代理A", "123-not-phone", "范围", "提醒", "QUAL_" + certNo + "_01_x.pdf"
        }});

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.results().get(0).getFailureReason()).contains("格式");
        verify(createQualificationAppService, never()).create(any());
    }

    @Test
    void importFromExcel_ExpiryBeforeIssue_ShouldReportRowFailure() throws Exception {
        String certNo = "IMP-DATE-1";
        MultipartFile file = buildExcel(new String[][]{{
                "测试", "FIRST", "科技局", certNo, "2027-12-31", "2024-01-15",
                "代理A", "13800138000", "范围", "提醒", "QUAL_" + certNo + "_01_x.pdf"
        }});

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.results().get(0).getFailureReason()).contains("有效期");
        verify(createQualificationAppService, never()).create(any());
    }

    @Test
    void importFromExcel_InvalidAttachmentName_ShouldReportRowFailure() throws Exception {
        String certNo = "IMP-FILE-1";
        MultipartFile file = buildExcel(new String[][]{{
                "测试", "FIRST", "科技局", certNo, "2024-01-15", "2027-12-31",
                "代理A", "13800138000", "范围", "提醒", "wrong_filename.pdf"
        }});

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.results().get(0).getFailureReason()).contains("附件");
        verify(createQualificationAppService, never()).create(any());
    }

    @Test
    void importFromExcel_MixedRows_ShouldAggregateSuccessAndFailure() throws Exception {
        String validCert = "IMP-MIX-A";
        String badCert = "IMP-MIX-B";
        MultipartFile file = buildExcel(new String[][]{
                {
                        "合法行", "FIRST", "科技局", validCert, "2024-01-15", "2027-12-31",
                        "代理A", "13800138000", "范围", "提醒", "QUAL_" + validCert + "_01_x.pdf"
                },
                {
                        "", "FIRST", "科技局", badCert, "2024-01-15", "2027-12-31",
                        "代理A", "13800138000", "范围", "提醒", "QUAL_" + badCert + "_01_x.pdf"
                }
        });
        when(qualificationJpaRepository.existsByCertificateNo(validCert)).thenReturn(false);
        

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.success()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        List<QualificationImportRowResult> results = summary.results();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).isSuccess()).isTrue();
        assertThat(results.get(1).isSuccess()).isFalse();
    }

    @Test
    void importFromExcel_InvalidDateFormat_ShouldReportRowFailure() throws Exception {
        String certNo = "IMP-DT-1";
        MultipartFile file = buildExcel(new String[][]{{
                "测试", "FIRST", "科技局", certNo, "not-a-date", "2027-12-31",
                "代理A", "13800138000", "范围", "提醒", "QUAL_" + certNo + "_01_x.pdf"
        }});

        var summary = importService.importFromExcel(file, "tester");

        assertThat(summary.failed()).isEqualTo(1);
        verify(createQualificationAppService, never()).create(any());
    }
}
