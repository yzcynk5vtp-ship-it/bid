package com.xiyu.bid.resources.repository;

import com.xiyu.bid.resources.entity.CaCertificatePlatformEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaCertificatePlatformRepository
        extends JpaRepository<CaCertificatePlatformEntity, Long> {

    List<CaCertificatePlatformEntity> findByCaCertificateId(Long caCertificateId);

    /**
     * CO-466: 批量查询多个 CA 关联的平台账号，用于借用申请列表 enrich caName。
     */
    List<CaCertificatePlatformEntity> findByCaCertificateIdIn(java.util.Collection<Long> caCertificateIds);

    void deleteByCaCertificateId(Long caCertificateId);
}
