package com.xiyu.bid.qualification.service;

import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.qualification.dto.BatchAttachResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchAttachmentServiceTest {

    @Mock
    private BusinessQualificationJpaRepository jpaRepository;

    private BatchAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new BatchAttachmentService(jpaRepository);
    }

    @Test
    void process_EmptyList_ShouldReturnEmptyResult() {
        BatchAttachResultDTO result = service.process(List.of());

        assertEquals(0, result.getTotal());
        assertEquals(0, result.getSuccess());
        assertEquals(0, result.getFailed());
        assertTrue(result.getMatched().isEmpty());
        assertTrue(result.getUnmatched().isEmpty());
    }

    @Test
    void process_InvalidFileName_ShouldReturnUnmatched() {
        MultipartFile file = new MockMultipartFile("file", "invalid.pdf", "application/pdf", new byte[]{1});

        BatchAttachResultDTO result = service.process(List.of(file));

        assertEquals(1, result.getTotal());
        assertEquals(0, result.getSuccess());
        assertEquals(1, result.getFailed());
        assertEquals(1, result.getUnmatched().size());
        assertEquals("invalid.pdf", result.getUnmatched().get(0).getFileName());
        assertTrue(result.getUnmatched().get(0).getReason().contains("格式不符"));
        verify(jpaRepository, never()).save(any());
    }

    @Test
    void process_CertificateNotFound_ShouldReturnUnmatched() {
        MultipartFile file = new MockMultipartFile("file", "QUAL_CERT001_1_test.pdf", "application/pdf", new byte[]{1});
        when(jpaRepository.findByCertificateNo("CERT001")).thenReturn(List.of());

        BatchAttachResultDTO result = service.process(List.of(file));

        assertEquals(1, result.getTotal());
        assertEquals(0, result.getSuccess());
        assertEquals(1, result.getFailed());
        assertTrue(result.getUnmatched().get(0).getReason().contains("不存在"));
    }

    @Test
    void process_ValidFile_ShouldMatchAndSave() {
        MultipartFile file = new MockMultipartFile("file", "QUAL_CERT001_1_test.pdf", "application/pdf", new byte[]{1});
        BusinessQualificationEntity entity = new BusinessQualificationEntity();
        entity.setId(1L);
        entity.setName("ISO认证");
        entity.setCertificateNo("CERT001");
        when(jpaRepository.findByCertificateNo("CERT001")).thenReturn(List.of(entity));

        BatchAttachResultDTO result = service.process(List.of(file));

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getSuccess());
        assertEquals(0, result.getFailed());
        assertEquals(1, result.getMatched().size());
        assertEquals("ISO认证", result.getMatched().get(0).getQualificationName());
        assertEquals("QUAL_CERT001_1_test.pdf", result.getMatched().get(0).getFileName());
        verify(jpaRepository).save(any(BusinessQualificationEntity.class));
    }

    @Test
    void process_MixedFiles_ShouldReturnCorrectResult() {
        MultipartFile valid = new MockMultipartFile("file", "QUAL_CERT001_1_test.pdf", "application/pdf", new byte[]{1});
        MultipartFile invalid = new MockMultipartFile("file", "bad.pdf", "application/pdf", new byte[]{1});
        MultipartFile notFound = new MockMultipartFile("file", "QUAL_CERT999_1_test.pdf", "application/pdf", new byte[]{1});

        BusinessQualificationEntity entity = new BusinessQualificationEntity();
        entity.setId(1L);
        entity.setName("ISO认证");
        entity.setCertificateNo("CERT001");
        when(jpaRepository.findByCertificateNo("CERT001")).thenReturn(List.of(entity));
        when(jpaRepository.findByCertificateNo("CERT999")).thenReturn(List.of());

        BatchAttachResultDTO result = service.process(List.of(valid, invalid, notFound));

        assertEquals(3, result.getTotal());
        assertEquals(1, result.getSuccess());
        assertEquals(2, result.getFailed());
        assertEquals(1, result.getMatched().size());
        assertEquals(2, result.getUnmatched().size());
    }
}
