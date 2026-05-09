package four_tential.potential.infra.web;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.MemberExceptionEnum;
import four_tential.potential.domain.member.social.SocialProvider;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class SocialProviderConverter implements Converter<String, SocialProvider> {

    @Override
    public SocialProvider convert(String source) {
        if (source == null || source.isBlank()) {
            throw new ServiceErrorException(MemberExceptionEnum.ERR_SOCIAL_PROVIDER_NOT_SUPPORTED);
        }
        try {
            return SocialProvider.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ServiceErrorException(MemberExceptionEnum.ERR_SOCIAL_PROVIDER_NOT_SUPPORTED);
        }
    }
}
