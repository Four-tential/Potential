package four_tential.potential.presentation.review.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

import java.util.List;

@Getter
public class ReviewUpdateRequest {

    @Min(value = 1, message = "별점은 1점 이상이어야 합니다")
    @Max(value = 5, message = "별점은 5점 이하이어야 합니다")
    private int rating;

    @NotBlank(message = "후기 내용은 필수입니다")
    private String content;

    private List<String> imageUrls;
}

