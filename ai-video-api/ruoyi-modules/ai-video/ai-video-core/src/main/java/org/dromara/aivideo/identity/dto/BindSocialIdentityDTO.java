package org.dromara.aivideo.identity.dto;

/** 绑定第三方身份的数据契约。 */
public record BindSocialIdentityDTO(long userId, String provider, String providerSubject,
                                    long expectedIdentityRevision) {
}
