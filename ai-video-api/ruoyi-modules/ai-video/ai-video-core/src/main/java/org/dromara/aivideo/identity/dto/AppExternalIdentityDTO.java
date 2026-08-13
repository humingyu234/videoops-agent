package org.dromara.aivideo.identity.dto;

/**
 * 已验证的创作端外部身份。
 *
 * @param provider 外部来源稳定键
 * @param providerSubject 外部来源中的主体稳定编号
 */
public record AppExternalIdentityDTO(String provider, String providerSubject) {

    public AppExternalIdentityDTO {
        if (isBlank(provider) || isBlank(providerSubject)) {
            throw new IllegalArgumentException("外部身份结果无效");
        }
    }

    @Override
    public String toString() {
        return "AppExternalIdentityResult[provider=" + provider + ", providerSubject=***]";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
