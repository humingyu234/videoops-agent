package org.dromara.aivideo.workflow.mapper;

import org.apache.ibatis.annotations.Select;
import org.dromara.aivideo.workflow.domain.DiscoveryTag;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;

public interface DiscoveryTagMapper extends BaseMapperPlus<DiscoveryTag, DiscoveryTag> {

    @Select("""
        SELECT tag_id, name, create_time, update_time
        FROM av_discovery_tag
        ORDER BY tag_id ASC
        """)
    List<DiscoveryTag> selectCatalogTags();
}
