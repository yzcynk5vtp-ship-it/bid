package com.xiyu.bid.tender.repository;

import com.xiyu.bid.tender.entity.TenderAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenderAttachmentRepository extends JpaRepository<TenderAttachment, Long> {

    List<TenderAttachment> findByTenderId(Long tenderId);

    void deleteByTenderId(Long tenderId);
}
