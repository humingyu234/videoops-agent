package org.dromara.aivideo.workflow.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.dromara.aivideo.workflow.domain.DiscoveryCategory;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;

public interface DiscoveryCategoryMapper extends BaseMapperPlus<DiscoveryCategory, DiscoveryCategory> {

    @Select("""
        SELECT CAST(dict_value AS UNSIGNED) AS category_id, dict_label AS name,
               dict_sort AS sort_order, 'active' AS status, create_time, update_time
        FROM sys_dict_data
        WHERE dict_type = 'aivideo_discovery_category'
          AND dict_value REGEXP '^[1-9][0-9]{0,18}$'
        ORDER BY dict_sort ASC, CAST(dict_value AS UNSIGNED) ASC
        """)
    List<DiscoveryCategory> selectActiveOrdered();

    @Select("""
        SELECT CAST(dict_value AS UNSIGNED) AS category_id, dict_label AS name,
               dict_sort AS sort_order, 'active' AS status, create_time, update_time
        FROM sys_dict_data
        WHERE dict_type = 'aivideo_discovery_category'
          AND dict_value = CAST(#{categoryId} AS CHAR)
        """)
    DiscoveryCategory selectActiveById(@Param("categoryId") long categoryId);
}
