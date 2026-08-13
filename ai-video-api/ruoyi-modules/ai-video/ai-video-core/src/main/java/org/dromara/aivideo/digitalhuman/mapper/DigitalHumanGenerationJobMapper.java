package org.dromara.aivideo.digitalhuman.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanGenerationJob;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobType;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

@InterceptorIgnore(tenantLine = "true")
public interface DigitalHumanGenerationJobMapper
    extends BaseMapperPlus<DigitalHumanGenerationJob, DigitalHumanGenerationJob> {

    @Select("""
        SELECT * FROM av_dh_generation_job
        WHERE id = #{id} AND tenant_id = #{tenantId} AND owner_user_id = #{ownerUserId}
        """)
    DigitalHumanGenerationJob selectOwnedById(@Param("id") Long id,
                                               @Param("tenantId") Long tenantId,
                                               @Param("ownerUserId") Long ownerUserId);

    @Select("""
        SELECT * FROM av_dh_generation_job
        WHERE tenant_id = #{tenantId} AND owner_user_id = #{ownerUserId}
          AND job_type = #{jobType} AND idempotency_key = #{idempotencyKey}
        LIMIT 1
        """)
    DigitalHumanGenerationJob selectByIdempotency(@Param("tenantId") Long tenantId,
                                                   @Param("ownerUserId") Long ownerUserId,
                                                   @Param("jobType") DigitalHumanJobType jobType,
                                                   @Param("idempotencyKey") String idempotencyKey);
}
