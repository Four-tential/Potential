package four_tential.potential.application.auth;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.member.social.SocialProvider;
import lombok.Getter;

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_SOCIAL_EMAIL_CONFLICT;

@Getter
public class SocialEmailConflictException extends ServiceErrorException {

    private final String challengeToken;
    private final String conflictEmail;
    private final SocialProvider provider;

    public SocialEmailConflictException(String challengeToken, String conflictEmail, SocialProvider provider) {
        super(ERR_SOCIAL_EMAIL_CONFLICT);
        this.challengeToken = challengeToken;
        this.conflictEmail = conflictEmail;
        this.provider = provider;
    }
}
