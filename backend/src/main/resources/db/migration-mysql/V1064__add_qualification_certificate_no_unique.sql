-- PR #336: 为 certificate_no 添加唯一约束，防止并发重复
-- 先处理现有空值（空字符串转为 NULL 以避免唯一约束冲突）
UPDATE business_qualifications
SET certificate_no = NULL
WHERE certificate_no = '';

-- 处理重复值：保留 id 最小的一条，其余置 NULL
UPDATE business_qualifications b1
JOIN (
    SELECT certificate_no, MIN(id) AS min_id
    FROM business_qualifications
    WHERE certificate_no IS NOT NULL
    GROUP BY certificate_no
    HAVING COUNT(*) > 1
) b2 ON b1.certificate_no = b2.certificate_no AND b1.id > b2.min_id
SET b1.certificate_no = NULL;

-- 添加唯一约束
ALTER TABLE business_qualifications
ADD UNIQUE INDEX uk_certificate_no (certificate_no);
