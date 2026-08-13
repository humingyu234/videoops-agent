package org.dromara.aivideo.user.discovery.domain.vo;

import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;

import java.util.List;
import java.util.Map;

/** 用户端发现首页。 */
public record DiscoveryHomeVo(
    List<BannerVo> banners,
    List<WorkflowTemplateCardVo> recommendations,
    List<ChannelVo> channels,
    List<CategoryVo> categories,
    List<TagVo> tags
) {
    public static DiscoveryHomeVo from(WorkflowTemplateDTOs.DiscoveryHome source) {
        return new DiscoveryHomeVo(
            List.of(),
            source.recommendations().stream().limit(6).map(WorkflowTemplateCardVo::from).toList(),
            source.channels().stream().map(channel -> new ChannelVo(
                channel.channel(), channel.label(), channel.description(), channel.templateCount())).toList(),
            source.categories().stream().map(category -> new CategoryVo(
                category.categoryCode(), category.label(), category.templateCount())).toList(),
            source.tags().stream().map(tag -> new TagVo(tag.tagCode(), tag.label())).toList()
        );
    }

    public record BannerVo(
        String bannerId,
        String title,
        String subtitle,
        Map<String, String> target,
        WorkflowTemplateCardVo.MediaVo media
    ) {
    }

    public record ChannelVo(String channel, String label, String description, String templateCount) {
    }

    public record CategoryVo(String categoryCode, String label, String templateCount) {
    }

    public record TagVo(String tagCode, String label) {
    }
}
